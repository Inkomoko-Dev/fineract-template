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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.CreocoreLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.RBILoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CGLT-562: a disbursement-charge edit reverses and replays the whole loan. On a loan that has already run past its
 * maturity date every period has elapsed and its interest is fully earned, so replaying a historical payment through
 * the prepayment handler would retro-forgo interest the borrower already paid - the cash then has nowhere to land and
 * surfaces as a phantom overpayment (loan 401626 / LOAN/19926/2024: 19,982.06 of interest retro-forgone, 17,116.85 phantom
 * overpaid, plus a Dr Interest Income / Cr Excess-payment journal that de-recognised earned income).
 *
 * <p>
 * Past maturity the payment must therefore be allocated by the ordinary on-time handler. Before maturity nothing
 * changes - the prepayment handler still writes off the unearned remainder, which is the CGLT-535 prepayment behaviour.
 */
class MaturedLoanAdvancePaymentTransactionProcessorTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    // Halfway through the period and before the due date -> classified as an "in advance" transaction.
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 1, 16);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    static List<AbstractLoanRepaymentScheduleTransactionProcessor> processors() {
        return List.of(new PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(),
                new InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(),
                new CreocoreLoanRepaymentScheduleTransactionProcessor(),
                new FineractStyleLoanRepaymentScheduleTransactionProcessor(),
                new RBILoanRepaymentScheduleTransactionProcessor());
    }

    @ParameterizedTest
    @MethodSource("processors")
    void replayingAPaymentOnAMaturedLoanChargesTheFullInterestAndWritesOffNothing(
            final AbstractLoanRepaymentScheduleTransactionProcessor processor) {
        // Business date is well past the loan's maturity, as it is when a closed loan's charge is edited months later.
        withBusinessDate(LocalDate.of(2026, 6, 1));
        final LoanRepaymentScheduleInstallment installment = installmentOwnedByLoanMaturingOn(DUE_DATE);

        applyRepaymentOf("550.00", processor, installment);

        // The whole period has run, so all 50.00 of interest is earned and payable - none of it is "future".
        assertAmount("50.00", installment.getInterestPaid(KES).getAmount());
        assertAmount("0.00", interestForgone(installment));
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("500.00", installment.getPrincipalCompleted(KES).getAmount());
    }

    @ParameterizedTest
    @MethodSource("processors")
    void replayingAPaymentBeforeMaturityStillWritesOffTheUnearnedRemainder(
            final AbstractLoanRepaymentScheduleTransactionProcessor processor) {
        // Loan is still running: CGLT-658 prepayment behaviour must be preserved exactly.
        withBusinessDate(PAYMENT_DATE);
        final LoanRepaymentScheduleInstallment installment = installmentOwnedByLoanMaturingOn(LocalDate.of(2026, 12, 31));

        applyRepaymentOf("550.00", processor, installment);

        // Paid 15 of the 30 days -> half the interest is earned, the unearned half is written off.
        assertAmount("25.00", installment.getInterestPaid(KES).getAmount());
        assertAmount("25.00", interestForgone(installment));
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("500.00", installment.getPrincipalCompleted(KES).getAmount());
    }

    private void applyRepaymentOf(final String amount, final AbstractLoanRepaymentScheduleTransactionProcessor processor,
            final LoanRepaymentScheduleInstallment installment) {
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(List.of(installment));
        final Money repaymentAmount = Money.of(KES, new BigDecimal(amount));
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), repaymentAmount, null, PAYMENT_DATE, null);
        processor.handleTransaction(repayment, KES, installments, Set.of());
    }

    private LoanRepaymentScheduleInstallment installmentOwnedByLoanMaturingOn(final LocalDate maturityDate) {
        final Loan loan = mock(Loan.class);
        when(loan.getMaturityDate()).thenReturn(maturityDate);
        when(loan.getExpectedMaturityDate()).thenReturn(maturityDate);
        return new LoanRepaymentScheduleInstallment(loan, 1, FROM_DATE, DUE_DATE, new BigDecimal("500.00"), new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }

    private static void withBusinessDate(final LocalDate businessDate) {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, businessDate)));
    }

    /**
     * Interest the schedule has given up rather than collected. Deliberately derived rather than read off a single
     * column: pre-CGLT-658 the prepayment handler books it as written-off, from CGLT-658 onwards as cancelled, and
     * this test has to keep meaning the same thing on both sides of that merge.
     */
    private static BigDecimal interestForgone(final LoanRepaymentScheduleInstallment installment) {
        return installment.getInterestCharged(KES).minus(installment.getInterestPaid(KES))
                .minus(installment.getInterestWaived(KES)).getAmount();
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
