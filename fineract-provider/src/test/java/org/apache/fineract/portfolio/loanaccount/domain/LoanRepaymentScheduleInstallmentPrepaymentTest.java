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

class LoanRepaymentScheduleInstallmentPrepaymentTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    private static final LocalDate MID_PERIOD_DATE = LocalDate.of(2026, 1, 16);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, MID_PERIOD_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void calculateAccruedInterestToDateReturnsProRataAmountMidPeriod() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");

        final Money accrued = installment.calculateAccruedInterestToDate(KES, MID_PERIOD_DATE);

        assertAmount("50.00", accrued.getAmount());
    }

    @Test
    void calculateAccruedInterestToDateIsZeroOncePaymentsExceedProRataEarned() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");
        installment.payInterestComponent(MID_PERIOD_DATE, Money.of(KES, new BigDecimal("95.00")));

        // 95 already paid exceeds the 50 pro-rata earned mid-period, so no further interest has accrued to collect.
        final Money accrued = installment.calculateAccruedInterestToDate(KES, MID_PERIOD_DATE);

        assertAmount("0.00", accrued.getAmount());
    }

    @Test
    void payAccruedInterestCancelsUnearnedOnlyWhenAccruedInterestFullyPaid() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");

        final Money paid = installment.payAccruedInterestComponentAndCancelUnearned(MID_PERIOD_DATE, Money.of(KES, new BigDecimal("20.00")));

        assertAmount("20.00", paid.getAmount());
        assertAmount("20.00", installment.getInterestPaid(KES).getAmount());
        assertAmount("80.00", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("0.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
    }

    @Test
    void payAccruedInterestCancelsUnearnedWhenAccruedInterestFullySettled() {
        final LoanRepaymentScheduleInstallment installment = installmentWithInterest("1000.00", "100.00");

        final Money paid = installment.payAccruedInterestComponentAndCancelUnearned(MID_PERIOD_DATE, Money.of(KES, new BigDecimal("50.00")));

        assertAmount("50.00", paid.getAmount());
        assertAmount("50.00", installment.getInterestPaid(KES).getAmount());
        assertAmount("50.00", installment.getInterestCancelled(KES).getAmount());
        assertAmount("0.00", installment.getInterestWrittenOff(KES).getAmount());
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
    }

    @Test
    void futureInstallmentHasNoAccruedInterestOnPrepaymentDate() {
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(null, 2, DUE_DATE.plusDays(1),
                DUE_DATE.plusMonths(1), new BigDecimal("1000.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, false,
                null);

        assertTrue(installment.isFutureInstallment(MID_PERIOD_DATE));
        assertAmount("0.00", installment.calculateAccruedInterestToDate(KES, MID_PERIOD_DATE).getAmount());
    }

    private static LoanRepaymentScheduleInstallment installmentWithInterest(final String principal, final String interest) {
        return new LoanRepaymentScheduleInstallment(null, 1, FROM_DATE, DUE_DATE, new BigDecimal(principal), new BigDecimal(interest),
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
