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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import java.util.TreeMap;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanUndoWriteOffScheduleRestorationTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIRST_DUE_DATE = LocalDate.of(2026, 2, 1);
    private static final LocalDate SECOND_DUE_DATE = LocalDate.of(2026, 3, 1);
    private static final LocalDate THIRD_DUE_DATE = LocalDate.of(2026, 4, 1);
    private static final LocalDate UNDO_DATE = LocalDate.of(2026, 5, 10);
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;
    private long nextTransactionId = 1L;

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
    void undoRestoresEveryInstallmentOfAPartlyRepaidLoanWrittenOffMidPeriod() {
        final Loan loan = loan();
        addRepayment(loan, FIRST_DUE_DATE, "38333.33");
        addRepayment(loan, LocalDate.of(2026, 2, 15), "10000.00");
        accrue(loan, 0, "5000.00");
        accrue(loan, 1, "3000.00");
        loan.reprocessTransactions();
        persistNewTransactions(loan);

        assertScheduleRestoredAfterWriteOffAndUndo(loan, LocalDate.of(2026, 2, 20));
    }

    @Test
    void undoRestoresEveryInstallmentOfAnOverdueLoanWrittenOffAfterTwoMissedInstallments() {
        final Loan loan = loan();
        accrue(loan, 0, "5000.00");
        accrue(loan, 1, "5000.00");
        loan.reprocessTransactions();
        persistNewTransactions(loan);

        assertScheduleRestoredAfterWriteOffAndUndo(loan, LocalDate.of(2026, 3, 15));
    }

    @Test
    void undoRestoresEveryInstallmentOfALoanRepaidAheadOfSchedule() {
        final Loan loan = loan();
        addRepayment(loan, LocalDate.of(2026, 1, 20), "60000.00");
        loan.reprocessTransactions();
        persistNewTransactions(loan);

        assertScheduleRestoredAfterWriteOffAndUndo(loan, LocalDate.of(2026, 1, 25));
    }

    private void persistNewTransactions(final Loan loan) {
        for (final LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction.getId() == null) {
                ReflectionTestUtils.setField(transaction, "id", this.nextTransactionId++);
            }
        }
    }

    private void assertScheduleRestoredAfterWriteOffAndUndo(final Loan loan, final LocalDate writeOffDate) {
        assertTrue(loan.getLoanTransactions().stream().noneMatch(LoanTransaction::isReversed),
                "every pre-write-off transaction must still be live, as it is once the service has saved the replay");
        final Map<String, String> beforeWriteOff = snapshot(loan);

        final LoanTransaction writeOff = transaction(LoanTransaction.writeoff(loan, mock(Office.class), writeOffDate, null));
        loan.addLoanTransaction(writeOff);
        new LoanRepaymentScheduleTransactionProcessorFactory().determineProcessor(null).handleWriteOff(writeOff, KES,
                loan.getRepaymentScheduleInstallments());
        loan.updateLoanSummaryDerivedFields();
        final LoanTransaction cancellation = loan.reconcileFutureInterestCancellation(writeOff, writeOffDate);
        if (cancellation != null) {
            transaction(cancellation);
        }
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.CLOSED_WRITTEN_OFF.getValue());
        assertNotEquals(beforeWriteOff, snapshot(loan), "the write-off must actually change the schedule for this test to mean anything");

        loan.undoWrittenOff(new ArrayList<>(), new ArrayList<>(), mock(ScheduleGeneratorDTO.class));

        assertEquals(beforeWriteOff, snapshot(loan));
    }

    private Map<String, String> snapshot(final Loan loan) {
        final Map<String, String> values = new TreeMap<>();
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            putValueFields(values, "installment " + installment.getInstallmentNumber(), installment,
                    LoanRepaymentScheduleInstallment.class);
        }
        putValueFields(values, "summary", loan.getSummary(), LoanSummary.class);
        return values;
    }

    private void putValueFields(final Map<String, String> values, final String prefix, final Object target, final Class<?> type) {
        for (final Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final Class<?> fieldType = field.getType();
            final Object value = ReflectionTestUtils.getField(target, field.getName());
            if (fieldType == BigDecimal.class) {
                values.put(prefix + "." + field.getName(),
                        value == null ? "0" : ((BigDecimal) value).stripTrailingZeros().toPlainString());
            } else if (fieldType == LocalDate.class || fieldType == boolean.class || fieldType == Boolean.class
                    || fieldType == Integer.class) {
                values.put(prefix + "." + field.getName(), String.valueOf(value));
            }
        }
    }

    private void addRepayment(final Loan loan, final LocalDate date, final String amount) {
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null, date,
                null);
        repayment.setCreatedDate(CREATED_AT);
        repayment.updateLoan(loan);
        loan.addLoanTransaction(repayment);
    }

    private void accrue(final Loan loan, final int installmentIndex, final String interest) {
        loan.getRepaymentScheduleInstallments().get(installmentIndex).updateAccrualPortion(Money.of(KES, new BigDecimal(interest)),
                Money.zero(KES), Money.zero(KES));
    }

    private Loan loan() {
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
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments",
                new ArrayList<>(List.of(installment(1, DISBURSEMENT_DATE, FIRST_DUE_DATE, "33333.33"),
                        installment(2, FIRST_DUE_DATE, SECOND_DUE_DATE, "33333.33"),
                        installment(3, SECOND_DUE_DATE, THIRD_DUE_DATE, "33333.34"))));
        final LoanTransaction disbursement = transaction(
                LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal("100000.00")), null, DISBURSEMENT_DATE, null));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(List.of(disbursement)));
        ReflectionTestUtils.setField(loan, "charges", Collections.emptySet());
        loan.setHelpers(new DefaultLoanLifecycleStateMachine(List.of(LoanStatus.values())), new LoanSummaryWrapper(),
                new LoanRepaymentScheduleTransactionProcessorFactory());
        disbursement.updateLoan(loan);
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            installment.updateLoan(loan);
        }
        return loan;
    }

    private LoanRepaymentScheduleInstallment installment(final int number, final LocalDate fromDate, final LocalDate dueDate,
            final String principal) {
        return new LoanRepaymentScheduleInstallment(null, number, fromDate, dueDate, new BigDecimal(principal), new BigDecimal("5000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }

    private LoanTransaction transaction(final LoanTransaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", this.nextTransactionId++);
        transaction.setCreatedDate(CREATED_AT);
        return transaction;
    }
}
