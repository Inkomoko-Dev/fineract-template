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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CGLT-658: system-generated late fees have been persisted at storage precision (6 dp) on a 2 dp currency, so a charge
 * can be left carrying a residue that no repayment can ever settle - the installment roll-up the repayment pays out is
 * rounded to the currency, the charge row is not. The loan then sits Active at zero outstanding because
 * {@code handleLoanRepaymentInFull} refuses the transition while any active charge looks unpaid.
 * <p>
 * Closure is therefore judged at the loan currency's precision: a residue that rounds to zero carries no real debt and
 * must not hold the loan open. Anything still owing at currency precision genuinely must.
 */
class LoanClosureChargePrecisionTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate REPAYMENT_DATE = LocalDate.of(2026, 6, 1);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, REPAYMENT_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
        ThreadLocalContextUtil.clearTenant();
    }

    /** A sub-cent residue rounds to 0.00 on a 2 dp currency, so it must not block closure. */
    @Test
    void closesWhenTheOnlyUnpaidChargeRoundsToZeroAtCurrencyPrecision() {
        final Loan loan = loanWithUnpaidCharge(new BigDecimal("0.004000"));

        handleLoanRepaymentInFull(loan);

        assertEquals(LoanStatus.CLOSED_OBLIGATIONS_MET.getValue(), ReflectionTestUtils.getField(loan, "loanStatus"),
                "a charge residue that rounds to zero at currency precision must not hold the loan open");
        assertEquals(REPAYMENT_DATE, ReflectionTestUtils.getField(loan, "closedOnDate"));
    }

    /**
     * Boundary: the residues seen on the stuck UAT loans (0.276877 on loan 430493, 0.29 on 430495) are more than a
     * cent, so they are still real debt at currency precision and the loan legitimately stays open. Those need the
     * residue settled or waived - widening the tolerance to swallow them would mask genuinely unpaid charges.
     */
    @Test
    void staysOpenWhenTheUnpaidChargeIsStillOwingAtCurrencyPrecision() {
        final Loan loan = loanWithUnpaidCharge(new BigDecimal("0.276877"));

        handleLoanRepaymentInFull(loan);

        assertEquals(LoanStatus.ACTIVE.getValue(), ReflectionTestUtils.getField(loan, "loanStatus"),
                "a charge still owing at currency precision must keep the loan open");
    }

    /** Guard: an ordinary wholly unpaid charge must keep blocking closure. */
    @Test
    void staysOpenWhenAChargeIsWhollyUnpaid() {
        final Loan loan = loanWithUnpaidCharge(new BigDecimal("107.53"));

        handleLoanRepaymentInFull(loan);

        assertEquals(LoanStatus.ACTIVE.getValue(), ReflectionTestUtils.getField(loan, "loanStatus"),
                "an unpaid charge must keep the loan open");
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private void handleLoanRepaymentInFull(final Loan loan) {
        ReflectionTestUtils.invokeMethod(loan, "handleLoanRepaymentInFull", REPAYMENT_DATE,
                new DefaultLoanLifecycleStateMachine(List.of(LoanStatus.values())));
    }

    /** An active loan whose schedule is repaid in full but which carries one active charge short by {@code outstanding}. */
    private Loan loanWithUnpaidCharge(final BigDecimal outstanding) {
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);

        final LoanCharge lateFee = mock(LoanCharge.class);
        when(lateFee.isActive()).thenReturn(true);
        when(lateFee.amount()).thenReturn(new BigDecimal("107.526877"));
        when(lateFee.isPaid()).thenReturn(false);
        when(lateFee.isWaived()).thenReturn(false);
        when(lateFee.amountOutstanding()).thenReturn(outstanding);

        final Loan loan = new Loan();
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "charges", Set.of(lateFee));
        return loan;
    }
}
