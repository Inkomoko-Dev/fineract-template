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

import java.time.LocalDate;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.junit.jupiter.api.Test;

class LoanApplicationTermsPreserveTermTest {

    @Test
    void yearlySixPeriodsBecomeSeventyTwoMonthlyNotSeventy() {
        final Integer converted = LoanApplicationTerms.calculateNumberOfRepaymentsForFrequencyChange(1, PeriodFrequencyType.YEARS, 1,
                PeriodFrequencyType.MONTHS, 6);
        assertEquals(72, converted);
    }

    @Test
    void remainingTermFromMidLoanDoesNotOvershootMaturity() {
        // Yearly loan reschedule from 30 Sep 2027 to maturity 30 Sep 2032 under monthly cadence.
        // Calendar intervals = 60; inclusive dues on both from and maturity = 61 (first due = from, last = maturity).
        final LocalDate from = LocalDate.of(2027, 9, 30);
        final LocalDate maturity = LocalDate.of(2032, 9, 30);
        final Integer intervals = LoanApplicationTerms.countRepaymentPeriodsBetween(from, maturity, 1, PeriodFrequencyType.MONTHS);
        assertEquals(60, intervals);
        assertEquals(61, intervals + 1);
    }

    @Test
    void monthlyToEveryTwoMonthsHalvesPeriodCount() {
        final Integer converted = LoanApplicationTerms.calculateNumberOfRepaymentsForFrequencyChange(1, PeriodFrequencyType.MONTHS, 2,
                PeriodFrequencyType.MONTHS, 12);
        assertEquals(6, converted);
    }
}
