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
package org.apache.fineract.portfolio.loanaccount.loanschedule.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.junit.jupiter.api.Test;

class LoanRepaymentFrequencyTest {

    private static final Integer MONTHS = PeriodFrequencyType.MONTHS.getValue();

    @Test
    void displayNameUsesNamedMonthlyIntervals() {
        assertEquals("Monthly", LoanRepaymentFrequency.displayName(1, MONTHS, "Months"));
        assertEquals("Quarterly", LoanRepaymentFrequency.displayName(3, MONTHS, "Months"));
        assertEquals("Semi-Annual", LoanRepaymentFrequency.displayName(6, MONTHS, "Months"));
        assertEquals("2 Months", LoanRepaymentFrequency.displayName(2, MONTHS, "Months"));
    }

    @Test
    void productRejectsZeroInstallments() {
        final List<ApiParameterError> errors = new ArrayList<>();
        LoanRepaymentFrequency.validateProduct(errors, 0, 3, MONTHS);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.getDefaultUserMessage().contains("at least 1")));
    }

    @Test
    void productAcceptsFourQuarterlyInstallments() {
        final List<ApiParameterError> errors = new ArrayList<>();
        LoanRepaymentFrequency.validateProduct(errors, 4, 3, MONTHS);
        assertTrue(errors.isEmpty());
    }

    @Test
    void loanRejectsQuarterlyWhenTermIsShorterThanThreeMonths() {
        final List<ApiParameterError> errors = new ArrayList<>();
        LoanRepaymentFrequency.validateLoan(errors, 2, MONTHS, 1, 3, MONTHS);
        assertTrue(errors.stream().anyMatch(error -> error.getDefaultUserMessage().contains("at least 3 months")));
    }

    @Test
    void loanRejectsSemiAnnualWhenTermIsShorterThanSixMonths() {
        final List<ApiParameterError> errors = new ArrayList<>();
        LoanRepaymentFrequency.validateLoan(errors, 5, MONTHS, 1, 6, MONTHS);
        assertTrue(errors.stream().anyMatch(error -> error.getDefaultUserMessage().contains("at least 6 months")));
    }

    @Test
    void loanRejectsTermThatIsNotAMultipleOfTheInterval() {
        final List<ApiParameterError> errors = new ArrayList<>();
        LoanRepaymentFrequency.validateLoan(errors, 10, MONTHS, 3, 3, MONTHS);
        assertTrue(errors.stream().anyMatch(error -> error.getDefaultUserMessage().contains("multiple of 3 months")));
    }

    @Test
    void loanAcceptsTwelveMonthQuarterlyAndSemiAnnualTerms() {
        final List<ApiParameterError> quarterly = new ArrayList<>();
        LoanRepaymentFrequency.validateLoan(quarterly, 12, MONTHS, 4, 3, MONTHS);
        assertTrue(quarterly.isEmpty());

        final List<ApiParameterError> semiAnnual = new ArrayList<>();
        LoanRepaymentFrequency.validateLoan(semiAnnual, 12, MONTHS, 2, 6, MONTHS);
        assertTrue(semiAnnual.isEmpty());
    }

    @Test
    void monthlyLoansAreUnchangedByTheNewNamedIntervalRules() {
        final List<ApiParameterError> errors = new ArrayList<>();
        LoanRepaymentFrequency.validateLoan(errors, 12, MONTHS, 12, 1, MONTHS);
        assertTrue(errors.isEmpty());
    }
}
