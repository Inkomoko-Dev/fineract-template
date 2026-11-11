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

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleFilterDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.RescheduleFromDateStrategy;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRescheduleRequestToTermVariationMapping;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTermVariations;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Dedicated Spring proxy for the atomic unit of one bulk loan operation. */
@Service
@RequiredArgsConstructor
public class BulkRescheduleLoanWorker {

    private static final Logger LOG = LoggerFactory.getLogger(BulkRescheduleLoanWorker.class);

    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();

    private final BulkRescheduleExecutionRepository executionRepository;
    private final BulkRescheduleResultRepository resultRepository;
    private final LoanRepository loanRepository;
    private final BulkLoanRescheduleEngine rescheduleEngine;
    private final BulkRescheduleValidationService validationService;
    private final BulkRescheduleNoteService noteService;
    private final BulkRescheduleLoanSnapshotService snapshotService;
    private final LoanRescheduleRequestRepository loanRescheduleRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeLoan(final Long executionId, final Long resultId, final ReschedulingDetailsDto details) {
        final BulkRescheduleExecution execution = executionRepository.findById(executionId).orElseThrow();
        final BulkRescheduleResult result = resultRepository.findById(resultId).orElseThrow();
        final Loan loan = loanRepository.findById(result.getLoanId()).orElseThrow();
        final BulkRescheduleFilterDto filters = gson.fromJson(execution.getFiltersJson(), BulkRescheduleFilterDto.class);
        final ReschedulingDetailsDto loanDetails = gson.fromJson(gson.toJson(details), ReschedulingDetailsDto.class);
        final InterestMethod interestMethod = filters == null ? null
                : LoanBulkRescheduleSpecification.parseInterestMethod(filters.getInterestMethod());
        final Optional<String> skipReason = validationService.findAlreadyAtTargetSkipReason(loan, loanDetails.getNewInterestRate(),
                interestMethod);
        if (skipReason.isPresent()) {
            if (validationService.looksLikeCompletedRateChange(result.getOriginalInterestRate(), loan)) {
                recoverSucceededAfterCrash(result, loan, execution, loanDetails.getNewInterestRate());
                return;
            }
            result.setStatus(BulkRescheduleResultStatus.SKIPPED);
            result.setExcludeReason(skipReason.get());
            resultRepository.save(result);
            return;
        }
        loanDetails.setRescheduleFromDate(resolveRescheduleFromDate(loan, filters == null ? null : filters.getRescheduleFromDateStrategy()));
        if (loanDetails.getRescheduleFromDate() == null) {
            throw new IllegalArgumentException("Loan has no repayment installment available for the selected strategy");
        }
        final List<String> errors = validationService.validateLoanEligibilityForReschedule(loan);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        validationService.validateRescheduleParameters(loanDetails, loan);
        final Long requestId = rescheduleEngine.performReschedule(loan, loanDetails);
        rescheduleEngine.approveReschedule(requestId);
        // Persist request id before note/snapshot so crash recovery can attribute this execution's request.
        result.setRescheduleRequestId(requestId);
        result.setOriginalRescheduleRequestId(requestId);
        resultRepository.save(result);
        noteService.addRescheduleNoteToLoan(loan, execution);
        markSucceeded(result, loan, requestId);
    }

    private void recoverSucceededAfterCrash(final BulkRescheduleResult result, final Loan loan,
            final BulkRescheduleExecution execution, final BigDecimal targetInterestRate) {
        final CrashRecoveryResolution resolution = resolveCrashRecoveryRequestId(loan.getId(), result, execution, targetInterestRate);
        if (resolution.status == BulkRescheduleResultStatus.SUCCEEDED && resolution.requestId != null) {
            noteService.addRescheduleNoteToLoan(loan, execution);
            markSucceeded(result, loan, resolution.requestId);
            return;
        }
        result.setRescheduleRequestId(null);
        result.setOriginalRescheduleRequestId(null);
        result.setStatus(resolution.status);
        if (resolution.status == BulkRescheduleResultStatus.SKIPPED) {
            result.setExcludeReason(resolution.reason);
            result.setErrorMessage(null);
        } else {
            result.setExcludeReason(null);
            result.setErrorMessage(resolution.reason);
        }
        resultRepository.save(result);
        LOG.warn("Crash recovery for loan {} result {}: {}", loan.getId(), result.getId(), resolution.reason);
    }

    /**
     * Returns a request only when exactly one approved target-rate candidate is attributable to this execution.
     */
    CrashRecoveryResolution resolveCrashRecoveryRequestId(final Long loanId, final BulkRescheduleResult result,
            final BulkRescheduleExecution execution, final BigDecimal targetInterestRate) {
        if (targetInterestRate == null) {
            return CrashRecoveryResolution.failed("Crash recovery failed: target interest rate is missing");
        }
        final List<LoanRescheduleRequest> approved = loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(loanId,
                LoanStatus.APPROVED.getValue());
        if (approved == null || approved.isEmpty()) {
            return CrashRecoveryResolution.skipped(
                    "Already at target rate with no attributable approved reschedule request for this execution");
        }

        final LocalDate notBefore = resolveAttributionFloorDate(execution);
        final List<LoanRescheduleRequest> candidates = new ArrayList<>();
        for (final LoanRescheduleRequest request : approved) {
            if (request == null || request.getId() == null) {
                continue;
            }
            if (!matchesTargetInterestRate(request, targetInterestRate)) {
                continue;
            }
            if (!isUnclaimedOrOwnedByResult(request.getId(), result)) {
                continue;
            }
            if (notBefore != null && !isOnOrAfterAttributionFloor(request, notBefore)) {
                continue;
            }
            candidates.add(request);
        }

        if (candidates.isEmpty()) {
            return CrashRecoveryResolution.skipped(
                    "Already at target rate with no attributable approved reschedule request for this execution");
        }
        if (candidates.size() > 1) {
            return CrashRecoveryResolution.failed(
                    "Crash recovery failed: multiple approved target-rate reschedule requests could not be attributed to this execution");
        }
        return CrashRecoveryResolution.succeeded(candidates.get(0).getId());
    }

