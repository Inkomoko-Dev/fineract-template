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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.data.JournalData;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.FailedClientCreationOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.FailedLoanCreationOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.FailedLoanRepaymentOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OdooServiceImplTest {

    @InjectMocks
    private OdooServiceImpl odooService;

    @Mock
    private ClientRepositoryWrapper clientRepository;

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private LoanReadPlatformService loanReadPlatformService;

    @Mock
    private FailedClientCreationOnDataMigrationRepository failedClientCreationOnDataMigrationRepository;

    @Mock
    private FailedLoanCreationOnDataMigrationRepository failedLoanCreationOnDataMigrationRepository;

    @Mock
    private FailedLoanRepaymentOnDataMigrationRepository failedLoanRepaymentOnDataMigrationRepository;

    @Mock
    private AppUserRepository appUserRepository;

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

    @Test
    public void createdByUserEnrichesOdooJournalWithUsernameAndDisplayName() {
        final JournalData journalData = new JournalData();
        final JournalEntry entry = mock(JournalEntry.class);
        final AppUser createdBy = mock(AppUser.class);

        when(entry.getCreatedBy()).thenReturn(Optional.of(7L));
        given(appUserRepository.findById(7L)).willReturn(Optional.of(createdBy));
        when(createdBy.getUsername()).thenReturn("jdoe");
        when(createdBy.getDisplayName()).thenReturn("John Doe");

        odooService.applyCreatedByToOdooJournal(journalData, entry);

        org.junit.jupiter.api.Assertions.assertEquals("jdoe", journalData.getCreatedByUsername());
        org.junit.jupiter.api.Assertions.assertEquals("John Doe", journalData.getCreatedByDisplayName());
    }

    @Test
    public void missingCreatedByLeavesOdooJournalFieldsNull() {
        final JournalData journalData = new JournalData();
        final JournalEntry entry = mock(JournalEntry.class);

        when(entry.getCreatedBy()).thenReturn(Optional.empty());

        odooService.applyCreatedByToOdooJournal(journalData, entry);

        org.junit.jupiter.api.Assertions.assertNull(journalData.getCreatedByUsername());
        org.junit.jupiter.api.Assertions.assertNull(journalData.getCreatedByDisplayName());
    }
}
