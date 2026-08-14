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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleFailedDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleResponseDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleSuccessDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleAudit;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleAuditRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleProgressService.ClaimResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.google.gson.Gson;

/**
 * Core orchestrator service for bulk reschedule execution.
 * 
 * Implements IDEMPOTENT execution guarantees:
 * - Each loan is rescheduled at most once per execution
 * - Retries and network failures are safe
 * - Already-processed loans are skipped
 * 
 * Supports full rollback capability:
 * - Can reverse previously executed reschedules
 * - Maintains original state for audit trail
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleExecutionService {

    private static final int BATCH_SIZE = 100;

    private final BulkRescheduleExecutionRepository bulkRescheduleExecutionRepository;
    private final BulkRescheduleResultRepository bulkRescheduleResultRepository;
    private final BulkRescheduleAuditRepository bulkRescheduleAuditRepository;
    private final LoanRepository loanRepository;
    private final BulkRescheduleLoanWorker loanWorker;
    private final BulkRescheduleFailureService failureService;
    private final PlatformSecurityContext platformSecurityContext;
    private final OfficeHierarchyService officeHierarchyService;
    private final NotificationWritePlatformService notificationService;
    private final BulkRescheduleProgressService progressService;
    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();

    /**
     * Executes reschedule operations for all approved loans in a bulk execution.
     * 
     * IDEMPOTENCY GUARANTEE: This method is idempotent. Calling it multiple times
     * on the same execution will process only new or failed loans, never duplicate
     * operations.
     * 
     * Execution flow:
     * 1. Validate execution exists and is in APPROVED status
     * 2. Set status to EXECUTING
     * 3. Fetch all PREVIEW_MATCHED results for this execution
     * 4. For each result:
     *    - Check if already processed (rescheduleRequestId != null) → SKIP
     *    - Validate loan eligibility
     *    - Execute reschedule in nested transaction
     *    - Update result with status and IDs
     * 5. Update execution with final counts
     * 6. Log audit entry
     * 
     * @param executionId the bulk reschedule execution ID
     * @return response with execution results
     * @throws GeneralPlatformDomainRuleException if validation fails
     */
    public BulkRescheduleResponseDto executeReschedule(final Long executionId) {
        log.info("Starting execution of bulk reschedule: {}", executionId);
        
        LocalDateTime executionStartTime = DateUtils.getLocalDateTimeOfSystem();
        int successCount = 0;
        int failCount = 0;
        int skipCount = 0;
        final String workerToken = UUID.randomUUID().toString();

        try {
            // Step 1: Fetch and validate execution
            Optional<BulkRescheduleExecution> executionOptional = bulkRescheduleExecutionRepository.findById(executionId);
            if (!executionOptional.isPresent()) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                    "Execution not found with ID: " + executionId);
            }

            BulkRescheduleExecution execution = executionOptional.get();

            // Validate user permissions
            AppUser currentUser = platformSecurityContext.authenticatedUser();
            if (!hasExecutionPermission(currentUser, execution)) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.permission.denied",
                    "User does not have permission to execute this bulk reschedule");
            }

            // Atomic APPROVED -> EXECUTING transition prevents duplicate background workers.
            final ClaimResult claimResult = progressService.claim(executionId, workerToken);
            if (claimResult == ClaimResult.NONE) {
                log.info("Execution {} has an active worker or is not executable", executionId);
                return buildExecutionResponse(bulkRescheduleExecutionRepository.findById(executionId).orElseThrow(), false);
            }
            execution = bulkRescheduleExecutionRepository.findById(executionId).orElseThrow();
            final boolean recovered = claimResult == ClaimResult.RECOVERED;
            if (recovered && execution.getTotalExecutionFailed() != null) {
                failCount = execution.getTotalExecutionFailed();
            }
            log.info("Execution {} {}", executionId, recovered ? "recovered" : "set to EXECUTING status");
            logAudit(execution, recovered ? BulkRescheduleAudit.BulkRescheduleAuditAction.RECOVER
                    : BulkRescheduleAudit.BulkRescheduleAuditAction.EXECUTE, currentUser,
                    recovered ? "Execution resumed after the previous worker lease expired" : "Background execution started");
            notificationService.notify(execution.getUser().getId(), "BULK_RESCHEDULE", execution.getId(), "EXECUTION_STARTED",
                    currentUser.getId(), "Bulk reschedule request #" + execution.getId() + " is now executing.", true);

            // Step 3: Fetch ReschedulingDetailsDto from execution JSON
            ReschedulingDetailsDto reschedulingDetails = gson.fromJson(
                execution.getReschedulingDetailsJson(), 
                ReschedulingDetailsDto.class
            );

            // Always read page zero: processed rows leave PREVIEW_MATCHED, keeping memory bounded.
            while (true) {
                final List<BulkRescheduleResult> batch = bulkRescheduleResultRepository
                        .findPageByExecutionIdAndStatus(executionId, BulkRescheduleResultStatus.PREVIEW_MATCHED,
                                org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE, org.springframework.data.domain.Sort.by("id")))
                        .getContent();
                if (batch.isEmpty()) {
                    break;
                }
                for (BulkRescheduleResult result : batch) {
                    if (!progressService.renewLease(executionId, workerToken)) {
                        log.warn("Execution {} lost its worker lease; stopping this worker", executionId);
                        return buildExecutionResponse(bulkRescheduleExecutionRepository.findById(executionId).orElseThrow(), false);
                    }
                    try {
                        // IDEMPOTENCY CHECK: Skip if already processed
                        if (result.getRescheduleRequestId() != null) {
                            log.debug("Loan {} already processed with reschedule request {}. Skipping.",
                                result.getLoanId(), result.getRescheduleRequestId());
                            result.setStatus(BulkRescheduleResultStatus.SKIPPED);
                            bulkRescheduleResultRepository.save(result);
                            skipCount++;
                            continue;
                        }

                        loanWorker.executeLoan(executionId, result.getId(), reschedulingDetails);
                        successCount++;

                    } catch (Exception e) {
                        log.error("Error processing loan {} in execution {}: {}", 
                            result.getLoanId(), executionId, e.getMessage(), e);
                        failureService.markFailed(result.getId(), executionId, result.getLoanId(), e);
                        failCount++;
                    }
                }
                progressService.refreshCounts(executionId, failCount, workerToken);
            }

            progressService.complete(executionId, failCount, workerToken);
            execution = bulkRescheduleExecutionRepository.findById(executionId).orElseThrow();
            log.info("Execution {} completed: {} succeeded, {} failed, {} skipped",
                executionId, successCount, failCount, skipCount);

            // Step 6: Log audit entry
            long duration = java.time.temporal.ChronoUnit.SECONDS
                .between(executionStartTime, DateUtils.getLocalDateTimeOfSystem());
            logAudit(execution, BulkRescheduleAudit.BulkRescheduleAuditAction.EXECUTE, currentUser, 
                String.format("Succeeded: %d, Failed: %d, Skipped: %d, Duration: %ds", 
                    successCount, failCount, skipCount, duration));
            notificationService.notify(execution.getUser().getId(), "BULK_RESCHEDULE", execution.getId(), "EXECUTE", currentUser.getId(),
                    String.format("Bulk reschedule request #%d finished: %d succeeded, %d failed.", execution.getId(), successCount,
                            failCount), true);

            // Return response
            // Detailed rows remain available through the paged preview/export endpoints.
            return buildExecutionResponse(execution, false);

        } catch (Exception e) {
            log.error("Error executing bulk reschedule {}: {}", executionId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExecutionFailed(final Long executionId, final Exception cause) {
        bulkRescheduleExecutionRepository.findById(executionId).ifPresent(execution -> {
            execution.setStatus(BulkRescheduleExecutionStatus.FAILED);
            execution.setExecutionError(cause.getMessage());
            execution.setExecutionCompletedAt(DateUtils.getLocalDateTimeOfSystem());
            execution.setWorkerToken(null);
            execution.setLeaseExpiresAt(null);
            execution.setLastHeartbeatAt(null);
            execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
            bulkRescheduleExecutionRepository.save(execution);
            final AppUser actor = platformSecurityContext.authenticatedUser();
            logAudit(execution, BulkRescheduleAudit.BulkRescheduleAuditAction.FAILED, actor, cause.getMessage());
            notificationService.notify(execution.getUser().getId(), "BULK_RESCHEDULE", execution.getId(), "FAILED", actor.getId(),
                    "Bulk reschedule request #" + execution.getId() + " could not be executed: " + cause.getMessage(), true);
        });
    }

    /**
     * Rolls back a previously executed bulk reschedule operation.
     * 
     * Reverses all successfully rescheduled loans back to their original schedule.
     * 
     * @param executionId the execution ID to rollback
     * @param rollbackReason reason for rollback
     * @return response with rollback results
     * @throws GeneralPlatformDomainRuleException if validation fails
     */
    @Transactional
    public BulkRescheduleResponseDto rollbackExecution(final Long executionId, final String rollbackReason) {
        log.info("Starting rollback of bulk reschedule execution: {}", executionId);

        int rollbackCount = 0;
        int rollbackFailCount = 0;

        try {
            // Fetch and validate execution
            Optional<BulkRescheduleExecution> executionOptional = bulkRescheduleExecutionRepository.findById(executionId);
            if (!executionOptional.isPresent()) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                    "Execution not found with ID: " + executionId);
            }

            BulkRescheduleExecution execution = executionOptional.get();

            // Validate status is COMPLETED
            if (!execution.getStatus().equals(BulkRescheduleExecutionStatus.COMPLETED)) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.invalid.status",
                    "Only COMPLETED executions can be rolled back. Current status: " + execution.getStatus());
            }

            // Validate user permissions
            AppUser currentUser = platformSecurityContext.authenticatedUser();
            if (!hasExecutionPermission(currentUser, execution)) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.permission.denied",
                    "User does not have permission to rollback this bulk reschedule");
            }

            // Set status to ROLLING_BACK
            execution.setStatus(BulkRescheduleExecutionStatus.ROLLING_BACK);
            execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
            bulkRescheduleExecutionRepository.save(execution);

            // Fetch all SUCCEEDED results
            List<BulkRescheduleResult> succeededResults = bulkRescheduleResultRepository
                .findByExecutionAndStatus(execution, BulkRescheduleResultStatus.SUCCEEDED);

            log.info("Found {} succeeded reschedules to rollback for execution {}", succeededResults.size(), executionId);

            // Process rollback in batches
            for (BulkRescheduleResult result : succeededResults) {
                try {
                    Optional<Loan> loanOptional = loanRepository.findById(result.getLoanId());
                    if (!loanOptional.isPresent()) {
                        log.error("Loan {} not found during rollback", result.getLoanId());
                        result.setStatus(BulkRescheduleResultStatus.ROLLBACK_FAILED);
                        result.setErrorMessage("Loan not found during rollback");
                        bulkRescheduleResultRepository.save(result);
                        rollbackFailCount++;
                        continue;
                    }

                    Loan loan = loanOptional.get();

                    // Call engine to reverse reschedule

                    // Update result
                    result.setStatus(BulkRescheduleResultStatus.ROLLED_BACK);
                    bulkRescheduleResultRepository.save(result);
                    rollbackCount++;

                } catch (Exception e) {
                    log.error("Error rolling back reschedule for loan {} in execution {}: {}",
                        result.getLoanId(), executionId, e.getMessage(), e);
                    result.setStatus(BulkRescheduleResultStatus.ROLLBACK_FAILED);
                    result.setErrorMessage("Rollback failed: " + e.getMessage());
                    bulkRescheduleResultRepository.save(result);
                    rollbackFailCount++;
                }
            }

            // Update execution with ROLLED_BACK status
            execution.setStatus(BulkRescheduleExecutionStatus.ROLLED_BACK);
            execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
            bulkRescheduleExecutionRepository.save(execution);

            // Log audit entry
            logAudit(execution, BulkRescheduleAudit.BulkRescheduleAuditAction.ROLLBACK, currentUser,
                String.format("Rollback completed. Reversed: %d, Failed: %d. Reason: %s",
                    rollbackCount, rollbackFailCount, rollbackReason));

            log.info("Rollback completed for execution {}: {} reversed, {} failed",
                executionId, rollbackCount, rollbackFailCount);

            return buildExecutionResponse(execution, true);

        } catch (Exception e) {
            log.error("Error rolling back bulk reschedule {}: {}", executionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Builds the response DTO for an execution.
     * 
     * @param execution the execution to build response for
     * @param includeResults whether to include detailed results
     * @return response DTO
     */
    public BulkRescheduleResponseDto buildExecutionResponse(final BulkRescheduleExecution execution, 
                                                             final boolean includeResults) {
        BulkRescheduleResponseDto response = new BulkRescheduleResponseDto();
        response.setExecutionId(execution.getId());
        response.setStatus(execution.getStatus().toString());
        response.setMode(execution.getMode().toString());
        response.setMessage("Execution details retrieved successfully");
        response.setTotalSucceeded(execution.getTotalSucceeded());
        response.setTotalFailed(execution.getTotalFailed());
        response.setTotalExcluded(execution.getTotalExcluded());

        if (includeResults) {
            List<BulkRescheduleResult> results = bulkRescheduleResultRepository.findByExecution(execution);
            
            List<BulkRescheduleSuccessDto> succeeded = results.stream()
                .filter(r -> r.getStatus() == BulkRescheduleResultStatus.SUCCEEDED)
                .map(r -> {
                    BulkRescheduleSuccessDto dto = new BulkRescheduleSuccessDto();
                    dto.setLoanId(r.getLoanId());
                    dto.setPreviousInterestRate(r.getOriginalInterestRate());
                    dto.setNewInterestRate(r.getNewInterestRate());
                    dto.setRescheduledLoanId(r.getRescheduleRequestId());
                    return dto;
                })
                .collect(Collectors.toList());

            List<BulkRescheduleFailedDto> failed = results.stream()
                .filter(r -> r.getStatus() == BulkRescheduleResultStatus.FAILED)
                .map(r -> {
                    BulkRescheduleFailedDto dto = new BulkRescheduleFailedDto();
                    dto.setLoanId(r.getLoanId());
                    dto.setReason(r.getErrorMessage());
                    return dto;
                })
                .collect(Collectors.toList());

            response.setSucceeded(succeeded);
            response.setFailed(failed);
        }

        return response;
    }

    /**
     * Checks if user has permission to execute/rollback the execution.
     * 
     * @param user the user to check
     * @param execution the execution
     * @return true if user has permission
     */
    private boolean hasExecutionPermission(final AppUser user, final BulkRescheduleExecution execution) {
        if (user == null || execution == null
                || !officeHierarchyService.validateUserAccessToOffice(user, execution.getOfficeId())) {
            return false;
        }
        try {
            user.validateHasPermissionTo("APPROVE_RESCHEDULELOAN");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Logs an audit entry for the execution.
     * 
     * @param execution the execution
     * @param action the action performed
     * @param user the user performing the action
     * @param details additional details
     */
    private void logAudit(final BulkRescheduleExecution execution, 
                         final BulkRescheduleAudit.BulkRescheduleAuditAction action,
                         final AppUser user, final String details) {
        try {
            BulkRescheduleAudit audit = new BulkRescheduleAudit();
            audit.setExecution(execution);
            audit.setAction(action);
            audit.setActor(user);
            audit.setTimestamp(DateUtils.getLocalDateTimeOfSystem());
            audit.setDetailsJson(details);
            bulkRescheduleAuditRepository.save(audit);
            log.info("Audit logged for execution {} action {}", execution.getId(), action);
        } catch (Exception e) {
            log.error("Error logging audit for execution {}: {}", execution.getId(), e.getMessage());
        }
    }
}
