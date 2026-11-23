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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.concurrent.ExecutionException;
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
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTermVariationType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.exception.ScheduleNotAmortisingException;
import org.apache.fineract.portfolio.loanproduct.domain.AmortizationMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestCalculationPeriodMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FixedPrincipalRescheduleScheduleTest {

    private static final LocalDate DISBURSED = LocalDate.of(2025, 12, 1);
    private static final LocalDate FIRST_REPAYMENT = LocalDate.of(2026, 1, 1);
    private static final BigDecimal PRINCIPAL = new BigDecimal("700000");
    private static final BigDecimal NEW_FIXED_PRINCIPAL = new BigDecimal("30000");

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
    void flatLoanWithoutPrincipalChangeKeepsTenEqualInstalments() throws Exception {
        List<LoanScheduleModelPeriod> periods = generate(InterestMethod.FLAT, List.of());

        assertEquals(10, periods.size());
        periods.forEach(p -> assertMoney("70000", p.principalDue()));
    }

    @Test
    void flatLoanReducedToThirtyThousandFromInstalmentSevenTerminatesAndExtendsTerm() throws Exception {
        List<LoanScheduleModelPeriod> periods = generate(InterestMethod.FLAT, fixedPrincipalFromInstalmentSeven());

        assertReschedulesToThirtyThousand(periods);
    }

    @Test
    void decliningLoanReducedToThirtyThousandFromInstalmentSevenTerminatesAndExtendsTerm() throws Exception {
        List<LoanScheduleModelPeriod> periods = generate(InterestMethod.DECLINING_BALANCE, fixedPrincipalFromInstalmentSeven());

        BigDecimal outstandingAfterSix = PRINCIPAL.subtract(totalPrincipal(periods.subList(0, 6)));
        int expectedRemaining = outstandingAfterSix.divide(NEW_FIXED_PRINCIPAL, 0, RoundingMode.CEILING).intValue();
        assertEquals(6 + expectedRemaining, periods.size());
        for (int i = 6; i < periods.size() - 1; i++) {
            assertMoney("30000", periods.get(i).principalDue());
        }
        assertMoney(outstandingAfterSix.subtract(NEW_FIXED_PRINCIPAL.multiply(BigDecimal.valueOf(expectedRemaining - 1))).toPlainString(),
                periods.get(periods.size() - 1).principalDue());
        assertMoney(PRINCIPAL.toPlainString(), totalPrincipal(periods));
        assertNoNegativeAmounts(periods);
    }

    @Test
    void extendedInstalmentsCarryTheSameInterestAsTheExtendRepaymentPeriodOption() throws Exception {
        List<LoanScheduleModelPeriod> fixedPrincipal = generate(InterestMethod.FLAT, fixedPrincipalFromInstalmentSeven());
        List<LoanScheduleModelPeriod> extended = generate(InterestMethod.FLAT,
                List.of(new LoanTermVariationsData(LoanEnumerations.loanvariationType(LoanTermVariationType.EXTEND_REPAYMENT_PERIOD),
                        LocalDate.of(2026, 7, 1), new BigDecimal("6"), null, false)));

        assertEquals(extended.size(), fixedPrincipal.size());
        assertMoney(totalInterest(extended).toPlainString(), totalInterest(fixedPrincipal));
        for (int i = 10; i < fixedPrincipal.size(); i++) {
            assertTrue(fixedPrincipal.get(i).interestDue().signum() > 0, "no interest on extended instalment " + (i + 1));
        }
    }

    @Test
    void decliningLoanChargesInterestOnExtendedInstalments() throws Exception {
        List<LoanScheduleModelPeriod> periods = generate(InterestMethod.DECLINING_BALANCE, fixedPrincipalFromInstalmentSeven());

        for (int i = 10; i < periods.size(); i++) {
            assertTrue(periods.get(i).interestDue().signum() > 0, "no interest on extended instalment " + (i + 1));
        }
    }

    @Test
    void newlyDisbursedFlatLoanReducedToThirtyThousandFromFirstInstalment() throws Exception {
        List<LoanScheduleModelPeriod> periods = generate(InterestMethod.FLAT,
                List.of(fixedPrincipal(FIRST_REPAYMENT, NEW_FIXED_PRINCIPAL)));

        assertEquals(24, periods.size(), "700,000 at 30,000 per instalment needs 24 instalments");
        for (int i = 0; i < 23; i++) {
            assertMoney("30000", periods.get(i).principalDue());
        }
        assertMoney("10000", periods.get(23).principalDue());
        assertMoney(PRINCIPAL.toPlainString(), totalPrincipal(periods));
        assertNoNegativeAmounts(periods);
    }

    @Test
    void scheduleThatNeverRepaysPrincipalFailsInsteadOfHanging() {
        List<LoanTermVariationsData> variations = List.of(fixedPrincipal(LocalDate.of(2026, 7, 1), BigDecimal.ZERO));

        ExecutionException e = assertThrows(ExecutionException.class, () -> generate(InterestMethod.FLAT, variations));
        assertTrue(e.getCause() instanceof ScheduleNotAmortisingException, "unexpected failure " + e.getCause());
    }

    @Test
    void fixedPrincipalAboveRemainingBalanceSettlesInOneInstalment() throws Exception {
        List<LoanTermVariationsData> variations = List.of(fixedPrincipal(LocalDate.of(2026, 7, 1), new BigDecimal("500000")));

        List<LoanScheduleModelPeriod> periods = generate(InterestMethod.FLAT, variations);

        assertEquals(7, periods.size());
        assertMoney("280000", periods.get(6).principalDue());
        assertMoney(PRINCIPAL.toPlainString(), totalPrincipal(periods));
    }

    @Test
    void yearlyToMonthlyRegenerationPreservesMaturity() throws Exception {
        final List<LocalDate> dates = dueDates(
                reschedulePreservingMaturity(6, 1, PeriodFrequencyType.YEARS, PeriodFrequencyType.MONTHS, 1));
        assertEquals(LocalDate.of(2027, 9, 30), dates.get(0));
        assertEquals(LocalDate.of(2028, 10, 30), dates.get(1));
        assertEquals(LocalDate.of(2032, 9, 30), dates.get(dates.size() - 1));
        assertEquals(49, dates.size());
    }

    @Test
    void shortRemainderRegeneratesOnlyMaturityInstallment() throws Exception {
        final List<LoanRepaymentScheduleInstallment> installments = reschedulePreservingMaturity(4, 7, PeriodFrequencyType.DAYS,
                PeriodFrequencyType.MONTHS, 2);
        assertEquals(List.of(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 28)), dueDates(installments));
        assertMoney("2722.22", installments.get(2).getInterestCharged());
    }

    @Test
    void monthlyToYearlyRegenerationReducesInstallments() throws Exception {
        final List<LocalDate> dates = dueDates(
                reschedulePreservingMaturity(24, 1, PeriodFrequencyType.MONTHS, PeriodFrequencyType.YEARS, 1));
        assertEquals(3, dates.size());
        assertEquals(LocalDate.of(2028, 9, 28), dates.get(2));
    }

    private static List<LocalDate> dueDates(final List<LoanRepaymentScheduleInstallment> installments) {
        return installments.stream().map(LoanRepaymentScheduleInstallment::getDueDate).toList();
    }

    private static List<LoanRepaymentScheduleInstallment> reschedulePreservingMaturity(int count, int every, PeriodFrequencyType originalFrequency,
            PeriodFrequencyType newFrequency, int boundaryIndex) throws Exception {
        final LocalDate disbursement = LocalDate.of(2026, 9, 30);
        final MathContext mathContext = new MathContext(8, RoundingMode.HALF_EVEN);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, disbursement)));
        try {
            final LoanApplicationTerms original = terms(InterestMethod.FLAT, new ArrayList<>(), count, every, originalFrequency,
                    disbursement, null);
            final LoanScheduleGenerator generator = new FlatInterestLoanScheduleGenerator();
            final LoanScheduleModel schedule = generator.generate(mathContext, original, new HashSet<>(), original.getHolidayDetailDTO());
            final Loan loan = mock(Loan.class);
            final List<LoanRepaymentScheduleInstallment> installments = schedule.getPeriods().stream()
                    .filter(LoanScheduleModelPeriod::isRepaymentPeriod)
                    .map(period -> new LoanRepaymentScheduleInstallment(loan, period.periodNumber(), period.periodFromDate(),
                            period.periodDueDate(), period.principalDue(), period.interestDue(), period.feeChargesDue(),
                            period.penaltyChargesDue(), false, period.getLoanCompoundingDetails(), period.rescheduleInterestPortion()))
                    .toList();
            when(loan.getRepaymentScheduleInstallments()).thenReturn(installments);
            when(loan.getCurrency()).thenReturn(original.getPrincipal().getCurrency());
            final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
            ReflectionTestUtils.setField(summary, "totalInterestCharged", original.getTotalInterestDue().getAmount());
            when(loan.getLoanSummary()).thenReturn(summary);
            final LocalDate boundary = installments.get(boundaryIndex).getDueDate();
            final List<LoanTermVariationsData> variations = new ArrayList<>(List.of(
                    new LoanTermVariationsData(LoanEnumerations.loanvariationType(LoanTermVariationType.REPAYMENT_EVERY), boundary,
                            BigDecimal.ONE, null, false),
                    new LoanTermVariationsData(LoanEnumerations.loanvariationType(LoanTermVariationType.REPAYMENT_FREQUENCY), boundary,
                            BigDecimal.valueOf(newFrequency.getValue()), null, false)));
            final LoanApplicationTerms terms = terms(InterestMethod.FLAT, variations, count, every, originalFrequency, disbursement, null);
            terms.preserveTermAtCadence(1, newFrequency);
            final LoanScheduleDTO regenerated = generator.rescheduleNextInstallments(mathContext, terms, loan, terms.getHolidayDetailDTO(),
                    null, boundary);
            assertMoney(PRINCIPAL.toPlainString(), totalPrincipal(regenerated.getLoanScheduleModel().getPeriods().stream()
                    .filter(LoanScheduleModelPeriod::isRepaymentPeriod).toList()));
            return regenerated.getInstallments();
        } finally {
            ThreadLocalContextUtil.clear();
        }
    }

    private static void assertReschedulesToThirtyThousand(List<LoanScheduleModelPeriod> periods) {
        for (int i = 0; i < 6; i++) {
            assertMoney("70000", periods.get(i).principalDue());
        }
        for (int i = 6; i < 15; i++) {
            assertMoney("30000", periods.get(i).principalDue());
        }
        assertEquals(16, periods.size(), "280,000 outstanding at 30,000 per instalment needs 10 more instalments");
        assertMoney("10000", periods.get(15).principalDue());
        assertMoney(PRINCIPAL.toPlainString(), totalPrincipal(periods));
        assertNoNegativeAmounts(periods);
    }

    private static void assertNoNegativeAmounts(List<LoanScheduleModelPeriod> periods) {
        for (LoanScheduleModelPeriod p : periods) {
            assertTrue(p.principalDue().signum() >= 0, "negative principal in period " + p.periodNumber());
            assertTrue(p.interestDue().signum() >= 0, "negative interest in period " + p.periodNumber());
        }
    }

    private static List<LoanTermVariationsData> fixedPrincipalFromInstalmentSeven() {
        List<LoanTermVariationsData> variations = new ArrayList<>();
        for (LocalDate due : List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1))) {
            variations.add(fixedPrincipal(due, NEW_FIXED_PRINCIPAL));
        }
        return variations;
    }

    private static LoanTermVariationsData fixedPrincipal(LocalDate due, BigDecimal amount) {
        return new LoanTermVariationsData(LoanEnumerations.loanvariationType(LoanTermVariationType.PRINCIPAL_DUE_FIXED_AMOUNT), due,
                amount, due, true);
    }

    private static List<LoanScheduleModelPeriod> generate(InterestMethod interestMethod, List<LoanTermVariationsData> variations)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "schedule-generator-under-test");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<List<LoanScheduleModelPeriod>> future = executor.submit(() -> {
                ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
                ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 9, 28))));
                LoanApplicationTerms terms = terms(interestMethod, new ArrayList<>(variations));
                LoanScheduleGenerator generator = interestMethod.isDecliningBalnce() ? new DecliningBalanceInterestLoanScheduleGenerator()
                        : new FlatInterestLoanScheduleGenerator();
                LoanScheduleModel model = generator.generate(new MathContext(8, RoundingMode.HALF_EVEN), terms, new HashSet<>(),
                        terms.getHolidayDetailDTO());
                List<LoanScheduleModelPeriod> repayments = new ArrayList<>();
                for (LoanScheduleModelPeriod period : model.getPeriods()) {
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
                fail("schedule generation did not terminate within 10s (preview hangs until nginx answers 404)");
                return null;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static LoanApplicationTerms terms(InterestMethod interestMethod, List<LoanTermVariationsData> variations) throws Exception {
        return terms(interestMethod, variations, 10, 1, PeriodFrequencyType.MONTHS, DISBURSED, FIRST_REPAYMENT);
    }

    private static LoanApplicationTerms terms(InterestMethod interestMethod, List<LoanTermVariationsData> variations,
            int count, int every, PeriodFrequencyType frequency, LocalDate disbursement, LocalDate firstRepayment) throws Exception {
        Constructor<ApplicationCurrency> currencyCtor = ApplicationCurrency.class.getDeclaredConstructor(String.class, String.class,
                int.class, Integer.class, String.class, String.class);
        currencyCtor.setAccessible(true);
        ApplicationCurrency rwf = currencyCtor.newInstance("RWF", "Rwandan Franc", 2, 0, "currency.RWF", "RF");

        Constructor<WorkingDays> workingDaysCtor = WorkingDays.class.getDeclaredConstructor(String.class, Integer.class, Boolean.class,
                Boolean.class);
        workingDaysCtor.setAccessible(true);
        WorkingDays workingDays = workingDaysCtor.newInstance("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR,SA,SU", 1, false, false);
        HolidayDetailDTO holidays = new HolidayDetailDTO(false, new ArrayList<>(), workingDays);

        Money principal = Money.of(rwf.toData(), PRINCIPAL);
        return LoanApplicationTerms.assembleFrom(rwf, count * every, frequency, count, every, frequency, null, null,
                AmortizationMethod.EQUAL_INSTALLMENTS, interestMethod, new BigDecimal("10"), PeriodFrequencyType.YEARS, new BigDecimal("10"),
                InterestCalculationPeriodMethod.SAME_AS_REPAYMENT_PERIOD, false, principal, disbursement, firstRepayment, firstRepayment,
                null, null, null, null, null, principal.zero(), false, null, new ArrayList<>(), null, null, DaysInMonthType.DAYS_30,
                DaysInYearType.DAYS_360, false, null, null, null, null, null, BigDecimal.ZERO, null, null, null, PRINCIPAL, variations,
                false, null, false, holidays, false, false, false, null, false, false, count);
    }

    private static BigDecimal totalInterest(List<LoanScheduleModelPeriod> periods) {
        return periods.stream().map(LoanScheduleModelPeriod::interestDue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalPrincipal(List<LoanScheduleModelPeriod> periods) {
        return periods.stream().map(LoanScheduleModelPeriod::principalDue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
