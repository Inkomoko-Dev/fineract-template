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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanTransactionChargePortionAlignTest {

    private static final MonetaryCurrency SSP = new MonetaryCurrency("SSP", 2, null);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2024, 1, 15);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUpMoneyHelper() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE);
        businessDates.put(BusinessDateType.COB_DATE, BUSINESS_DATE.minusDays(1));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @AfterEach
    void resetMoneyHelper() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
        ThreadLocalContextUtil.clear();
    }

    @Test
    void rebuildsChargeLinesKeepingFeePortionWhenLinesEmpty() {
        final LoanTransaction transaction = bareRepayment();
        ReflectionTestUtils.setField(transaction, "feeChargesPortion", new BigDecimal("1333.330000"));
        ReflectionTestUtils.setField(transaction, "loanChargesPaid", new HashSet<>());
        ReflectionTestUtils.setField(transaction, "overPaymentPortion", null);

        final LoanCharge feeCharge = mock(LoanCharge.class);
        when(feeCharge.isPenaltyCharge()).thenReturn(false);
        when(feeCharge.isOverdueInstallmentCharge()).thenReturn(true);
        when(feeCharge.getDueLocalDate()).thenReturn(BUSINESS_DATE.minusDays(10));
        when(feeCharge.getOverdueInstallmentCharge()).thenReturn(null);

        final boolean changed = transaction.rebuildMissingChargePaymentLines(SSP, List.of(feeCharge));

        assertThat(changed).isTrue();
        assertThat(transaction.getFeeChargesPortion(SSP).getAmount()).isEqualByComparingTo(new BigDecimal("1333.330000"));
        assertThat(transaction.getOverPaymentPortion(SSP).isZero()).isTrue();
        assertThat(transaction.getLoanChargesPaid()).hasSize(1);
        assertThat(transaction.getLoanChargesPaid().iterator().next().getAmount()).isEqualByComparingTo(new BigDecimal("1333.330000"));
    }

    @Test
    void rebuildsResidualFeeOntoEligibleChargeWithoutChangingPortion() {
        final LoanTransaction transaction = bareRepayment();
        ReflectionTestUtils.setField(transaction, "feeChargesPortion", new BigDecimal("500"));

        final LoanCharge existing = mock(LoanCharge.class);
        when(existing.isPenaltyCharge()).thenReturn(false);
        final HashSet<LoanChargePaidBy> paid = new HashSet<>();
        paid.add(new LoanChargePaidBy(transaction, existing, new BigDecimal("200"), 1));
        ReflectionTestUtils.setField(transaction, "loanChargesPaid", paid);

        final LoanCharge additional = mock(LoanCharge.class);
        when(additional.isPenaltyCharge()).thenReturn(false);
        when(additional.isOverdueInstallmentCharge()).thenReturn(false);
        when(additional.getDueLocalDate()).thenReturn(BUSINESS_DATE);
        when(additional.getOverdueInstallmentCharge()).thenReturn(null);

        final boolean changed = transaction.rebuildMissingChargePaymentLines(SSP, List.of(existing, additional));

        assertThat(changed).isTrue();
        assertThat(transaction.getFeeChargesPortion(SSP).getAmount()).isEqualByComparingTo(new BigDecimal("500"));
        BigDecimal lineSum = transaction.getLoanChargesPaid().stream().map(LoanChargePaidBy::getAmount).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        assertThat(lineSum).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void doesNotChangeWhenPortionsAlreadyMatchChargeLines() {
        final LoanTransaction transaction = bareRepayment();
        ReflectionTestUtils.setField(transaction, "feeChargesPortion", new BigDecimal("100"));

        final LoanCharge feeCharge = mock(LoanCharge.class);
        when(feeCharge.isPenaltyCharge()).thenReturn(false);
        final HashSet<LoanChargePaidBy> paid = new HashSet<>();
        paid.add(new LoanChargePaidBy(transaction, feeCharge, new BigDecimal("100"), 1));
        ReflectionTestUtils.setField(transaction, "loanChargesPaid", paid);

        final boolean changed = transaction.rebuildMissingChargePaymentLines(SSP, List.of(feeCharge));

        assertThat(changed).isFalse();
        assertThat(transaction.getFeeChargesPortion(SSP).getAmount()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void failsWhenNoEligibleChargeCanAbsorbResidual() {
        final LoanTransaction transaction = bareRepayment();
        ReflectionTestUtils.setField(transaction, "feeChargesPortion", new BigDecimal("1333.330000"));
        ReflectionTestUtils.setField(transaction, "loanChargesPaid", new HashSet<>());

        assertThatThrownBy(() -> transaction.rebuildMissingChargePaymentLines(SSP, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to rebuild charge payment lines");
    }

    private static LoanTransaction bareRepayment() {
        return LoanTransaction.repayment(mock(Office.class), Money.of(SSP, new BigDecimal("1333.33")), null, BUSINESS_DATE, null);
    }
}
