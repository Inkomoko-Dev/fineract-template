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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.stream.Collectors;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanUndoWriteOffReversalTransactionTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final CurrencyData KES_DATA = new CurrencyData("KES", "Kenyan Shilling", 2, 0, "KSh", "currency.KES");
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    private static final LocalDate WRITE_OFF_DATE = LocalDate.of(2026, 1, 7);
    private static final LocalDate UNDO_DATE = LocalDate.of(2026, 3, 15);
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final Long DISBURSEMENT_ID = 1L;
    private static final Long WRITE_OFF_ID = 9L;
    private static final Long CANCELLATION_ID = 10L;
    private static final Long REPAYMENT_ID = 5L;
    private static final LocalDate REPAYMENT_DATE = LocalDate.of(2026, 1, 5);
    private static final String WRITE_OFF_REVERSAL = "Write-off Reversal";

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, UNDO_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void undoWriteOffAddsExactlyOneNewTransactionToTheLoan() {
        final Loan loan = writtenOffLoan();
        final List<LoanTransaction> before = new ArrayList<>(loan.getLoanTransactions());

        undoWriteOff(loan);

        assertEquals(1, transactionsAddedSince(before, loan).size(),
                "undo write-off must record its own transaction instead of only flagging the original write-off as reversed");
    }

    @Test
    void undoWriteOffTransactionIsAWriteOffReversalLinkedToTheOriginalWriteOff() {
        final LoanTransaction reversal = undoTransactionOf(writtenOffLoan());

        assertEquals(WRITE_OFF_REVERSAL, LoanEnumerations.transactionType(reversal.getTypeOf()).getValue());
        assertTrue(reversal.isReversalTransaction());
        assertEquals(WRITE_OFF_ID, reversal.getOriginalTransactionId());
        assertFalse(reversal.isReversed());
    }

    @Test
    void undoWriteOffTransactionIsDatedOnTheUndoDateNotTheWriteOffDate() {
        final LoanTransaction reversal = undoTransactionOf(writtenOffLoan());

        assertEquals(UNDO_DATE, reversal.getTransactionDate());
    }

    @Test
    void undoWriteOffTransactionCarriesTheReversedWriteOffAmountAndPortions() {
        final LoanTransaction reversal = undoTransactionOf(writtenOffLoan());

        assertAmount("106000.00", reversal.getAmount(KES).getAmount());
        assertAmount("100000.00", reversal.getPrincipalPortion(KES).getAmount());
        assertAmount("6000.00", reversal.getInterestPortion(KES).getAmount());
        assertAmount("0.00", reversal.getFeeChargesPortion(KES).getAmount());
        assertAmount("0.00", reversal.getPenaltyChargesPortion(KES).getAmount());
    }

    @Test
    void originalWriteOffIsKeptUnchangedAndMarkedReversed() {
        final Loan loan = writtenOffLoan();
        final LoanTransaction writeOff = loan.findWriteOffTransaction();

        undoWriteOff(loan);

        assertTrue(loan.getLoanTransactions().contains(writeOff));
        assertTrue(writeOff.isReversed());
        assertEquals(WRITE_OFF_DATE, writeOff.getTransactionDate());
        assertAmount("106000.00", writeOff.getAmount(KES).getAmount());
    }

    @Test
    void writeOffReversalLeavesTheReinstatedLoanUntouchedOnReprocessing() {
        final Loan loan = writtenOffLoan();
        undoWriteOff(loan);

        loan.reprocessTransactions();

        assertEquals(LoanStatus.ACTIVE.getValue(), loan.getLoanStatus());
        assertNull(loan.findWriteOffTransaction());
        assertAmount("0.00", loan.getSummary().getTotalPrincipalWrittenOff());
        assertAmount("0.00", loan.getSummary().getTotalInterestWrittenOff());
        assertAmount("120000.00", loan.getSummary().getTotalOutstanding());
    }

    @Test
    void writeOffReversalDoesNotMoveTheLastUserTransactionDate() {
        final Loan loan = writtenOffLoan();

        undoWriteOff(loan);

        assertEquals(DISBURSEMENT_DATE, loan.getLastUserTransactionDate());
    }

    @Test
    @SuppressWarnings("unchecked")
    void accountingBridgePostsTheReversalOnceUnderTheNewTransactionOnTheUndoDate() {
        final Loan loan = writtenOffLoan();
        final List<Long> existingTransactionIds = new ArrayList<>();
        final List<Long> existingReversedTransactionIds = new ArrayList<>();
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.CLOSED_WRITTEN_OFF.getValue());
        loan.undoWrittenOff(existingTransactionIds, existingReversedTransactionIds, mock(ScheduleGeneratorDTO.class));

        final Map<String, Object> bridge = loan.deriveAccountingBridgeData(KES_DATA, existingTransactionIds,
                existingReversedTransactionIds, false);
        final List<Map<String, Object>> postings = (List<Map<String, Object>>) bridge.get("newLoanTransactions");

        assertTrue(postings.stream().noneMatch(posting -> WRITE_OFF_ID.equals(posting.get("id"))),
                "the original write-off must not be re-posted as a reversal of itself");
        final List<Map<String, Object>> writeOffReversals = postings.stream()
                .filter(posting -> new BigDecimal("106000.00").compareTo((BigDecimal) posting.get("amount")) == 0)
                .collect(Collectors.toList());
        assertEquals(1, writeOffReversals.size());
        final Map<String, Object> posting = writeOffReversals.get(0);
        assertEquals(Boolean.TRUE, posting.get("reversed"));
        assertEquals(UNDO_DATE, posting.get("date"));
        assertEquals(WRITE_OFF_REVERSAL, ((LoanTransactionEnumData) posting.get("type")).getValue());
        assertNotEquals(WRITE_OFF_ID, posting.get("id"));
    }

    @Test
    void adjustingARepaymentOnAWrittenOffLoanAlsoRecordsTheWriteOffReversal() {
        final Loan loan = writtenOffLoanWithRepayment();
        final LoanTransaction writeOff = loan.findWriteOffTransaction();

        adjustRepayment(loan);

        final List<LoanTransaction> reversals = writeOffReversalsOf(loan);
        assertEquals(1, reversals.size(),
                "adjusting a transaction on a written-off loan reverses the write-off, so it must leave the same audit row as undo write-off");
        assertEquals(WRITE_OFF_ID, reversals.get(0).getOriginalTransactionId());
        assertTrue(writeOff.isReversed());
    }

    @Test
    void writeOffReversalRecordedByAnAdjustmentIsDatedOnTheAdjustmentDateNotTheWriteOffDate() {
        final Loan loan = writtenOffLoanWithRepayment();

        adjustRepayment(loan);

        assertEquals(UNDO_DATE, writeOffReversalsOf(loan).get(0).getTransactionDate());
    }

    private List<LoanTransaction> writeOffReversalsOf(final Loan loan) {
        return loan.getLoanTransactions().stream()
                .filter(transaction -> LoanTransactionType.WRITEOFF_REVERSAL.equals(transaction.getTypeOf()))
                .collect(Collectors.toList());
    }

    private Loan writtenOffLoanWithRepayment() {
        final Loan loan = loan(transaction(DISBURSEMENT_ID,
                LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal("100000.00")), null, DISBURSEMENT_DATE, null)));
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("20000.00")), null,
                REPAYMENT_DATE, null);
        repayment.updateLoan(loan);
        loan.addLoanTransaction(repayment);
        loan.reprocessTransactions();
        transaction(REPAYMENT_ID, repayment);
        final LoanTransaction writeOff = transaction(WRITE_OFF_ID, LoanTransaction.writeoff(loan, mock(Office.class), WRITE_OFF_DATE, null));
        loan.addLoanTransaction(writeOff);
        new LoanRepaymentScheduleTransactionProcessorFactory().determineProcessor(null).handleWriteOff(writeOff, KES,
                loan.getRepaymentScheduleInstallments());
        loan.updateLoanSummaryDerivedFields();
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.CLOSED_WRITTEN_OFF.getValue());
        return loan;
    }

    private void adjustRepayment(final Loan loan) {
        final LoanTransaction repayment = loan.getLoanTransactions().stream()
                .filter(transaction -> REPAYMENT_ID.equals(transaction.getId())).findFirst().orElseThrow();
        final HolidayDetailDTO holidayDetailDTO = mock(HolidayDetailDTO.class);
        when(holidayDetailDTO.isAllowTransactionsOnHoliday()).thenReturn(true);
        when(holidayDetailDTO.isAllowTransactionsOnNonWorkingDay()).thenReturn(true);
        final ScheduleGeneratorDTO scheduleGeneratorDTO = mock(ScheduleGeneratorDTO.class);
        when(scheduleGeneratorDTO.getHolidayDetailDTO()).thenReturn(holidayDetailDTO);
        final LoanTransaction replacement = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("25000.00")), null,
                REPAYMENT_DATE, null);
        replacement.updateLoan(loan);
        loan.adjustExistingTransaction(replacement, new DefaultLoanLifecycleStateMachine(List.of(LoanStatus.values())), repayment,
                new ArrayList<>(), new ArrayList<>(), scheduleGeneratorDTO, true);
    }

    private Loan writtenOffLoan() {
        final Loan loan = loan(transaction(DISBURSEMENT_ID,
                LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal("100000.00")), null, DISBURSEMENT_DATE, null)));
        loan.getRepaymentScheduleInstallments().get(0).updateAccrualPortion(Money.of(KES, new BigDecimal("6000.00")), Money.zero(KES),
                Money.zero(KES));
        final LoanTransaction writeOff = transaction(WRITE_OFF_ID, LoanTransaction.writeoff(loan, mock(Office.class), WRITE_OFF_DATE, null));
        loan.addLoanTransaction(writeOff);
        new LoanRepaymentScheduleTransactionProcessorFactory().determineProcessor(null).handleWriteOff(writeOff, KES,
                loan.getRepaymentScheduleInstallments());
        loan.updateLoanSummaryDerivedFields();
        final LoanTransaction cancellation = loan.reconcileFutureInterestCancellation(writeOff, WRITE_OFF_DATE);
        assertNotNull(cancellation);
        transaction(CANCELLATION_ID, cancellation);
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.CLOSED_WRITTEN_OFF.getValue());
        return loan;
    }

    private void undoWriteOff(final Loan loan) {
        loan.undoWrittenOff(new ArrayList<>(), new ArrayList<>(), mock(ScheduleGeneratorDTO.class));
    }

    private LoanTransaction undoTransactionOf(final Loan loan) {
        final List<LoanTransaction> before = new ArrayList<>(loan.getLoanTransactions());
        undoWriteOff(loan);
        final List<LoanTransaction> added = transactionsAddedSince(before, loan);
        assertEquals(1, added.size(), "expected undo write-off to add exactly one transaction, but it added " + added.size());
        return added.get(0);
    }

    private List<LoanTransaction> transactionsAddedSince(final List<LoanTransaction> before, final Loan loan) {
        return loan.getLoanTransactions().stream().filter(transaction -> before.stream().noneMatch(existing -> existing == transaction))
                .collect(Collectors.toList());
    }

    private Loan loan(final LoanTransaction... transactions) {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(detail.getCurrency()).thenReturn(KES);
        when(detail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("100000.00")));
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "office", mock(Office.class));
        ReflectionTestUtils.setField(loan, "loanProduct", mock(LoanProduct.class));
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", detail);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", new ArrayList<>(List.of(new LoanRepaymentScheduleInstallment(null,
                1, DISBURSEMENT_DATE, DUE_DATE, new BigDecimal("100000.00"), new BigDecimal("20000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                false, null))));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(List.of(transactions)));
        ReflectionTestUtils.setField(loan, "charges", Collections.emptySet());
        loan.setHelpers(new DefaultLoanLifecycleStateMachine(List.of(LoanStatus.values())), new LoanSummaryWrapper(),
                new LoanRepaymentScheduleTransactionProcessorFactory());
        for (final LoanTransaction transaction : transactions) {
            transaction.updateLoan(loan);
        }
        return loan;
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
