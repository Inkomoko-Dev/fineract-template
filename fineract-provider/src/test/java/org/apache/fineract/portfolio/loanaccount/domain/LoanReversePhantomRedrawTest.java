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
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CGLT-649: When an overpaying repayment is reversed/adjusted, the DEPOSIT_REDRAW it spawned is orphaned and the loan is
 * left stranded in OVERPAID with a phantom, withdrawable redraw balance even though it still owes money.
 * {@link Loan#reversePhantomDepositRedraws()} unwinds the orphaned redraw and demotes the loan back to ACTIVE. These
 * tests exercise that domain method directly.
 */
class LoanReversePhantomRedrawTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 4, 1);
    private static final LocalDate FIRST_DUE_DATE = LocalDate.of(2026, 5, 1);
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DISBURSEMENT_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    /**
     * Reproduces loan 000422992: 1,000 disbursed, only 300 legitimately repaid (700 still owed), but an erroneous
     * overpaying repayment that was later reversed left an active DEPOSIT_REDRAW of 500 behind and the loan stuck in
     * OVERPAID with total_overpaid 500. The unwind must reverse the redraw, clear the overpayment and demote to ACTIVE
     * while preserving the 700 outstanding.
     */
    @Test
    void unwindsOrphanedDepositRedrawAndDemotesOverpaidLoanToActive() {
        final LoanTransaction disbursement = disbursement(1L, "1000.00");
        final LoanTransaction goodRepayment = repayment(2L, "300.00");
        final LoanTransaction phantomRedraw = depositRedraw(3L, "500.00");
        final Loan loan = loan(disbursement, goodRepayment);

        // allocate the genuine 300 repayment -> 700 outstanding on the schedule
        loan.reprocessTransactions();

        // simulate the corrupted post-reversal state: the redraw is still live and the loan is stranded OVERPAID
        addTransaction(loan, phantomRedraw);
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.OVERPAID.getValue());
        ReflectionTestUtils.setField(loan, "totalOverpaid", new BigDecimal("500.00"));

        final Money reversed = loan.reversePhantomDepositRedraws();

        assertAmount("500.00", reversed.getAmount());
        assertTrue(phantomRedraw.isReversed(), "orphaned deposit-redraw should be reversed");
        assertNull(ReflectionTestUtils.getField(loan, "totalOverpaid"), "overpayment should be cleared");
        assertEquals(LoanStatus.ACTIVE.getValue(), ReflectionTestUtils.getField(loan, "loanStatus"),
                "loan should be demoted to ACTIVE");
        assertAmount("700.00", loan.getSummary().getTotalOutstanding());
    }

    /**
     * Guard: if cash has already left the institution via a withdrawal-redraw, the automatic unwind must be skipped
     * (the situation needs manual / finance handling) and the deposit-redraw left untouched.
     */
    @Test
    void skipsUnwindWhenAWithdrawalRedrawIsLive() {
        final LoanTransaction disbursement = disbursement(1L, "1000.00");
        final LoanTransaction goodRepayment = repayment(2L, "300.00");
        final LoanTransaction phantomRedraw = depositRedraw(3L, "500.00");
        final LoanTransaction withdrawal = withdrawalRedraw(4L, "500.00");
        final Loan loan = loan(disbursement, goodRepayment);
        loan.reprocessTransactions();
        addTransaction(loan, phantomRedraw);
        addTransaction(loan, withdrawal);
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.OVERPAID.getValue());
        ReflectionTestUtils.setField(loan, "totalOverpaid", new BigDecimal("500.00"));

        final Money reversed = loan.reversePhantomDepositRedraws();

        assertAmount("0.00", reversed.getAmount());
        assertFalse(phantomRedraw.isReversed(), "deposit-redraw must be left untouched when a withdrawal is live");
        assertEquals(LoanStatus.OVERPAID.getValue(), ReflectionTestUtils.getField(loan, "loanStatus"),
                "status must be left unchanged");
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private Loan loan(final LoanTransaction... transactions) {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        when(loanProductRelatedDetail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("1000.00")));
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
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

    @SuppressWarnings("unchecked")
    private void addTransaction(final Loan loan, final LoanTransaction transaction) {
        transaction.updateLoan(loan);
        ((List<LoanTransaction>) ReflectionTestUtils.getField(loan, "loanTransactions")).add(transaction);
    }

    private List<LoanRepaymentScheduleInstallment> installments() {
        return new ArrayList<>(List.of(new LoanRepaymentScheduleInstallment(null, 1, DISBURSEMENT_DATE, FIRST_DUE_DATE,
                new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null)));
    }

    private LoanTransaction disbursement(final Long id, final String amount) {
        return transaction(id, LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null,
                DISBURSEMENT_DATE, null));
    }

    private LoanTransaction repayment(final Long id, final String amount) {
        return transaction(id,
                LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null, DISBURSEMENT_DATE, null));
    }

    private LoanTransaction depositRedraw(final Long id, final String amount) {
        return transaction(id, LoanTransaction.applyRedrawRepayment(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null,
                DISBURSEMENT_DATE, null, null));
    }

    private LoanTransaction withdrawalRedraw(final Long id, final String amount) {
        return transaction(id, LoanTransaction.withdrawFromRedraw(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null,
                DISBURSEMENT_DATE, null, null));
    }

    private LoanTransaction transaction(final Long id, final LoanTransaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", id);
        transaction.setCreatedDate(CREATED_AT);
        return transaction;
    }

    private void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + " but was " + actual);
    }
}
