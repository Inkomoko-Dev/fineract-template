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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.data.JournalData;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.FailedClientCreationOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.FailedLoanCreationOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.FailedLoanRepaymentOnDataMigrationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.service.EntityDisbursementDefaultsService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    private org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournalRepository provisionBatchJournalRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @BeforeEach
    void setTenant() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.clearTenant();
    }

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
    public void matchingDisbursementDetailStillEnrichesEntityDefaults() {
        final JournalData journalData = new JournalData();
        journalData.setLocation("Nairobi");

        final LocalDate disbursementDate = LocalDate.of(2026, 9, 9);
        final Loan loan = mock(Loan.class);
        final LoanTransaction txn = mock(LoanTransaction.class);
        final Office office = mock(Office.class);
        final LoanDisbursementDetails detail = mock(LoanDisbursementDetails.class);
        final MonetaryCurrency currency = mock(MonetaryCurrency.class);
        final Money amount = mock(Money.class);

        when(txn.isDisbursement()).thenReturn(true);
        when(txn.getTransactionDate()).thenReturn(disbursementDate);
        when(loan.getDisbursementDetails()).thenReturn(Collections.singletonList(detail));
        when(detail.getActualDisbursementDate()).thenReturn(disbursementDate);
        when(detail.getPrincipal()).thenReturn(BigDecimal.TEN);
        when(loan.getCurrency()).thenReturn(currency);
        when(txn.getAmount(currency)).thenReturn(amount);
        when(amount.getAmount()).thenReturn(BigDecimal.TEN);

        odooService.applyDisbursementFieldsToOdooJournal(journalData, loan, txn, office);

        verify(entityDisbursementDefaultsService).enrichOdooJournalData(journalData, loan, txn, office);
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
