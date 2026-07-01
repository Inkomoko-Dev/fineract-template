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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.data.LoanScheduleAccrualData;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanAccrualWritePlatformServiceImplTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 14);
    private static final CurrencyData SSP = new CurrencyData("SSP", 2, 0);
    private static final EnumOptionData SPECIFIED_DUE_DATE = new EnumOptionData(
            ChargeTimeType.SPECIFIED_DUE_DATE.getValue().longValue(), ChargeTimeType.SPECIFIED_DUE_DATE.getCode(),
            "Specified due date");

    @Test
    void fullPenaltyWaiverDoesNotAddApplicableChargeWhenChargeWasAlreadyAccrued() {
        final LoanChargeData penaltyCharge = penaltyCharge(new BigDecimal("300000000.00"), new BigDecimal("300000000.00"),
                new BigDecimal("299462600.00"));
        final LoanScheduleAccrualData accrualData = accrualData(new BigDecimal("299462600.00"));

        updateCharges(penaltyCharge, accrualData);

        assertEquals(0, new BigDecimal("299462600.00").compareTo(accrualData.getDueDatePenaltyIncome()));
        assertTrue(accrualData.getApplicableCharges().isEmpty());
    }

    @Test
    void partialPenaltyWaiverAccruesOnlyTheUnwaivedResidualAmount() {
        final LoanChargeData penaltyCharge = penaltyCharge(new BigDecimal("300000000.00"), new BigDecimal("299462600.00"), null);
        final LoanScheduleAccrualData accrualData = accrualData(null);

        updateCharges(penaltyCharge, accrualData);

        assertEquals(0, new BigDecimal("537400.00").compareTo(accrualData.getDueDatePenaltyIncome()));
        assertEquals(0, new BigDecimal("537400.00").compareTo(accrualData.getApplicableCharges().get(penaltyCharge)));
    }

    @Test
    void fullPenaltyWaiverBeforeAccrualLeavesNoPenaltyIncome() {
        final LoanChargeData penaltyCharge = penaltyCharge(new BigDecimal("300000000.00"), new BigDecimal("300000000.00"), null);
        final LoanScheduleAccrualData accrualData = accrualData(null);

        updateCharges(penaltyCharge, accrualData);

        assertNull(accrualData.getDueDatePenaltyIncome());
        assertTrue(accrualData.getApplicableCharges().isEmpty());
    }

    private void updateCharges(final LoanChargeData penaltyCharge, final LoanScheduleAccrualData accrualData) {
        final LoanAccrualWritePlatformServiceImpl service = new LoanAccrualWritePlatformServiceImpl(null, null, null, null, null, null,
                null, null);

        ReflectionTestUtils.invokeMethod(service, "updateCharges", List.of(penaltyCharge), accrualData, START_DATE, DUE_DATE);
    }

    private LoanChargeData penaltyCharge(final BigDecimal amount, final BigDecimal amountWaived, final BigDecimal amountAccrued) {
        return new LoanChargeData(1L, 1L, DUE_DATE, SPECIFIED_DUE_DATE, amount, amountAccrued, amountWaived, true);
    }

    private LoanScheduleAccrualData accrualData(final BigDecimal accruedPenaltyIncome) {
        return new LoanScheduleAccrualData(1L, 1L, 1, DUE_DATE, PeriodFrequencyType.MONTHS, 1, DUE_DATE, START_DATE, 1L, 1L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, accruedPenaltyIncome, SSP, null, null);
    }
}
