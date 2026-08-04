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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
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
 * Proves the premise the historical penalty waiver rests on: once a paid penalty is reset and waived, a full replay
 * reverses the repayment that settled it and reallocates the freed cash, with no repayment undone by hand.
 */
public class HistoricalPenaltyWaiverReallocationTransactionProcessorTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, null);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate JANUARY_DUE = LocalDate.of(2026, 1, 15);
    private static final LocalDate FEBRUARY_DUE = LocalDate.of(2026, 2, 15);
    private static final LocalDate MARCH_DUE = LocalDate.of(2026, 3, 15);

    private static final BigDecimal INSTALMENT_PRINCIPAL = new BigDecimal("10000.00");
    private static final BigDecimal INSTALMENT_INTEREST = new BigDecimal("1000.00");
    private static final BigDecimal PENALTY_AMOUNT = new BigDecimal("5000.00");

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

    static List<AbstractLoanRepaymentScheduleTransactionProcessor> processors() {
        return List.of(new PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(),
                new InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(),
                new CreocoreLoanRepaymentScheduleTransactionProcessor(),
                new FineractStyleLoanRepaymentScheduleTransactionProcessor(), new RBILoanRepaymentScheduleTransactionProcessor());
    }

    @ParameterizedTest
    @MethodSource("processors")
    void waivingAPaidPenaltyReversesTheSettlingRepaymentAndReallocatesTheFreedCash(
            final AbstractLoanRepaymentScheduleTransactionProcessor processor) {

        final List<LoanRepaymentScheduleInstallment> installments = threeMonthlyInstallments();
        final LoanCharge penalty = januaryPenalty();
        final Set<LoanCharge> charges = new LinkedHashSet<>(List.of(penalty));

        // January pays its instalment plus the penalty; February and March pay their instalments.
        final List<LoanTransaction> seed = new ArrayList<>(List.of(repayment(1L, JANUARY_DUE, "16000.00"),
                repayment(2L, FEBRUARY_DUE, "11000.00"), repayment(3L, MARCH_DUE, "11000.00")));

        // ---- Replay 1: establishes the "before" state, in which the penalty is settled by a repayment.
        final List<LoanTransaction> transactions = replayAndPersist(processor, seed, installments, charges);
        final LoanTransaction january = transactions.get(0);

        assertTrue(penalty.isPaid(), "fixture precondition: the penalty should be settled by the January repayment");
        assertAmount("5000.00", penalty.getAmountPaid(KES).getAmount());
        assertAmount("0.00", penalty.getAmountOutstanding(KES).getAmount());

        final BigDecimal penaltyAllocatedBefore = totalPenaltyAllocated(transactions, null);
        final BigDecimal nonPenaltyAllocatedBefore = totalNonPenaltyAllocated(transactions, null);
        assertAmount("5000.00", penaltyAllocatedBefore);

        // ---- The correction: free the cash the penalty had absorbed, then waive it.
        penalty.resetPaidAmount(KES);
        penalty.waive(KES, null);

        assertTrue(penalty.isWaived());
        assertAmount("5000.00", penalty.getAmountWaived(KES).getAmount());

        // ---- Replay 2: the whole point of the feature. No repayment is undone by hand.
        final ChangedTransactionDetail changed = processor.handleTransaction(DISBURSEMENT_DATE, transactions, KES, installments, charges);

        // The penalty must stay waived and must NOT be re-absorbed by any repayment.
        assertAmount("5000.00", penalty.getAmountWaived(KES).getAmount());
        assertAmount("0.00", penalty.getAmountPaid(KES).getAmount());
        assertAmount("0.00", penalty.getAmountOutstanding(KES).getAmount());
        assertFalse(penalty.isPaid(), "a waived penalty must not be marked paid again by the replay");

        // The repayment that had settled the penalty is reversed and replaced, rather than left inconsistent.
        assertTrue(january.isReversed(), "the repayment that settled the penalty must be reversed by the replay");
        final LoanTransaction replacementForJanuary = changed.getNewTransactionMappings().get(january.getId());
        assertNotNull(replacementForJanuary, "the replay must supply a replacement for the reversed repayment");
        assertAmount("0.00", replacementForJanuary.getPenaltyChargesPortion(KES).getAmount());

        // Nothing is allocated to the penalty anywhere across the surviving allocations.
        assertAmount("0.00", totalPenaltyAllocated(transactions, changed));

        // The freed 5,000 moves wholesale onto the other obligations or emerges as surplus - the conservation
        // identity the correction must satisfy under any configured allocation order.
        assertAmount(nonPenaltyAllocatedBefore.add(new BigDecimal("5000.00")).toPlainString(),
                totalNonPenaltyAllocated(transactions, changed));

        // The client never paid a different amount; only its allocation changed.
        assertAmount("38000.00", totalAllocated(transactions, changed));
    }

    @ParameterizedTest
    @MethodSource("processors")
    void aPartiallyWaivedPenaltyKeepsAbsorbingOnlyItsUnwaivedRemainder(
            final AbstractLoanRepaymentScheduleTransactionProcessor processor) {

        final List<LoanRepaymentScheduleInstallment> installments = threeMonthlyInstallments();
        final LoanCharge penalty = januaryPenalty();
        final Set<LoanCharge> charges = new LinkedHashSet<>(List.of(penalty));

        final List<LoanTransaction> seed = new ArrayList<>(List.of(repayment(1L, JANUARY_DUE, "16000.00"),
                repayment(2L, FEBRUARY_DUE, "11000.00"), repayment(3L, MARCH_DUE, "11000.00")));

        final List<LoanTransaction> transactions = replayAndPersist(processor, seed, installments, charges);
        assertTrue(penalty.isPaid());

        // Waive only 2,000 of the 5,000 penalty.
        penalty.resetPaidAmount(KES);
        penalty.waivePartially(KES, null, new BigDecimal("2000.00"));

        final ChangedTransactionDetail changed = processor.handleTransaction(DISBURSEMENT_DATE, transactions, KES, installments, charges);

        assertAmount("2000.00", penalty.getAmountWaived(KES).getAmount());
        // The unwaived 3,000 is still a genuine obligation and must still be collected by the repayments.
        assertAmount("3000.00", penalty.getAmountPaid(KES).getAmount());
        assertAmount("0.00", penalty.getAmountOutstanding(KES).getAmount());

        // Only the unwaived remainder is still collected from the repayments.
        assertAmount("3000.00", totalPenaltyAllocated(transactions, changed));
        assertAmount("38000.00", totalAllocated(transactions, changed));
    }

    /**
     * Replays, then does what the write service does with the result: gives each replacement an identity and drops the
     * reversed originals. Without this a second replay would be handed already-reversed transactions.
     */
    private List<LoanTransaction> replayAndPersist(final AbstractLoanRepaymentScheduleTransactionProcessor processor,
            final List<LoanTransaction> transactions, final List<LoanRepaymentScheduleInstallment> installments,
            final Set<LoanCharge> charges) {

        final ChangedTransactionDetail changed = processor.handleTransaction(DISBURSEMENT_DATE, transactions, KES, installments, charges);
        final List<LoanTransaction> persisted = new ArrayList<>();
        long nextId = 100L;
        for (final LoanTransaction original : transactions) {
            final LoanTransaction replacement = changed.getNewTransactionMappings().get(original.getId());
            if (replacement != null) {
                ReflectionTestUtils.setField(replacement, "id", nextId++);
                persisted.add(replacement);
            } else if (!original.isReversed()) {
                persisted.add(original);
            }
        }
        return persisted;
    }

    private BigDecimal totalPenaltyAllocated(final List<LoanTransaction> originals, final ChangedTransactionDetail changed) {
        BigDecimal total = BigDecimal.ZERO;
        for (final LoanTransaction survivor : survivingTransactions(originals, changed)) {
            total = total.add(survivor.getPenaltyChargesPortion(KES).getAmount());
        }
        return total;
    }

    /** Everything the money went to other than the penalty: principal, interest, fees and surplus. */
    private BigDecimal totalNonPenaltyAllocated(final List<LoanTransaction> originals, final ChangedTransactionDetail changed) {
        BigDecimal total = BigDecimal.ZERO;
        for (final LoanTransaction survivor : survivingTransactions(originals, changed)) {
            total = total.add(survivor.getPrincipalPortion(KES).getAmount()).add(survivor.getInterestPortion(KES).getAmount())
                    .add(survivor.getFeeChargesPortion(KES).getAmount()).add(survivor.getOverPaymentPortion(KES).getAmount());
        }
        return total;
    }

    private BigDecimal totalAllocated(final List<LoanTransaction> originals, final ChangedTransactionDetail changed) {
        return totalPenaltyAllocated(originals, changed).add(totalNonPenaltyAllocated(originals, changed));
    }

    /** Replacements carry the allocations; reversed originals must be ignored to avoid double counting. */
    private List<LoanTransaction> survivingTransactions(final List<LoanTransaction> originals, final ChangedTransactionDetail changed) {
        final List<LoanTransaction> surviving = new ArrayList<>();
        for (final LoanTransaction original : originals) {
            final LoanTransaction replacement = changed == null ? null : changed.getNewTransactionMappings().get(original.getId());
            if (replacement != null) {
                surviving.add(replacement);
            } else if (!original.isReversed()) {
                surviving.add(original);
            }
        }
        return surviving;
    }

    private List<LoanRepaymentScheduleInstallment> threeMonthlyInstallments() {
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>();
        installments.add(installment(1, DISBURSEMENT_DATE, JANUARY_DUE));
        installments.add(installment(2, JANUARY_DUE, FEBRUARY_DUE));
        installments.add(installment(3, FEBRUARY_DUE, MARCH_DUE));
        return installments;
    }

    private LoanRepaymentScheduleInstallment installment(final int number, final LocalDate from, final LocalDate due) {
        return new LoanRepaymentScheduleInstallment(null, number, from, due, INSTALMENT_PRINCIPAL, INSTALMENT_INTEREST, BigDecimal.ZERO,
                BigDecimal.ZERO, false, null);
    }

    private LoanCharge januaryPenalty() {
        final Charge definition = mock(Charge.class);
        when(definition.isPenalty()).thenReturn(true);
        when(definition.getChargeTimeType()).thenReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        return new LoanCharge(mock(Loan.class), definition, PENALTY_AMOUNT, PENALTY_AMOUNT, ChargeTimeType.SPECIFIED_DUE_DATE,
                ChargeCalculationType.FLAT, JANUARY_DUE, ChargePaymentMode.REGULAR, 1, PENALTY_AMOUNT);
    }

    private LoanTransaction repayment(final Long id, final LocalDate date, final String amount) {
        final LoanTransaction transaction = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null, date,
                null);
        // The replay only produces mappings for transactions that already exist.
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
