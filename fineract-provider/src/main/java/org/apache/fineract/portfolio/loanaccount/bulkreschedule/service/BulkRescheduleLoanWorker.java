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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Dedicated Spring proxy for the atomic unit of one bulk loan operation. */
@Service
@RequiredArgsConstructor
public class BulkRescheduleLoanWorker {

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
                recoverSucceededAfterCrash(result, loan, execution);
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
        noteService.addRescheduleNoteToLoan(loan, execution);
        markSucceeded(result, loan, requestId);
    }

    private void recoverSucceededAfterCrash(final BulkRescheduleResult result, final Loan loan,
            final BulkRescheduleExecution execution) {
        final Long requestId = latestApprovedRescheduleRequestId(loan.getId());
        if (requestId != null) {
            result.setRescheduleRequestId(requestId);
            result.setOriginalRescheduleRequestId(requestId);
        }
        noteService.addRescheduleNoteToLoan(loan, execution);
        markSucceeded(result, loan, requestId);
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

    private Long latestApprovedRescheduleRequestId(final Long loanId) {
        final List<LoanRescheduleRequest> approved = loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(loanId,
                LoanStatus.APPROVED.getValue());
        if (approved == null || approved.isEmpty()) {
            return null;
        }
        return approved.stream().max(Comparator.comparing(LoanRescheduleRequest::getId)).map(LoanRescheduleRequest::getId).orElse(null);
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
}
