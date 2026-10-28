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
package org.apache.fineract.portfolio.loanaccount.loanschedule.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.apache.fineract.portfolio.common.domain.DaysInYearType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanproduct.domain.AmortizationMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestCalculationPeriodMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Schedule generation for quarterly (every 3 months) and semi-annual (every 6 months) repayments.
 */
class NamedIntervalLoanScheduleGeneratorTest {

    private static final LocalDate DISBURSED = LocalDate.of(2026, 1, 15);
    private static final LocalDate FIRST_REPAYMENT = LocalDate.of(2026, 7, 15);
    private static final BigDecimal PRINCIPAL = new BigDecimal("12000");

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
    void twelveMonthQuarterlyFlatScheduleHasFourInstallmentsThreeMonthsApart() throws Exception {
        assertNamedIntervalSchedule(InterestMethod.FLAT, 3, 4, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 10, 15),
                LocalDate.of(2027, 1, 15), LocalDate.of(2027, 4, 15));
    }

    @Test
    void twelveMonthQuarterlyDecliningScheduleHasFourInstallmentsThreeMonthsApart() throws Exception {
        assertNamedIntervalSchedule(InterestMethod.DECLINING_BALANCE, 3, 4, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 10, 15),
                LocalDate.of(2027, 1, 15), LocalDate.of(2027, 4, 15));
    }

    @Test
    void twelveMonthSemiAnnualFlatScheduleHasTwoInstallmentsSixMonthsApart() throws Exception {
        assertNamedIntervalSchedule(InterestMethod.FLAT, 6, 2, LocalDate.of(2026, 7, 15), LocalDate.of(2027, 1, 15));
    }

    @Test
    void twelveMonthSemiAnnualDecliningScheduleHasTwoInstallmentsSixMonthsApart() throws Exception {
        assertNamedIntervalSchedule(InterestMethod.DECLINING_BALANCE, 6, 2, LocalDate.of(2026, 7, 15), LocalDate.of(2027, 1, 15));
    }

    private void assertNamedIntervalSchedule(final InterestMethod interestMethod, final int repayEvery, final int expectedInstallments,
            final LocalDate... expectedDueDates) throws Exception {
        final List<LoanScheduleModelPeriod> periods = generate(interestMethod, repayEvery, expectedInstallments);

        assertEquals(expectedInstallments, periods.size());
        assertEquals(expectedDueDates.length, periods.size());
        for (int i = 0; i < expectedDueDates.length; i++) {
            assertEquals(expectedDueDates[i], periods.get(i).periodDueDate());
        }
        assertEquals(0, PRINCIPAL.compareTo(totalPrincipal(periods)), "principal must amortize fully");
    }

    private static List<LoanScheduleModelPeriod> generate(final InterestMethod interestMethod, final int repayEvery,
            final int numberOfRepayments) throws Exception {
        final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "named-interval-schedule-generator");
            t.setDaemon(true);
            return t;
        });
        try {
            final Future<List<LoanScheduleModelPeriod>> future = executor.submit(() -> {
                ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
                ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 1, 15))));
                final LoanApplicationTerms terms = terms(interestMethod, repayEvery, numberOfRepayments);
                final LoanScheduleGenerator generator = interestMethod.isDecliningBalnce()
                        ? new DecliningBalanceInterestLoanScheduleGenerator()
                        : new FlatInterestLoanScheduleGenerator();
                final LoanScheduleModel model = generator.generate(new MathContext(8, RoundingMode.HALF_EVEN), terms, new HashSet<>(),
                        terms.getHolidayDetailDTO());
                final List<LoanScheduleModelPeriod> repayments = new ArrayList<>();
                for (final LoanScheduleModelPeriod period : model.getPeriods()) {
                    if (period.isRepaymentPeriod()) {
                        repayments.add(period);
                    }
                }
                return repayments;
            });
            try {
                return future.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                fail("schedule generation did not terminate within 10s");
                return null;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static LoanApplicationTerms terms(final InterestMethod interestMethod, final int repayEvery, final int numberOfRepayments)
            throws Exception {
        final Constructor<ApplicationCurrency> currencyCtor = ApplicationCurrency.class.getDeclaredConstructor(String.class, String.class,
                int.class, Integer.class, String.class, String.class);
        currencyCtor.setAccessible(true);
        final ApplicationCurrency rwf = currencyCtor.newInstance("RWF", "Rwandan Franc", 2, 0, "currency.RWF", "RF");

        final Constructor<WorkingDays> workingDaysCtor = WorkingDays.class.getDeclaredConstructor(String.class, Integer.class, Boolean.class,
                Boolean.class);
        workingDaysCtor.setAccessible(true);
        final WorkingDays workingDays = workingDaysCtor.newInstance("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR,SA,SU", 1, false, false);
        final HolidayDetailDTO holidays = new HolidayDetailDTO(false, new ArrayList<>(), workingDays);

        final Money principal = Money.of(rwf.toData(), PRINCIPAL);
        final int termMonths = numberOfRepayments * repayEvery;
        return LoanApplicationTerms.assembleFrom(rwf, termMonths, PeriodFrequencyType.MONTHS, numberOfRepayments, repayEvery,
                PeriodFrequencyType.MONTHS, null, null, AmortizationMethod.EQUAL_INSTALLMENTS, interestMethod, new BigDecimal("10"),
                PeriodFrequencyType.YEARS, new BigDecimal("10"), InterestCalculationPeriodMethod.SAME_AS_REPAYMENT_PERIOD, false, principal,
                DISBURSED, FIRST_REPAYMENT, FIRST_REPAYMENT, null, null, null, null, null, principal.zero(), false, null, new ArrayList<>(),
                null, null, DaysInMonthType.DAYS_30, DaysInYearType.DAYS_360, false, null, null, null, null, null, BigDecimal.ZERO, null,
                null, null, PRINCIPAL, new ArrayList<>(), false, null, false, holidays, false, false, false, null, false, false,
                numberOfRepayments);
    }

    private static BigDecimal totalPrincipal(final List<LoanScheduleModelPeriod> periods) {
        return periods.stream().map(LoanScheduleModelPeriod::principalDue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
