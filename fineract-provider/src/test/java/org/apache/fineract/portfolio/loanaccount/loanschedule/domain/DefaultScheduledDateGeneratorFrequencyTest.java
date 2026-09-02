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

class DefaultScheduledDateGeneratorFrequencyTest {

    private final DefaultScheduledDateGenerator generator = new DefaultScheduledDateGenerator();

    @Test
    void quarterlyInstallmentsAreThreeMonthsApart() {
        final LocalDate firstDueDate = LocalDate.of(2026, 1, 15);
        LocalDate dueDate = firstDueDate;
        final LocalDate[] expected = { LocalDate.of(2026, 4, 15), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 10, 15) };
        for (final LocalDate next : expected) {
            dueDate = generator.getRepaymentPeriodDate(PeriodFrequencyType.MONTHS, 3, dueDate);
            assertEquals(next, dueDate);
        }
    }

    @Test
    void semiAnnualInstallmentsAreSixMonthsApart() {
        final LocalDate firstDueDate = LocalDate.of(2026, 1, 15);
        final LocalDate second = generator.getRepaymentPeriodDate(PeriodFrequencyType.MONTHS, 6, firstDueDate);
        assertEquals(LocalDate.of(2026, 7, 15), second);
    }

    @Test
    void monthlyInstallmentsRemainOneMonthApart() {
        final LocalDate firstDueDate = LocalDate.of(2026, 1, 15);
        final LocalDate second = generator.getRepaymentPeriodDate(PeriodFrequencyType.MONTHS, 1, firstDueDate);
        assertEquals(LocalDate.of(2026, 2, 15), second);
    }

    @Test
    void twelveMonthQuarterlyScheduleHasFourDueDatesFromTheFirst() {
        LocalDate dueDate = LocalDate.of(2026, 2, 1);
        int installments = 1;
        final LocalDate lastExpected = LocalDate.of(2026, 11, 1);
        while (dueDate.isBefore(lastExpected)) {
            dueDate = generator.getRepaymentPeriodDate(PeriodFrequencyType.MONTHS, 3, dueDate);
            installments++;
        }
        assertEquals(4, installments);
        assertEquals(lastExpected, dueDate);
    }

    @Test
    void twelveMonthSemiAnnualScheduleHasTwoDueDatesFromTheFirst() {
        final LocalDate firstDueDate = LocalDate.of(2026, 2, 1);
        final LocalDate secondDueDate = generator.getRepaymentPeriodDate(PeriodFrequencyType.MONTHS, 6, firstDueDate);
        assertEquals(LocalDate.of(2026, 8, 1), secondDueDate);
    }
}
