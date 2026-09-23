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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
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

    public static final String ROLLBACK_REASON_PREFIX = "ROLLBACK:";
    private static final int LOAN_THREADS = 4;
    private static final int BATCH_SIZE = 100;

    private final BulkRescheduleExecutionRepository bulkRescheduleExecutionRepository;
    private final BulkRescheduleResultRepository bulkRescheduleResultRepository;
    private final BulkRescheduleAuditRepository bulkRescheduleAuditRepository;
    private final BulkRescheduleLoanWorker loanWorker;
    private final BulkRescheduleFailureService failureService;
    private final PlatformSecurityContext platformSecurityContext;
    private final OfficeHierarchyService officeHierarchyService;
    private final NotificationWritePlatformService notificationService;
    private final BulkRescheduleAlertService alertService;
    private final BulkRescheduleProgressService progressService;
    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();
    private ExecutorService loanExecutor;

    @PostConstruct
    void initializeLoanExecutor() {
        loanExecutor = new ThreadPoolExecutor(LOAN_THREADS, LOAN_THREADS, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    void shutdownLoanExecutor() {
        if (loanExecutor != null) {
            loanExecutor.shutdown();
        }
    }

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
            final FineractContext context = copyContext(ThreadLocalContextUtil.getContext());
            while (true) {
                final List<BulkRescheduleResult> batch = bulkRescheduleResultRepository
                        .findPageByExecutionIdAndStatus(executionId, BulkRescheduleResultStatus.PREVIEW_MATCHED,
                                org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE, org.springframework.data.domain.Sort.by("id")))
                        .getContent();
                if (batch.isEmpty()) {
                    break;
                }
                if (!progressService.renewLease(executionId, workerToken)) {
                    log.warn("Execution {} lost its worker lease; stopping this worker", executionId);
                    return buildExecutionResponse(bulkRescheduleExecutionRepository.findById(executionId).orElseThrow(), false);
                }
                final List<Future<?>> tasks = new ArrayList<>();
                for (BulkRescheduleResult result : batch) {
                    final Long resultId = result.getId();
                    final Long loanId = result.getLoanId();
                    tasks.add(loanExecutor.submit(() -> processLoanResult(executionId, resultId, loanId, reschedulingDetails,
                            context)));
                }
                for (Future<?> task : tasks) {
                    progressService.renewLease(executionId, workerToken);
                    try {
                        task.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Bulk reschedule execution was interrupted", e);
                    } catch (ExecutionException e) {
                        log.error("Unexpected error while processing a bulk reschedule loan in execution {}", executionId, e);
                    }
                }
                if (!progressService.renewLease(executionId, workerToken)) {
                    log.warn("Execution {} lost its worker lease; stopping this worker", executionId);
                    return buildExecutionResponse(bulkRescheduleExecutionRepository.findById(executionId).orElseThrow(), false);
                }
                progressService.refreshCounts(executionId, workerToken);
            }

            progressService.complete(executionId, workerToken);
            execution = bulkRescheduleExecutionRepository.findById(executionId).orElseThrow();
            final int succeeded = zero(execution.getTotalSucceeded());
            final int failed = zero(execution.getTotalFailed());
            final int skipped = (int) bulkRescheduleResultRepository.countByExecutionIdAndStatus(executionId,
                    BulkRescheduleResultStatus.SKIPPED);
            log.info("Execution {} completed: {} succeeded, {} failed, {} skipped", executionId, succeeded, failed, skipped);

            // Step 6: Log audit entry
            long duration = java.time.temporal.ChronoUnit.SECONDS
                .between(executionStartTime, DateUtils.getLocalDateTimeOfSystem());
            logAudit(execution, BulkRescheduleAudit.BulkRescheduleAuditAction.EXECUTE, currentUser, 
                    String.format("Succeeded: %d, Failed: %d, Skipped: %d, Duration: %ds",
                    succeeded, failed, skipped, duration));
            final long remaining = bulkRescheduleResultRepository.countByExecutionIdAndStatus(executionId,
                    BulkRescheduleResultStatus.PREVIEW_MATCHED);
            if (remaining == 0) {
                alertService.notifyExecutionCompleted(execution, currentUser, succeeded, failed);
            }

            // Return response
            // Detailed rows remain available through the paged preview/export endpoints.
            return buildExecutionResponse(execution, false);

        } catch (Exception e) {
            log.error("Error executing bulk reschedule {}: {}", executionId, e.getMessage(), e);
            throw e;
        }
    }

    private void processLoanResult(final Long executionId, final Long resultId, final Long loanId,
            final ReschedulingDetailsDto reschedulingDetails, final FineractContext context) {
        // Worker threads start with empty ThreadLocals. getContext() requires business dates and
        // must not run before init(). getTenant() is safe to read when unset.
        final boolean inheritedContext = ThreadLocalContextUtil.getTenant() != null;
        try {
            ThreadLocalContextUtil.init(copyContext(context));
            final BulkRescheduleResult result = bulkRescheduleResultRepository.findById(resultId).orElse(null);
            if (result == null) {
                return;
            }
            if (result.getRescheduleRequestId() != null) {
                result.setStatus(BulkRescheduleResultStatus.SKIPPED);
                bulkRescheduleResultRepository.save(result);
                return;
            }
            loanWorker.executeLoan(executionId, resultId, reschedulingDetails);
        } catch (Exception e) {
            log.error("Error processing loan {} in execution {}: {}", loanId, executionId, e.getMessage(), e);
            try {
                if (ThreadLocalContextUtil.getTenant() == null) {
                    ThreadLocalContextUtil.init(copyContext(context));
                }
                failureService.markFailed(resultId, executionId, loanId, e);
            } catch (Exception persistFailure) {
                log.error("Could not persist failure for loan {} in execution {}", loanId, executionId, persistFailure);
            }
        } finally {
            if (!inheritedContext) {
                ThreadLocalContextUtil.clear();
            } else {
                ThreadLocalContextUtil.init(copyContext(context));
            }
        }
    }

    private FineractContext copyContext(final FineractContext source) {
        return new FineractContext(source.getContextHolder(), source.getTenantContext(), source.getAuthTokenContext(),
                source.getBusinessDateContext() == null ? null : new HashMap<>(source.getBusinessDateContext()),
                source.getActionContext());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExecutionFailed(final Long executionId, final Exception cause) {
        bulkRescheduleExecutionRepository.findById(executionId).ifPresent(execution -> {
            if (execution.getStatus() == BulkRescheduleExecutionStatus.ROLLING_BACK) {
                execution.setWorkerToken(null);
                execution.setLeaseExpiresAt(null);
                execution.setLastHeartbeatAt(null);
                execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
                bulkRescheduleExecutionRepository.save(execution);
                return;
            }
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
     * Marks the execution as rolling back and returns immediately. The command handler
     * starts {@link #runRollback(Long)} after commit so the request is not blocked.
     */
    @Transactional
    public CommandProcessingResult startRollback(final Long executionId, final String rollbackReason) {
        if (rollbackReason == null || rollbackReason.isBlank()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.rollback.reason.required",
                    "A rollback reason is required");
        }
        final BulkRescheduleExecution execution = bulkRescheduleExecutionRepository.findById(executionId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                        "Execution not found with ID: " + executionId));
        validateRollbackAllowed(execution);
        final AppUser currentUser = platformSecurityContext.authenticatedUser();
        if (!hasExecutionPermission(currentUser, execution)) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.permission.denied",
                    "User does not have permission to rollback this bulk reschedule");
        }
        execution.setStatus(BulkRescheduleExecutionStatus.ROLLING_BACK);
        execution.setExecutionError(ROLLBACK_REASON_PREFIX + rollbackReason.trim());
        execution.setWorkerToken(null);
        execution.setLeaseExpiresAt(null);
        execution.setLastHeartbeatAt(null);
        execution.setExecutionCompletedAt(null);
        execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
        bulkRescheduleExecutionRepository.save(execution);
        logAudit(execution, BulkRescheduleAudit.BulkRescheduleAuditAction.ROLLBACK, currentUser,
                "Background rollback started. Reason: " + rollbackReason.trim());
        return new CommandProcessingResultBuilder().withEntityId(execution.getId()).withOfficeId(execution.getOfficeId()).build();
    }

    public void runRollback(final Long executionId) {
        log.info("Starting background rollback of bulk reschedule: {}", executionId);
        final String workerToken = UUID.randomUUID().toString();
        final ClaimResult claimResult = progressService.claimRollback(executionId, workerToken);
        if (claimResult == ClaimResult.NONE) {
            log.info("Rollback {} has an active worker or is not rolling back", executionId);
            return;
        }
        final BulkRescheduleExecution execution = bulkRescheduleExecutionRepository.findById(executionId).orElseThrow();
        final String rollbackReason = rollbackReason(execution.getExecutionError());
        final FineractContext context = copyContext(ThreadLocalContextUtil.getContext());
        try {
            while (true) {
                final List<BulkRescheduleResult> batch = bulkRescheduleResultRepository
                        .findPageByExecutionIdAndStatus(executionId, BulkRescheduleResultStatus.SUCCEEDED,
                                org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE, org.springframework.data.domain.Sort.by("id")))
                        .getContent();
                if (batch.isEmpty()) {
                    break;
                }
                if (!progressService.renewLease(executionId, workerToken)) {
                    log.warn("Rollback {} lost its worker lease; stopping this worker", executionId);
                    return;
                }
                final List<Future<?>> tasks = new ArrayList<>();
                for (BulkRescheduleResult result : batch) {
                    final Long resultId = result.getId();
                    final Long loanId = result.getLoanId();
                    tasks.add(loanExecutor.submit(() -> processRollbackResult(resultId, loanId, rollbackReason, context)));
                }
                for (Future<?> task : tasks) {
                    progressService.renewLease(executionId, workerToken);
                    try {
                        task.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Bulk reschedule rollback was interrupted", e);
                    } catch (ExecutionException e) {
                        log.error("Unexpected error while rolling back a loan in execution {}", executionId, e);
                    }
                }
                progressService.refreshCounts(executionId, workerToken);
            }
            progressService.completeRollback(executionId, workerToken);
            log.info("Background rollback completed for execution {}", executionId);
        } catch (Exception e) {
            log.error("Background rollback failed for execution {}", executionId, e);
            throw e;
        }
    }

    private void processRollbackResult(final Long resultId, final Long loanId, final String rollbackReason,
            final FineractContext context) {
        final boolean inheritedContext = ThreadLocalContextUtil.getTenant() != null;
        try {
            ThreadLocalContextUtil.init(copyContext(context));
            loanWorker.rollbackLoan(resultId, rollbackReason);
        } catch (Exception e) {
            log.error("Error rolling back loan {} (result {}): {}", loanId, resultId, e.getMessage(), e);
            try {
                if (ThreadLocalContextUtil.getTenant() == null) {
                    ThreadLocalContextUtil.init(copyContext(context));
                }
                markRollbackFailed(resultId, e);
            } catch (Exception persistFailure) {
                log.error("Could not persist rollback failure for loan {}", loanId, persistFailure);
            }
        } finally {
            if (!inheritedContext) {
                ThreadLocalContextUtil.clear();
            } else {
                ThreadLocalContextUtil.init(copyContext(context));
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRollbackFailed(final Long resultId, final Exception cause) {
        bulkRescheduleResultRepository.findById(resultId).ifPresent(result -> {
            if (result.getStatus() == BulkRescheduleResultStatus.ROLLED_BACK) {
                return;
            }
            result.setStatus(BulkRescheduleResultStatus.ROLLBACK_FAILED);
            result.setErrorMessage("Rollback failed: " + cause.getMessage());
            bulkRescheduleResultRepository.save(result);
        });
    }

    private static String rollbackReason(final String executionError) {
        if (executionError != null && executionError.startsWith(ROLLBACK_REASON_PREFIX)) {
            return executionError.substring(ROLLBACK_REASON_PREFIX.length());
        }
        return "Bulk reschedule rollback";
    }

    private void validateRollbackAllowed(final BulkRescheduleExecution execution) {
        final BulkRescheduleExecutionStatus status = execution.getStatus();
        if (status == BulkRescheduleExecutionStatus.ROLLED_BACK || status == BulkRescheduleExecutionStatus.ROLLING_BACK) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.already.rolled.back",
                    "This execution has already been rolled back");
        }
        final long remaining = bulkRescheduleResultRepository.countByExecutionIdAndStatus(execution.getId(),
                BulkRescheduleResultStatus.PREVIEW_MATCHED);
        final boolean completed = status == BulkRescheduleExecutionStatus.COMPLETED;
        final boolean partialWithNoWorkLeft = status == BulkRescheduleExecutionStatus.PARTIAL_SUCCESS && remaining == 0;
        if (!completed && !partialWithNoWorkLeft) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.invalid.status",
                    "Only a completed execution, or a partial success with no remaining loans, can be rolled back. Current status: "
                            + status);
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

    private static int zero(final Integer value) {
        return value == null ? 0 : value;
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
