/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.handler;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.persistence.AfterCommitExecutor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleAudit;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleAuditRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
@Slf4j
@CommandType(entity = "RESCHEDULELOAN", action = "SUBMITFORAPPROVAL")
public class SubmitBulkRescheduleForApprovalCommandHandler implements NewCommandSourceHandler {

    private final PlatformSecurityContext platformSecurityContext;
    private final BulkRescheduleExecutionRepository executionRepository;
    private final OfficeHierarchyService officeHierarchyService;
    private final AppUserRepository appUserRepository;
    private final BulkRescheduleAuditRepository auditRepository;
    private final NotificationWritePlatformService notificationService;
    private final PlatformEmailService emailService;

    @Value("${mifos.system.base-url}")
    private String baseUrl;

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
        sendApproverEmailAfterCommit(execution, user, approver, submissionNote.trim());

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

    private void sendApproverEmailAfterCommit(final BulkRescheduleExecution execution, final AppUser initiator,
            final AppUser approver, final String reason) {
        if (StringUtils.isBlank(approver.getEmail())) {
            log.warn("Bulk reschedule request {} was submitted, but approver {} has no email address", execution.getId(),
                    approver.getId());
            return;
        }
        final String requestUrl = StringUtils.removeEnd(baseUrl, "/") + "/#/bulkreschedule/" + execution.getId();
        final String subject = "Bulk reschedule request #" + execution.getId() + " requires approval";
        final String body = "Dear " + HtmlUtils.htmlEscape(approver.getDisplayName()) + ",<br><br>"
                + "Bulk reschedule request <strong>#" + execution.getId() + "</strong> was submitted by "
                + HtmlUtils.htmlEscape(initiator.getDisplayName()) + ".<br>"
                + "Reason: " + HtmlUtils.htmlEscape(reason) + "<br><br>"
                + "Please <a href=\"" + HtmlUtils.htmlEscape(requestUrl) + "\">review the request</a> and approve or reject it.<br><br>"
                + "Kind regards.";
        final EmailDetail email = new EmailDetail(subject, body, approver.getEmail(), approver.getDisplayName());
        AfterCommitExecutor.execute(() -> {
            try {
                emailService.sendDefinedEmail(email);
            } catch (RuntimeException e) {
                log.error("Bulk reschedule approval email could not be sent for request {} to {}. Check SMTP configuration.",
                        execution.getId(), approver.getEmail(), e);
            }
        });
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
