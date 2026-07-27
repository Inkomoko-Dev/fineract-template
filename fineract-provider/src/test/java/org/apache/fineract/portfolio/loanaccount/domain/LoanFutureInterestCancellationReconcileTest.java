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
 * CGLT-658: drives a real early full payoff through the schedule and asserts the linked, GL-free
 * FUTURE_INTEREST_CANCELLATION audit transaction is produced, kept in step with the schedule, and unwound on reversal.
 * Uses the ticket's own numbers: principal 500,000 / scheduled interest 50,000.
 */
class LoanFutureInterestCancellationReconcileTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
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
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DISBURSEMENT_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void earlyFullPayoffProducesLinkedGlFreeCancellationTransaction() {
        final LoanTransaction disbursement = disbursement(1L, "500000.00");
        final LoanTransaction payoff = payoff(2L, "500000.00");
        final Loan loan = loan(disbursement, payoff);

        loan.reprocessTransactions();

        final LoanTransaction cancellation = loan.reconcileFutureInterestCancellation(payoff, DISBURSEMENT_DATE);

        assertNotNull(cancellation);
        assertEquals(LoanTransactionType.FUTURE_INTEREST_CANCELLATION, cancellation.getTypeOf());
        assertAmount("50000.00", cancellation.getAmount(KES).getAmount());
        assertAmount("50000.00", cancellation.getInterestPortion(KES).getAmount());
        assertAmount("0.00", cancellation.getPrincipalPortion(KES).getAmount());
        assertEquals(2L, cancellation.getOriginalTransactionId());
        assertTrue(cancellation.isNonMonetaryTransaction());

        assertAmount("50000.00", loan.getTotalInterestCancelled().getAmount());
        assertAmount("50000.00", loan.getSummary().getTotalInterestCancelled());
        assertAmount("0.00", loan.getSummary().getTotalInterestWrittenOff());
        assertAmount("0.00", loan.getSummary().getTotalOutstanding());
    }

    @Test
    void payoffPreviewReportsFutureInterestToCancelBeforeSettlement() {
        final Loan loan = loan(disbursement(1L, "500000.00"), payoff(2L, "500000.00"));

        assertAmount("50000.00", loan.getFutureInterestToCancelAsOf(DISBURSEMENT_DATE).getAmount());
    }

    @Test
    void reconcileIsIdempotent() {
        final Loan loan = loan(disbursement(1L, "500000.00"), payoff(2L, "500000.00"));
        loan.reprocessTransactions();

        assertNotNull(loan.reconcileFutureInterestCancellation(byId(loan, 2L), DISBURSEMENT_DATE));
        assertNull(loan.reconcileFutureInterestCancellation(byId(loan, 2L), DISBURSEMENT_DATE));
    }

    @Test
    void reversingThePayoffUnwindsTheCancellationAudit() {
        final LoanTransaction disbursement = disbursement(1L, "500000.00");
        final LoanTransaction payoff = payoff(2L, "500000.00");
        final Loan loan = loan(disbursement, payoff);
        loan.reprocessTransactions();
        final LoanTransaction cancellation = loan.reconcileFutureInterestCancellation(payoff, DISBURSEMENT_DATE);

        payoff.reverse();
        loan.reprocessTransactions();
        final LoanTransaction afterReversal = loan.reconcileFutureInterestCancellation(payoff, DISBURSEMENT_DATE);

        assertNull(afterReversal);
        assertTrue(cancellation.isReversed());
        assertAmount("0.00", loan.getTotalInterestCancelled().getAmount());
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private Loan loan(final LoanTransaction... transactions) {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(detail.getCurrency()).thenReturn(KES);
        when(detail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("500000.00")));
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
                new BigDecimal("500000.00"), new BigDecimal("50000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false, null)));
    }

    @SuppressWarnings("unchecked")
    private LoanTransaction byId(final Loan loan, final long id) {
        for (final LoanTransaction transaction : (List<LoanTransaction>) ReflectionTestUtils.getField(loan, "loanTransactions")) {
            if (Long.valueOf(id).equals(transaction.getId())) {
                return transaction;
            }
        }
        throw new IllegalArgumentException("no transaction " + id);
    }

    private LoanTransaction disbursement(final Long id, final String amount) {
        return transaction(id, LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null,
                DISBURSEMENT_DATE, null));
    }

    private LoanTransaction payoff(final Long id, final String amount) {
        // An early full repayment; the advance handler settles principal and cancels the unearned interest. (A live
        // PAY_OFF is allocated directly in makeRepayment, not via the reprocess loop which filters on isRepaymentType.)
        return transaction(id,
                LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null, DISBURSEMENT_DATE, null));
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
