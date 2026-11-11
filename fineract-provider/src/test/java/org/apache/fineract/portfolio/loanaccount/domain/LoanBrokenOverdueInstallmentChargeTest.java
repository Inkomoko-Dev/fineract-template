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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanBrokenOverdueInstallmentChargeTest {

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUpMoneyHelper() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
    }

    @AfterEach
    void resetMoneyHelper() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void repairInactivatesBrokenOverdueChargeWhenNoInstallmentMatches() {
        final Loan loan = new Loan();
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(charge.isActive()).thenReturn(true);
        when(charge.getOverdueInstallmentCharge()).thenReturn(null);
        when(charge.getDueLocalDate()).thenReturn(LocalDate.of(2024, 6, 15));

        final Set<LoanCharge> charges = new HashSet<>();
        charges.add(charge);
        ReflectionTestUtils.setField(loan, "charges", charges);
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", new ArrayList<>());

        loan.repairOrInactivateBrokenOverdueInstallmentCharges();

        verify(charge).markAsInactive();
    }

    @Test
    void repairRelinksBrokenOverdueChargeWhenDueDateMatchesInstallment() {
        final Loan loan = new Loan();
        final LocalDate dueDate = LocalDate.of(2024, 6, 15);
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(charge.isActive()).thenReturn(true);
        when(charge.getOverdueInstallmentCharge()).thenReturn(null);
        when(charge.getDueLocalDate()).thenReturn(dueDate);

        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, dueDate.minusMonths(1), dueDate,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        final Set<LoanCharge> charges = new HashSet<>();
        charges.add(charge);
        ReflectionTestUtils.setField(loan, "charges", charges);
        final ArrayList<LoanRepaymentScheduleInstallment> installments = new ArrayList<>();
        installments.add(installment);
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", installments);

        loan.repairOrInactivateBrokenOverdueInstallmentCharges();

        verify(charge).updateOverdueInstallmentCharge(org.mockito.ArgumentMatchers.any(LoanOverdueInstallmentCharge.class));
    }

    @Test
    void recalculateLoanChargeDoesNotNpeWhenOverdueLinkMissing() {
        final Loan loan = new Loan();
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.getChargeCalculation()).thenReturn(ChargeCalculationType.PERCENT_OF_AMOUNT);
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(charge.isActive()).thenReturn(true);
        when(charge.getOverdueInstallmentCharge()).thenReturn(null);

        ReflectionTestUtils.invokeMethod(loan, "recalculateLoanCharge", charge, 0);

        // Guard returns early; no further interactions required.
        assertThat(charge.isActive()).isTrue();
    }

    @Test
    void calculateOverdueAmountReturnsZeroWhenLinkMissing() {
        final Loan loan = new Loan();
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.getOverdueInstallmentCharge()).thenReturn(null);

        final BigDecimal amount = ReflectionTestUtils.invokeMethod(loan, "calculateOverdueAmountPercentageAppliedTo", charge, 0);

        assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
