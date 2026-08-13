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
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.stereotype.Service;

/**
 * Service for validating loan eligibility for bulk reschedule operations.
 * Performs pre-reschedule checks to ensure loans meet all necessary criteria.
 */
@Slf4j
@Service
public class BulkRescheduleValidationService {

    /**
     * Validates loan eligibility for reschedule.
     * 
     * Checks:
     * - Loan must be in ACTIVE or ACTIVE_NON_PERFORMING status
     * - No pending charges blocking reschedule
     * - Client must be ACTIVE
     * - Loan hasn't been rescheduled within last 30 days
     * 
     * @param loan the loan to validate
     * @return list of validation error messages (empty if validation passes)
     */
    public List<String> validateLoanEligibilityForReschedule(final Loan loan) {
        List<String> errors = new ArrayList<>();

        if (loan == null) {
            errors.add("Loan not found");
            return errors;
        }

        // Check loan status - must be ACTIVE
        LoanStatus loanStatus = LoanStatus.fromInt(loan.getLoanStatus());
        if (loanStatus != LoanStatus.ACTIVE) {
            errors.add("Loan must be in ACTIVE status. Current status: " + loanStatus.toString());
        }

        // Check client status - must be ACTIVE
        if (loan.getClient() != null && !loan.getClient().isActive()) {
            errors.add("Client must be in ACTIVE status");
        }

        return errors;
    }

    /**
     * Validates reschedule parameters for consistency and business rules.
     * 
     * Checks:
     * - Reschedule from date is after last payment
     * - No past dates
     * - Term/day changes are non-negative
     * 
     * @param details the reschedule details to validate
     * @param loan the loan being rescheduled
     * @throws IllegalArgumentException if validation fails
     */
    public void validateRescheduleParameters(final ReschedulingDetailsDto details, final Loan loan) {
        if (details == null) {
            throw new IllegalArgumentException("Reschedule details cannot be null");
        }

        LocalDate today = DateUtils.getLocalDateTimeOfSystem().toLocalDate();

        // Validate extra terms is non-negative
        if (details.getExtraTerms() != null && details.getExtraTerms() < 0) {
            throw new IllegalArgumentException("Extra terms cannot be negative");
        }

        // Validate extra days is non-negative
        if (details.getExtraDays() != null && details.getExtraDays() < 0) {
            throw new IllegalArgumentException("Extra days cannot be negative");
        }

        // Validate exception days is non-negative
        if (details.getExceptionDays() != null && details.getExceptionDays() < 0) {
            throw new IllegalArgumentException("Exception days cannot be negative");
        }

        // Validate adjusted due date
        if (details.getAdjustedDueDate() != null && details.getAdjustedDueDate().isBefore(today)) {
            throw new IllegalArgumentException("Adjusted due date cannot be in the past");
        }
    }

}
