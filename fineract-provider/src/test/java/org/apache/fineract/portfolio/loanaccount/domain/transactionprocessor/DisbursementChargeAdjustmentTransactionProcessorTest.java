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
package org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DisbursementChargeAdjustmentTransactionProcessorTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 6, 11);
    private static final LocalDate FIRST_DUE_DATE = LocalDate.of(2026, 7, 11);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DISBURSEMENT_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void downwardDisbursementChargeAdjustmentReducesPrincipalBalanceWithoutAdvancePaymentOrInterestWriteOff() {
        final LoanRepaymentScheduleInstallment installment = installment(1, new BigDecimal("833.33"),
                new BigDecimal("41.67"));
        final LoanTransaction adjustment = chargeDecrease(new BigDecimal("300.00"));

        final ChangedTransactionDetail changedTransactionDetail = processor().handleTransaction(DISBURSEMENT_DATE,
                new ArrayList<>(List.of(adjustment)), KES, new ArrayList<>(List.of(installment)), Set.of());

        assertTrue(changedTransactionDetail.getNewTransactionMappings().isEmpty());
        assertAmount("300.00", adjustment.getAmount(KES).getAmount());
        assertAmount("300.00", adjustment.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getInterestPortion(KES).getAmount());
        assertAmount("-300.00", adjustment.getFeeChargesPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getOverPaymentPortion(KES).getAmount());
        assertAmount("533.33", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("41.67", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
    }

    @Test
    void downwardDisbursementChargeAdjustmentKeepsExcessAsOverpaymentAfterPrincipalBalanceIsCleared() {
        final LoanRepaymentScheduleInstallment installment = installment(1, new BigDecimal("200.00"), new BigDecimal("50.00"));
        final LoanTransaction adjustment = chargeDecrease(new BigDecimal("300.00"));

        processor().handleTransaction(DISBURSEMENT_DATE, new ArrayList<>(List.of(adjustment)), KES,
                new ArrayList<>(List.of(installment)), Set.of());

        assertAmount("300.00", adjustment.getAmount(KES).getAmount());
        assertAmount("200.00", adjustment.getPrincipalPortion(KES).getAmount());
        assertAmount("50.00", adjustment.getInterestPortion(KES).getAmount());
        assertAmount("-300.00", adjustment.getFeeChargesPortion(KES).getAmount());
        assertAmount("50.00", adjustment.getOverPaymentPortion(KES).getAmount());
        assertAmount("0.00", installment.getTotalOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
    }

    @Test
    void downwardDisbursementChargeAdjustmentDoesNotForceExistingRepaymentToBeRecreated() {
        final LoanRepaymentScheduleInstallment installment = installment(1, new BigDecimal("833.33"),
                new BigDecimal("41.67"));
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("100.00")),
                null, FIRST_DUE_DATE, null);
        ReflectionTestUtils.setField(repayment, "id", 10L);
        repayment.updateComponents(Money.of(KES, new BigDecimal("100.00")), Money.zero(KES), Money.zero(KES),
                Money.zero(KES));
        final LoanTransaction adjustment = chargeDecrease(new BigDecimal("300.00"));

        final ChangedTransactionDetail changedTransactionDetail = processor().handleTransaction(DISBURSEMENT_DATE,
                new ArrayList<>(List.of(repayment, adjustment)), KES, new ArrayList<>(List.of(installment)), Set.of());

        assertTrue(changedTransactionDetail.getNewTransactionMappings().isEmpty());
        assertAmount("100.00", repayment.getPrincipalPortion(KES).getAmount());
        assertAmount("300.00", adjustment.getPrincipalPortion(KES).getAmount());
        assertAmount("433.33", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
    }

    @Test
    void repaymentAtDisbursementWithoutExplicitOverpaymentClearsStalePrincipalAllocation() {
        final LoanRepaymentScheduleInstallment installment = installment(1, new BigDecimal("833.33"),
                new BigDecimal("41.67"));
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("1000.00")), null, DISBURSEMENT_DATE, null);
        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("1000.00")),
                Money.zero(KES), Money.zero(KES));
        repaymentAtDisbursement.updateComponents(Money.of(KES, new BigDecimal("833.33")), Money.zero(KES),
                Money.zero(KES), Money.zero(KES));

        processor().handleTransaction(DISBURSEMENT_DATE, new ArrayList<>(List.of(repaymentAtDisbursement)), KES,
                new ArrayList<>(List.of(installment)), Set.of());

        assertAmount("0.00", repaymentAtDisbursement.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", repaymentAtDisbursement.getInterestPortion(KES).getAmount());
        assertAmount("1000.00", repaymentAtDisbursement.getFeeChargesPortion(KES).getAmount());
        assertAmount("0.00", repaymentAtDisbursement.getOverPaymentPortion(KES).getAmount());
        assertAmount("833.33", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
    }

    @Test
    void repaymentAtDisbursementOverpaymentIsRecomputedFromExplicitOverpaymentOnly() {
        final LoanRepaymentScheduleInstallment installment = installment(1, new BigDecimal("833.33"),
                new BigDecimal("41.67"));
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("1300.00")), null, DISBURSEMENT_DATE, null);
        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("1000.00")),
                Money.zero(KES), Money.of(KES, new BigDecimal("300.00")));
        repaymentAtDisbursement.updateComponents(Money.of(KES, new BigDecimal("100.00")), Money.zero(KES),
                Money.zero(KES), Money.zero(KES));

        processor().handleTransaction(DISBURSEMENT_DATE, new ArrayList<>(List.of(repaymentAtDisbursement)), KES,
                new ArrayList<>(List.of(installment)), Set.of());

        assertAmount("300.00", repaymentAtDisbursement.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", repaymentAtDisbursement.getInterestPortion(KES).getAmount());
        assertAmount("1000.00", repaymentAtDisbursement.getFeeChargesPortion(KES).getAmount());
        assertAmount("0.00", repaymentAtDisbursement.getOverPaymentPortion(KES).getAmount());
        assertAmount("533.33", installment.getPrincipalOutstanding(KES).getAmount());
    }

    @Test
    void downwardDisbursementChargeAdjustmentAfterInstallmentIsClearedBecomesCustomerOverpayment() {
        final LoanRepaymentScheduleInstallment installment = installment(1, new BigDecimal("833.33"),
                new BigDecimal("41.67"));
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("875.00")),
                null, FIRST_DUE_DATE, null);
        final LoanTransaction adjustment = chargeDecrease(new BigDecimal("300.00"), FIRST_DUE_DATE.plusDays(1));

        processor().handleTransaction(DISBURSEMENT_DATE, new ArrayList<>(List.of(repayment, adjustment)), KES,
                new ArrayList<>(List.of(installment)), Set.of());

        assertAmount("0.00", adjustment.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getInterestPortion(KES).getAmount());
        assertAmount("-300.00", adjustment.getFeeChargesPortion(KES).getAmount());
        assertAmount("300.00", adjustment.getOverPaymentPortion(KES).getAmount());
        assertAmount("0.00", installment.getTotalOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
    }

    @Test
    void upwardDisbursementChargeAdjustmentRestoresPriorDownwardCreditDuringReplay() {
        final LoanRepaymentScheduleInstallment installment = installment(1, new BigDecimal("833.33"),
                new BigDecimal("41.67"));
        final LoanTransaction decrease = chargeDecrease(new BigDecimal("500.00"));
        final LoanTransaction increase = chargeIncrease(new BigDecimal("1000.00"));

        processor().handleTransaction(DISBURSEMENT_DATE, new ArrayList<>(List.of(decrease, increase)), KES,
                new ArrayList<>(List.of(installment)), Set.of());

        assertAmount("833.33", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("41.67", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
    }

    @Test
    void upwardDisbursementChargeAdjustmentConsumesPriorChargeOverpaymentBeforeInstallmentCredit() {
        final LoanRepaymentScheduleInstallment installment = installment(1, new BigDecimal("300.00"), BigDecimal.ZERO);
        final LoanTransaction decrease = chargeDecrease(new BigDecimal("800.00"));
        final LoanTransaction increase = chargeIncrease(new BigDecimal("700.00"));

        processor().handleTransaction(DISBURSEMENT_DATE, new ArrayList<>(List.of(decrease, increase)), KES,
                new ArrayList<>(List.of(installment)), Set.of());

        assertAmount("500.00", increase.getOverPaymentPortion(KES).getAmount());
        assertAmount("200.00", increase.getPrincipalPortion(KES).getAmount());
        assertAmount("200.00", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
    }

    private PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor processor() {
        return new PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor();
    }

    private LoanRepaymentScheduleInstallment installment(final int installmentNumber, final BigDecimal principal,
            final BigDecimal interest) {
        return new LoanRepaymentScheduleInstallment(null, installmentNumber, DISBURSEMENT_DATE, FIRST_DUE_DATE, principal,
                interest, BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }

    private LoanTransaction chargeDecrease(final BigDecimal amount) {
        return chargeDecrease(amount, DISBURSEMENT_DATE);
    }

    private LoanTransaction chargeDecrease(final BigDecimal amount, final LocalDate transactionDate) {
        return LoanTransaction.disbursementChargeAdjustment(null, mock(Office.class), Money.of(KES, amount), transactionDate, true);
    }

    private LoanTransaction chargeIncrease(final BigDecimal amount) {
        return LoanTransaction.disbursementChargeAdjustment(null, mock(Office.class), Money.of(KES, amount), DISBURSEMENT_DATE, false);
    }

    private void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
