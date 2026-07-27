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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FutureInterestCancellationTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    private static final LocalDate PAYOFF_DATE = LocalDate.of(2026, 1, 16);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, PAYOFF_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void unearnedInterestIsCancelledNotWrittenOffWhenAccruedInterestIsSettled() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");

        final Money paid = installment.payAccruedInterestComponentAndCancelUnearned(PAYOFF_DATE, Money.of(KES, new BigDecimal("50.00")));

        assertAmount("50.00", paid.getAmount());
        assertAmount("50.00", installment.getInterestPaid(KES).getAmount());
        assertAmount("50.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", installment.getInterestWaived(KES).getAmount());
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
    }

    @Test
    void unearnedInterestIsNotCancelledUntilAccruedInterestIsFullySettled() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");

        final Money paid = installment.payAccruedInterestComponentAndCancelUnearned(PAYOFF_DATE, Money.of(KES, new BigDecimal("20.00")));

        assertAmount("20.00", paid.getAmount());
        assertAmount("0.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("80.00", installment.getInterestOutstanding(KES).getAmount());
    }

    @Test
    void futureInstallmentInterestIsFullyCancelledOnEarlySettlement() {
        final LoanRepaymentScheduleInstallment futureInstallment = new LoanRepaymentScheduleInstallment(null, 2, DUE_DATE.plusDays(1),
                DUE_DATE.plusMonths(1), new BigDecimal("1000.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);

        futureInstallment.payAccruedInterestComponentAndCancelUnearned(PAYOFF_DATE, Money.zero(KES));

        assertAmount("100.00", futureInstallment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", futureInstallment.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", futureInstallment.getInterestOutstanding(KES).getAmount());
    }

    @Test
    void interestAlreadyRecognisedInTheGlIsNeverCancelled() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");
        // accrual job already recognised 80.00 in the GL; pro-rata to the payoff date would only be 50.00
        installment.updateAccrualPortion(Money.of(KES, new BigDecimal("80.00")), Money.zero(KES), Money.zero(KES));

        final Money paid = installment.payAccruedInterestComponentAndCancelUnearned(PAYOFF_DATE, Money.of(KES, new BigDecimal("80.00")));

        assertAmount("80.00", paid.getAmount());
        assertAmount("20.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
    }

    @Test
    void cancellableInterestExcludesTheAccruedPortion() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");
        installment.updateAccrualPortion(Money.of(KES, new BigDecimal("80.00")), Money.zero(KES), Money.zero(KES));

        assertAmount("80.00", installment.getInterestPayableOnEarlySettlement(KES, PAYOFF_DATE).getAmount());
        assertAmount("20.00", installment.getCancellableFutureInterest(KES, PAYOFF_DATE).getAmount());
    }

    @Test
    void cancellableInterestIsProRataWhenNothingHasBeenAccrued() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");

        assertAmount("50.00", installment.getInterestPayableOnEarlySettlement(KES, PAYOFF_DATE).getAmount());
        assertAmount("50.00", installment.getCancellableFutureInterest(KES, PAYOFF_DATE).getAmount());
    }

    @Test
    void futureInterestCancellationIsItsOwnTransactionType() {
        assertEquals(30, LoanTransactionType.FUTURE_INTEREST_CANCELLATION.getValue());
        assertEquals(LoanTransactionType.FUTURE_INTEREST_CANCELLATION, LoanTransactionType.fromInt(30));
        assertTrue(LoanTransactionType.FUTURE_INTEREST_CANCELLATION.isFutureInterestCancellation());
        assertFalse(LoanTransactionType.FUTURE_INTEREST_CANCELLATION.isWriteOff());
        assertFalse(LoanTransactionType.FUTURE_INTEREST_CANCELLATION.isWaiveInterest());
        assertFalse(LoanTransactionType.FUTURE_INTEREST_CANCELLATION.isRepaymentType());
    }

    @Test
    void cancellationTransactionCarriesInterestOnlyAndLinksToThePayoff() {
        final LoanTransaction payoff = LoanTransaction.repaymentType(LoanTransactionType.PAY_OFF, null,
                Money.of(KES, new BigDecimal("500000.00")), null, PAYOFF_DATE, null);
        ReflectionTestUtils.setField(payoff, "id", 4711L);

        final LoanTransaction cancellation = LoanTransaction.futureInterestCancellation(null, null,
                Money.of(KES, new BigDecimal("50000.00")), PAYOFF_DATE, payoff);

        assertEquals(LoanTransactionType.FUTURE_INTEREST_CANCELLATION, cancellation.getTypeOf());
        assertAmount("50000.00", cancellation.getAmount(KES).getAmount());
        assertAmount("50000.00", cancellation.getInterestPortion(KES).getAmount());
        assertAmount("0.00", cancellation.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", cancellation.getFeeChargesPortion(KES).getAmount());
        assertAmount("0.00", cancellation.getPenaltyChargesPortion(KES).getAmount());
        assertEquals(PAYOFF_DATE, cancellation.getTransactionDate());
        assertEquals(4711L, cancellation.getOriginalTransactionId());
        assertTrue(cancellation.isFutureInterestCancellation());
        assertFalse(cancellation.isWriteOff());
        assertFalse(cancellation.isInterestWaiver());
    }

    @Test
    void cancellationTransactionIsInvisibleToTheAccountingProcessors() {
        final LoanTransactionEnumData data = LoanEnumerations.transactionType(LoanTransactionType.FUTURE_INTEREST_CANCELLATION);

        assertFalse(data.isWriteOff());
        assertFalse(data.isWaiveInterest());
        assertFalse(data.isWaiveCharges());
        assertFalse(data.isRepaymentType());
        assertFalse(data.isRepaymentAtDisbursement());
        assertFalse(data.isChargePayment());
        assertFalse(data.isAccrual());
        assertFalse(data.isPaymentOrReceipt());
        assertTrue(data.isFutureInterestCancellation());
    }

    @Test
    void payOffReceiptIsJournalledLikeAnyOtherRepayment() {
        final LoanTransactionEnumData data = LoanEnumerations.transactionType(LoanTransactionType.PAY_OFF);

        assertTrue(data.isRepaymentType());
        assertTrue(data.isPaymentOrReceipt());
        assertFalse(data.isWriteOff());
    }

    @Test
    void loanSummaryReportsCancelledInterestSeparatelyFromWrittenOff() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("500000.00", "50000.00");
        installment.payPrincipalComponent(PAYOFF_DATE, Money.of(KES, new BigDecimal("500000.00")));
        installment.payAccruedInterestComponentAndCancelUnearned(FROM_DATE, Money.zero(KES));

        final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
        summary.updateSummary(KES, Money.of(KES, new BigDecimal("500000.00")), List.of(installment), new LoanSummaryWrapper(), true, null);

        assertAmount("50000.00", summary.getTotalInterestCancelled());
        assertAmount("0.00", summary.getTotalInterestWrittenOff());
        assertAmount("0.00", summary.getTotalInterestWaived());
        assertAmount("0.00", summary.getTotalWrittenOff());
        assertAmount("0.00", summary.getTotalInterestOutstanding());
        assertAmount("0.00", summary.getTotalOutstanding());
        assertTrue(summary.isRepaidInFull(KES));
    }

    private static LoanRepaymentScheduleInstallment installmentWithInterest(final String principal, final String interest) {
        return new LoanRepaymentScheduleInstallment(null, 1, FROM_DATE, DUE_DATE, new BigDecimal(principal), new BigDecimal(interest),
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + (actual == null ? "null" : actual.toPlainString()));
    }
}
