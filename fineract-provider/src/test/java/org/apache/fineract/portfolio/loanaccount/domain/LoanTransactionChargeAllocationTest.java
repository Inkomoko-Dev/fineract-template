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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The replay keeps the original transaction whenever the recomputed component totals match, so the charge links it
 * carries have to be refreshed there too. Without this a repayment keeps paying a charge the replay moved its money
 * away from - which is how a waived penalty stayed settled by the client's cash (CGLT-656).
 */
public class LoanTransactionChargeAllocationTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, null);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 15);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 4, 1))));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    @DisplayName("adopts the recomputed allocation when the replay moved the money to a different charge")
    public void adoptsTheRecomputedAllocation() {
        final LoanCharge waived = penalty();
        final LoanCharge stillPayable = penalty();

        final LoanTransaction repayment = repayment("16000.00");
        repayment.getLoanChargesPaid().add(new LoanChargePaidBy(repayment, waived, new BigDecimal("5000.00"), 1));

        final LoanTransaction recomputed = repayment("16000.00");
        recomputed.getLoanChargesPaid().add(new LoanChargePaidBy(recomputed, stillPayable, new BigDecimal("5000.00"), 1));

        repayment.updateLoanChargesPaid(recomputed.getLoanChargesPaid());

        assertEquals(1, repayment.getLoanChargesPaid().size());
        final LoanChargePaidBy adopted = repayment.getLoanChargesPaid().iterator().next();
        assertSame(stillPayable, adopted.getLoanCharge(), "the repayment must stop paying the charge the replay released");
        assertEquals(0, new BigDecimal("5000.00").compareTo(adopted.getAmount()));
        assertSame(repayment, adopted.getLoanTransaction(), "the adopted row must belong to the surviving transaction");
    }

    @Test
    @DisplayName("drops the allocation entirely when the replay no longer pays any charge")
    public void dropsTheAllocationWhenNothingIsPaidAnyMore() {
        final LoanCharge waived = penalty();

        final LoanTransaction repayment = repayment("16000.00");
        repayment.getLoanChargesPaid().add(new LoanChargePaidBy(repayment, waived, new BigDecimal("5000.00"), 1));

        repayment.updateLoanChargesPaid(repayment("16000.00").getLoanChargesPaid());

        assertTrue(repayment.getLoanChargesPaid().isEmpty(), "a released charge must not stay linked to the repayment");
    }

    @Test
    @DisplayName("leaves an unchanged allocation alone rather than churning the collection")
    public void leavesAnUnchangedAllocationAlone() {
        final LoanCharge charge = penalty();

        final LoanTransaction repayment = repayment("16000.00");
        final LoanChargePaidBy original = new LoanChargePaidBy(repayment, charge, new BigDecimal("5000.00"), 1);
        repayment.getLoanChargesPaid().add(original);

        final LoanTransaction recomputed = repayment("16000.00");
        recomputed.getLoanChargesPaid().add(new LoanChargePaidBy(recomputed, charge, new BigDecimal("5000.00"), 1));

        repayment.updateLoanChargesPaid(recomputed.getLoanChargesPaid());

        assertEquals(1, repayment.getLoanChargesPaid().size());
        assertSame(original, repayment.getLoanChargesPaid().iterator().next(),
                "an identical allocation must not be torn down and rebuilt on every replay");
    }

    private LoanTransaction repayment(final String amount) {
        return LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null, DUE_DATE, null);
    }

    private LoanCharge penalty() {
        final Charge definition = mock(Charge.class);
        when(definition.isPenalty()).thenReturn(true);
        when(definition.getChargeTimeType()).thenReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        final BigDecimal amount = new BigDecimal("5000.00");
        return new LoanCharge(new Loan(), definition, amount, amount, ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT,
                DUE_DATE, ChargePaymentMode.REGULAR, 1, amount);
    }
}
