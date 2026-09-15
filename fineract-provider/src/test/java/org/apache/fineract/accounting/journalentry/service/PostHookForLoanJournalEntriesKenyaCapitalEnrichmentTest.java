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
package org.apache.fineract.accounting.journalentry.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.data.JournalData;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientAddressRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.service.EntityDisbursementDefaultsService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Live Odoo posting for loan journals goes through {@code postHookForLoanJournalEntries}, not
 * {@code OdooServiceImpl}. Kenya Capital department/budget enrichment must run on this path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PostHookForLoanJournalEntriesKenyaCapitalEnrichmentTest {

    private static final Long DISBURSEMENT = 1L;
    private static final Long REPAYMENT = 2L;

    @Mock
    private JournalEntryRepository glJournalEntryRepository;

    @Mock
    private AccountingProcessorHelper helper;

    @Mock
    private ClientAddressRepositoryWrapper clientAddressRepositoryWrapper;

    @Mock
    private LoanTransactionRepository loanTransactionRepository;

    @Mock
    private EntityDisbursementDefaultsService entityDisbursementDefaultsService;

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private JournalEntryWritePlatformServiceJpaRepositoryImpl service;

    @Test
    public void disbursementJournalIsEnrichedBeforeOdooWebhook() {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(120L);
        when(this.helper.getOfficeById(120L)).thenReturn(office);

        final Client client = mock(Client.class);
        when(client.getId()).thenReturn(594191L);
        when(client.getOdooCustomerId()).thenReturn(999);
        when(client.getDisplayName()).thenReturn("Winnie");

        final GLAccount glAccount = mock(GLAccount.class);
        when(glAccount.getGlCode()).thenReturn("1110-5976");

        final JournalEntry entry = mock(JournalEntry.class);
        when(entry.getCurrencyCode()).thenReturn("KES");
        when(entry.getGlAccount()).thenReturn(glAccount);
        when(entry.getClient()).thenReturn(client);
        when(entry.isCorrection()).thenReturn(false);
        when(this.glJournalEntryRepository.findJournalEntriesByLoanTransactionId("L3410536"))
                .thenReturn(Collections.singletonList(entry));
        when(this.clientAddressRepositoryWrapper.findAddressesForClient(594191L)).thenReturn(Collections.emptyList());

        final Loan loan = mock(Loan.class);
        final LoanTransaction loanTransaction = mock(LoanTransaction.class);
        when(loanTransaction.isDisbursement()).thenReturn(true);
        when(loanTransaction.getLoan()).thenReturn(loan);
        when(this.loanTransactionRepository.findById(3410536L)).thenReturn(Optional.of(loanTransaction));

        final AppUser user = mock(AppUser.class);
        when(user.getDisplayName()).thenReturn("IT Devs");
        when(this.context.authenticatedUser()).thenReturn(user);

        final LoanTransactionDTO txn = new LoanTransactionDTO(120L, null, "3410536", LocalDate.of(2026, 9, 15),
                new LoanTransactionEnumData(DISBURSEMENT, "disbursement", "Disbursement"), BigDecimal.valueOf(1000000),
                BigDecimal.valueOf(1000000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                Collections.emptyList(), Collections.emptyList(), false);
        final LoanDTO loanDTO = new LoanDTO(432744L, 4L, 1L, "KES", false, false, true, Collections.singletonList(txn), null);

        this.service.postHookForLoanJournalEntries(loanDTO);

        verify(this.entityDisbursementDefaultsService).enrichOdooJournalData(any(JournalData.class), eq(loan), eq(loanTransaction),
                eq(office));
    }

    @Test
    public void repaymentJournalIsNotEnrichedForEntityDefaults() {
        when(this.glJournalEntryRepository.findJournalEntriesByLoanTransactionId("L99")).thenReturn(Collections.emptyList());

        final LoanTransactionDTO txn = new LoanTransactionDTO(120L, null, "99", LocalDate.of(2026, 9, 15),
                new LoanTransactionEnumData(REPAYMENT, "repayment", "Repayment"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, Collections.emptyList(), Collections.emptyList(), false);
        final LoanDTO loanDTO = new LoanDTO(432744L, 4L, 1L, "KES", false, false, true, Collections.singletonList(txn), null);

        this.service.postHookForLoanJournalEntries(loanDTO);

        verify(this.entityDisbursementDefaultsService, never()).enrichOdooJournalData(any(), any(), any(), any());
    }
}
