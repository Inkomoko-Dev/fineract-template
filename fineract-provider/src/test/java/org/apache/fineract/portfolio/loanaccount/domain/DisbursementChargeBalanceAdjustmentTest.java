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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DisbursementChargeBalanceAdjustmentTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 6, 12);
    private static final LocalDate FIRST_DUE_DATE = LocalDate.of(2026, 7, 12);

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
    void downwardDisbursementChargeCreditReducesVisibleOutstandingBalance() {
        final LoanTransaction adjustment = chargeDecrease(new BigDecimal("800.00"));
        final Loan loan = loan(new BigDecimal("5000.00"), installments(new BigDecimal("5000.00"), new BigDecimal("250.00")),
                adjustment);

        loan.reprocessTransactions();

        assertAmount("4450.00", loan.getSummary().getTotalOutstanding());
        assertAmount("800.00", adjustment.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getInterestPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getOverPaymentPortion(KES).getAmount());
        assertNoAdvanceOrWriteOff(loan.getRepaymentScheduleInstallments());
    }

    @Test
    void downwardDisbursementChargeCreditCanClearVisibleOutstandingBalanceExactly() {
        final LoanTransaction adjustment = chargeDecrease(new BigDecimal("800.00"));
        final Loan loan = loan(new BigDecimal("500.00"), installments(new BigDecimal("500.00"), new BigDecimal("300.00")),
                adjustment);

        loan.reprocessTransactions();

        assertAmount("0.00", loan.getSummary().getTotalOutstanding());
        assertAmount("500.00", adjustment.getPrincipalPortion(KES).getAmount());
        assertAmount("300.00", adjustment.getInterestPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getOverPaymentPortion(KES).getAmount());
        assertNoAdvanceOrWriteOff(loan.getRepaymentScheduleInstallments());
    }

    @Test
    void downwardDisbursementChargeCreditCreatesOverpaymentOnlyAfterBalanceIsCleared() {
        final LoanTransaction adjustment = chargeDecrease(new BigDecimal("800.00"));
        final Loan loan = loan(new BigDecimal("300.00"), installments(new BigDecimal("300.00"), BigDecimal.ZERO), adjustment);

        loan.reprocessTransactions();

        assertAmount("0.00", loan.getSummary().getTotalOutstanding());
        assertAmount("300.00", adjustment.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getInterestPortion(KES).getAmount());
        assertAmount("500.00", adjustment.getOverPaymentPortion(KES).getAmount());
        assertAmount("500.00", (BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid"));
        assertNoAdvanceOrWriteOff(loan.getRepaymentScheduleInstallments());
    }

    @Test
    void upwardDisbursementChargeAdjustmentRestoresCustomerOverpaymentBeforeReopeningInstallmentBalance() {
        final LoanTransaction decrease = chargeDecrease(new BigDecimal("800.00"));
        final LoanTransaction increase = chargeIncrease(new BigDecimal("700.00"));
        final Loan loan = loan(new BigDecimal("300.00"), installments(new BigDecimal("300.00"), BigDecimal.ZERO), decrease,
                increase);

        loan.reprocessTransactions();

        final LoanRepaymentScheduleInstallment installment = loan.getRepaymentScheduleInstallments().get(0);
        assertAmount("200.00", loan.getSummary().getTotalOutstanding());
        assertAmount("200.00", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("500.00", increase.getOverPaymentPortion(KES).getAmount());
        assertAmount("200.00", increase.getPrincipalPortion(KES).getAmount());
        assertNullAmount((BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid"));
        assertNoAdvanceOrWriteOff(loan.getRepaymentScheduleInstallments());
    }

    @Test
    void reversedDownwardDisbursementChargeCreditDoesNotChangeVisibleOutstandingBalanceOrOverpayment() {
        final LoanTransaction adjustment = chargeDecrease(new BigDecimal("800.00"));
        adjustment.reverse();
        final Loan loan = loan(new BigDecimal("5000.00"), installments(new BigDecimal("5000.00"), new BigDecimal("250.00")),
                adjustment);

        loan.reprocessTransactions();

        assertAmount("5250.00", loan.getSummary().getTotalOutstanding());
        assertAmount("0.00", adjustment.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getInterestPortion(KES).getAmount());
        assertAmount("0.00", adjustment.getOverPaymentPortion(KES).getAmount());
        assertNullAmount((BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid"));
        assertNoAdvanceOrWriteOff(loan.getRepaymentScheduleInstallments());
    }

    @Test
    void multipleAdjustmentsCreditOnlyPreviouslyPaidChargeReduction() {
        final LoanTransaction increase = chargeIncrease(new BigDecimal("200.00"));
        final LoanTransaction decrease = chargeDecrease(new BigDecimal("400.00"));
        final Loan loan = loan(new BigDecimal("5000.00"), installments(new BigDecimal("5000.00"), new BigDecimal("250.00")),
                increase, decrease);

        loan.reprocessTransactions();

        assertAmount("4850.00", loan.getSummary().getTotalOutstanding());
        assertAmount("400.00", decrease.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", decrease.getInterestPortion(KES).getAmount());
        assertAmount("0.00", decrease.getOverPaymentPortion(KES).getAmount());
        assertNoAdvanceOrWriteOff(loan.getRepaymentScheduleInstallments());
    }

    @Test
    void downThenUpDisbursementChargeAdjustmentRestoresPriorCustomerBalanceCredit() {
        final LoanTransaction decrease = chargeDecrease(new BigDecimal("500.00"));
        final LoanTransaction increase = chargeIncrease(new BigDecimal("1000.00"));
        final Loan loan = loan(new BigDecimal("5000.00"), installments(new BigDecimal("5000.00"), new BigDecimal("250.00")),
                decrease, increase);

        loan.reprocessTransactions();

        final LoanRepaymentScheduleInstallment installment = loan.getRepaymentScheduleInstallments().get(0);
        assertAmount("5250.00", loan.getSummary().getTotalOutstanding());
        assertAmount("5000.00", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("250.00", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertNullAmount((BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid"));
    }

    @Test
    void downToZeroThenUpDisbursementChargeAdjustmentConsumesExistingCustomerCreditFirst() {
        final LoanTransaction decrease = chargeDecrease(new BigDecimal("1000.00"));
        final LoanTransaction increase = chargeIncrease(new BigDecimal("700.00"));
        final Loan loan = loan(new BigDecimal("5000.00"), installments(new BigDecimal("5000.00"), new BigDecimal("250.00")),
                decrease, increase);

        loan.reprocessTransactions();

        final LoanRepaymentScheduleInstallment installment = loan.getRepaymentScheduleInstallments().get(0);
        assertAmount("4950.00", loan.getSummary().getTotalOutstanding());
        assertAmount("4700.00", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("250.00", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertNullAmount((BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid"));
    }

    @Test
    void upDownUpDisbursementChargeAdjustmentsDoNotLeaveStaleCustomerBalanceCredit() {
        final LoanTransaction firstIncrease = chargeIncrease(new BigDecimal("200.00"));
        final LoanTransaction decrease = chargeDecrease(new BigDecimal("600.00"));
        final LoanTransaction secondIncrease = chargeIncrease(new BigDecimal("1100.00"));
        final Loan loan = loan(new BigDecimal("5000.00"), installments(new BigDecimal("5000.00"), new BigDecimal("250.00")),
                firstIncrease, decrease, secondIncrease);

        loan.reprocessTransactions();

        final LoanRepaymentScheduleInstallment installment = loan.getRepaymentScheduleInstallments().get(0);
        assertAmount("5250.00", loan.getSummary().getTotalOutstanding());
        assertAmount("5000.00", installment.getPrincipalOutstanding(KES).getAmount());
        assertAmount("250.00", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertNullAmount((BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid"));
    }

    private Loan loan(final BigDecimal principal, final List<LoanRepaymentScheduleInstallment> installments,
            final LoanTransaction... transactions) {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        when(loanProductRelatedDetail.getPrincipal()).thenReturn(Money.of(KES, principal));
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", installments);
        final List<LoanTransaction> loanTransactions = new ArrayList<>();
        loanTransactions.add(LoanTransaction.disbursement(mock(Office.class), Money.of(KES, principal), null, DISBURSEMENT_DATE, null));
        loanTransactions.addAll(Arrays.asList(transactions));
        ReflectionTestUtils.setField(loan, "loanTransactions", loanTransactions);
        ReflectionTestUtils.setField(loan, "charges", Collections.emptySet());
        loan.setHelpers(new DefaultLoanLifecycleStateMachine(Arrays.asList(LoanStatus.values())), new LoanSummaryWrapper(),
                new LoanRepaymentScheduleTransactionProcessorFactory());
        for (final LoanTransaction transaction : loanTransactions) {
            transaction.updateLoan(loan);
        }
        return loan;
    }

    private List<LoanRepaymentScheduleInstallment> installments(final BigDecimal principal, final BigDecimal interest) {
        return new ArrayList<>(List.of(new LoanRepaymentScheduleInstallment(null, 1, DISBURSEMENT_DATE, FIRST_DUE_DATE, principal,
                interest, BigDecimal.ZERO, BigDecimal.ZERO, false, null)));
    }

    private LoanTransaction chargeDecrease(final BigDecimal amount) {
        return LoanTransaction.disbursementChargeAdjustment(null, mock(Office.class), Money.of(KES, amount), DISBURSEMENT_DATE, true);
    }

    private LoanTransaction chargeIncrease(final BigDecimal amount) {
        return LoanTransaction.disbursementChargeAdjustment(null, mock(Office.class), Money.of(KES, amount), DISBURSEMENT_DATE, false);
    }

    private void assertNoAdvanceOrWriteOff(final List<LoanRepaymentScheduleInstallment> installments) {
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
            assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        }
    }

    private void assertAmount(final String expected, final BigDecimal actual) {
        assertNotNull(actual, "Expected " + expected + " but was null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + " but was " + actual);
    }

    private void assertNullAmount(final BigDecimal actual) {
        assertNull(actual);
    }
}
