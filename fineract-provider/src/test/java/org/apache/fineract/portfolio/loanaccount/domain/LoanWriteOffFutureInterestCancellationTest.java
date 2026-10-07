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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CGLT-632: writing a loan off must write off only the interest earned/recognised as at the user-selected write-off
 * date, cancel the future unaccrued remainder as a separate GL-free audit transaction, and unwind cleanly on undo.
 *
 * Uses the ticket's accrual-basis worked example: principal 100,000, scheduled interest 20,000, of which 6,000 has been
 * accrued to the GL - so the accounting write-off is 106,000 and 14,000 is cancelled.
 */
class LoanWriteOffFutureInterestCancellationTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    /** Day 6 of 30: pro-rata earned would be 4,000, but 6,000 is already recognised in the GL, so 6,000 wins. */
    private static final LocalDate WRITE_OFF_DATE = LocalDate.of(2026, 1, 7);
    /** Deliberately far from the write-off date - the split must never use the business date. */
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 15);
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    /** R7 - the write-off transaction itself carries only principal + recognised interest, not the full 120,000. */
    @Test
    void writeOffTransactionExcludesFutureUnaccruedInterest() {
        final Loan loan = writtenOffLoan();
        final LoanTransaction writeOff = writeOffTransactionOf(loan);

        assertAmount("106000.00", writeOff.getAmount(KES).getAmount());
        assertAmount("100000.00", writeOff.getPrincipalPortion(KES).getAmount());
        assertAmount("6000.00", writeOff.getInterestPortion(KES).getAmount());
    }

    /** R8 - the cancelled future interest is its own transaction, linked to the write-off and free of any GL impact. */
    @Test
    void cancelledFutureInterestIsALinkedGlFreeTransaction() {
        final Loan loan = writtenOffLoan();
        final LoanTransaction writeOff = writeOffTransactionOf(loan);

        final LoanTransaction cancellation = loan.reconcileFutureInterestCancellation(writeOff, WRITE_OFF_DATE);

        assertNotNull(cancellation);
        assertEquals(LoanTransactionType.FUTURE_INTEREST_CANCELLATION, cancellation.getTypeOf());
        assertAmount("14000.00", cancellation.getInterestPortion(KES).getAmount());
        assertAmount("0.00", cancellation.getPrincipalPortion(KES).getAmount());
        assertEquals(writeOff.getId(), cancellation.getOriginalTransactionId());
        // Non-monetary => excluded from every balance/reprocessing list, and matches no accounting predicate.
        assertTrue(cancellation.isNonMonetaryTransaction());
        assertFalse(cancellation.getTypeOf().isWriteOff());
        assertFalse(cancellation.getTypeOf().isWaiveInterest());
        assertFalse(cancellation.getTypeOf().isRepaymentType());
        assertFalse(cancellation.getTypeOf().isAccrual());
    }

    /** R9 - the summary reports written-off and cancelled interest separately, and clears the outstanding balance. */
    @Test
    void summaryReportsWrittenOffAndCancelledInterestSeparately() {
        final Loan loan = writtenOffLoan();

        assertAmount("100000.00", loan.getSummary().getTotalPrincipalWrittenOff());
        assertAmount("6000.00", loan.getSummary().getTotalInterestWrittenOff());
        assertAmount("14000.00", loan.getSummary().getTotalInterestCancelled());
        assertAmount("0.00", loan.getSummary().getTotalOutstanding());
    }

    /** R10 (AC 1) - the split uses the user-selected write-off date, never the business date. */
    @Test
    void splitUsesTheSelectedWriteOffDateNotTheBusinessDate() {
        // No GL accrual here, so the split is pure pro-rata and therefore sensitive to which date is used.
        // Business date is 15 March (past due date => would earn the full 20,000); write-off date is day 6 => 4,000.
        final Loan loan = loan(disbursement());
        writeOff(loan, WRITE_OFF_DATE);

        assertAmount("4000.00", loan.getSummary().getTotalInterestWrittenOff());
        assertAmount("16000.00", loan.getSummary().getTotalInterestCancelled());
    }

    /** R11 - undoing a write-off must leave the loan exactly as it was before: nothing written off, nothing cancelled. */
    @Test
    void undoingAWriteOffRestoresTheLoanToItsPreWriteOffState() {
        final Loan loan = writtenOffLoan();
        final LoanTransaction writeOff = writeOffTransactionOf(loan);
        final LoanTransaction cancellation = loan.reconcileFutureInterestCancellation(writeOff, WRITE_OFF_DATE);
        assertNotNull(cancellation);

        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.CLOSED_WRITTEN_OFF.getValue());
        // Interest recalculation is disabled on this fixture, so the schedule generator is never consulted.
        loan.undoWrittenOff(new ArrayList<>(), new ArrayList<>(), mock(ScheduleGeneratorDTO.class));

        // Both audit transactions are reversed...
        assertTrue(writeOff.isReversed());
        assertTrue(cancellation.isReversed());
        // ...the schedule is fully reinstated...
        final LoanRepaymentScheduleInstallment installment = loan.getRepaymentScheduleInstallments().get(0);
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getPrincipalWrittenOff(KES).getAmount());
        assertAmount("20000.00", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("100000.00", installment.getPrincipalOutstanding(KES).getAmount());
        // ...and so are the summary totals and the loan status.
        assertAmount("0.00", loan.getSummary().getTotalInterestWrittenOff());
        assertAmount("0.00", loan.getSummary().getTotalInterestCancelled());
        assertAmount("0.00", loan.getSummary().getTotalPrincipalWrittenOff());
        assertAmount("120000.00", loan.getSummary().getTotalOutstanding());
        assertEquals(LoanStatus.ACTIVE.getValue(), loan.getLoanStatus());
        assertAmount("0.00", loan.getTotalInterestCancelled().getAmount());
    }

    /** R12 - a loan with nothing left to cancel produces no cancellation transaction at all. */
    @Test
    void noCancellationTransactionWhenThereIsNoFutureInterest() {
        final Loan loan = loan(disbursement());
        // Written off on the due date: every last shilling of interest has been earned.
        writeOff(loan, DUE_DATE);
        final LoanTransaction writeOff = writeOffTransactionOf(loan);

        assertAmount("20000.00", loan.getSummary().getTotalInterestWrittenOff());
        assertAmount("0.00", loan.getSummary().getTotalInterestCancelled());
        assertNull(loan.reconcileFutureInterestCancellation(writeOff, DUE_DATE));
    }

    /**
     * R13 - a write-off is replayed by the reprocess loop, which must reproduce the same split. Without this the
     * interest silently re-inflates to the full 20,000 on the next reprocess, quietly undoing the fix.
     */
    @Test
    void reprocessingAWrittenOffLoanPreservesTheSplit() {
        final Loan loan = writtenOffLoan();

        loan.reprocessTransactions();

        assertAmount("6000.00", loan.getSummary().getTotalInterestWrittenOff());
        assertAmount("14000.00", loan.getSummary().getTotalInterestCancelled());
        assertAmount("0.00", loan.getSummary().getTotalOutstanding());
    }

    /**
     * R14 (FR7) - the pre-confirmation breakdown, previewed before anything is written off. Recognised and future
     * interest must always add back up to the interest outstanding, so the user is never shown a partial picture.
     */
    @Test
    void breakdownPreviewSplitsInterestWithoutWritingAnythingOff() {
        final Loan loan = loan(disbursement());
        loan.getRepaymentScheduleInstallments().get(0).updateAccrualPortion(Money.of(KES, new BigDecimal("6000.00")), Money.zero(KES),
                Money.zero(KES));

        final BigDecimal recognised = loan.getInterestRecognisedAsOf(WRITE_OFF_DATE).getAmount();
        final BigDecimal future = loan.getFutureInterestToCancelAsOf(WRITE_OFF_DATE).getAmount();

        assertAmount("6000.00", recognised);
        assertAmount("14000.00", future);
        assertAmount("20000.00", recognised.add(future));
        // Previewing must not mutate the schedule.
        assertAmount("0.00", loan.getRepaymentScheduleInstallments().get(0).getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", loan.getRepaymentScheduleInstallments().get(0).getInterestCancelled(KES).getAmount());
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    /** The ticket's accrual-basis example: 6,000 of the 20,000 scheduled interest already recognised in the GL. */
    private Loan writtenOffLoan() {
        final Loan loan = loan(disbursement());
        loan.getRepaymentScheduleInstallments().get(0).updateAccrualPortion(Money.of(KES, new BigDecimal("6000.00")), Money.zero(KES),
                Money.zero(KES));
        writeOff(loan, WRITE_OFF_DATE);
        return loan;
    }

    private void writeOff(final Loan loan, final LocalDate writeOffDate) {
        final LoanTransaction writeOff = transaction(9L, LoanTransaction.writeoff(loan, mock(Office.class), writeOffDate, null));
        loan.addLoanTransaction(writeOff);
        new LoanRepaymentScheduleTransactionProcessorFactory().determineProcessor(null).handleWriteOff(writeOff, KES,
                loan.getRepaymentScheduleInstallments());
        loan.updateLoanSummaryDerivedFields();
    }

    private LoanTransaction writeOffTransactionOf(final Loan loan) {
        final LoanTransaction writeOff = loan.findWriteOffTransaction();
        assertNotNull(writeOff, "expected a write-off transaction on the loan");
        return writeOff;
    }

    private Loan loan(final LoanTransaction... transactions) {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(detail.getCurrency()).thenReturn(KES);
        when(detail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("100000.00")));
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "office", mock(Office.class));
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", detail);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", installments());
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(List.of(transactions)));
        ReflectionTestUtils.setField(loan, "charges", Collections.emptySet());
        loan.setHelpers(new DefaultLoanLifecycleStateMachine(List.of(LoanStatus.values())), new LoanSummaryWrapper(),
                new LoanRepaymentScheduleTransactionProcessorFactory());
        for (final LoanTransaction transaction : transactions) {
            transaction.updateLoan(loan);
        }
        return loan;
    }

    private List<LoanRepaymentScheduleInstallment> installments() {
        return new ArrayList<>(List.of(new LoanRepaymentScheduleInstallment(null, 1, DISBURSEMENT_DATE, DUE_DATE,
                new BigDecimal("100000.00"), new BigDecimal("20000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false, null)));
    }

    private LoanTransaction disbursement() {
        return transaction(1L, LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal("100000.00")), null,
                DISBURSEMENT_DATE, null));
    }

    private LoanTransaction transaction(final Long id, final LoanTransaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", id);
        transaction.setCreatedDate(CREATED_AT);
        return transaction;
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + (actual == null ? "null" : actual.toPlainString()));
    }
}
