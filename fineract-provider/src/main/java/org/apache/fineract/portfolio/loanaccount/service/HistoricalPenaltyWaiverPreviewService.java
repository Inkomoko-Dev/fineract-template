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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.charge.exception.LoanChargeNotFoundException;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverApprovalRequirement;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverPreviewData;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverTransactionImpactData;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.HistoricalPenaltyWaiverResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CGLT-656 dry run. Runs the real correction and then throws the database work away, so the preview cannot drift from
 * what submitting would actually do.
 *
 * <p>
 * Two things make this safe. The transaction is marked rollback-only before any work starts, so nothing survives even
 * on an unexpected path; and {@code spring.jpa.open-in-view=false} keeps the persistence context transaction-scoped, so
 * the mutated entities are discarded with it. <b>Changing that property silently breaks this design.</b>
 * </p>
 *
 * <p>
 * It calls the domain method directly and never the write service, which structurally excludes business events,
 * journal entries and their Odoo push, notes, accruals and the audit insert.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class HistoricalPenaltyWaiverPreviewService {

    private final LoanAssembler loanAssembler;
    private final LoanChargeRepository loanChargeRepository;
    private final LoanChargeReadPlatformService loanChargeReadPlatformService;
    private final HistoricalPenaltyWaiverApprovalPolicy approvalPolicy;
    private final HistoricalPenaltyWaiverReadPlatformService readPlatformService;
    private final GLClosureRepository glClosureRepository;
    private final ConfigurationDomainService configurationDomainService;
    private final PlatformTransactionManager transactionManager;

    public HistoricalPenaltyWaiverPreviewData preview(final Long loanId, final Long loanChargeId, final BigDecimal requestedWaiverAmount,
            final LocalDate requestedEffectiveDate) {

        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(true);

        return template.execute(status -> {
            status.setRollbackOnly();
            return buildPreview(loanId, loanChargeId, requestedWaiverAmount, requestedEffectiveDate);
        });
    }

    private HistoricalPenaltyWaiverPreviewData buildPreview(final Long loanId, final Long loanChargeId,
            final BigDecimal requestedWaiverAmount, final LocalDate requestedEffectiveDate) {

        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        final LoanCharge loanCharge = retrieveLoanChargeBy(loanId, loanChargeId);
        final MonetaryCurrency currency = loan.getCurrency();

        final Money amountPaidBefore = loanCharge.getAmountPaid(currency);
        final Money amountWaivedBefore = loanCharge.getAmountWaived(currency);
        final Money amountOutstandingBefore = loanCharge.getAmountOutstanding(currency);
        final Money waivable = amountPaidBefore.plus(amountOutstandingBefore);
        final BigDecimal waiverAmount = requestedWaiverAmount != null ? requestedWaiverAmount : waivable.getAmount();

        final LocalDate suggestedEffectiveDate = earliestRepaymentPaying(loan, loanCharge);
        final LocalDate effectiveDate = requestedEffectiveDate != null ? requestedEffectiveDate : suggestedEffectiveDate;

        final Integer loanStatusBefore = loan.status().getValue();
        final BigDecimal totalOutstandingBefore = loan.getSummary().getTotalOutstanding();
        final Map<Long, LoanTransaction> before = snapshotTransactions(loan);

        final HistoricalPenaltyWaiverResult result = loan.waiveLoanChargeHistorically(loanCharge, new LinkedHashMap<>(), new ArrayList<>(),
                new ArrayList<>(), null, requestedWaiverAmount, effectiveDate, accruedCharge(loan, loanCharge));

        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final HistoricalPenaltyWaiverApprovalRequirement requirement = this.approvalPolicy.determine(waiverAmount,
                loanCharge.getDueLocalDate(), businessDate);

        final GLClosure latestClosure = this.glClosureRepository.getLatestGLClosureByBranch(loan.getOfficeId());

        return HistoricalPenaltyWaiverPreviewData.builder().loanId(loanId).loanChargeId(loanChargeId)
                .chargeName(loanCharge.getCharge() == null ? null : loanCharge.getCharge().getName())
                .chargeDueDate(loanCharge.getDueLocalDate()).chargeAgeInDays(ageInDays(loanCharge.getDueLocalDate(), businessDate))
                .chargeAmount(loanCharge.amount()).chargeAmountPaidBefore(amountPaidBefore.getAmount())
                .chargeAmountWaivedBefore(amountWaivedBefore.getAmount()).chargeAmountOutstandingBefore(amountOutstandingBefore.getAmount())
                .chargeAmountWaivedAfter(loanCharge.getAmountWaived(currency).getAmount())
                .chargeAmountOutstandingAfter(loanCharge.getAmountOutstanding(currency).getAmount()).waiverAmount(waiverAmount)
                .partialWaiver(Money.of(currency, waiverAmount).isLessThan(waivable)).waiverEffectiveDate(effectiveDate)
                .suggestedEffectiveDate(suggestedEffectiveDate).loanStatusBefore(loanStatusBefore)
                .loanStatusAfter(loan.status().getValue()).totalOutstandingBefore(totalOutstandingBefore)
                .totalOutstandingAfter(loan.getSummary().getTotalOutstanding())
                .transactionProcessingStrategyName(strategyNameOf(loan))
                .reprocessedTransactionCount(result.getChangedTransactionDetail().getNewTransactionMappings().size())
                .transactions(describeImpact(before, result, currency)).requiresApproval(requirement.isRequired())
                .approvalTrigger(requirement.getTrigger()).nextApproverRequired(requirement.isRequired())
                .approverOptions(approverOptions(loanId, requirement)).correctionAllowed(correctionAllowed(latestClosure, effectiveDate))
                .latestClosureDate(latestClosure == null ? null : latestClosure.getClosingDate()).build();
    }

    private List<HistoricalPenaltyWaiverTransactionImpactData> describeImpact(final Map<Long, LoanTransaction> before,
            final HistoricalPenaltyWaiverResult result, final MonetaryCurrency currency) {

        final ChangedTransactionDetail changed = result.getChangedTransactionDetail();
        final List<HistoricalPenaltyWaiverTransactionImpactData> impact = new ArrayList<>();

        for (final Map.Entry<Long, LoanTransaction> entry : before.entrySet()) {
            final LoanTransaction original = entry.getValue();
            final LoanTransaction replacement = changed.getNewTransactionMappings().get(entry.getKey());

            if (replacement == null) {
                impact.add(row(entry.getKey(), original, HistoricalPenaltyWaiverTransactionImpactData.UNCHANGED, original, currency));
            } else {
                impact.add(row(entry.getKey(), original, HistoricalPenaltyWaiverTransactionImpactData.REVERSED_AND_REPLACED, replacement,
                        currency));
            }
        }

        final LoanTransaction waive = result.getWaiveTransaction();
        impact.add(new HistoricalPenaltyWaiverTransactionImpactData(null, waive.getTransactionDate(), "waiveCharges",
                HistoricalPenaltyWaiverTransactionImpactData.NEW, null, null, null, null, null, waive.getAmount(currency).getAmount(),
                BigDecimal.ZERO, BigDecimal.ZERO, waive.getFeeChargesPortion(currency).getAmount(),
                waive.getPenaltyChargesPortion(currency).getAmount()));

        return impact;
    }

    private HistoricalPenaltyWaiverTransactionImpactData row(final Long transactionId, final LoanTransaction original,
            final String changeType, final LoanTransaction after, final MonetaryCurrency currency) {

        return new HistoricalPenaltyWaiverTransactionImpactData(transactionId, original.getTransactionDate(),
                original.getTypeOf() == null ? null : original.getTypeOf().name(), changeType, original.getAmount(currency).getAmount(),
                original.getPrincipalPortion(), original.getInterestPortion(currency).getAmount(),
                original.getFeeChargesPortion(currency).getAmount(), original.getPenaltyChargesPortion(currency).getAmount(),
                after.getAmount(currency).getAmount(), after.getPrincipalPortion(), after.getInterestPortion(currency).getAmount(),
                after.getFeeChargesPortion(currency).getAmount(), after.getPenaltyChargesPortion(currency).getAmount());
    }

    /**
     * Copies the allocation of every live transaction before the replay touches it. The replay mutates the originals in
     * place, so reading them afterwards would report the new figures as though they were the old ones.
     */
    private Map<Long, LoanTransaction> snapshotTransactions(final Loan loan) {
        final Map<Long, LoanTransaction> snapshot = new LinkedHashMap<>();
        for (final LoanTransaction transaction : loan.getLoanTransactions()) {
            if (!transaction.isReversed() && transaction.getId() != null) {
                snapshot.put(transaction.getId(), LoanTransaction.copyTransactionProperties(transaction));
            }
        }
        return snapshot;
    }

    private LocalDate earliestRepaymentPaying(final Loan loan, final LoanCharge loanCharge) {
        LocalDate earliest = null;
        for (final LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction.isReversed() || !transaction.isRepaymentType()) {
                continue;
            }
            for (final LoanChargePaidBy paidBy : transaction.getLoanChargesPaid()) {
                if (paidBy.getLoanCharge() != null && loanCharge.getId() != null
                        && loanCharge.getId().equals(paidBy.getLoanCharge().getId())
                        && (earliest == null || transaction.getTransactionDate().isBefore(earliest))) {
                    earliest = transaction.getTransactionDate();
                }
            }
        }
        return earliest;
    }

    private Collection<org.apache.fineract.useradministration.data.AppUserData> approverOptions(final Long loanId,
            final HistoricalPenaltyWaiverApprovalRequirement requirement) {
        return requirement.isRequired() ? this.readPlatformService.retrieveApproverOptions(loanId) : new ArrayList<>();
    }

    private boolean correctionAllowed(final GLClosure latestClosure, final LocalDate effectiveDate) {
        if (latestClosure == null || effectiveDate == null) {
            return true;
        }
        final boolean closed = !latestClosure.getClosingDate().isBefore(effectiveDate);
        return !closed || this.configurationDomainService.isCorrectionsInClosedPeriodsAllowed();
    }

    private Money accruedCharge(final Loan loan, final LoanCharge loanCharge) {
        Money accruedCharge = Money.zero(loan.getCurrency());
        if (loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()) {
            for (final org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByData paidBy : this.loanChargeReadPlatformService
                    .retriveLoanChargesPaidBy(loanCharge.getId(),
                            org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType.ACCRUAL, null)) {
                accruedCharge = accruedCharge.plus(paidBy.getAmount());
            }
        }
        return accruedCharge;
    }

    private Integer ageInDays(final LocalDate dueDate, final LocalDate businessDate) {
        return dueDate == null ? null : (int) ChronoUnit.DAYS.between(dueDate, businessDate);
    }

    private String strategyNameOf(final Loan loan) {
        return loan.transactionProcessingStrategy() == null ? null : loan.transactionProcessingStrategy().toData().name();
    }

    private LoanCharge retrieveLoanChargeBy(final Long loanId, final Long loanChargeId) {
        final LoanCharge loanCharge = this.loanChargeRepository.findById(loanChargeId)
                .orElseThrow(() -> new LoanChargeNotFoundException(loanChargeId));
        if (loanCharge.hasNotLoanIdentifiedBy(loanId)) {
            throw new LoanChargeNotFoundException(loanChargeId, loanId);
        }
        return loanCharge;
    }
}
