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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.account.domain.AccountTransferRepository;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanApplyOverdueChargeBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDailyLateFee;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDailyLateFeeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

class LoanDailyLateFeeServiceTest {

    private static final long LOAN_ID = 1L;
    private static final BigDecimal DISBURSED_PRINCIPAL = new BigDecimal("5000");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2030, 12, 31);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    private LoanChargeRepository loanChargeRepository;
    private LoanDailyLateFeeRepository loanDailyLateFeeRepository;
    private ConfigurationDomainService configurationDomainService;
    private BusinessEventNotifierService businessEventNotifierService;
    private JdbcTemplate jdbcTemplate;
    private final List<Object> principalReductionRows = new ArrayList<>();
    private LoanRepaymentScheduleInstallment installment;
    private LoanDailyLateFeeService service;
    private Loan loan;

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
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void calculatesDailyLateFeeRateWithThirtyDayCommercialMonth() {
        final BigDecimal dailyRate = LoanDailyLateFeeService.calculateDailyLateFeeRate(new BigDecimal("2"), DaysInMonthType.DAYS_30,
                LocalDate.of(2026, 4, 1));
        final BigDecimal dailyPenaltyAmount = new BigDecimal("2000").multiply(dailyRate, MoneyHelper.getMathContext()).setScale(6,
                MoneyHelper.getRoundingMode());
        final BigDecimal postedPenaltyAmount = dailyPenaltyAmount.multiply(new BigDecimal("4")).setScale(6,
                MoneyHelper.getRoundingMode());

        assertEquals(0, new BigDecimal("0.000666666667").compareTo(dailyRate));
        assertEquals(0, new BigDecimal("5.33").compareTo(postedPenaltyAmount.setScale(2, MoneyHelper.getRoundingMode())));
    }

    /**
     * CGLT-181 QA regression: a loan carried penalty charges that are not tracked in m_loan_daily_late_fee (legacy
     * monthly overdue charges, manual "Add Loan Charge" penalties). The cap must be enforced against the loan's total
     * life-to-date penalty charges, so daily late fees may only accrue up to (disbursed principal - other penalties).
     */
    @Test
    void dailyLateFeesRespectCapWhenLoanHasPenaltyChargesOutsideDailyMetadata() {
        setUpLoanFixture(new BigDecimal("27.97"));

        this.service.syncDailyLateFeesForLoan(LOAN_ID, BUSINESS_DATE);

        final ArgumentCaptor<LoanCharge> chargeCaptor = ArgumentCaptor.forClass(LoanCharge.class);
        verify(this.loanChargeRepository, atLeastOnce()).saveAndFlush(chargeCaptor.capture());
        BigDecimal totalDailyLateFees = BigDecimal.ZERO;
        for (LoanCharge dailyLateFeeCharge : chargeCaptor.getAllValues()) {
            totalDailyLateFees = totalDailyLateFees.add(dailyLateFeeCharge.amount());
        }

        assertEquals(0, new BigDecimal("4972.03").compareTo(totalDailyLateFees.setScale(2, MoneyHelper.getRoundingMode())),
                "daily late fees must stop at cap minus pre-existing penalty charges, but accrued " + totalDailyLateFees);
    }

    /**
     * AC1 + AC2 (story example): installment due on the 15th, 5-day grace (16th-20th), client pays on the 23rd →
     * penalties for exactly 3 days (21st, 22nd, 23rd) at the daily rate derived from 2%/month on the overdue
     * principal — not a full month. AC5: each day is posted as a penalty charge and mirrored in the daily metadata.
     */
    @Test
    void dailyLateFeeIsProRataPerPenaltyDayOnOverduePrincipal() {
        setUpLoanFixture(BigDecimal.ZERO);

        this.service.syncDailyLateFeesForLoan(LOAN_ID, LocalDate.of(2026, 1, 23));

        final ArgumentCaptor<LoanCharge> chargeCaptor = ArgumentCaptor.forClass(LoanCharge.class);
        verify(this.loanChargeRepository, times(3)).saveAndFlush(chargeCaptor.capture());
        BigDecimal totalDailyLateFees = BigDecimal.ZERO;
        for (LoanCharge dailyLateFeeCharge : chargeCaptor.getAllValues()) {
            assertTrue(dailyLateFeeCharge.isPenaltyCharge(), "daily late fee must post as a penalty charge");
            assertTrue(!dailyLateFeeCharge.getDueLocalDate().isBefore(LocalDate.of(2026, 1, 21))
                    && !dailyLateFeeCharge.getDueLocalDate().isAfter(LocalDate.of(2026, 1, 23)),
                    "penalty day outside the expected window: " + dailyLateFeeCharge.getDueLocalDate());
            assertEquals(0, new BigDecimal("3.333333").compareTo(dailyLateFeeCharge.amount()),
                    "each penalty day must charge dailyRate x overdue principal");
            totalDailyLateFees = totalDailyLateFees.add(dailyLateFeeCharge.amount());
        }
        assertEquals(0, new BigDecimal("9.999999").compareTo(totalDailyLateFees));
        verify(this.loanDailyLateFeeRepository, times(3)).save(any(LoanDailyLateFee.class));
    }

    /** AC3: no penalty accrues between the due date and the end of the grace period. */
    @Test
    void noDailyLateFeeAccruesWithinGracePeriod() {
        setUpLoanFixture(BigDecimal.ZERO);

        this.service.syncDailyLateFeesForLoan(LOAN_ID, LocalDate.of(2026, 1, 20));

        verify(this.loanChargeRepository, never()).saveAndFlush(any(LoanCharge.class));
    }

    /** AC3: the grace period is read from the penalty-wait-period configuration, not hardcoded to 5 days. */
    @Test
    void gracePeriodComesFromConfiguration() {
        setUpLoanFixture(BigDecimal.ZERO);
        when(this.configurationDomainService.retrievePenaltyWaitPeriod()).thenReturn(3L);

        this.service.syncDailyLateFeesForLoan(LOAN_ID, LocalDate.of(2026, 1, 19));

        // with the default 5-day wait nothing would accrue on the 19th; with 3 days the first penalty day is the 19th
        verify(this.loanChargeRepository, times(1)).saveAndFlush(any(LoanCharge.class));
    }

    /** AC2: once the overdue principal is fully repaid, no further daily late fees accrue. */
    @Test
    void accrualStopsWhenOverduePrincipalIsRepaid() throws Exception {
        setUpLoanFixture(BigDecimal.ZERO);
        ReflectionTestUtils.setField(this.installment, "id", 10L);
        this.principalReductionRows.add(principalReductionEvent(10L, LocalDate.of(2026, 1, 23), DISBURSED_PRINCIPAL));

        this.service.syncDailyLateFeesForLoan(LOAN_ID, LocalDate.of(2026, 2, 28));

        // paid on the 23rd -> penalty days are only the 21st, 22nd and 23rd even though the run goes to Feb 28
        verify(this.loanChargeRepository, times(3)).saveAndFlush(any(LoanCharge.class));
    }

    /**
     * Cron path (job 12 "Apply penalty to overdue loans"): the overdue-charge entry point accrues up to the business
     * date, stops exactly at the disbursed-principal cap (AC4) and publishes the apply-overdue-charge business events.
     */
    @Test
    void cronEntryPointAccruesToCapAndPublishesBusinessEvents() {
        setUpLoanFixture(BigDecimal.ZERO);

        this.service.applyOverdueChargesForLoan(LOAN_ID, null);

        final ArgumentCaptor<LoanCharge> chargeCaptor = ArgumentCaptor.forClass(LoanCharge.class);
        verify(this.loanChargeRepository, atLeastOnce()).saveAndFlush(chargeCaptor.capture());
        BigDecimal totalDailyLateFees = BigDecimal.ZERO;
        for (LoanCharge dailyLateFeeCharge : chargeCaptor.getAllValues()) {
            totalDailyLateFees = totalDailyLateFees.add(dailyLateFeeCharge.amount());
        }
        assertEquals(0, DISBURSED_PRINCIPAL.compareTo(totalDailyLateFees.setScale(2, MoneyHelper.getRoundingMode())),
                "cron accrual must stop exactly at the disbursed-principal cap, but accrued " + totalDailyLateFees);
        verify(this.businessEventNotifierService).notifyPreBusinessEvent(any(LoanApplyOverdueChargeBusinessEvent.class));
        verify(this.businessEventNotifierService).notifyPostBusinessEvent(any(LoanApplyOverdueChargeBusinessEvent.class));
    }

    private Object principalReductionEvent(final Long installmentId, final LocalDate transactionDate, final BigDecimal principalPortion)
            throws Exception {
        final Class<?> eventClass = Class
                .forName("org.apache.fineract.portfolio.loanaccount.service.LoanDailyLateFeeService$PrincipalReductionEvent");
        final Constructor<?> constructor = eventClass.getDeclaredConstructor(Long.class, LocalDate.class, BigDecimal.class);
        constructor.setAccessible(true);
        return constructor.newInstance(installmentId, transactionDate, principalPortion);
    }

    @Test
    void manualPenaltyChargeBreachingCapIsRejected() {
        setUpLoanFixture(new BigDecimal("4980"));

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> this.service.validatePenaltyChargeAgainstCap(this.loan, new BigDecimal("30")));
    }

    @Test
    void manualPenaltyChargeUpToCapIsAllowed() {
        setUpLoanFixture(new BigDecimal("4980"));

        assertDoesNotThrow(() -> this.service.validatePenaltyChargeAgainstCap(this.loan, new BigDecimal("20")));
    }

    @Test
    void noDailyLateFeesAccrueWhenExistingPenaltiesAlreadyAtCap() {
        setUpLoanFixture(DISBURSED_PRINCIPAL);

        this.service.syncDailyLateFeesForLoan(LOAN_ID, BUSINESS_DATE);

        verify(this.loanChargeRepository, never()).saveAndFlush(any(LoanCharge.class));
    }

    /**
     * Builds a service with an active loan whose single 5,000 principal installment fell due on 2026-01-15 and was
     * never repaid, plus one pre-existing active penalty charge of {@code existingPenaltyAmount} that has no
     * m_loan_daily_late_fee metadata.
     */
    private void setUpLoanFixture(final BigDecimal existingPenaltyAmount) {
        final MonetaryCurrency currency = new MonetaryCurrency("KES", 2, 0);

        final Charge chargeDefinition = mock(Charge.class);
        when(chargeDefinition.isActive()).thenReturn(true);
        when(chargeDefinition.isLoanCharge()).thenReturn(true);
        when(chargeDefinition.isPenalty()).thenReturn(true);
        when(chargeDefinition.getChargeTimeType()).thenReturn(ChargeTimeType.OVERDUE_INSTALLMENT.getValue());
        when(chargeDefinition.getChargeCalculation()).thenReturn(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue());
        when(chargeDefinition.getAmount()).thenReturn(new BigDecimal("2"));
        when(chargeDefinition.getId()).thenReturn(99L);
        when(chargeDefinition.getCurrencyCode()).thenReturn("KES");
        when(chargeDefinition.getChargePaymentMode()).thenReturn(0);

        final LoanProduct loanProduct = mock(LoanProduct.class);
        when(loanProduct.getLoanProductCharges()).thenReturn(List.of(chargeDefinition));

        final LoanProductRelatedDetail productRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(productRelatedDetail.fetchDaysInMonthType()).thenReturn(DaysInMonthType.DAYS_30);
        when(productRelatedDetail.isInterestRecalculationEnabled()).thenReturn(false);

        final LoanCharge existingPenaltyCharge = mock(LoanCharge.class);
        when(existingPenaltyCharge.isPenaltyCharge()).thenReturn(true);
        when(existingPenaltyCharge.isActive()).thenReturn(true);
        when(existingPenaltyCharge.amount()).thenReturn(existingPenaltyAmount);
        final Set<LoanCharge> loanCharges = new HashSet<>();
        loanCharges.add(existingPenaltyCharge);

        this.loan = mock(Loan.class);
        this.installment = new LoanRepaymentScheduleInstallment(this.loan, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15),
                DISBURSED_PRINCIPAL, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        final LoanRepaymentScheduleInstallment installment = this.installment;
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>();
        installments.add(installment);

        when(this.loan.getId()).thenReturn(LOAN_ID);
        when(this.loan.status()).thenReturn(LoanStatus.ACTIVE);
        when(this.loan.getCurrency()).thenReturn(currency);
        when(this.loan.hasCurrencyCodeOf("KES")).thenReturn(true);
        when(this.loan.getDisbursedAmount()).thenReturn(DISBURSED_PRINCIPAL);
        when(this.loan.getLoanProduct()).thenReturn(loanProduct);
        when(this.loan.getLoanProductRelatedDetail()).thenReturn(productRelatedDetail);
        when(this.loan.repaymentScheduleDetail()).thenReturn(productRelatedDetail);
        when(this.loan.getRepaymentScheduleInstallments()).thenReturn(installments);
        when(this.loan.fetchRepaymentScheduleInstallment(anyInt())).thenReturn(installment);
        when(this.loan.charges()).thenReturn(loanCharges);
        when(this.loan.findExistingTransactionIds()).thenReturn(new ArrayList<>());
        when(this.loan.findExistingReversedTransactionIds()).thenReturn(new ArrayList<>());
        when(this.loan.fetchInterestRecalculateFromDate()).thenReturn(BUSINESS_DATE);
        when(this.loan.reprocessTransactions()).thenReturn(null);
        when(this.loan.isUpfrontAccrualAccountingEnabledOnLoanProduct()).thenReturn(false);

        final LoanAssembler loanAssembler = mock(LoanAssembler.class);
        when(loanAssembler.assembleFrom(LOAN_ID)).thenReturn(this.loan);

        this.loanChargeRepository = mock(LoanChargeRepository.class);
        this.loanDailyLateFeeRepository = mock(LoanDailyLateFeeRepository.class);
        when(this.loanDailyLateFeeRepository.findByLoanIdAndActiveTrueOrderByPenaltyDateAsc(LOAN_ID))
                .thenReturn(Collections.emptyList());
        when(this.loanDailyLateFeeRepository.findByLoanIdAndChargeIdAndPenaltyDate(any(), any(), any()))
                .thenReturn(Optional.empty());

        this.configurationDomainService = mock(ConfigurationDomainService.class);
        when(this.configurationDomainService.retrievePenaltyWaitPeriod()).thenReturn(5L);

        final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository = mock(ApplicationCurrencyRepositoryWrapper.class);
        when(applicationCurrencyRepository.findOneWithNotFoundDetection(any(MonetaryCurrency.class)))
                .thenReturn(mock(ApplicationCurrency.class));

        this.jdbcTemplate = mock(JdbcTemplate.class);
        this.principalReductionRows.clear();
        doAnswer(invocation -> new ArrayList<>(this.principalReductionRows)).when(this.jdbcTemplate).query(anyString(),
                any(RowMapper.class), any());

        this.businessEventNotifierService = mock(BusinessEventNotifierService.class);

        this.service = new LoanDailyLateFeeService(loanAssembler, mock(LoanRepositoryWrapper.class),
                mock(LoanTransactionRepository.class), this.loanChargeRepository, this.loanDailyLateFeeRepository,
                mock(ChargeRepositoryWrapper.class), applicationCurrencyRepository, mock(JournalEntryWritePlatformService.class),
                this.configurationDomainService, mock(AccountAssociationsReadPlatformService.class),
                mock(AccountTransferRepository.class), this.businessEventNotifierService, mock(LoanUtilService.class),
                mock(LoanScheduleHistoryWritePlatformService.class), mock(LoanAccountDomainService.class), this.jdbcTemplate);
    }
}
