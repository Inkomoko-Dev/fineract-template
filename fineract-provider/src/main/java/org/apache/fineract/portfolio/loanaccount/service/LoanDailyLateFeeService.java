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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.account.data.PortfolioAccountData;
import org.apache.fineract.portfolio.account.domain.AccountTransferRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferTransaction;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanApplyOverdueChargeBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.exception.ClientNotActiveException;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.group.exception.GroupNotActiveException;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDailyLateFee;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDailyLateFeeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInterestRecalcualtionAdditionalDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanproduct.exception.InvalidCurrencyException;
import org.apache.fineract.portfolio.loanproduct.exception.LinkedAccountRequiredException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanDailyLateFeeService {

    private final LoanAssembler loanAssembler;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanTransactionRepository loanTransactionRepository;
    private final LoanChargeRepository loanChargeRepository;
    private final LoanDailyLateFeeRepository loanDailyLateFeeRepository;
    private final ChargeRepositoryWrapper chargeRepository;
    private final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;
    private final JournalEntryWritePlatformService journalEntryWritePlatformService;
    private final ConfigurationDomainService configurationDomainService;
    private final AccountAssociationsReadPlatformService accountAssociationsReadPlatformService;
    private final AccountTransferRepository accountTransferRepository;
    private final BusinessEventNotifierService businessEventNotifierService;
    private final LoanUtilService loanUtilService;
    private final LoanScheduleHistoryWritePlatformService loanScheduleHistoryWritePlatformService;
    private final LoanAccountDomainService loanAccountDomainService;
    private final JdbcTemplate jdbcTemplate;

    private static final class PrincipalReductionEvent {

        private final Long installmentId;
        private final LocalDate transactionDate;
        private final BigDecimal principalPortion;

        private PrincipalReductionEvent(final Long installmentId, final LocalDate transactionDate, final BigDecimal principalPortion) {
            this.installmentId = installmentId;
            this.transactionDate = transactionDate;
            this.principalPortion = principalPortion;
        }
    }

    private static final class PrincipalReductionEventMapper implements RowMapper<PrincipalReductionEvent> {

        @Override
        public PrincipalReductionEvent mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return new PrincipalReductionEvent(rs.getLong("installmentId"), JdbcSupport.getLocalDate(rs, "transactionDate"),
                    JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalPortion"));
        }
    }

    private static final class DailyLateFeeInstallmentState {

        private final Long installmentId;
        private final LocalDate eligibleFromDate;
        private final BigDecimal principalDue;
        private final Map<LocalDate, BigDecimal> principalReductionsByDate = new TreeMap<>();
        private BigDecimal outstandingPrincipal;

        private DailyLateFeeInstallmentState(final Long installmentId, final LocalDate eligibleFromDate, final BigDecimal principalDue) {
            this.installmentId = installmentId;
            this.eligibleFromDate = eligibleFromDate;
            this.principalDue = principalDue;
            this.outstandingPrincipal = principalDue;
        }

        private void addReduction(final LocalDate transactionDate, final BigDecimal principalPortion) {
            this.principalReductionsByDate.merge(transactionDate, principalPortion, BigDecimal::add);
        }

        private void initializeOutstanding(final LocalDate generationStartDate) {
            BigDecimal reducedPrincipal = BigDecimal.ZERO;
            for (Map.Entry<LocalDate, BigDecimal> entry : this.principalReductionsByDate.entrySet()) {
                if (!entry.getKey().isBefore(generationStartDate)) {
                    break;
                }
                reducedPrincipal = reducedPrincipal.add(entry.getValue());
            }
            this.outstandingPrincipal = this.principalDue.subtract(reducedPrincipal).max(BigDecimal.ZERO);
        }

        private void applyReductionsForDate(final LocalDate currentDate) {
            final BigDecimal reduction = this.principalReductionsByDate.get(currentDate);
            if (reduction != null) {
                this.outstandingPrincipal = this.outstandingPrincipal.subtract(reduction).max(BigDecimal.ZERO);
            }
        }

        private boolean isPenaltyEligibleOn(final LocalDate penaltyDate) {
            return !penaltyDate.isBefore(this.eligibleFromDate) && this.outstandingPrincipal.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    private static final class DailyLateFeeProcessingState {

        private boolean changed;
        private boolean runInterestRecalculation;
        private LocalDate recalculateFrom;
        private LocalDate lastChargeDate;

        private void markChanged() {
            this.changed = true;
        }

        private void includeRecalculateFrom(final LocalDate date) {
            if (date == null) {
                return;
            }
            if (this.recalculateFrom == null || this.recalculateFrom.isAfter(date)) {
                this.recalculateFrom = date;
            }
        }

        private void includeLastChargeDate(final LocalDate date) {
            if (date == null) {
                return;
            }
            if (this.lastChargeDate == null || this.lastChargeDate.isBefore(date)) {
                this.lastChargeDate = date;
            }
        }
    }

    @Transactional
    public void applyOverdueChargesForLoan(final Long loanId, final java.util.Collection<OverdueLoanScheduleData> overdueLoanScheduleDatas) {
        processDailyLateFeesForLoan(loanId, DateUtils.getBusinessLocalDate(), null, true);
    }

    @Transactional
    public void syncDailyLateFeesForLoan(final Long loanId, final LocalDate effectiveDate) {
        processDailyLateFeesForLoan(loanId, effectiveDate, null, false);
    }

    @Transactional
    public void rebuildAndSyncDailyLateFeesForLoan(final Long loanId, final LocalDate rebuildFromDate,
            final LocalDate effectiveDate) {
        processDailyLateFeesForLoan(loanId, effectiveDate, rebuildFromDate, false);
    }

    private Charge findDailyLateFeeChargeDefinition(final Loan loan) {
        final List<Charge> overduePenaltyCharges = loan.getLoanProduct().getLoanProductCharges().stream().filter(Charge::isActive)
                .filter(Charge::isLoanCharge).filter(Charge::isPenalty)
                .filter(charge -> ChargeTimeType.fromInt(charge.getChargeTimeType()).isOverdueInstallment()).toList();

        if (overduePenaltyCharges.isEmpty()) {
            return null;
        }
        if (overduePenaltyCharges.size() > 1) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.daily.late.fee.multiple.overdue.charges",
                    "Only one active overdue penalty charge definition is supported for daily late fees.", loan.getId());
        }

        final Charge chargeDefinition = overduePenaltyCharges.get(0);
        final ChargeCalculationType calculationType = ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation());
        if (!calculationType.isPercentageOfAmount()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.daily.late.fee.invalid.charge.calculation",
                    "Daily late fees require an overdue penalty charge configured as a percentage of principal.", chargeDefinition.getId(),
                    calculationType.getCode());
        }
        return chargeDefinition;
    }

    private BigDecimal defaultToZero(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal calculateDailyLateFeeCap(final Loan loan) {
        BigDecimal disbursedPrincipal = loan.getDisbursedAmount();
        if (disbursedPrincipal == null || disbursedPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            disbursedPrincipal = loan.getPrincpal().getAmount();
        }
        return defaultToZero(disbursedPrincipal).setScale(6, MoneyHelper.getRoundingMode());
    }

    private BigDecimal calculateDailyLateFeeRate(final BigDecimal monthlyRate, final Loan loan, final LocalDate penaltyDate) {
        final DaysInMonthType daysInMonthType = loan.getLoanProductRelatedDetail().fetchDaysInMonthType();
        final int divisor = daysInMonthType.isDaysInMonth_30() ? 30 : penaltyDate.lengthOfMonth();
        return monthlyRate.divide(BigDecimal.valueOf(100L), MoneyHelper.getMathContext())
                .divide(BigDecimal.valueOf(divisor), 12, MoneyHelper.getRoundingMode());
    }

    private LocalDate determineFirstDailyLateFeeDate(final Loan loan, final long gracePeriod) {
        return loan.getRepaymentScheduleInstallments().stream().filter(installment -> !installment.isRecalculatedInterestComponent())
                .filter(installment -> installment.getPrincipal(loan.getCurrency()).isGreaterThanZero())
                .map(installment -> installment.getDueDate().plusDays(gracePeriod + 1)).min(LocalDate::compareTo).orElse(null);
    }

    private LocalDate determineGenerationStartDate(final Long loanId, final LocalDate firstPenaltyDate, final LocalDate rebuildFromDate) {
        if (rebuildFromDate != null) {
            return rebuildFromDate;
        }
        final List<LoanDailyLateFee> activeDailyLateFees = this.loanDailyLateFeeRepository.findByLoanIdAndActiveTrueOrderByPenaltyDateAsc(loanId);
        if (activeDailyLateFees.isEmpty()) {
            return firstPenaltyDate;
        }
        return activeDailyLateFees.get(activeDailyLateFees.size() - 1).getPenaltyDate().plusDays(1);
    }

    private List<DailyLateFeeInstallmentState> buildDailyLateFeeInstallmentStates(final Loan loan, final long gracePeriod,
            final LocalDate generationStartDate) {
        final Map<Long, DailyLateFeeInstallmentState> states = new LinkedHashMap<>();
        for (LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (installment.isRecalculatedInterestComponent()) {
                continue;
            }
            final BigDecimal principalDue = installment.getPrincipal(loan.getCurrency()).getAmount();
            if (principalDue == null || principalDue.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            states.put(installment.getId(), new DailyLateFeeInstallmentState(installment.getId(),
                    installment.getDueDate().plusDays(gracePeriod + 1), principalDue));
        }

        if (states.isEmpty()) {
            return new ArrayList<>();
        }

        final String sql = "select m.loan_repayment_schedule_id as installmentId, lt.transaction_date as transactionDate, "
                + "sum(coalesce(m.principal_portion_derived, 0)) as principalPortion "
                + "from m_loan_transaction_repayment_schedule_mapping m "
                + "join m_loan_transaction lt on lt.id = m.loan_transaction_id "
                + "where lt.loan_id = ? and lt.is_reversed = false and m.principal_portion_derived is not null "
                + "group by m.loan_repayment_schedule_id, lt.transaction_date order by lt.transaction_date asc";
        final List<PrincipalReductionEvent> reductionEvents = this.jdbcTemplate.query(sql, new PrincipalReductionEventMapper(), loan.getId());
        for (PrincipalReductionEvent reductionEvent : reductionEvents) {
            final DailyLateFeeInstallmentState state = states.get(reductionEvent.installmentId);
            if (state != null) {
                state.addReduction(reductionEvent.transactionDate, reductionEvent.principalPortion);
            }
        }

        final List<DailyLateFeeInstallmentState> orderedStates = new ArrayList<>(states.values());
        for (DailyLateFeeInstallmentState state : orderedStates) {
            state.initializeOutstanding(generationStartDate);
        }
        orderedStates.sort(Comparator.comparing(state -> state.eligibleFromDate));
        return orderedStates;
    }

    private BigDecimal calculateCumulativeOverduePrincipal(final List<DailyLateFeeInstallmentState> installmentStates,
            final LocalDate penaltyDate) {
        BigDecimal cumulativeOverduePrincipal = BigDecimal.ZERO;
        for (DailyLateFeeInstallmentState installmentState : installmentStates) {
            if (installmentState.isPenaltyEligibleOn(penaltyDate)) {
                cumulativeOverduePrincipal = cumulativeOverduePrincipal.add(installmentState.outstandingPrincipal);
            }
        }
        return cumulativeOverduePrincipal;
    }

    private void applyPrincipalReductionsForDate(final List<DailyLateFeeInstallmentState> installmentStates, final LocalDate penaltyDate) {
        for (DailyLateFeeInstallmentState installmentState : installmentStates) {
            installmentState.applyReductionsForDate(penaltyDate);
        }
    }

    private void upsertDailyLateFeeMetadata(final Loan loan, final Charge chargeDefinition, final LoanCharge loanCharge,
            final LocalDate penaltyDate, final BigDecimal overduePrincipalAmount, final BigDecimal monthlyRate,
            final BigDecimal dailyRate, final BigDecimal penaltyAmount) {
        final Optional<LoanDailyLateFee> existingDailyLateFee = this.loanDailyLateFeeRepository.findByLoanIdAndChargeIdAndPenaltyDate(
                loan.getId(), chargeDefinition.getId(), penaltyDate);
        if (existingDailyLateFee.isPresent()) {
            final LoanDailyLateFee lateFee = existingDailyLateFee.get();
            lateFee.reactivate(loanCharge, overduePrincipalAmount, monthlyRate, dailyRate, penaltyAmount);
            this.loanDailyLateFeeRepository.save(lateFee);
        } else {
            this.loanDailyLateFeeRepository
                    .save(new LoanDailyLateFee(loan, chargeDefinition, loanCharge, penaltyDate, overduePrincipalAmount, monthlyRate,
                            dailyRate, penaltyAmount));
        }
    }

    private void deactivateDailyLateFeesFrom(final Loan loan, final LocalDate rebuildFromDate,
            final DailyLateFeeProcessingState processingState) {
        final List<LoanDailyLateFee> lateFeesToDeactivate = this.loanDailyLateFeeRepository
                .findByLoanIdAndPenaltyDateGreaterThanEqualOrderByPenaltyDateAsc(loan.getId(), rebuildFromDate);
        if (lateFeesToDeactivate.isEmpty()) {
            return;
        }

        boolean metadataChanged = false;
        for (LoanDailyLateFee lateFee : lateFeesToDeactivate) {
            if (!lateFee.isActive()) {
                continue;
            }
            lateFee.deactivate();
            metadataChanged = true;
            processingState.markChanged();
            processingState.runInterestRecalculation = true;
            processingState.includeRecalculateFrom(lateFee.getPenaltyDate());

            final LoanCharge loanCharge = lateFee.getLoanCharge();
            if (loanCharge != null && loanCharge.isActive()) {
                loanCharge.setActive(false);
            }
        }

        if (metadataChanged) {
            this.loanDailyLateFeeRepository.saveAll(lateFeesToDeactivate);
        }
    }

    private LoanCharge createDailyLateFeeCharge(final Loan loan, final Charge chargeDefinition, final LocalDate penaltyDate,
            final BigDecimal dailyPenaltyAmount) {
        return new LoanCharge(loan, chargeDefinition, BigDecimal.ZERO, dailyPenaltyAmount, ChargeTimeType.OVERDUE_INSTALLMENT,
                ChargeCalculationType.FLAT, penaltyDate, null, null, BigDecimal.ZERO);
    }

    private void finalizeDailyLateFeeChanges(final Loan loan, final List<Long> existingTransactionIds,
            final List<Long> existingReversedTransactionIds, final DailyLateFeeProcessingState processingState) {
        if (!processingState.changed) {
            return;
        }

        boolean reprocessRequired = true;
        LocalDate recalculateFrom = processingState.recalculateFrom == null ? DateUtils.getBusinessLocalDate()
                : processingState.recalculateFrom;
        final LocalDate recalculatedTill = loan.fetchInterestRecalculateFromDate();
        if (recalculateFrom.isAfter(recalculatedTill)) {
            recalculateFrom = recalculatedTill;
        }

        if (loan.repaymentScheduleDetail().isInterestRecalculationEnabled()) {
            if (processingState.runInterestRecalculation && loan.isFeeCompoundingEnabledForInterestRecalculation()) {
                runScheduleRecalculation(loan, recalculateFrom);
                reprocessRequired = false;
            }
            updateOriginalSchedule(loan);
        }

        if (reprocessRequired) {
            addInstallmentIfPenaltyAppliedAfterLastDueDate(loan, processingState.lastChargeDate);
            final ChangedTransactionDetail changedTransactionDetail = loan.reprocessTransactions();
            if (changedTransactionDetail != null) {
                for (final Map.Entry<Long, LoanTransaction> mapEntry : changedTransactionDetail.getNewTransactionMappings().entrySet()) {
                    this.loanTransactionRepository.save(mapEntry.getValue());
                    loan.addLoanTransaction(mapEntry.getValue());
                    updateLoanTransaction(mapEntry.getKey(), mapEntry.getValue());
                }
            }
            saveLoanWithDataIntegrityViolationChecks(loan);
        }

        postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds);

        if (loan.repaymentScheduleDetail().isInterestRecalculationEnabled() && processingState.runInterestRecalculation
                && loan.isFeeCompoundingEnabledForInterestRecalculation()) {
            this.loanAccountDomainService.recalculateAccruals(loan);
        }
    }

    private void processDailyLateFeesForLoan(final Long loanId, final LocalDate effectiveDate, final LocalDate rebuildFromDate,
            final boolean publishBusinessEvent) {
        if (effectiveDate == null) {
            return;
        }

        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        checkClientOrGroupActive(loan);

        if (publishBusinessEvent) {
            businessEventNotifierService.notifyPreBusinessEvent(new LoanApplyOverdueChargeBusinessEvent(loan));
        }

        final List<Long> existingTransactionIds = new ArrayList<>(loan.findExistingTransactionIds());
        final List<Long> existingReversedTransactionIds = new ArrayList<>(loan.findExistingReversedTransactionIds());
        final DailyLateFeeProcessingState processingState = new DailyLateFeeProcessingState();

        if (rebuildFromDate != null) {
            deactivateDailyLateFeesFrom(loan, rebuildFromDate, processingState);
        }

        final Charge chargeDefinition = findDailyLateFeeChargeDefinition(loan);
        if (chargeDefinition == null || !loan.status().isActive()) {
            finalizeDailyLateFeeChanges(loan, existingTransactionIds, existingReversedTransactionIds, processingState);
            if (publishBusinessEvent) {
                businessEventNotifierService.notifyPostBusinessEvent(new LoanApplyOverdueChargeBusinessEvent(loan));
            }
            return;
        }

        final long gracePeriod = this.configurationDomainService.retrievePenaltyWaitPeriod();
        final LocalDate firstPenaltyDate = determineFirstDailyLateFeeDate(loan, gracePeriod);
        if (firstPenaltyDate == null || effectiveDate.isBefore(firstPenaltyDate)) {
            finalizeDailyLateFeeChanges(loan, existingTransactionIds, existingReversedTransactionIds, processingState);
            if (publishBusinessEvent) {
                businessEventNotifierService.notifyPostBusinessEvent(new LoanApplyOverdueChargeBusinessEvent(loan));
            }
            return;
        }

        LocalDate generationStartDate = determineGenerationStartDate(loanId, firstPenaltyDate, rebuildFromDate);
        if (generationStartDate.isBefore(firstPenaltyDate)) {
            generationStartDate = firstPenaltyDate;
        }
        if (generationStartDate.isAfter(effectiveDate)) {
            finalizeDailyLateFeeChanges(loan, existingTransactionIds, existingReversedTransactionIds, processingState);
            if (publishBusinessEvent) {
                businessEventNotifierService.notifyPostBusinessEvent(new LoanApplyOverdueChargeBusinessEvent(loan));
            }
            return;
        }

        final BigDecimal capAmount = calculateDailyLateFeeCap(loan);
        BigDecimal cumulativePenaltyAmount = rebuildFromDate == null
                ? defaultToZero(this.loanDailyLateFeeRepository.sumActivePenaltyAmount(loanId))
                : defaultToZero(this.loanDailyLateFeeRepository.sumActivePenaltyAmountBeforeDate(loanId, generationStartDate));

        if (cumulativePenaltyAmount.compareTo(capAmount) >= 0) {
            finalizeDailyLateFeeChanges(loan, existingTransactionIds, existingReversedTransactionIds, processingState);
            if (publishBusinessEvent) {
                businessEventNotifierService.notifyPostBusinessEvent(new LoanApplyOverdueChargeBusinessEvent(loan));
            }
            return;
        }

        final BigDecimal monthlyRate = chargeDefinition.getAmount();
        final List<DailyLateFeeInstallmentState> installmentStates = buildDailyLateFeeInstallmentStates(loan, gracePeriod,
                generationStartDate);
        LocalDate penaltyDate = generationStartDate;
        while (!penaltyDate.isAfter(effectiveDate) && cumulativePenaltyAmount.compareTo(capAmount) < 0) {
            final BigDecimal overduePrincipalAmount = calculateCumulativeOverduePrincipal(installmentStates, penaltyDate);
            if (overduePrincipalAmount.compareTo(BigDecimal.ZERO) > 0) {
                final BigDecimal dailyRate = calculateDailyLateFeeRate(monthlyRate, loan, penaltyDate);
                BigDecimal penaltyAmount = overduePrincipalAmount.multiply(dailyRate, MoneyHelper.getMathContext())
                        .setScale(6, MoneyHelper.getRoundingMode());
                final BigDecimal remainingCap = capAmount.subtract(cumulativePenaltyAmount);
                if (penaltyAmount.compareTo(remainingCap) > 0) {
                    penaltyAmount = remainingCap.setScale(6, MoneyHelper.getRoundingMode());
                }
                if (penaltyAmount.compareTo(BigDecimal.ZERO) > 0) {
                    final LoanCharge dailyLateFeeCharge = createDailyLateFeeCharge(loan, chargeDefinition, penaltyDate, penaltyAmount);
                    final boolean appliedOnBackDate = addCharge(loan, chargeDefinition, dailyLateFeeCharge);
                    upsertDailyLateFeeMetadata(loan, chargeDefinition, dailyLateFeeCharge, penaltyDate, overduePrincipalAmount,
                            monthlyRate, dailyRate, penaltyAmount);
                    processingState.markChanged();
                    processingState.runInterestRecalculation = processingState.runInterestRecalculation || appliedOnBackDate;
                    processingState.includeRecalculateFrom(penaltyDate);
                    processingState.includeLastChargeDate(penaltyDate);
                    cumulativePenaltyAmount = cumulativePenaltyAmount.add(penaltyAmount);
                }
            }
            applyPrincipalReductionsForDate(installmentStates, penaltyDate);
            penaltyDate = penaltyDate.plusDays(1);
        }

        finalizeDailyLateFeeChanges(loan, existingTransactionIds, existingReversedTransactionIds, processingState);
        if (publishBusinessEvent) {
            businessEventNotifierService.notifyPostBusinessEvent(new LoanApplyOverdueChargeBusinessEvent(loan));
        }
    }

    private void addInstallmentIfPenaltyAppliedAfterLastDueDate(final Loan loan, final LocalDate lastChargeDate) {
        if (lastChargeDate != null) {
            List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
            LoanRepaymentScheduleInstallment lastInstallment = loan.fetchRepaymentScheduleInstallment(installments.size());
            if (lastChargeDate.isAfter(lastInstallment.getDueDate())) {
                if (lastInstallment.isRecalculatedInterestComponent()) {
                    installments.remove(lastInstallment);
                    lastInstallment = loan.fetchRepaymentScheduleInstallment(installments.size());
                }
                boolean recalculatedInterestComponent = true;
                BigDecimal principal = BigDecimal.ZERO;
                BigDecimal interest = BigDecimal.ZERO;
                BigDecimal feeCharges = BigDecimal.ZERO;
                BigDecimal penaltyCharges = BigDecimal.ONE;
                final Set<LoanInterestRecalcualtionAdditionalDetails> compoundingDetails = null;
                LoanRepaymentScheduleInstallment newEntry = new LoanRepaymentScheduleInstallment(loan, installments.size() + 1,
                        lastInstallment.getDueDate(), lastChargeDate, principal, interest, feeCharges, penaltyCharges,
                        recalculatedInterestComponent, compoundingDetails);
                loan.addLoanRepaymentScheduleInstallment(newEntry);
            }
        }
    }

    private boolean addCharge(final Loan loan, final Charge chargeDefinition, final LoanCharge loanCharge) {
        if (!loan.hasCurrencyCodeOf(chargeDefinition.getCurrencyCode())) {
            final String errorMessage = "Charge and Loan must have the same currency.";
            throw new InvalidCurrencyException("loanCharge", "attach.to.loan", errorMessage);
        }

        if (loanCharge.getChargePaymentMode().isPaymentModeAccountTransfer()) {
            final PortfolioAccountData portfolioAccountData = this.accountAssociationsReadPlatformService
                    .retriveLoanLinkedAssociation(loan.getId());
            if (portfolioAccountData == null) {
                final String errorMessage = loanCharge.name() + "Charge  requires linked savings account for payment";
                throw new LinkedAccountRequiredException("loanCharge.add", errorMessage, loanCharge.name());
            }
        }

        loan.addLoanCharge(loanCharge);
        this.loanChargeRepository.saveAndFlush(loanCharge);

        if (loan.status().isActive() && loan.isUpfrontAccrualAccountingEnabledOnLoanProduct()) {
            final LoanTransaction applyLoanChargeTransaction = loan.handleChargeAppliedTransaction(loanCharge, null);
            this.loanTransactionRepository.saveAndFlush(applyLoanChargeTransaction);
        }
        return loanCharge.getDueLocalDate() == null || DateUtils.getBusinessLocalDate().isAfter(loanCharge.getDueLocalDate());
    }

    private void runScheduleRecalculation(final Loan loan, final LocalDate recalculateFrom) {
        if (loan.repaymentScheduleDetail().isInterestRecalculationEnabled()) {
            ScheduleGeneratorDTO generatorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
            ChangedTransactionDetail changedTransactionDetail = loan
                    .handleRegenerateRepaymentScheduleWithInterestRecalculation(generatorDTO);
            saveLoanWithDataIntegrityViolationChecks(loan);
            if (changedTransactionDetail != null) {
                for (final Map.Entry<Long, LoanTransaction> mapEntry : changedTransactionDetail.getNewTransactionMappings().entrySet()) {
                    this.loanTransactionRepository.save(mapEntry.getValue());
                    loan.addLoanTransaction(mapEntry.getValue());
                    updateLoanTransaction(mapEntry.getKey(), mapEntry.getValue());
                }
            }
        }
    }

    private void updateOriginalSchedule(final Loan loan) {
        if (loan.repaymentScheduleDetail().isInterestRecalculationEnabled()) {
            final LocalDate recalculateFrom = null;
            ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
            createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
        }
    }

    private void createAndSaveLoanScheduleArchive(final Loan loan, final ScheduleGeneratorDTO scheduleGeneratorDTO) {
        LoanRescheduleRequest loanRescheduleRequest = null;
        LoanScheduleModel loanScheduleModel = loan.regenerateScheduleModel(scheduleGeneratorDTO);
        List<LoanRepaymentScheduleInstallment> installments = retrieveRepaymentScheduleFromModel(loanScheduleModel);
        this.loanScheduleHistoryWritePlatformService.createAndSaveLoanScheduleArchive(installments, loan, loanRescheduleRequest);
    }

    private List<LoanRepaymentScheduleInstallment> retrieveRepaymentScheduleFromModel(final LoanScheduleModel model) {
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>();
        for (final LoanScheduleModelPeriod scheduledLoanInstallment : model.getPeriods()) {
            if (scheduledLoanInstallment.isRepaymentPeriod()) {
                final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(null,
                        scheduledLoanInstallment.periodNumber(), scheduledLoanInstallment.periodFromDate(),
                        scheduledLoanInstallment.periodDueDate(), scheduledLoanInstallment.principalDue(),
                        scheduledLoanInstallment.interestDue(), scheduledLoanInstallment.feeChargesDue(),
                        scheduledLoanInstallment.penaltyChargesDue(), scheduledLoanInstallment.isRecalculatedInterestComponent(),
                        scheduledLoanInstallment.getLoanCompoundingDetails());
                installments.add(installment);
            }
        }
        return installments;
    }

    private void saveLoanWithDataIntegrityViolationChecks(final Loan loan) {
        try {
            this.loanRepositoryWrapper.save(loan);
        } catch (final JpaSystemException | DataIntegrityViolationException e) {
            final Throwable realCause = e.getCause();
            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");
            if (realCause.getMessage().toLowerCase().contains("external_id_unique")) {
                baseDataValidator.reset().parameter("externalId").failWithCode("value.must.be.unique");
            }
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                        dataValidationErrors, e);
            }
        }
    }

    private void postJournalEntries(final Loan loan, final List<Long> existingTransactionIds,
            final List<Long> existingReversedTransactionIds) {
        final MonetaryCurrency currency = loan.getCurrency();
        final ApplicationCurrency applicationCurrency = this.applicationCurrencyRepository.findOneWithNotFoundDetection(currency);
        boolean isAccountTransfer = false;
        final Map<String, Object> accountingBridgeData = loan.deriveAccountingBridgeData(applicationCurrency.toData(),
                existingTransactionIds, existingReversedTransactionIds, isAccountTransfer);
        this.journalEntryWritePlatformService.createJournalEntriesForLoan(accountingBridgeData);
    }

    private void checkClientOrGroupActive(final Loan loan) {
        final Client client = loan.client();
        if (client != null && client.isNotActive()) {
            throw new ClientNotActiveException(client.getId());
        }
        final Group group = loan.group();
        if (group != null && group.isNotActive()) {
            throw new GroupNotActiveException(group.getId());
        }
    }

    private void updateLoanTransaction(final Long loanTransactionId, final LoanTransaction newLoanTransaction) {
        final AccountTransferTransaction transferTransaction = this.accountTransferRepository.findByToLoanTransactionId(loanTransactionId);
        if (transferTransaction != null) {
            transferTransaction.updateToLoanTransaction(newLoanTransaction);
            this.accountTransferRepository.save(transferTransaction);
        }
    }
}