    private LocalDate resolveAttributionFloorDate(final BulkRescheduleExecution execution) {
        if (execution == null) {
            return null;
        }
        final LocalDateTime started = execution.getExecutionStartedAt() != null ? execution.getExecutionStartedAt()
                : execution.getApprovedAt();
        return started == null ? null : started.toLocalDate();
    }

    private boolean isOnOrAfterAttributionFloor(final LoanRescheduleRequest request, final LocalDate notBefore) {
        final LocalDate approvedOn = request.getApprovedOnDate();
        if (approvedOn != null) {
            return !approvedOn.isBefore(notBefore);
        }
        final LocalDate submittedOn = request.getSubmittedOnDate();
        return submittedOn == null || !submittedOn.isBefore(notBefore);
    }

    private boolean isUnclaimedOrOwnedByResult(final Long requestId, final BulkRescheduleResult result) {
        final Optional<BulkRescheduleResult> claimed = resultRepository.findByRescheduleRequestId(requestId);
        if (claimed.isEmpty()) {
            return true;
        }
        final BulkRescheduleResult owner = claimed.get();
        return result != null && result.getId() != null && result.getId().equals(owner.getId());
    }

    static boolean matchesTargetInterestRate(final LoanRescheduleRequest request, final BigDecimal targetInterestRate) {
        if (request == null || targetInterestRate == null) {
            return false;
        }
        final Set<LoanRescheduleRequestToTermVariationMapping> mappings = request.getLoanRescheduleRequestToTermVariationMappings();
        if (mappings == null || mappings.isEmpty()) {
            return false;
        }
        for (final LoanRescheduleRequestToTermVariationMapping mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            final LoanTermVariations variation = mapping.getLoanTermVariations();
            if (variation == null || variation.getTermType() == null || !variation.getTermType().isInterestRateFromInstallment()) {
                continue;
            }
            final BigDecimal value = variation.getTermValue();
            if (value != null && value.compareTo(targetInterestRate) == 0) {
                return true;
            }
        }
        return false;
    }

    private void markSucceeded(final BulkRescheduleResult result, final Loan loan, final Long requestId) {
        final Loan refreshed = loanRepository.findById(loan.getId()).orElse(loan);
        try {
            entityManager.refresh(refreshed);
        } catch (RuntimeException ignored) {
            // Refresh is best-effort when the entity is not managed in this persistence context.
        }
        snapshotService.applyPostSuccessSnapshot(result, refreshed);
        if (requestId != null) {
            result.setRescheduleRequestId(requestId);
            result.setOriginalRescheduleRequestId(requestId);
        }
        result.setStatus(BulkRescheduleResultStatus.SUCCEEDED);
        result.setExcludeReason(null);
        result.setErrorMessage(null);
        resultRepository.save(result);
    }

    private java.time.LocalDate resolveRescheduleFromDate(final Loan loan, final RescheduleFromDateStrategy strategy) {
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null || installments.isEmpty()) {
            return null;
        }
        if (strategy == RescheduleFromDateStrategy.NEXT_UNPAID) {
            return installments.stream().filter(installment -> !installment.isObligationsMet())
                    .map(LoanRepaymentScheduleInstallment::getDueDate).findFirst().orElse(null);
        }
        return installments.get(0).getDueDate();
    }

    /** Restores the archived schedule for the approved reschedule request, then marks the result rolled back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackLoan(final Long resultId, final String rollbackReason) {
        final BulkRescheduleResult result = resultRepository.findById(resultId).orElseThrow();
        if (result.getStatus() == BulkRescheduleResultStatus.ROLLED_BACK) {
            return;
        }
        final Long requestId = result.getOriginalRescheduleRequestId() != null ? result.getOriginalRescheduleRequestId()
                : result.getRescheduleRequestId();
        if (requestId == null) {
            throw new IllegalArgumentException("Loan has no reschedule request to reverse");
        }
        final BulkRescheduleExecution execution = result.getExecution();
        final Loan loan = loanRepository.findById(result.getLoanId()).orElse(null);
        rescheduleEngine.undoApprovedReschedule(requestId, null, result.getOriginalInterestRate());
        if (loan != null && execution != null) {
            noteService.addRollbackNoteToLoan(loan, execution, rollbackReason);
        }
        result.setStatus(BulkRescheduleResultStatus.ROLLED_BACK);
        result.setErrorMessage(null);
        resultRepository.save(result);
    }

    static final class CrashRecoveryResolution {
        private final BulkRescheduleResultStatus status;
        private final Long requestId;
        private final String reason;

        private CrashRecoveryResolution(final BulkRescheduleResultStatus status, final Long requestId, final String reason) {
            this.status = status;
            this.requestId = requestId;
            this.reason = reason;
        }

        static CrashRecoveryResolution succeeded(final Long requestId) {
            return new CrashRecoveryResolution(BulkRescheduleResultStatus.SUCCEEDED, requestId, null);
        }

        static CrashRecoveryResolution failed(final String reason) {
            return new CrashRecoveryResolution(BulkRescheduleResultStatus.FAILED, null, reason);
        }

        static CrashRecoveryResolution skipped(final String reason) {
            return new CrashRecoveryResolution(BulkRescheduleResultStatus.SKIPPED, null, reason);
        }

        BulkRescheduleResultStatus getStatus() {
            return status;
        }

        Long getRequestId() {
            return requestId;
        }

        String getReason() {
            return reason;
        }
    }
}
