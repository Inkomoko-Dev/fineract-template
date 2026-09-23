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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.RescheduleFromDateStrategy;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Fills one preview result row without holding the HTTP request open. */
@Service
@RequiredArgsConstructor
public class BulkReschedulePreviewWorker {

    private final BulkRescheduleResultRepository resultRepository;
    private final LoanRepository loanRepository;
    private final BulkRescheduleValidationService validationService;
    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enrichResult(final Long resultId, final ReschedulingDetailsDto details,
            final RescheduleFromDateStrategy strategy) {
        final BulkRescheduleResult result = resultRepository.findById(resultId).orElseThrow();
        if (result.getStatus() != BulkRescheduleResultStatus.PREVIEW_MATCHED || result.getNextScheduledInstallment() != null) {
            return;
        }
        final Loan loan = loanRepository.findById(result.getLoanId()).orElse(null);
        if (loan == null) {
            result.setStatus(BulkRescheduleResultStatus.FAILED);
            result.setErrorMessage("Loan not found");
            result.setLoanAccountNumber(result.getLoanAccountNumber() == null ? "" : result.getLoanAccountNumber());
            resultRepository.save(result);
            return;
        }
        final ReschedulingDetailsDto loanDetails = gson.fromJson(gson.toJson(details), ReschedulingDetailsDto.class);
        result.setOriginalInterestRate(currentInterestRate(loan));
        result.setNewInterestRate(loanDetails.getNewInterestRate());
        populateSnapshot(result, loan, loanDetails);
        try {
            final Long executionId = result.getExecution() == null ? null : result.getExecution().getId();
            final List<String> eligibilityErrors = validationService.validateLoanEligibilityForReschedule(loan, executionId);
            if (!eligibilityErrors.isEmpty()) {
                throw new IllegalArgumentException(String.join("; ", eligibilityErrors));
            }
            final LocalDate rescheduleFromDate = resolveRescheduleFromDate(loan, strategy);
            if (rescheduleFromDate == null) {
                throw new IllegalArgumentException("Loan has no repayment installment available for the selected strategy");
            }
            loanDetails.setRescheduleFromDate(rescheduleFromDate);
            validationService.validateRescheduleParameters(loanDetails, loan);
            populateProposedSnapshot(result, loanDetails, rescheduleFromDate);
        } catch (Exception e) {
            result.setStatus(BulkRescheduleResultStatus.FAILED);
            result.setErrorMessage(e.getMessage() == null ? "Unable to calculate reschedule preview" : e.getMessage());
        }
        resultRepository.save(result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final Long resultId, final String message) {
        resultRepository.findById(resultId).ifPresent(result -> {
            if (result.getStatus() != BulkRescheduleResultStatus.PREVIEW_MATCHED) {
                return;
            }
            result.setStatus(BulkRescheduleResultStatus.FAILED);
            result.setErrorMessage(message == null ? "Unable to calculate reschedule preview" : message);
            if (result.getLoanAccountNumber() == null) {
                result.setLoanAccountNumber("");
            }
            resultRepository.save(result);
        });
    }

    private LocalDate resolveRescheduleFromDate(final Loan loan, final RescheduleFromDateStrategy strategy) {
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

    private BigDecimal currentInterestRate(final Loan loan) {
        if (loan.getLoanProductRelatedDetail() == null) {
            return null;
        }
        return loan.getLoanProductRelatedDetail().getNominalInterestRatePerPeriod();
    }

    private void populateSnapshot(final BulkRescheduleResult result, final Loan loan, final ReschedulingDetailsDto details) {
        result.setLoanAccountNumber(loan.getAccountNumber());
        result.setAccountNumber(loan.getAccountNumber());
        result.setClientName(loan.getClient() == null ? null : loan.getClient().getDisplayName());
        result.setOfficeId(loan.getOffice() == null ? null : loan.getOffice().getId());
        result.setOfficeName(loan.getOffice() == null ? null : loan.getOffice().getName());
        result.setLoanProductName(loan.getLoanProduct() == null ? null : loan.getLoanProduct().getShortName());
        result.setLoanOfficerId(loan.getLoanOfficer() == null ? null : loan.getLoanOfficer().getId());
        result.setLoanOfficerName(loan.getLoanOfficer() == null ? null : loan.getLoanOfficer().displayName());
        result.setLoanStatus(loan.status() == null ? null : loan.status().toString());
        result.setInterestRateMethod(loan.getLoanProductRelatedDetail() == null
                || loan.getLoanProductRelatedDetail().getInterestMethod() == null ? null
                        : loan.getLoanProductRelatedDetail().getInterestMethod().name());
        result.setTotalOutstanding(loan.getSummary() == null ? null : loan.getSummary().getTotalOutstanding());
        result.setCurrentTerm(loan.getRepaymentScheduleInstallments() == null ? 0
                : (int) loan.getRepaymentScheduleInstallments().stream().filter(i -> !i.isObligationsMet()).count());
        result.setRescheduleReason(details.getRescheduleReasonComment());
        result.setCreatedAt(result.getCreatedAt() == null ? DateUtils.getLocalDateTimeOfSystem() : result.getCreatedAt());
    }

    private void populateProposedSnapshot(final BulkRescheduleResult result, final ReschedulingDetailsDto details,
            final LocalDate rescheduleFromDate) {
        final int extraTerms = details.getExtraTerms() == null ? 0 : details.getExtraTerms();
        result.setNewTerm((result.getCurrentTerm() == null ? 0 : result.getCurrentTerm()) + extraTerms);
        result.setNextScheduledInstallment(rescheduleFromDate);
        result.setNewTotalOutstanding(result.getTotalOutstanding());
    }
}
