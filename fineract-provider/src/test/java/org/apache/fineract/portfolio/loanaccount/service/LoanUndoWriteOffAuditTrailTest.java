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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.DefaultLoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummaryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LoanUndoWriteOffAuditTrailTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final CurrencyData KES_DATA = new CurrencyData("KES", "Kenyan Shilling", 2, 0, "KSh", "currency.KES");
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    private static final LocalDate WRITE_OFF_DATE = LocalDate.of(2026, 1, 7);
    private static final LocalDate UNDO_DATE = LocalDate.of(2026, 3, 15);
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final Long LOAN_ID = 393409L;
    private static final Long WRITE_OFF_ID = 9L;
    private static final Long REVERSAL_ID = 77L;

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;
    private LoanWritePlatformServiceJpaRepositoryImpl service;
    private JournalEntryWritePlatformService journalEntryWritePlatformService;
    private Loan loan;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, UNDO_DATE)));

        this.loan = writtenOffLoan();
        this.service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class, CALLS_REAL_METHODS);
        this.journalEntryWritePlatformService = mock(JournalEntryWritePlatformService.class);

        final LoanAssembler loanAssembler = mock(LoanAssembler.class);
        when(loanAssembler.assembleFrom(LOAN_ID)).thenReturn(this.loan);
        final LoanTransactionRepository loanTransactionRepository = mock(LoanTransactionRepository.class);
        when(loanTransactionRepository.saveAndFlush(any(LoanTransaction.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        when(loanTransactionRepository.save(any(LoanTransaction.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        final ApplicationCurrency applicationCurrency = mock(ApplicationCurrency.class);
        when(applicationCurrency.toData()).thenReturn(KES_DATA);
        final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository = mock(ApplicationCurrencyRepositoryWrapper.class);
        when(applicationCurrencyRepository.findOneWithNotFoundDetection(any(MonetaryCurrency.class))).thenReturn(applicationCurrency);

        ReflectionTestUtils.setField(this.service, "context", mock(PlatformSecurityContext.class));
        ReflectionTestUtils.setField(this.service, "loanAssembler", loanAssembler);
        ReflectionTestUtils.setField(this.service, "loanTransactionRepository", loanTransactionRepository);
        ReflectionTestUtils.setField(this.service, "loanRepositoryWrapper", mock(LoanRepositoryWrapper.class));
        ReflectionTestUtils.setField(this.service, "loanAccountDomainService", mock(LoanAccountDomainService.class));
        ReflectionTestUtils.setField(this.service, "noteRepository", mock(NoteRepository.class));
        ReflectionTestUtils.setField(this.service, "applicationCurrencyRepository", applicationCurrencyRepository);
        ReflectionTestUtils.setField(this.service, "journalEntryWritePlatformService", this.journalEntryWritePlatformService);
        ReflectionTestUtils.setField(this.service, "accountTransfersWritePlatformService", mock(AccountTransfersWritePlatformService.class));
        ReflectionTestUtils.setField(this.service, "businessEventNotifierService", mock(BusinessEventNotifierService.class));
        ReflectionTestUtils.setField(this.service, "loanUtilService", mock(LoanUtilService.class));
        ReflectionTestUtils.setField(this.service, "loanDailyLateFeeService", mock(LoanDailyLateFeeService.class));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void auditLogResourceIsTheWriteOffReversalTransaction() {
        final CommandProcessingResult result = this.service.undoWriteOff(LOAN_ID, null);

        assertEquals(REVERSAL_ID, result.resourceId());
        assertEquals(String.valueOf(REVERSAL_ID), result.getTransactionId());
    }

    @Test
    void auditLogChangesReferenceTheOriginalWriteOffAndTheStatusChange() {
        final CommandProcessingResult result = this.service.undoWriteOff(LOAN_ID, null);

        final Map<String, Object> changes = result.getChanges();
        assertNotNull(changes, "undo write-off must record what changed so the audit log is not empty");
        assertEquals(WRITE_OFF_ID, changes.get("originalTransactionId"));
        assertEquals(WRITE_OFF_DATE.toString(), changes.get("originalTransactionDate"));
        assertEquals(LoanEnumerations.status(LoanStatus.CLOSED_WRITTEN_OFF).value(), changes.get("previousStatus"));
        assertEquals(LoanEnumerations.status(LoanStatus.ACTIVE).value(), changes.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void journalEntriesArePostedAgainstThePersistedReversalTransaction() {
        this.service.undoWriteOff(LOAN_ID, null);

        final ArgumentCaptor<Map<String, Object>> bridge = ArgumentCaptor.forClass(Map.class);
        verify(this.journalEntryWritePlatformService).createJournalEntriesForLoan(bridge.capture());
        final List<Map<String, Object>> postings = (List<Map<String, Object>>) bridge.getValue().get("newLoanTransactions");
        assertTrue(postings.stream().anyMatch(posting -> REVERSAL_ID.equals(posting.get("id"))));
        assertTrue(postings.stream().noneMatch(posting -> WRITE_OFF_ID.equals(posting.get("id"))));
    }

    @Test
    void loanIsReturnedToActive() {
        this.service.undoWriteOff(LOAN_ID, null);

        assertEquals(LoanStatus.ACTIVE.getValue(), this.loan.getLoanStatus());
    }

    private LoanTransaction persisted(final LoanTransaction transaction) {
        if (transaction.getId() == null) {
            ReflectionTestUtils.setField(transaction, "id", REVERSAL_ID);
        }
        return transaction;
    }

    private Loan writtenOffLoan() {
        final Loan writtenOff = new TestLoan();
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(detail.getCurrency()).thenReturn(KES);
        when(detail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("100000.00")));
        ReflectionTestUtils.setField(writtenOff, "id", LOAN_ID);
        ReflectionTestUtils.setField(writtenOff, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(writtenOff, "office", mock(Office.class));
        ReflectionTestUtils.setField(writtenOff, "loanProduct", mock(LoanProduct.class));
        ReflectionTestUtils.setField(writtenOff, "expectedDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(writtenOff, "actualDisbursementDate", DISBURSEMENT_DATE);
        ReflectionTestUtils.setField(writtenOff, "loanRepaymentScheduleDetail", detail);
        ReflectionTestUtils.setField(writtenOff, "summary", LoanSummary.create(BigDecimal.ZERO));
        ReflectionTestUtils.setField(writtenOff, "repaymentScheduleInstallments", new ArrayList<>(List.of(new LoanRepaymentScheduleInstallment(
                null, 1, DISBURSEMENT_DATE, DUE_DATE, new BigDecimal("100000.00"), new BigDecimal("20000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, false, null))));
        final LoanTransaction disbursement = transaction(1L,
                LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal("100000.00")), null, DISBURSEMENT_DATE, null));
        ReflectionTestUtils.setField(writtenOff, "loanTransactions", new ArrayList<>(List.of(disbursement)));
        ReflectionTestUtils.setField(writtenOff, "charges", Collections.emptySet());
        writtenOff.setHelpers(new DefaultLoanLifecycleStateMachine(List.of(LoanStatus.values())), new LoanSummaryWrapper(),
                new LoanRepaymentScheduleTransactionProcessorFactory());
        disbursement.updateLoan(writtenOff);

        final LoanTransaction writeOff = transaction(WRITE_OFF_ID, LoanTransaction.writeoff(writtenOff, mock(Office.class), WRITE_OFF_DATE, null));
        writtenOff.addLoanTransaction(writeOff);
        new LoanRepaymentScheduleTransactionProcessorFactory().determineProcessor(null).handleWriteOff(writeOff, KES,
                writtenOff.getRepaymentScheduleInstallments());
        writtenOff.updateLoanSummaryDerivedFields();
        ReflectionTestUtils.setField(writtenOff, "loanStatus", LoanStatus.CLOSED_WRITTEN_OFF.getValue());
        return writtenOff;
    }

    private LoanTransaction transaction(final Long id, final LoanTransaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", id);
        transaction.setCreatedDate(CREATED_AT);
        return transaction;
    }

    private static final class TestLoan extends Loan {

        private TestLoan() {}
    }
}
