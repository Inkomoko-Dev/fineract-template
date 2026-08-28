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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers {@code Loan.waiveLoanChargeHistorically}: waiving a penalty a repayment already settled, and handing back the
 * repayments the replay reversed and replaced.
 */
public class LoanHistoricalPenaltyWaiverTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, null);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate JANUARY_DUE = LocalDate.of(2026, 1, 15);
    private static final LocalDate FEBRUARY_DUE = LocalDate.of(2026, 2, 15);
    private static final LocalDate MARCH_DUE = LocalDate.of(2026, 3, 15);
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

    @Test
    @DisplayName("waives a fully paid penalty and reports every repayment the replay had to reverse")
    public void waivesAFullyPaidPenaltyAndReportsTheAffectedRepayments() {
        final Fixture fixture = new Fixture();

        assertTrue(fixture.penalty.isPaid(), "fixture precondition: the penalty is settled by the January repayment");

        final Map<String, Object> changes = new LinkedHashMap<>();
        final List<Long> existingTransactionIds = new ArrayList<>();
        final List<Long> existingReversedTransactionIds = new ArrayList<>();

        final HistoricalPenaltyWaiverResult result = fixture.loan.waiveLoanChargeHistorically(fixture.penalty, changes,
                existingTransactionIds, existingReversedTransactionIds, null, null, JANUARY_DUE, Money.zero(KES));

        assertNotNull(result, "the historical waiver must report its outcome");
        assertNotNull(result.getWaiveTransaction());
        assertNotNull(result.getChangedTransactionDetail(),
                "the reprocessed repayments must be handed back, not discarded as the existing waive path does");

        // The penalty is genuinely waived for its full amount, even though it had been fully paid.
        assertAmount("5000.00", result.getWaiveTransaction().getAmount(KES).getAmount());
        assertAmount("5000.00", result.getWaiveTransaction().getPenaltyChargesPortion(KES).getAmount());
        assertAmount("5000.00", fixture.penalty.getAmountWaived(KES).getAmount());
        assertAmount("0.00", fixture.penalty.getAmountPaid(KES).getAmount());
        assertAmount("0.00", fixture.penalty.getAmountOutstanding(KES).getAmount());
        assertFalse(fixture.penalty.isPaid(), "the waived penalty must not be re-absorbed by a later repayment");

        // The waiver is dated at the caller's effective date, not silently derived.
        assertEquals(JANUARY_DUE, result.getWaiveTransaction().getTransactionDate());

        // The repayment that had settled the penalty is reversed and a replacement supplied, in one action.
        assertTrue(fixture.january.isReversed(), "the repayment that settled the penalty must be reversed automatically");
        final LoanTransaction replacement = result.getChangedTransactionDetail().getNewTransactionMappings()
                .get(fixture.january.getId());
        assertNotNull(replacement, "a replacement must be supplied for the reversed repayment");
        assertAmount("0.00", replacement.getPenaltyChargesPortion(KES).getAmount());

        // The snapshot needed to post the correcting journal entries was taken before anything was reversed.
        assertTrue(existingTransactionIds.contains(fixture.january.getId()),
                "the pre-replay snapshot must contain the repayments, so their journal entries can be reversed");
        assertFalse(existingReversedTransactionIds.contains(fixture.january.getId()),
                "the January repayment was not reversed before the correction ran");

        assertAmount("5000.00", (BigDecimal) changes.get("amount"));
    }

    @Test
    @DisplayName("waives only the requested portion when a partial waiver amount is supplied")
    public void waivesOnlyTheRequestedPortion() {
        final Fixture fixture = new Fixture();

        final HistoricalPenaltyWaiverResult result = fixture.loan.waiveLoanChargeHistorically(fixture.penalty,
                new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), null, new BigDecimal("2000.00"), JANUARY_DUE,
                Money.zero(KES));

        assertAmount("2000.00", result.getWaiveTransaction().getAmount(KES).getAmount());
        assertAmount("2000.00", fixture.penalty.getAmountWaived(KES).getAmount());
        // The unwaived remainder is still a genuine obligation and is still collected by the repayments.
        assertAmount("3000.00", fixture.penalty.getAmountPaid(KES).getAmount());
        // Nothing left owing, so it cannot be mistaken for a CGLT-624 residual (waived-but-still-outstanding). The
        // charge does end up flagged waived: updatePaidAmountBy sets that on any fully covered charge with a waived
        // amount.
        assertAmount("0.00", fixture.penalty.getAmountOutstanding(KES).getAmount());
    }

    @Test
    @DisplayName("falls back to the charge due date when no effective date is supplied")
    public void fallsBackToTheChargeDueDate() {
        final Fixture fixture = new Fixture();

        final HistoricalPenaltyWaiverResult result = fixture.loan.waiveLoanChargeHistorically(fixture.penalty,
                new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), null, null, null, Money.zero(KES));

        assertEquals(JANUARY_DUE, result.getWaiveTransaction().getTransactionDate());
    }

    /** A three-instalment loan whose January repayment settled a 5,000 penalty. */
    private final class Fixture {

        private final Loan loan = new Loan();
        private final LoanCharge penalty;
        private final LoanTransaction january;

        private Fixture() {
            final LoanProductRelatedDetail scheduleDetail = mock(LoanProductRelatedDetail.class);
            when(scheduleDetail.getCurrency()).thenReturn(KES);
            when(scheduleDetail.isInterestRecalculationEnabled()).thenReturn(false);

            this.penalty = januaryPenalty(this.loan);

            final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(
                    List.of(installment(1, DISBURSEMENT_DATE, JANUARY_DUE), installment(2, JANUARY_DUE, FEBRUARY_DUE),
                            installment(3, FEBRUARY_DUE, MARCH_DUE)));

            final org.apache.fineract.portfolio.loanproduct.domain.LoanProduct loanProduct = mock(
                    org.apache.fineract.portfolio.loanproduct.domain.LoanProduct.class);
            when(loanProduct.isPeriodicAccrualAccountingEnabled()).thenReturn(false);

            final org.apache.fineract.portfolio.client.domain.Client client = mock(
                    org.apache.fineract.portfolio.client.domain.Client.class);
            when(client.getOffice()).thenReturn(mock(Office.class));

            ReflectionTestUtils.setField(this.loan, "client", client);
            ReflectionTestUtils.setField(this.loan, "actualDisbursementDate", DISBURSEMENT_DATE);
            ReflectionTestUtils.setField(this.loan, "expectedDisbursementDate", DISBURSEMENT_DATE);
            ReflectionTestUtils.setField(this.loan, "loanProduct", loanProduct);
            ReflectionTestUtils.setField(this.loan, "loanRepaymentScheduleDetail", scheduleDetail);
            ReflectionTestUtils.setField(this.loan, "charges", new LinkedHashSet<>(List.of(this.penalty)));
            ReflectionTestUtils.setField(this.loan, "repaymentScheduleInstallments", installments);
            ReflectionTestUtils.setField(this.loan, "transactionProcessorFactory",
                    new LoanRepaymentScheduleTransactionProcessorFactory());
            ReflectionTestUtils.setField(this.loan, "summary", new LoanSummary());

            final List<LoanTransaction> seed = new ArrayList<>(List.of(repayment(1L, JANUARY_DUE, "16000.00"),
                    repayment(2L, FEBRUARY_DUE, "11000.00"), repayment(3L, MARCH_DUE, "11000.00")));
            ReflectionTestUtils.setField(this.loan, "loanTransactions", seed);

            // Establish the "before" state: replay once so the penalty is settled by January, then adopt the
            // replacements as persisted, as the write service does.
            final ChangedTransactionDetail changed = new LoanRepaymentScheduleTransactionProcessorFactory()
                    .determineProcessor(null)
                    .handleTransaction(DISBURSEMENT_DATE, seed, KES, installments, Set.of(this.penalty));
            final List<LoanTransaction> persisted = new ArrayList<>();
            long nextId = 100L;
            for (final LoanTransaction original : seed) {
                final LoanTransaction replacement = changed.getNewTransactionMappings().get(original.getId());
                if (replacement != null) {
                    ReflectionTestUtils.setField(replacement, "id", nextId++);
                    persisted.add(replacement);
                } else if (!original.isReversed()) {
                    persisted.add(original);
                }
            }
            ReflectionTestUtils.setField(this.loan, "loanTransactions", persisted);
            this.january = persisted.get(0);
        }
    }

    private LoanRepaymentScheduleInstallment installment(final int number, final LocalDate from, final LocalDate due) {
        return new LoanRepaymentScheduleInstallment(null, number, from, due, new BigDecimal("10000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }

    private LoanCharge januaryPenalty(final Loan loan) {
        final Charge definition = mock(Charge.class);
        when(definition.isPenalty()).thenReturn(true);
        when(definition.getChargeTimeType()).thenReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        return new LoanCharge(loan, definition, PENALTY_AMOUNT, PENALTY_AMOUNT, ChargeTimeType.SPECIFIED_DUE_DATE,
                ChargeCalculationType.FLAT, JANUARY_DUE, ChargePaymentMode.REGULAR, 1, PENALTY_AMOUNT);
    }

    private LoanTransaction repayment(final Long id, final LocalDate date, final String amount) {
        final LoanTransaction transaction = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal(amount)), null,
                date, null);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
