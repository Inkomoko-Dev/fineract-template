package org.apache.fineract.portfolio.loanaccount.bulkreschedule.handler;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleAudit;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleAuditRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@CommandType(entity = "RESCHEDULELOAN", action = "SUBMITFORAPPROVAL")
public class SubmitBulkRescheduleForApprovalCommandHandler implements NewCommandSourceHandler {

    private final PlatformSecurityContext platformSecurityContext;
    private final BulkRescheduleExecutionRepository executionRepository;
    private final OfficeHierarchyService officeHierarchyService;
    private final AppUserRepository appUserRepository;
    private final BulkRescheduleAuditRepository auditRepository;
    private final NotificationWritePlatformService notificationService;

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {

        final var user = platformSecurityContext.authenticatedUser();

        final Long executionId = command.entityId();

        final BulkRescheduleExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bulk reschedule execution not found: " + executionId));
        if (!officeHierarchyService.validateUserAccessToOffice(user, execution.getOfficeId())) {
            throw new IllegalStateException("User does not have access to bulk reschedule execution " + executionId);
        }
        if (!execution.getUser().getId().equals(user.getId()) && !hasCreatePermission(user)) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.submit.owner.denied",
                    "Only the request creator or a user with create permission can submit this preview for approval");
        }

        /*
         * Only a successfully generated preview can be submitted.
         *
         * This also prevents:
         * - duplicate submissions
         * - rejected requests being resubmitted accidentally
         * - completed executions being submitted
         * - failed executions being submitted
         */
        if (execution.getStatus() != BulkRescheduleExecutionStatus.PREVIEW) {
            throw new IllegalStateException(
                    "Bulk reschedule execution " + executionId
                            + " cannot be submitted for approval from status "
                            + execution.getStatus());
        }

        final Long approverId = command.longValueOfParameterNamed("approverId");
        if (approverId == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approver.required",
                    "An approver must be selected");
        }
        final AppUser approver = appUserRepository.findById(approverId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approver.not.found",
                        "Selected approver was not found"));
        if (!approver.isEnabled() || approver.isDeleted()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approver.inactive",
                    "Selected approver is not active");
        }
        if (approver.getId().equals(user.getId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.maker.checker.required",
                    "The request creator cannot approve their own request");
        }
        try {
            approver.validateHasPermissionTo("APPROVE_RESCHEDULELOAN");
        } catch (Exception e) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approver.permission.denied",
                    "Selected user does not have bulk reschedule approval permission");
        }
        if (!officeHierarchyService.validateUserAccessToOffice(approver, execution.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approver.office.denied",
                    "Selected approver cannot access the request office");
        }

        final String submissionNote = command.stringValueOfParameterNamedAllowingNull("submissionNote");
        if (submissionNote == null || submissionNote.isBlank()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.submission.note.required",
                    "A reason for the bulk reschedule is required");
        }

        /*
         * The preview already contains the calculated loan results.
         * Do NOT rerun the dry-run here.
         *
         * We are only moving the request into the approval workflow.
         */
        execution.setStatus(BulkRescheduleExecutionStatus.PENDING_APPROVAL);
        execution.setApprover(approver);
        execution.setSubmissionNote(submissionNote.trim());
        execution.setSubmittedAt(DateUtils.getLocalDateTimeOfSystem());
        execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());

        executionRepository.save(execution);

        final BulkRescheduleAudit audit = new BulkRescheduleAudit();
        audit.setExecution(execution);
        audit.setAction(BulkRescheduleAudit.BulkRescheduleAuditAction.SUBMIT_FOR_APPROVAL);
        audit.setActor(user);
        audit.setTimestamp(DateUtils.getLocalDateTimeOfSystem());
        audit.setDetailsJson(submissionNote.trim());
        auditRepository.save(audit);
        notificationService.notify(approver.getId(), "BULK_RESCHEDULE", execution.getId(), "SUBMIT_FOR_APPROVAL", user.getId(),
                "Bulk reschedule request #" + execution.getId() + " requires your approval. Reason: " + submissionNote.trim(), false);

        final Map<String, Object> changes = new HashMap<>();
        changes.put("status", execution.getStatus().name());
        changes.put("executionId", execution.getId());
        changes.put("approverId", approver.getId());

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(execution.getId())
                .withOfficeId(execution.getOfficeId())
                .with(changes)
                .build();
    }

    private boolean hasCreatePermission(final AppUser user) {
        try {
            user.validateHasPermissionTo("CREATE_RESCHEDULELOAN");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
