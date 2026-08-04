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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.businessevent.domain.loan.charge.LoanWaiveChargeBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeWaivedException;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason;
import org.apache.fineract.portfolio.charge.exception.LoanChargeNotFoundException;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverApprovalRequirement;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverRequest;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByData;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.HistoricalPenaltyWaiverResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiver;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverTxn;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverTxnRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentReminder;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentReminderRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanproduct.domain.HistoricalCorrectionProductApproverRepository;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * CGLT-656. Waives a penalty a repayment has already paid, then lets the product's own transaction processing strategy
 * reallocate every dependent repayment, under one correction reference.
 *
 * <p>
 * Deliberately its own service rather than another method on {@link LoanWritePlatformServiceJpaRepositoryImpl}: the
 * historical waiver needs to be exercised in isolation, and that class takes sixty collaborators.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class HistoricalPenaltyWaiverService {

    private final LoanAssembler loanAssembler;
    private final LoanChargeRepository loanChargeRepository;
    private final LoanTransactionRepository loanTransactionRepository;
    private final LoanHistoricalPenaltyWaiverRepository waiverRepository;
    private final LoanHistoricalPenaltyWaiverTxnRepository waiverTxnRepository;
    private final HistoricalCorrectionProductApproverRepository approverRepository;
    private final HistoricalPenaltyWaiverApprovalPolicy approvalPolicy;
    private final NoteRepository noteRepository;
    private final BusinessEventNotifierService businessEventNotifierService;
    private final LoanChargeReadPlatformService loanChargeReadPlatformService;
    private final JournalEntryWritePlatformService journalEntryWritePlatformService;
    private final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanRepaymentReminderRepository loanRepaymentReminderRepository;

    @Transactional
    public CommandProcessingResult submit(final Long loanId, final Long loanChargeId, final HistoricalPenaltyWaiverRequest request,
            final Long submittedByUserId, final OffsetDateTime submittedOn, final LocalDate businessDate) {

        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        final LoanCharge loanCharge = retrieveLoanChargeBy(loanId, loanChargeId);
        final MonetaryCurrency currency = loan.getCurrency();

        validateWaivable(loan, loanCharge, currency);

        final BigDecimal expectedPaidAmount = request.getExpectedPaidAmount();
        final Money amountPaid = loanCharge.getAmountPaid(currency);
        if (Money.of(currency, expectedPaidAmount).isNotEqualTo(amountPaid)) {
            throw validationError("validation.msg.loan.charge.historical.waiver.expectedPaidAmount.stale",
                    "The penalty has been paid or adjusted since this request was prepared; the amount currently paid is "
                            + amountPaid.getAmount() + ".",
                    LoanApiConstants.expectedPaidAmountParamName, expectedPaidAmount);
        }

        final BigDecimal requestedWaiverAmount = request.getWaiverAmount();
        final Money waivable = amountPaid.plus(loanCharge.getAmountOutstanding(currency));
        if (requestedWaiverAmount != null && Money.of(currency, requestedWaiverAmount).isGreaterThan(waivable)) {
            throw validationError("validation.msg.loan.charge.historical.waiver.amount.exceeds.penalty",
                    "The waiver amount may not exceed the penalty balance of " + waivable.getAmount() + ".",
                    LoanApiConstants.waiverAmountParamName, requestedWaiverAmount);
        }

        final BigDecimal waiverAmount = requestedWaiverAmount != null ? requestedWaiverAmount : waivable.getAmount();
        final LocalDate effectiveDate = request.getWaiverEffectiveDate();
        final String reason = request.getReason();
        final Integer installmentNumber = request.getInstallmentNumber();

        final HistoricalPenaltyWaiverApprovalRequirement requirement = this.approvalPolicy.determine(waiverAmount,
                loanCharge.getDueLocalDate(), businessDate);

        Long nextApproverId = null;
        if (requirement.isRequired()) {
            nextApproverId = request.getNextApproverUserId();
            validateApprover(loan.productId(), nextApproverId);
        }

        final LoanHistoricalPenaltyWaiver waiver = LoanHistoricalPenaltyWaiver.submit(loanId, loan.getClientId(), loan.productId(),
                loan.getOfficeId(), loanChargeId, chargeIdOf(loanCharge), chargeNameOf(loanCharge), loanCharge.getDueLocalDate(),
                penaltyAgeInDays(loanCharge, businessDate), installmentNumber, loanCharge.amount(), amountPaid.getAmount(),
                loanCharge.getAmountWaived(currency).getAmount(), loanCharge.getAmountOutstanding(currency).getAmount(), waiverAmount,
                isPartial(waivable, waiverAmount, currency), effectiveDate, reason, requirement.isRequired(), requirement.getTrigger(),
                nextApproverId, submittedByUserId, submittedOn);

        this.waiverRepository.saveAndFlush(waiver);
        waiver.assignCorrectionReference();

        if (requirement.isRequired()) {
            return resultFor(request.getCommandId(), loan, waiver);
        }
        return execute(waiver, loan, loanCharge, request.getCommandId());
    }

    @Transactional
    public CommandProcessingResult approve(final Long waiverId, final Long approvedByUserId, final OffsetDateTime approvedOn) {

        final LoanHistoricalPenaltyWaiver waiver = retrieveWaiverBy(waiverId);
        requirePendingApproval(waiver);
        validateApprover(waiver.getProductId(), approvedByUserId);

        waiver.markApproved(approvedByUserId, approvedOn);

        final Loan loan = this.loanAssembler.assembleFrom(waiver.getLoanId());
        final LoanCharge loanCharge = retrieveLoanChargeBy(waiver.getLoanId(), waiver.getLoanChargeId());
        validateWaivable(loan, loanCharge, loan.getCurrency());

        return execute(waiver, loan, loanCharge, null);
    }

    @Transactional
    public CommandProcessingResult reject(final Long waiverId, final String decisionReason, final Long rejectedByUserId,
            final OffsetDateTime rejectedOn) {

        final LoanHistoricalPenaltyWaiver waiver = retrieveWaiverBy(waiverId);
        requirePendingApproval(waiver);

        if (StringUtils.isBlank(decisionReason)) {
            throw validationError("validation.msg.loan.charge.historical.waiver.decision.reason.required",
                    "A reason is mandatory when rejecting a historical penalty waiver.", LoanApiConstants.reasonParamName, null);
        }

        waiver.markRejected(rejectedByUserId, rejectedOn, decisionReason);
        this.waiverRepository.save(waiver);

        return new CommandProcessingResultBuilder().withEntityId(waiverId).withLoanId(waiver.getLoanId()).build();
    }

    /**
     * The whole correction, in one database transaction. Ordering matters: the accounting snapshot is taken inside the
     * domain method before the replay reverses anything, and the replacements the replay produced are persisted here
     * rather than in the aggregate.
     */
    private CommandProcessingResult execute(final LoanHistoricalPenaltyWaiver waiver, final Loan loan, final LoanCharge loanCharge,
            final Long commandId) {

        final MonetaryCurrency currency = loan.getCurrency();
        final Integer loanStatusBefore = loan.status().getValue();
        final BigDecimal totalOutstandingBefore = loan.getSummary().getTotalOutstanding();

        this.businessEventNotifierService.notifyPreBusinessEvent(new LoanWaiveChargeBusinessEvent(loanCharge));
        deleteLoanRepaymentReminders(loan);

        final Map<String, Object> changes = new LinkedHashMap<>();
        final List<Long> existingTransactionIds = new ArrayList<>();
        final List<Long> existingReversedTransactionIds = new ArrayList<>();

        final HistoricalPenaltyWaiverResult result = loan.waiveLoanChargeHistorically(loanCharge, changes, existingTransactionIds,
                existingReversedTransactionIds, waiver.getInstallmentNumber(), waiver.getWaiverAmount(), waiver.getWaiverEffectiveDate(),
                accruedCharge(loan, loanCharge, waiver.getInstallmentNumber()));

        final LoanTransaction waiveTransaction = result.getWaiveTransaction();
        this.loanTransactionRepository.saveAndFlush(waiveTransaction);
        this.loanRepositoryWrapper.saveAndFlush(loan);

        recordTouchedTransaction(waiver, waiveTransaction, LoanHistoricalPenaltyWaiverTxn.ROLE_WAIVE, currency);

        final ChangedTransactionDetail changedTransactionDetail = result.getChangedTransactionDetail();
        for (final Map.Entry<Long, LoanTransaction> mapEntry : changedTransactionDetail.getNewTransactionMappings().entrySet()) {
            final LoanTransaction replacement = mapEntry.getValue();
            this.loanTransactionRepository.save(replacement);
            loan.addLoanTransaction(replacement);
            recordReversedTransaction(waiver, mapEntry.getKey());
            recordTouchedTransaction(waiver, replacement, LoanHistoricalPenaltyWaiverTxn.ROLE_REPLACEMENT, currency);
        }

        this.noteRepository.save(Note.loanTransactionNote(loan, waiveTransaction, waiver.getCorrectionReference() + ": "
                + waiver.getReason()));

        waiver.markAwaitingOdooSync(waiveTransaction.getId(), changedTransactionDetail.getNewTransactionMappings().size(), loanStatusBefore,
                loan.status().getValue(), totalOutstandingBefore, loan.getSummary().getTotalOutstanding(),
                loanCharge.getAmountWaived(currency).getAmount(), loanCharge.getAmountOutstanding(currency).getAmount());
        waiver.recordStrategy(strategyIdOf(loan), strategyNameOf(loan), null);
        this.waiverRepository.save(waiver);

        postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds);
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanWaiveChargeBusinessEvent(loanCharge));

        changes.put("correctionReference", waiver.getCorrectionReference());
        return commandResultBuilder(commandId, loan, waiver).with(changes).build();
    }

    private CommandProcessingResult resultFor(final Long commandId, final Loan loan, final LoanHistoricalPenaltyWaiver waiver) {
        return commandResultBuilder(commandId, loan, waiver).build();
    }

    private CommandProcessingResultBuilder commandResultBuilder(final Long commandId, final Loan loan,
            final LoanHistoricalPenaltyWaiver waiver) {
        return new CommandProcessingResultBuilder().withCommandId(commandId).withEntityId(waiver.getId()).withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId()).withGroupId(loan.getGroupId()).withLoanId(loan.getId());
    }

    private void validateWaivable(final Loan loan, final LoanCharge loanCharge, final MonetaryCurrency currency) {

        if (!loan.status().isActive()) {
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedReason.LOAN_INACTIVE, loanCharge.getId());
        }
        if (!loanCharge.isPenaltyCharge() || loanCharge.isDueAtDisbursement()) {
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedReason.WAIVE_NOT_ALLOWED_FOR_CHARGE, loanCharge.getId());
        }
        // An unpaid penalty is what the standard waive endpoint is for; this one exists only to undo a payment.
        if (!loanCharge.getAmountPaid(currency).isGreaterThanZero()) {
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedReason.WAIVE_NOT_ALLOWED_FOR_CHARGE, loanCharge.getId());
        }
    }

    private void validateApprover(final Long productId, final Long approverUserId) {

        if (approverUserId == null) {
            throw validationError("validation.msg.loan.charge.historical.waiver.approver.required",
                    "This waiver crosses an approval threshold, so an approver must be named.",
                    LoanApiConstants.nextApproverUserIdParamName, null);
        }
        if (!this.approverRepository.existsByLoanProductIdAndAppUserId(productId, approverUserId)) {
            throw validationError("validation.msg.loan.charge.historical.waiver.approver.not.authorised",
                    "The selected user is not an approver for this loan product.", LoanApiConstants.nextApproverUserIdParamName,
                    approverUserId);
        }
    }

    private void requirePendingApproval(final LoanHistoricalPenaltyWaiver waiver) {
        if (!waiver.getStatus().isPendingApproval()) {
            throw validationError("validation.msg.loan.charge.historical.waiver.already.decided",
                    "This historical penalty waiver is already " + waiver.getStatus().name() + ".", "status", waiver.getStatus().name());
        }
    }

    private void recordTouchedTransaction(final LoanHistoricalPenaltyWaiver waiver, final LoanTransaction transaction, final String role,
            final MonetaryCurrency currency) {

        this.waiverTxnRepository.save(LoanHistoricalPenaltyWaiverTxn.create(waiver.getId(), transaction.getId(), role, null, null, null,
                null, transaction.getPrincipalPortion(), transaction.getInterestPortion(currency).getAmount(),
                transaction.getFeeChargesPortion(currency).getAmount(), transaction.getPenaltyChargesPortion(currency).getAmount()));
    }

    private void recordReversedTransaction(final LoanHistoricalPenaltyWaiver waiver, final Long loanTransactionId) {
        this.waiverTxnRepository.save(LoanHistoricalPenaltyWaiverTxn.create(waiver.getId(), loanTransactionId,
                LoanHistoricalPenaltyWaiverTxn.ROLE_REVERSED, null, null, null, null, null, null, null, null));
    }

    private Money accruedCharge(final Loan loan, final LoanCharge loanCharge, final Integer loanInstallmentNumber) {

        Money accruedCharge = Money.zero(loan.getCurrency());
        if (loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()) {
            final Collection<LoanChargePaidByData> chargePaidByDatas = this.loanChargeReadPlatformService
                    .retriveLoanChargesPaidBy(loanCharge.getId(), LoanTransactionType.ACCRUAL, loanInstallmentNumber);
            for (final LoanChargePaidByData chargePaidByData : chargePaidByDatas) {
                accruedCharge = accruedCharge.plus(chargePaidByData.getAmount());
            }
        }
        return accruedCharge;
    }

    private void postJournalEntries(final Loan loan, final List<Long> existingTransactionIds,
            final List<Long> existingReversedTransactionIds) {

        final ApplicationCurrency applicationCurrency = this.applicationCurrencyRepository.findOneWithNotFoundDetection(loan.getCurrency());
        final Map<String, Object> accountingBridgeData = loan.deriveAccountingBridgeData(applicationCurrency.toData(),
                existingTransactionIds, existingReversedTransactionIds, false);
        this.journalEntryWritePlatformService.createJournalEntriesForLoan(accountingBridgeData);
    }

    private void deleteLoanRepaymentReminders(final Loan loan) {
        final List<LoanRepaymentReminder> reminders = this.loanRepaymentReminderRepository
                .getLoanRepaymentReminderByLoanId(loan.getId().intValue());
        if (!CollectionUtils.isEmpty(reminders)) {
            this.loanRepaymentReminderRepository.deleteAll(reminders);
        }
    }

    private LoanCharge retrieveLoanChargeBy(final Long loanId, final Long loanChargeId) {
        final LoanCharge loanCharge = this.loanChargeRepository.findById(loanChargeId)
                .orElseThrow(() -> new LoanChargeNotFoundException(loanChargeId));
        if (loanCharge.hasNotLoanIdentifiedBy(loanId)) {
            throw new LoanChargeNotFoundException(loanChargeId, loanId);
        }
        return loanCharge;
    }

    private LoanHistoricalPenaltyWaiver retrieveWaiverBy(final Long waiverId) {
        return this.waiverRepository.findById(waiverId)
                .orElseThrow(() -> validationError("validation.msg.loan.charge.historical.waiver.not.found",
                        "No historical penalty waiver exists with identifier " + waiverId + ".", "id", waiverId));
    }

    private boolean isPartial(final Money waivable, final BigDecimal waiverAmount, final MonetaryCurrency currency) {
        return Money.of(currency, waiverAmount).isLessThan(waivable);
    }

    private Integer penaltyAgeInDays(final LoanCharge loanCharge, final LocalDate businessDate) {
        final LocalDate dueDate = loanCharge.getDueLocalDate();
        return dueDate == null ? null : (int) ChronoUnit.DAYS.between(dueDate, businessDate);
    }

    private Long chargeIdOf(final LoanCharge loanCharge) {
        return loanCharge.getCharge() == null ? null : loanCharge.getCharge().getId();
    }

    private String chargeNameOf(final LoanCharge loanCharge) {
        return loanCharge.getCharge() == null ? null : loanCharge.getCharge().getName();
    }

    private Long strategyIdOf(final Loan loan) {
        return loan.transactionProcessingStrategy() == null ? null : loan.transactionProcessingStrategy().getId();
    }

    private String strategyNameOf(final Loan loan) {
        return loan.transactionProcessingStrategy() == null ? null : loan.transactionProcessingStrategy().toData().name();
    }

    private PlatformApiDataValidationException validationError(final String code, final String message, final String parameter,
            final Object value) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        dataValidationErrors.add(ApiParameterError.parameterError(code, message, parameter, value));
        return new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                dataValidationErrors);
    }
}
