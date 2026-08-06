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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Write-off must split outstanding interest, as at the user-selected write-off date, into interest that has been
 * earned/recognised (written off, hits the GL) and future unaccrued interest (cancelled, no GL impact).
 *
 * The separation rule is deliberately the same for accrual-basis and cash-basis products:
 * {@code recognised = max(proRataEarnedToWriteOffDate, interestAlreadyAccruedToGl)}. On a cash-basis product nothing is
 * ever accrued to the GL, so the second term is zero and the rule collapses to pro-rata earned - which is why no
 * branching on the product's accounting rule is needed here.
 *
 * Figures follow the ticket's worked examples: 20,000 scheduled interest over a 30-day period, written off on day 6.
 */
class LoanWriteOffInterestSeparationTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    /** Day 6 of a 30-day period: pro-rata earned = 20,000 * 6/30 = 4,000. */
    private static final LocalDate WRITE_OFF_DATE = LocalDate.of(2026, 1, 7);
    /** The business date is deliberately different from the write-off date - the split must ignore it. */
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 15);

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

    /** R1 - accrual basis: the accrual job recognised 6,000 in the GL, so 6,000 is written off and 14,000 cancelled. */
    @Test
    void accrualBasisWritesOffInterestRecognisedInTheGlAndCancelsTheRest() {
        final LoanRepaymentScheduleInstallment installment = installment("100000.00", "20000.00");
        installment.updateAccrualPortion(Money.of(KES, new BigDecimal("6000.00")), Money.zero(KES), Money.zero(KES));

        final Money writtenOff = installment.writeOffOutstandingInterestAndCancelUnearned(WRITE_OFF_DATE, KES);

        assertAmount("6000.00", writtenOff.getAmount());
        assertAmount("6000.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("14000.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
    }

    /** R2 - cash basis: nothing accrued to the GL, so the split is pure pro-rata earned to the write-off date. */
    @Test
    void cashBasisWritesOffInterestEarnedToDateAndCancelsTheRest() {
        final LoanRepaymentScheduleInstallment installment = installment("100000.00", "20000.00");

        final Money writtenOff = installment.writeOffOutstandingInterestAndCancelUnearned(WRITE_OFF_DATE, KES);

        assertAmount("4000.00", writtenOff.getAmount());
        assertAmount("4000.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("16000.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
    }

    /** R3 (edge case 1) - write-off on/after the due date: everything is earned, nothing left to cancel. */
    @Test
    void nothingIsCancelledWhenAllInterestIsAlreadyEarnedAtTheWriteOffDate() {
        final LoanRepaymentScheduleInstallment installment = installment("100000.00", "20000.00");

        installment.writeOffOutstandingInterestAndCancelUnearned(DUE_DATE, KES);

        assertAmount("20000.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
    }

    /** R4 (edge case 7) - an overdue installment is fully earned; overdue interest must never be treated as future. */
    @Test
    void overdueInstallmentInterestIsWrittenOffInFullAndNeverCancelled() {
        final LoanRepaymentScheduleInstallment overdue = new LoanRepaymentScheduleInstallment(null, 1, LocalDate.of(2025, 12, 1),
                LocalDate.of(2025, 12, 31), new BigDecimal("100000.00"), new BigDecimal("20000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                false, null);

        overdue.writeOffOutstandingInterestAndCancelUnearned(WRITE_OFF_DATE, KES);

        assertAmount("20000.00", overdue.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", overdue.getInterestCancelled(KES).getAmount());
    }

    /** R5 (edge case 3) - a write-off date falling mid-period splits the period's interest pro-rata to the day. */
    @Test
    void writeOffDateMidPeriodSplitsInterestProRata() {
        final LoanRepaymentScheduleInstallment installment = installment("100000.00", "20000.00");

        // day 15 of 30
        installment.writeOffOutstandingInterestAndCancelUnearned(LocalDate.of(2026, 1, 16), KES);

        assertAmount("10000.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("10000.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
    }

    /** R6 (edge case 8) - cash basis with interest already received: the split works on outstanding, not charged. */
    @Test
    void cashBasisWithPartialInterestReceivedSplitsOnlyTheOutstandingRemainder() {
        final LoanRepaymentScheduleInstallment installment = installment("100000.00", "20000.00");
        installment.payInterestComponent(FROM_DATE, Money.of(KES, new BigDecimal("3000.00")));

        installment.writeOffOutstandingInterestAndCancelUnearned(WRITE_OFF_DATE, KES);

        // 4,000 earned by day 6, of which 3,000 has already been received -> 1,000 still to write off.
        assertAmount("3000.00", installment.getInterestPaid(KES).getAmount());
        assertAmount("1000.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("16000.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
    }

    /** The split must never create or destroy value: written off + cancelled always equals what was outstanding. */
    @Test
    void writtenOffPlusCancelledAlwaysEqualsTheInterestThatWasOutstanding() {
        for (final LocalDate writeOffDate : new LocalDate[] { FROM_DATE, WRITE_OFF_DATE, LocalDate.of(2026, 1, 16), DUE_DATE }) {
            final LoanRepaymentScheduleInstallment installment = installment("100000.00", "20000.00");
            final BigDecimal outstandingBefore = installment.getInterestOutstanding(KES).getAmount();

            installment.writeOffOutstandingInterestAndCancelUnearned(writeOffDate, KES);

            final BigDecimal split = installment.getInterestWrittenOff(KES).getAmount()
                    .add(installment.getInterestCancelled(KES).getAmount());
            assertEquals(0, outstandingBefore.compareTo(split),
                    () -> "write-off on " + writeOffDate + ": expected " + outstandingBefore + " but split totalled " + split);
        }
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private LoanRepaymentScheduleInstallment installment(final String principal, final String interest) {
        return new LoanRepaymentScheduleInstallment(null, 1, FROM_DATE, DUE_DATE, new BigDecimal(principal), new BigDecimal(interest),
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + (actual == null ? "null" : actual.toPlainString()));
    }
}
