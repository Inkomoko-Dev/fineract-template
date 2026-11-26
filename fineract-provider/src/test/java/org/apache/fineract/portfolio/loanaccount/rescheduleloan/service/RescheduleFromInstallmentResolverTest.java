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
package org.apache.fineract.portfolio.loanaccount.rescheduleloan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.Test;

class RescheduleFromInstallmentResolverTest {

    @Test
    void exactDueDateMatchReturnsThatInstallmentNumber() {
        var i1 = installment(1, LocalDate.of(2027, 1, 1));
        var i2 = installment(2, LocalDate.of(2028, 1, 1));
        assertEquals(Optional.of(1), RescheduleFromInstallmentResolver.resolveInstallmentNumber(List.of(i1, i2), LocalDate.of(2027, 1, 1)));
    }

    @Test
    void dateBeforeAllDueDatesReturnsFirstInstallmentNumber() {
        // CGLT-857: rescheduleFromDate in 2026, first due 2027
        var i1 = installment(1, LocalDate.of(2027, 1, 1));
        assertEquals(Optional.of(1), RescheduleFromInstallmentResolver.resolveInstallmentNumber(List.of(i1), LocalDate.of(2026, 7, 1)));
    }

    @Test
    void dateBetweenDueDatesReturnsNextInstallmentNumber() {
        var i1 = installment(1, LocalDate.of(2026, 1, 1));
        var i2 = installment(2, LocalDate.of(2026, 2, 1));
        assertEquals(Optional.of(2), RescheduleFromInstallmentResolver.resolveInstallmentNumber(List.of(i1, i2), LocalDate.of(2026, 1, 15)));
    }

    @Test
    void dateAfterLastDueDateReturnsEmpty() {
        var i1 = installment(1, LocalDate.of(2027, 1, 1));
        assertTrue(RescheduleFromInstallmentResolver.resolveInstallmentNumber(List.of(i1), LocalDate.of(2028, 1, 1)).isEmpty());
    }

    private static LoanRepaymentScheduleInstallment installment(int number, LocalDate due) {
        return new LoanRepaymentScheduleInstallment(null, number, due.minusMonths(1), due, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, false, null);
    }
}
