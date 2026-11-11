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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.springframework.stereotype.Service;

/** Pre-reschedule checks so ineligible loans fail with a review reason instead of being auto-processed. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleValidationService {

    public static final String SKIP_ALREADY_AT_TARGET_RATE = "Loan already at target interest rate";

    private final LoanRescheduleRequestRepository loanRescheduleRequestRepository;

    public List<String> validateLoanEligibilityForReschedule(final Loan loan) {
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
        if (loan.isMultiDisburmentLoan()) {
            errors.add(
                    "Multi-tranche loans must be rescheduled individually. The new schedule covers only principal already disbursed, "
                            + "and disbursing another tranche afterwards rebuilds the schedule and can replace the reschedule.");
        }
        final var pending = loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(loan.getId(),
                LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getValue());
        if (pending != null && !pending.isEmpty()) {
            errors.add("Loan already has a pending reschedule request");
        }
        return errors;
    }

    /**
     * Idempotent skip when the loan's current nominal rate already equals the bulk target rate and the interest method
     * matches the filter (when a method filter is supplied).
     */
    public Optional<String> findAlreadyAtTargetSkipReason(final Loan loan, final BigDecimal newInterestRate,
            final InterestMethod interestMethod) {
        if (loan == null || newInterestRate == null) {
            return Optional.empty();
        }
        final LoanProductRelatedDetail detail = loan.getLoanRepaymentScheduleDetail();
        if (detail == null || detail.getNominalInterestRatePerPeriod() == null) {
            return Optional.empty();
        }
        if (detail.getNominalInterestRatePerPeriod().compareTo(newInterestRate) != 0) {
            return Optional.empty();
        }
        if (interestMethod != null && interestMethod != InterestMethod.INVALID && detail.getInterestMethod() != null
                && detail.getInterestMethod() != interestMethod) {
            return Optional.empty();
        }
        return Optional.of(SKIP_ALREADY_AT_TARGET_RATE);
    }

    /**
     * True when the preview snapshot rate differs from the loan's current rate — typically after approve committed but
     * the bulk result row was not persisted (crash/resume).
     */
    public boolean looksLikeCompletedRateChange(final BigDecimal originalInterestRate, final Loan loan) {
        if (originalInterestRate == null || loan == null) {
            return false;
        }
        final BigDecimal current = loan.getLoanRepaymentScheduleDetail() == null ? null
                : loan.getLoanRepaymentScheduleDetail().getNominalInterestRatePerPeriod();
        return current != null && originalInterestRate.compareTo(current) != 0;
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
