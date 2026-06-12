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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

class LoanTransactionOutstandingBalanceTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 6, 12);
    private static final LocalDate FIRST_DUE_DATE = LocalDate.of(2026, 7, 12);
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 6, 12, 10, 0, 0, 0, ZoneOffset.UTC);

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
    void sameDayChargeAdjustmentsRestoreTransactionOutstandingBalanceToSummaryPrincipal() {
        final LoanTransaction repaymentAtDisbursement = repaymentAtDisbursement(3233645L, new BigDecimal("1000.00"));
        final LoanTransaction disbursement = disbursement(3233646L, new BigDecimal("5000.00"));
        final LoanTransaction decrease = chargeDecrease(3233647L, new BigDecimal("200.00"));
        final LoanTransaction increase = chargeIncrease(3233648L, new BigDecimal("700.00"));
        final Loan loan = loan(new BigDecimal("5000.00"), new BigDecimal("250.00"), repaymentAtDisbursement, disbursement,
                decrease, increase);

        loan.reprocessTransactions();

        assertAmount("5000.00", disbursement.getOutstandingLoanBalance());
        assertAmount("5000.00", repaymentAtDisbursement.getOutstandingLoanBalance());
        assertAmount("4800.00", decrease.getOutstandingLoanBalance());
        assertAmount("5000.00", increase.getOutstandingLoanBalance());
        assertAmount("200.00", increase.getPrincipalPortion(KES).getAmount());
        assertPrincipalSummaryAndSchedule(loan, "5000.00");
        assertAmount("5250.00", loan.getSummary().getTotalOutstanding());
    }

    @Test
    void downOnlyChargeAdjustmentReducesTransactionOutstandingBalanceAndSchedulePrincipal() {
        final LoanTransaction disbursement = disbursement(1L, new BigDecimal("5000.00"));
        final LoanTransaction repaymentAtDisbursement = repaymentAtDisbursement(2L, new BigDecimal("1000.00"));
        final LoanTransaction decrease = chargeDecrease(3L, new BigDecimal("200.00"));
        final Loan loan = loan(new BigDecimal("5000.00"), new BigDecimal("250.00"), disbursement,
                repaymentAtDisbursement, decrease);

        loan.reprocessTransactions();

        assertAmount("4800.00", decrease.getOutstandingLoanBalance());
        assertAmount("200.00", decrease.getPrincipalPortion(KES).getAmount());
        assertPrincipalSummaryAndSchedule(loan, "4800.00");
        assertAmount("5050.00", loan.getSummary().getTotalOutstanding());
    }

    @Test
    void upOnlyChargeAdjustmentDoesNotChangePrincipalOutstandingWithoutPriorCustomerCredit() {
        final LoanTransaction disbursement = disbursement(1L, new BigDecimal("5000.00"));
        final LoanTransaction repaymentAtDisbursement = repaymentAtDisbursement(2L, new BigDecimal("1000.00"));
        final LoanTransaction increase = chargeIncrease(3L, new BigDecimal("500.00"));
        final Loan loan = loan(new BigDecimal("5000.00"), new BigDecimal("250.00"), disbursement,
                repaymentAtDisbursement, increase);

        loan.reprocessTransactions();

        assertAmount("5000.00", increase.getOutstandingLoanBalance());
        assertAmount("0.00", increase.getPrincipalPortion(KES).getAmount());
        assertPrincipalSummaryAndSchedule(loan, "5000.00");
        assertAmount("5250.00", loan.getSummary().getTotalOutstanding());
    }

    @Test
    void downToZeroChargeAdjustmentStopsOutstandingBalanceAtZeroAndKeepsExcessAsOverpayment() {
        final LoanTransaction disbursement = disbursement(1L, new BigDecimal("300.00"));
        final LoanTransaction decrease = chargeDecrease(2L, new BigDecimal("800.00"));
        final Loan loan = loan(new BigDecimal("300.00"), BigDecimal.ZERO, disbursement, decrease);

        loan.reprocessTransactions();

        assertAmount("0.00", decrease.getOutstandingLoanBalance());
        assertAmount("300.00", decrease.getPrincipalPortion(KES).getAmount());
        assertAmount("500.00", decrease.getOverPaymentPortion(KES).getAmount());
        assertPrincipalSummaryAndSchedule(loan, "0.00");
        assertAmount("0.00", loan.getSummary().getTotalOutstanding());
        assertAmount("500.00", (BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid"));
    }

    @Test
    void upAfterOverpaymentConsumesOverpaymentBeforeReopeningPrincipalOutstandingBalance() {
        final LoanTransaction disbursement = disbursement(1L, new BigDecimal("300.00"));
        final LoanTransaction decrease = chargeDecrease(2L, new BigDecimal("800.00"));
        final LoanTransaction increase = chargeIncrease(3L, new BigDecimal("700.00"));
        final Loan loan = loan(new BigDecimal("300.00"), BigDecimal.ZERO, disbursement, decrease, increase);

        loan.reprocessTransactions();

        assertAmount("0.00", decrease.getOutstandingLoanBalance());
        assertAmount("200.00", increase.getOutstandingLoanBalance());
        assertAmount("500.00", increase.getOverPaymentPortion(KES).getAmount());
        assertAmount("200.00", increase.getPrincipalPortion(KES).getAmount());
        assertPrincipalSummaryAndSchedule(loan, "200.00");
        assertAmount("200.00", loan.getSummary().getTotalOutstanding());
        assertNull((BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid"));
    }

    @Test
    void reversedChargeAdjustmentDoesNotChangeTransactionOutstandingBalanceOrSummaryPrincipal() {
        final LoanTransaction disbursement = disbursement(1L, new BigDecimal("5000.00"));
        final LoanTransaction decrease = chargeDecrease(2L, new BigDecimal("800.00"));
        decrease.reverse();
        final Loan loan = loan(new BigDecimal("5000.00"), new BigDecimal("250.00"), disbursement, decrease);

        loan.reprocessTransactions();

        assertAmount("5000.00", disbursement.getOutstandingLoanBalance());
        assertNull(decrease.getOutstandingLoanBalance());
        assertPrincipalSummaryAndSchedule(loan, "5000.00");
        assertAmount("5250.00", loan.getSummary().getTotalOutstanding());
    }

    private Loan loan(final BigDecimal principal, final BigDecimal interest, final LoanTransaction... transactions) {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        when(loanProductRelatedDetail.getPrincipal()).thenReturn(Money.of(KES, principal));
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", installments(principal, interest));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(Arrays.asList(transactions)));
        ReflectionTestUtils.setField(loan, "charges", Collections.emptySet());
        loan.setHelpers(new DefaultLoanLifecycleStateMachine(Arrays.asList(LoanStatus.values())), new LoanSummaryWrapper(),
                new LoanRepaymentScheduleTransactionProcessorFactory());
        for (final LoanTransaction transaction : transactions) {
            transaction.updateLoan(loan);
        }
        return loan;
    }

    private List<LoanRepaymentScheduleInstallment> installments(final BigDecimal principal, final BigDecimal interest) {
        return new ArrayList<>(List.of(new LoanRepaymentScheduleInstallment(null, 1, DISBURSEMENT_DATE, FIRST_DUE_DATE, principal,
                interest, BigDecimal.ZERO, BigDecimal.ZERO, false, null)));
    }

    private LoanTransaction disbursement(final Long id, final BigDecimal amount) {
        return transaction(id, LoanTransaction.disbursement(mock(Office.class), Money.of(KES, amount), null, DISBURSEMENT_DATE, null));
    }

    private LoanTransaction repaymentAtDisbursement(final Long id, final BigDecimal amount) {
        final LoanTransaction transaction = transaction(id, LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, amount), null, DISBURSEMENT_DATE, null));
        transaction.updateRepaymentAtDisbursementComponents(Money.of(KES, amount), Money.zero(KES));
        return transaction;
    }

    private LoanTransaction chargeDecrease(final Long id, final BigDecimal amount) {
        return transaction(id,
                LoanTransaction.disbursementChargeAdjustment(null, mock(Office.class), Money.of(KES, amount), DISBURSEMENT_DATE, true));
    }

    private LoanTransaction chargeIncrease(final Long id, final BigDecimal amount) {
        return transaction(id,
                LoanTransaction.disbursementChargeAdjustment(null, mock(Office.class), Money.of(KES, amount), DISBURSEMENT_DATE, false));
    }

    private LoanTransaction transaction(final Long id, final LoanTransaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", id);
        transaction.setCreatedDate(CREATED_AT);
        return transaction;
    }

    private void assertPrincipalSummaryAndSchedule(final Loan loan, final String expectedPrincipalOutstanding) {
        assertAmount(expectedPrincipalOutstanding, loan.getSummary().getTotalPrincipalOutstanding());
        BigDecimal schedulePrincipalOutstanding = BigDecimal.ZERO;
        BigDecimal scheduleTotalOutstanding = BigDecimal.ZERO;
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            schedulePrincipalOutstanding = schedulePrincipalOutstanding
                    .add(installment.getPrincipalOutstanding(KES).getAmount());
            scheduleTotalOutstanding = scheduleTotalOutstanding.add(installment.getTotalOutstanding(KES).getAmount());
            assertAmount("0.00", installment.getTotalPaidInAdvance(KES).getAmount());
            assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        }
        assertAmount(expectedPrincipalOutstanding, schedulePrincipalOutstanding);
        assertAmount(loan.getSummary().getTotalOutstanding().toPlainString(), scheduleTotalOutstanding);
    }

    private void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + " but was " + actual);
    }
}
