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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleLoansApiConstants;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleAudit;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleAuditRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing approval and rejection of bulk loan reschedule executions. Handles approval
 * workflow, validation of approver roles, and audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class  BulkRescheduleApprovalService {

    private final BulkRescheduleExecutionRepository bulkRescheduleExecutionRepository;
    private final BulkRescheduleAuditRepository bulkRescheduleAuditRepository;
    private final BulkRescheduleResultRepository bulkRescheduleResultRepository;
    private final PlatformSecurityContext platformSecurityContext;
    private final OfficeHierarchyService officeHierarchyService;
    private final NotificationWritePlatformService notificationService;

    /** Persists approval; the command handler starts execution after this transaction commits. */
    @Transactional
    public CommandProcessingResult approveExecution(JsonCommand jsonCommand) {
        final Long executionId = jsonCommand.entityId();
        if (executionId == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.id.required",
                    "Execution ID is required");
        }
        final BulkRescheduleExecution execution = bulkRescheduleExecutionRepository.findById(executionId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                        "Execution not found with ID: " + executionId));
        if (execution.getStatus() != BulkRescheduleExecutionStatus.PENDING_APPROVAL) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.invalid.status",
                    "Only PENDING_APPROVAL executions can be approved. Current status: " + execution.getStatus());
        }

        final AppUser currentUser = platformSecurityContext.authenticatedUser();
        try {
            currentUser.validateHasPermissionTo("APPROVE_RESCHEDULELOAN");
        } catch (Exception e) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approval.permission.denied",
                    "User does not have permission to approve bulk reschedule operations");
        }
        if (!officeHierarchyService.validateUserAccessToOffice(currentUser, execution.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approval.office.denied",
                    "User does not have access to this bulk reschedule office");
        }
        validateAssignedApprover(execution, currentUser);
        if (bulkRescheduleResultRepository.findByExecution(execution).isEmpty()
                && execution.getTotalLoansFound() > 0) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.preview.required",
                    "A preview is required before approval");
        }

        final String approvalNote = jsonCommand.stringValueOfParameterNamedAllowingNull(
                BulkRescheduleLoansApiConstants.APPROVAL_NOTE_PARAM_NAME);
        if (approvalNote == null || approvalNote.isBlank()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approval.note.required",
                    "An approval reason is required");
        }
        final var now = DateUtils.getLocalDateTimeOfSystem();
        execution.setStatus(BulkRescheduleExecutionStatus.APPROVED);
        execution.setApprover(currentUser);
        execution.setApprovalNote(approvalNote.trim());
        execution.setApprovedAt(now);
        execution.setUpdatedAt(now);
        bulkRescheduleExecutionRepository.save(execution);
        logAudit(execution, BulkRescheduleAudit.BulkRescheduleAuditAction.APPROVE, currentUser, approvalNote);
        notificationService.notify(execution.getUser().getId(), "BULK_RESCHEDULE", execution.getId(), "APPROVE", currentUser.getId(),
                "Bulk reschedule request #" + execution.getId() + " was approved and execution has started.", false);

        return new CommandProcessingResultBuilder().withCommandId(jsonCommand.commandId()).withEntityId(execution.getId())
                .withOfficeId(execution.getOfficeId()).build();
    }

    /**
     * Rejects a bulk reschedule execution. Updates the execution status to REJECTED and logs
     * the rejection in the audit trail.
     *
     * @param jsonCommand  the ID of the execution to reject
     * @return
     * @throws GeneralPlatformDomainRuleException if user lacks approval permissions
     */
    @Transactional
    public CommandProcessingResult rejectExecution(JsonCommand jsonCommand) {
        final Long executionId = jsonCommand.entityId();
        final BulkRescheduleExecution execution = bulkRescheduleExecutionRepository.findById(executionId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                        "Execution not found with ID: " + executionId));
        if (execution.getStatus() != BulkRescheduleExecutionStatus.PENDING_APPROVAL) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.invalid.status",
                    "Only PENDING_APPROVAL executions can be rejected. Current status: " + execution.getStatus());
        }

        final AppUser currentUser = platformSecurityContext.authenticatedUser();
        currentUser.validateHasPermissionTo("APPROVE_RESCHEDULELOAN");
        if (!officeHierarchyService.validateUserAccessToOffice(currentUser, execution.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.rejection.office.denied",
                    "User does not have access to this bulk reschedule office");
        }
        validateAssignedApprover(execution, currentUser);
        final String rejectReason = jsonCommand.stringValueOfParameterNamedAllowingNull("rejectReason");
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.rejection.reason.required",
                    "A rejection reason is required");
        }

        final var now = DateUtils.getLocalDateTimeOfSystem();
        execution.setStatus(BulkRescheduleExecutionStatus.REJECTED);
        execution.setApprover(currentUser);
        execution.setApprovalNote(rejectReason.trim());
        execution.setApprovedAt(now);
        execution.setUpdatedAt(now);
        bulkRescheduleExecutionRepository.save(execution);
        logAudit(execution, BulkRescheduleAudit.BulkRescheduleAuditAction.REJECT, currentUser, rejectReason.trim());
        notificationService.notify(execution.getUser().getId(), "BULK_RESCHEDULE", execution.getId(), "REJECT", currentUser.getId(),
                "Bulk reschedule request #" + execution.getId() + " was rejected. Reason: " + rejectReason.trim(), false);

        return new CommandProcessingResultBuilder().withCommandId(jsonCommand.commandId()).withEntityId(execution.getId())
                .withOfficeId(execution.getOfficeId()).build();
    }

    private void logAudit(final BulkRescheduleExecution execution, final BulkRescheduleAudit.BulkRescheduleAuditAction action,
            final AppUser actor, final String details) {
        final BulkRescheduleAudit audit = new BulkRescheduleAudit();
        audit.setExecution(execution);
        audit.setAction(action);
        audit.setActor(actor);
        audit.setTimestamp(DateUtils.getLocalDateTimeOfSystem());
        audit.setDetailsJson(details);
        bulkRescheduleAuditRepository.save(audit);
    }

    private void validateAssignedApprover(final BulkRescheduleExecution execution, final AppUser currentUser) {
        if (execution.getApprover() == null || !execution.getApprover().getId().equals(currentUser.getId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approver.assignment.denied",
                    "This request is assigned to another approver");
        }
        if (execution.getUser().getId().equals(currentUser.getId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.maker.checker.required",
                    "The request creator cannot approve or reject their own request");
        }
    }

    /**
     * Validates that the current user has the role to approve bulk reschedule operations.
     *
     * @param user the user to validate
     * @return true if user can approve, false otherwise
     */
//    private boolean lacksApprovalPermission(final AppUser user) {
//        if (user == null) {
//            return true;
//        }
//
//        log.debug("Validating approval role for user {}", user.getId());
//
//        // Check if user has MANAGER or HEAD_OFFICE_ADMIN role
//        try {
//            user.validateHasPermissionTo("APPROVE_RESCHEDULELOAN");
//            return false;
//        } catch (Exception e) {
//            return true;
//        }
//    }

    // Persists a single audit row for approval or rejection actions.
//    private void logAudit(final BulkRescheduleExecution execution, final String action, final AppUser user, final String note) {
//        log.debug("Logging audit event for execution {}: {}", execution.getId(), action);
//
//        BulkRescheduleAudit audit = new BulkRescheduleAudit();
//        audit.setExecution(execution);
//        audit.setAction(BulkRescheduleAudit.BulkRescheduleAuditAction.valueOf(action));
//        audit.setActor(user);
//        audit.setTimestamp(DateUtils.getLocalDateTimeOfSystem());
//        audit.setDetailsJson(note);
//
//        bulkRescheduleAuditRepository.save(audit);
//    }

}
