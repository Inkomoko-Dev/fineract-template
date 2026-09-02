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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
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

@ExtendWith(MockitoExtension.class)
public class OdooServiceImplTest {

    @InjectMocks
    private OdooServiceImpl odooService;

    // postJournalEntryToOddo() touches tenant timezone / business-date thread-locals
    // unconditionally; without this, this test only passes when another test class happens to
    // leak that state into ThreadLocalContextUtil first
    @BeforeEach
    void setUpTenantContext() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 6, 8))));
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

    // the whole point of the fix: one round trip for a multi-line Odoo response instead of a
    // saveAndFlush per journal-entry line, which is what was driving per-message processing time
    // high enough to blow the outcome consumer's max.poll.interval.ms budget
    @Test
    public void applyOdooStatusBatchesJournalEntrySavesInOneRoundTrip() {
        JournalEntry line1 = mock(JournalEntry.class);
        given(line1.getId()).willReturn(1L);
        JournalEntry line2 = mock(JournalEntry.class);
        given(line2.getId()).willReturn(2L);
        given(journalEntryRepository.findJournalEntriesByLoanTransactionId("L555")).willReturn(List.of(line1, line2));

        String odooResponse = "{"
                + "\"cbs_journal_entry_id\":\"555\","
                + "\"responseCode\":\"POSTED\","
                + "\"journal_entry_no\":\"JE-1\","
                + "\"journalDetails\":["
                + "{\"id\":1,\"credit\":100,\"gl_account\":\"1000\"},"
                + "{\"id\":2,\"debit\":100,\"gl_account\":\"2000\"}"
                + "]}";

        odooService.updateJournalEntryWithOdooStatus(odooResponse);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalEntry>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(journalEntryRepository).saveAllAndFlush(savedCaptor.capture());
        assertEquals(2, savedCaptor.getValue().size());
        verify(journalEntryRepository, never()).saveAndFlush(any(JournalEntry.class));
    }
}
