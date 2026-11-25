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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanScheduleSupersededByRecalculationTest {

    private static final MonetaryCurrency RWF = new MonetaryCurrency("RWF", 2, 0);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void recalculationThatChangesInterestKeepsTheScheduleItReplaces() {
        final Loan loan = loan(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "100"));
        final List<LoanRepaymentScheduleInstallment> current = loan.getRepaymentScheduleInstallments();

        loan.keepScheduleSupersededByRecalculation(List.of(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "80")));
        current.get(1).copyFrom(instalment(2, "2026-05-31", "80"));

        final List<LoanRepaymentScheduleInstallment> superseded = loan.takeScheduleSupersededByRecalculation();
        assertEquals(2, superseded.size());
        assertEquals(0, new BigDecimal("100").compareTo(superseded.get(1).getInterestCharged(RWF).getAmount()));
        assertEquals(LocalDate.parse("2026-05-31"), superseded.get(1).getDueDate());
        assertTrue(loan.takeScheduleSupersededByRecalculation().isEmpty());
    }

    @Test
    void recalculationThatMovesDueDatesOrDropsInstalmentsKeepsTheScheduleItReplaces() {
        final Loan moved = loan(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "100"));
        moved.keepScheduleSupersededByRecalculation(List.of(instalment(1, "2026-04-30", "100"), instalment(2, "2026-06-15", "100")));
        assertEquals(2, moved.takeScheduleSupersededByRecalculation().size());

        final Loan shortened = loan(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "100"));
        shortened.keepScheduleSupersededByRecalculation(List.of(instalment(1, "2026-04-30", "100")));
        assertEquals(2, shortened.takeScheduleSupersededByRecalculation().size());
    }

    @Test
    void applyingARecalculatedScheduleKeepsTheOldOneBeforeOverwritingIt() {
        final Loan loan = spy(loan(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "100")));
        final List<LoanRepaymentScheduleInstallment> recalculated = List.of(instalment(1, "2026-04-30", "100"),
                instalment(2, "2026-05-31", "80"));
        doAnswer(invocation -> {
            loan.getRepaymentScheduleInstallments().get(1).copyFrom(recalculated.get(1));
            return null;
        }).when(loan).updateLoanSchedule(recalculated);

        loan.applyRecalculatedSchedule(recalculated);

        verify(loan).updateLoanSchedule(recalculated);
        final List<LoanRepaymentScheduleInstallment> superseded = loan.takeScheduleSupersededByRecalculation();
        assertEquals(0, new BigDecimal("100").compareTo(superseded.get(1).getInterestCharged(RWF).getAmount()));
    }

    @Test
    void recalculationThatLeavesTheScheduleAsItWasKeepsNothing() {
        final Loan loan = loan(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "100"));

        loan.keepScheduleSupersededByRecalculation(List.of(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "100")));

        assertTrue(loan.takeScheduleSupersededByRecalculation().isEmpty());
    }

    @Test
    void repeatedRecalculationsBeforeASaveKeepTheScheduleInForceBeforeTheFirst() {
        final Loan loan = loan(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "100"));

        loan.keepScheduleSupersededByRecalculation(List.of(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "100")));
        loan.keepScheduleSupersededByRecalculation(List.of(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "90")));
        loan.getRepaymentScheduleInstallments().get(1).copyFrom(instalment(2, "2026-05-31", "90"));
        loan.keepScheduleSupersededByRecalculation(List.of(instalment(1, "2026-04-30", "100"), instalment(2, "2026-05-31", "80")));

        final List<LoanRepaymentScheduleInstallment> superseded = loan.takeScheduleSupersededByRecalculation();
        assertEquals(0, new BigDecimal("100").compareTo(superseded.get(1).getInterestCharged(RWF).getAmount()));
    }

    private static Loan loan(final LoanRepaymentScheduleInstallment... instalments) {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(detail.getCurrency()).thenReturn(RWF);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", detail);
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", new ArrayList<>(List.of(instalments)));
        return loan;
    }

    private static LoanRepaymentScheduleInstallment instalment(final int number, final String dueDate, final String interest) {
        final LocalDate due = LocalDate.parse(dueDate);
        return new LoanRepaymentScheduleInstallment(null, number, due.minusMonths(1), due, new BigDecimal("1000"), new BigDecimal(interest),
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }
}
