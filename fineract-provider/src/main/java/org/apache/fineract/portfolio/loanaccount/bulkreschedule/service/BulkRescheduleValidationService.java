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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTermVariationType;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.springframework.stereotype.Service;

/** Pre-reschedule checks so ineligible loans fail with a review reason instead of being auto-processed. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleValidationService {

    private final LoanRescheduleRequestRepository loanRescheduleRequestRepository;
    private final BulkRescheduleResultRepository resultRepository;

    public List<String> validateLoanEligibilityForReschedule(final Loan loan) {
        return validateLoanEligibilityForReschedule(loan, null);
    }

    public List<String> validateLoanEligibilityForReschedule(final Loan loan, final Long currentExecutionId) {
        final List<String> errors = new ArrayList<>();
        if (loan == null) {
            errors.add("Loan not found");
            return errors;
        }
        final LoanStatus loanStatus = LoanStatus.fromInt(loan.getLoanStatus());
        if (loanStatus != LoanStatus.ACTIVE) {
            errors.add("Loan must be in ACTIVE status. Current status: " + loanStatus);
        }
        if (loan.getClient() != null && !loan.getClient().isActive()) {
            errors.add("Client must be in ACTIVE status");
        }
        if (hasCustomSchedule(loan)) {
            errors.add("Loan has an overridden or custom repayment schedule and must be reviewed individually");
        }
        final var pending = loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(loan.getId(),
                LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getValue());
        if (pending != null && !pending.isEmpty()) {
            errors.add("Loan already has a pending reschedule request");
        }
        if (currentExecutionId != null && resultRepository.countByLoanIdAndStatusAndExecution_IdNot(loan.getId(),
                BulkRescheduleResultStatus.SUCCEEDED, currentExecutionId) > 0) {
            errors.add("Loan was already rescheduled in a previous bulk run");
        }
        return errors;
    }

    private static boolean hasCustomSchedule(final Loan loan) {
        if (loan.getActiveLoanTermVariations() == null || loan.getActiveLoanTermVariations().isEmpty()) {
            return false;
        }
        return loan.getActiveLoanTermVariations().stream().anyMatch(variation -> {
            final var type = variation.getTermType();
            return type.isEMIAmountVariation() || type.isPrincipalAmountVariation() || type.isDueDateVariation()
                    || type.isInsertInstallment() || type.isDeleteInstallment() || type.isExtendRepaymentPeriod()
                    || type.isRepaymentFrequencyVariation() || type.isRepaymentEveryVariation()
                    || type.isGraceOnInterest() || type.isGraceOnPrincipal()
                    || type == LoanTermVariationType.PRINCIPAL_DUE_FIXED_AMOUNT;
        });
    }

    public void validateRescheduleParameters(final ReschedulingDetailsDto details, final Loan loan) {
        if (details == null) {
            throw new IllegalArgumentException("Reschedule details cannot be null");
        }
        final LocalDate today = DateUtils.getLocalDateTimeOfSystem().toLocalDate();
        if (details.getExtraTerms() != null && details.getExtraTerms() < 0) {
            throw new IllegalArgumentException("Extra terms cannot be negative");
        }
        if (details.getExtraDays() != null && details.getExtraDays() < 0) {
            throw new IllegalArgumentException("Extra days cannot be negative");
        }
        if (details.getExceptionDays() != null && details.getExceptionDays() < 0) {
            throw new IllegalArgumentException("Exception days cannot be negative");
        }
        if (details.getAdjustedDueDate() != null && details.getAdjustedDueDate().isBefore(today)) {
            throw new IllegalArgumentException("Adjusted due date cannot be in the past");
        }
    }
}
