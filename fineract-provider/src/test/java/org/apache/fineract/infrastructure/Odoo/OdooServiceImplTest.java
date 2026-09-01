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
package org.apache.fineract.infrastructure.Odoo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.persistence.AfterCommitExecutor;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.FailedClientCreationOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.FailedLoanCreationOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.FailedLoanRepaymentOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.service.EntityDisbursementDefaultsService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class OdooServiceImplTest {

    @InjectMocks
    private OdooServiceImpl odooService;

    // postJournalEntryToOddo() and postJournalEntryToOddoOnDisburseTask(...) both touch tenant
    // timezone / business-date thread-locals unconditionally; without this, tests here only pass
    // when another test class happens to leak that state in first
    @BeforeEach
    void setUpTenantContext() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 6, 8))));
        // genericExecutorService is populated by @PostConstruct in real use, which @InjectMocks never
        // runs — wire the mock in directly rather than relying on Mockito's field-injection heuristics
        ReflectionTestUtils.setField(odooService, "genericExecutorService", genericExecutorService);
    }

    @AfterEach
    void tearDownTenantContext() {
        ThreadLocalContextUtil.clear();
    }

    @Mock
    private ClientRepositoryWrapper clientRepository;

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private LoanReadPlatformService loanReadPlatformService;

    @Mock
    private LoanTransactionRepository loanTransactionRepository;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private EntityDisbursementDefaultsService entityDisbursementDefaultsService;

    @Mock
    private LoanHistoricalPenaltyWaiverRepository loanHistoricalPenaltyWaiverRepository;

    @Mock
    private FailedClientCreationOnDataMigrationRepository failedClientCreationOnDataMigrationRepository;

    @Mock
    private FailedLoanCreationOnDataMigrationRepository failedLoanCreationOnDataMigrationRepository;

    @Mock
    private FailedLoanRepaymentOnDataMigrationRepository failedLoanRepaymentOnDataMigrationRepository;

    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    @Mock
    private ExecutorService genericExecutorService;

    @Test
    public void scheduledJournalPostingFetchesAllUnpostedTransactions() throws JobExecutionException {
        given(configurationDomainService.isOdooIntegrationEnabled()).willReturn(true);
        given(loanReadPlatformService.retrieveLoanTransactionWhoseJournalEntriesAreNotPostedToOdoo())
                .willReturn(Collections.emptyList());

        odooService.postJournalEntryToOddo();

        verify(loanReadPlatformService).retrieveLoanTransactionWhoseJournalEntriesAreNotPostedToOdoo();
        verify(loanReadPlatformService, never()).retrieveLoanTransactionWhoseJournalEntriesAreNotPostedToOdoo(any(LocalDate.class),
                any(LocalDate.class), isNull(), isNull());
    }

    // AfterCommitExecutor.execute is static, so it runs its real "no active transaction ->
    // run immediately" fallback here — the disburse task synchronously reaches the background
    // executor within this call, then we drive the submitted task ourselves to prove it lands
    // on the same transaction-scoped query the cron uses, not the bulk unposted-backlog scan
    @Test
    public void disburseTaskDefersToBackgroundExecutorAndQueriesOnlyThatTransaction() {
        final Long loanTransactionId = 42L;
        given(configurationDomainService.isOdooIntegrationEnabled()).willReturn(true);
        given(loanReadPlatformService.retrieveLoanTransactionWhoseJournalEntriesAreNotPostedToOdoo(null, null, null, null,
                loanTransactionId)).willReturn(Collections.emptyList());

        odooService.postJournalEntryToOddoOnDisburseTask(loanTransactionId);

        final ArgumentCaptor<Runnable> submittedTask = ArgumentCaptor.forClass(Runnable.class);
        verify(genericExecutorService).execute(submittedTask.capture());

        // simulate the executor thread actually running the submitted task
        submittedTask.getValue().run();

        verify(loanReadPlatformService).retrieveLoanTransactionWhoseJournalEntriesAreNotPostedToOdoo(null, null, null, null,
                loanTransactionId);
        verify(loanReadPlatformService, never()).retrieveLoanTransactionWhoseJournalEntriesAreNotPostedToOdoo();
    }
}
