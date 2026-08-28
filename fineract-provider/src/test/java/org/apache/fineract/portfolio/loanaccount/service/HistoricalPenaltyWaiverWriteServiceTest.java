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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeWaivedException;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverApprovalRequirement;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverRequest;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.HistoricalPenaltyWaiverResult;
import org.apache.fineract.portfolio.loanaccount.domain.HistoricalPenaltyWaiverStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiver;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverTxn;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverTxnRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentReminderRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanproduct.domain.HistoricalCorrectionProductApproverRepository;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The two guarantees this feature stands on: nothing on the loan moves until an above-threshold waiver is approved, and
 * once it does move every replacement transaction the replay produced is persisted. Both are invisible to the domain
 * tests, which stop at the aggregate boundary.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HistoricalPenaltyWaiverWriteServiceTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, null);
    private static final LocalDate CHARGE_DUE_DATE = LocalDate.of(2026, 1, 15);
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 1, 31);
    private static final Long LOAN_ID = 4001L;
    private static final Long CHARGE_ID = 77L;
    private static final Long PRODUCT_ID = 9L;
    private static final Long OFFICE_ID = 3L;
    private static final Long APPROVER_ID = 55L;
    private static final OffsetDateTime SUBMITTED_ON = OffsetDateTime.parse("2026-08-04T09:15:00Z");

    static {
        // Money arithmetic reads these statics; the fixture builds Money in its field initialisers.
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
    }

    private final Fixture fixture = new Fixture();

    /** Wires only the collaborators the historical waiver path actually touches; the rest stay null. */
    private static final class Fixture {

        private final LoanAssembler loanAssembler = mock(LoanAssembler.class);
        private final LoanChargeRepository loanChargeRepository = mock(LoanChargeRepository.class);
        private final LoanTransactionRepository loanTransactionRepository = mock(LoanTransactionRepository.class);
        private final LoanHistoricalPenaltyWaiverRepository waiverRepository = mock(LoanHistoricalPenaltyWaiverRepository.class);
        private final LoanHistoricalPenaltyWaiverTxnRepository waiverTxnRepository = mock(LoanHistoricalPenaltyWaiverTxnRepository.class);
        private final HistoricalCorrectionProductApproverRepository approverRepository = mock(
                HistoricalCorrectionProductApproverRepository.class);
        private final HistoricalPenaltyWaiverApprovalPolicy approvalPolicy = mock(HistoricalPenaltyWaiverApprovalPolicy.class);
        private final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository = mock(
                ApplicationCurrencyRepositoryWrapper.class);
        private final NoteRepository noteRepository = mock(NoteRepository.class);
        private final BusinessEventNotifierService businessEventNotifierService = mock(BusinessEventNotifierService.class);
        private final LoanChargeReadPlatformService loanChargeReadPlatformService = mock(LoanChargeReadPlatformService.class);
        private final JournalEntryWritePlatformService journalEntryWritePlatformService = mock(JournalEntryWritePlatformService.class);
        private final LoanRepositoryWrapper loanRepositoryWrapper = mock(LoanRepositoryWrapper.class);
        private final LoanRepaymentReminderRepository loanRepaymentReminderRepository = mock(LoanRepaymentReminderRepository.class);
        private final HistoricalPenaltyWaiverNotificationService notificationService = mock(HistoricalPenaltyWaiverNotificationService.class);

        private final Loan loan = mock(Loan.class);
        private final LoanCharge penalty = mock(LoanCharge.class);
        private final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);

        private Fixture() {
            when(this.loanAssembler.assembleFrom(LOAN_ID)).thenReturn(this.loan);
            when(this.loanChargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(this.penalty));
            when(this.applicationCurrencyRepository.findOneWithNotFoundDetection(any(MonetaryCurrency.class)))
                    .thenReturn(mock(ApplicationCurrency.class));

            when(this.loan.getId()).thenReturn(LOAN_ID);
            when(this.loan.status()).thenReturn(LoanStatus.ACTIVE);
            when(this.loan.getCurrency()).thenReturn(KES);
            when(this.loan.productId()).thenReturn(PRODUCT_ID);
            when(this.loan.getOfficeId()).thenReturn(OFFICE_ID);
            when(this.loan.getClientId()).thenReturn(1200L);
            when(this.loan.getSummary()).thenReturn(this.summary);
            ReflectionTestUtils.setField(this.summary, "totalOutstanding", new BigDecimal("120000.00"));

            when(this.penalty.getId()).thenReturn(CHARGE_ID);
            when(this.penalty.hasNotLoanIdentifiedBy(LOAN_ID)).thenReturn(false);
            when(this.penalty.isPenaltyCharge()).thenReturn(true);
            when(this.penalty.isDueAtDisbursement()).thenReturn(false);
            when(this.penalty.isInstalmentFee()).thenReturn(false);
            when(this.penalty.amount()).thenReturn(new BigDecimal("5000.00"));
            when(this.penalty.getDueLocalDate()).thenReturn(CHARGE_DUE_DATE);
            when(this.penalty.getAmountPaid(KES)).thenReturn(Money.of(KES, new BigDecimal("5000.00")));
            when(this.penalty.getAmountWaived(KES)).thenReturn(Money.zero(KES));
            when(this.penalty.getAmountOutstanding(KES)).thenReturn(Money.zero(KES));

            when(this.waiverRepository.saveAndFlush(any(LoanHistoricalPenaltyWaiver.class)))
                    .thenAnswer(invocation -> withId(invocation.getArgument(0), 4321L));
            when(this.waiverRepository.save(any(LoanHistoricalPenaltyWaiver.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private LoanHistoricalPenaltyWaiver withId(final LoanHistoricalPenaltyWaiver waiver, final Long id) {
            ReflectionTestUtils.setField(waiver, "id", id);
            return waiver;
        }

        private HistoricalPenaltyWaiverService service() {
            return new HistoricalPenaltyWaiverService(this.loanAssembler, this.loanChargeRepository, this.loanTransactionRepository,
                    this.waiverRepository, this.waiverTxnRepository, this.approverRepository, this.approvalPolicy, this.noteRepository,
                    this.businessEventNotifierService, this.loanChargeReadPlatformService, this.journalEntryWritePlatformService,
                    this.applicationCurrencyRepository, this.loanRepositoryWrapper, this.loanRepaymentReminderRepository,
                    this.notificationService);
        }

        private void requiresApproval(final boolean required) {
            when(this.approvalPolicy.determine(any(), any(), any())).thenReturn(
                    required ? HistoricalPenaltyWaiverApprovalRequirement.of(true, false)
                            : HistoricalPenaltyWaiverApprovalRequirement.notRequired());
        }

        private void replayProduces(final int replacementCount) {
            final ChangedTransactionDetail changed = new ChangedTransactionDetail();
            final Map<Long, LoanTransaction> mappings = new HashMap<>();
            for (int i = 0; i < replacementCount; i++) {
                mappings.put(900L + i, transaction());
            }
            changed.getNewTransactionMappings().putAll(mappings);
            // Built before the stubbing below: transaction() stubs its own mock, and Mockito rejects nested stubbing.
            final HistoricalPenaltyWaiverResult result = new HistoricalPenaltyWaiverResult(transaction(), changed);
            when(this.loan.waiveLoanChargeHistorically(any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                    .thenReturn(result);
        }

        private LoanTransaction transaction() {
            final LoanTransaction transaction = mock(LoanTransaction.class);
            when(transaction.getPrincipalPortion()).thenReturn(BigDecimal.ZERO);
            when(transaction.getInterestPortion(KES)).thenReturn(Money.zero(KES));
            when(transaction.getFeeChargesPortion(KES)).thenReturn(Money.zero(KES));
            when(transaction.getPenaltyChargesPortion(KES)).thenReturn(Money.zero(KES));
            return transaction;
        }
    }

    private HistoricalPenaltyWaiverRequest request(final String expectedPaidAmount) {
        return new HistoricalPenaltyWaiverRequest(1L, new BigDecimal(expectedPaidAmount), null, EFFECTIVE_DATE,
                "Penalty charged in error", null, APPROVER_ID);
    }

    private HistoricalPenaltyWaiverRequest requestWithoutApprover(final String expectedPaidAmount) {
        return new HistoricalPenaltyWaiverRequest(1L, new BigDecimal(expectedPaidAmount), null, EFFECTIVE_DATE,
                "Penalty charged in error", null, null);
    }

    @Test
    public void anAboveThresholdWaiverIsParkedForApprovalAndTheLoanIsNotTouched() {
        this.fixture.requiresApproval(true);
        when(this.fixture.approverRepository.existsByLoanProductIdAndAppUserId(PRODUCT_ID, APPROVER_ID)).thenReturn(true);

        this.fixture.service().submit(LOAN_ID, CHARGE_ID, request("5000.00"), APPROVER_ID, SUBMITTED_ON, LocalDate.of(2026, 8, 4));

        verify(this.fixture.loan, never()).waiveLoanChargeHistorically(any(), any(), anyList(), anyList(), any(), any(), any(), any());
        verifyNoInteractions(this.fixture.loanTransactionRepository);
        verify(this.fixture.waiverRepository).saveAndFlush(any(LoanHistoricalPenaltyWaiver.class));
    }

    @Test
    public void aBelowThresholdWaiverExecutesImmediately() {
        this.fixture.requiresApproval(false);
        this.fixture.replayProduces(3);

        this.fixture.service().submit(LOAN_ID, CHARGE_ID, requestWithoutApprover("5000.00"), null, SUBMITTED_ON, LocalDate.of(2026, 8, 4));

        verify(this.fixture.loan).waiveLoanChargeHistorically(any(), any(), anyList(), anyList(), any(), any(), eq(EFFECTIVE_DATE), any());
    }

    @Test
    public void everyReplacementTransactionTheReplayProducedIsPersisted() {
        this.fixture.requiresApproval(false);
        this.fixture.replayProduces(3);

        this.fixture.service().submit(LOAN_ID, CHARGE_ID, requestWithoutApprover("5000.00"), null, SUBMITTED_ON, LocalDate.of(2026, 8, 4));

        // The gap this ticket closes: waiveLoanCharge ran the replay and then dropped its replacements on the floor.
        verify(this.fixture.loanTransactionRepository, times(3)).save(any(LoanTransaction.class));
        verify(this.fixture.loan, times(3)).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    public void eachTouchedTransactionIsRecordedInTheAuditTrail() {
        this.fixture.requiresApproval(false);
        this.fixture.replayProduces(2);

        this.fixture.service().submit(LOAN_ID, CHARGE_ID, requestWithoutApprover("5000.00"), null, SUBMITTED_ON, LocalDate.of(2026, 8, 4));

        // one WAIVE row, plus a REVERSED and a REPLACEMENT row for each of the two mappings
        verify(this.fixture.waiverTxnRepository, times(5)).save(any(LoanHistoricalPenaltyWaiverTxn.class));
    }

    @Test
    public void aStaleExpectedPaidAmountIsRejected() {
        this.fixture.requiresApproval(false);

        assertThrows(PlatformApiDataValidationException.class, () -> this.fixture.service().submit(LOAN_ID, CHARGE_ID, requestWithoutApprover("4000.00"),
                null, SUBMITTED_ON, LocalDate.of(2026, 8, 4)));

        verify(this.fixture.loan, never()).waiveLoanChargeHistorically(any(), any(), anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void anInactiveLoanIsRejected() {
        when(this.fixture.loan.status()).thenReturn(LoanStatus.CLOSED_OBLIGATIONS_MET);

        assertThrows(LoanChargeCannotBeWaivedException.class, () -> this.fixture.service().submit(LOAN_ID, CHARGE_ID, requestWithoutApprover("5000.00"),
                null, SUBMITTED_ON, LocalDate.of(2026, 8, 4)));
    }

    @Test
    public void aFeeChargeIsRejected() {
        when(this.fixture.penalty.isPenaltyCharge()).thenReturn(false);

        assertThrows(LoanChargeCannotBeWaivedException.class, () -> this.fixture.service().submit(LOAN_ID, CHARGE_ID, requestWithoutApprover("5000.00"),
                null, SUBMITTED_ON, LocalDate.of(2026, 8, 4)));
    }

    @Test
    public void anUnpaidPenaltyIsRejectedBecauseTheStandardWaiverAlreadyHandlesIt() {
        when(this.fixture.penalty.getAmountPaid(KES)).thenReturn(Money.zero(KES));

        assertThrows(LoanChargeCannotBeWaivedException.class, () -> this.fixture.service().submit(LOAN_ID, CHARGE_ID, requestWithoutApprover("0"), null,
                SUBMITTED_ON, LocalDate.of(2026, 8, 4)));
    }

    @Test
    public void anApproverNotMappedToTheProductIsRejected() {
        this.fixture.requiresApproval(true);
        when(this.fixture.approverRepository.existsByLoanProductIdAndAppUserId(PRODUCT_ID, APPROVER_ID)).thenReturn(false);

        assertThrows(PlatformApiDataValidationException.class, () -> this.fixture.service().submit(LOAN_ID, CHARGE_ID, request("5000.00"),
                APPROVER_ID, SUBMITTED_ON, LocalDate.of(2026, 8, 4)));
    }

    @Test
    public void anAboveThresholdWaiverWithoutANamedApproverIsRejected() {
        this.fixture.requiresApproval(true);

        assertThrows(PlatformApiDataValidationException.class, () -> this.fixture.service().submit(LOAN_ID, CHARGE_ID, requestWithoutApprover("5000.00"),
                null, SUBMITTED_ON, LocalDate.of(2026, 8, 4)));
    }

    @Test
    public void aRejectedRequestLeavesTheLoanUntouched() {
        final LoanHistoricalPenaltyWaiver request = pendingRequest();
        when(this.fixture.waiverRepository.findById(4321L)).thenReturn(Optional.of(request));

        this.fixture.service().reject(4321L, "Not supported by the file", 12L, SUBMITTED_ON);

        assertEquals(HistoricalPenaltyWaiverStatus.REJECTED, request.getStatus());
        verifyNoInteractions(this.fixture.loanAssembler);
    }

    @Test
    public void anApprovedRequestExecutesTheWaiver() {
        this.fixture.replayProduces(1);
        final LoanHistoricalPenaltyWaiver request = pendingRequest();
        when(this.fixture.waiverRepository.findById(4321L)).thenReturn(Optional.of(request));
        when(this.fixture.approverRepository.existsByLoanProductIdAndAppUserId(PRODUCT_ID, APPROVER_ID)).thenReturn(true);

        this.fixture.service().approve(4321L, APPROVER_ID, SUBMITTED_ON);

        verify(this.fixture.loan).waiveLoanChargeHistorically(any(), any(), anyList(), anyList(), any(), any(), eq(EFFECTIVE_DATE), any());
        assertEquals(HistoricalPenaltyWaiverStatus.PENDING_ODOO_SYNC, request.getStatus());
    }

    @Test
    public void anApproverNotMappedToTheProductCannotApprove() {
        final LoanHistoricalPenaltyWaiver request = pendingRequest();
        when(this.fixture.waiverRepository.findById(4321L)).thenReturn(Optional.of(request));
        when(this.fixture.approverRepository.existsByLoanProductIdAndAppUserId(PRODUCT_ID, APPROVER_ID)).thenReturn(false);

        assertThrows(PlatformApiDataValidationException.class,
                () -> this.fixture.service().approve(4321L, APPROVER_ID, SUBMITTED_ON));

        verify(this.fixture.loan, never()).waiveLoanChargeHistorically(any(), any(), anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void anAlreadyDecidedRequestCannotBeApprovedTwice() {
        final LoanHistoricalPenaltyWaiver request = pendingRequest();
        request.markRejected(12L, SUBMITTED_ON, "Not supported by the file");
        when(this.fixture.waiverRepository.findById(4321L)).thenReturn(Optional.of(request));

        assertThrows(PlatformApiDataValidationException.class,
                () -> this.fixture.service().approve(4321L, APPROVER_ID, SUBMITTED_ON));
    }

    private LoanHistoricalPenaltyWaiver pendingRequest() {
        final LoanHistoricalPenaltyWaiver waiver = LoanHistoricalPenaltyWaiver.submit(LOAN_ID, 1200L, PRODUCT_ID, OFFICE_ID, CHARGE_ID, 8L,
                "Late repayment penalty", CHARGE_DUE_DATE, 201, null, new BigDecimal("5000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("5000.00"), false, EFFECTIVE_DATE, "Penalty charged in error", true,
                HistoricalPenaltyWaiverApprovalRequirement.TRIGGER_AGE, APPROVER_ID, 12L, SUBMITTED_ON);
        ReflectionTestUtils.setField(waiver, "id", 4321L);
        return waiver;
    }
}
