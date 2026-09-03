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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * CGLT-656 is the first thing in the codebase to post a multi-transaction batch (the waiver, plus a reversal and a
 * replacement for every repayment it reallocates), which is what exposes this: a transaction the Odoo hook skips must
 * not stop the transactions behind it in the batch from being posted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PostHookForLoanJournalEntriesBatchTest {

    private static final Long REPAYMENT = 2L;
    private static final Long WAIVE_CHARGES = 9L;
    private static final Long NOT_POSTED_TO_ODOO = 3L;

    @Mock
    private JournalEntryRepository glJournalEntryRepository;

    @Mock
    private AccountingProcessorHelper helper;

    @InjectMocks
    private JournalEntryWritePlatformServiceJpaRepositoryImpl service;

    private LoanTransactionDTO transaction(final String transactionId, final Long typeId) {
        return new LoanTransactionDTO(1L, null, transactionId, LocalDate.of(2026, 2, 1), new LoanTransactionEnumData(typeId, "code", "value"),
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, new ArrayList<>(),
                new ArrayList<>(), false);
    }

    private LoanDTO batch(final LoanTransactionDTO... transactions) {
        return new LoanDTO(4001L, 9L, 1L, "KES", false, false, true, Arrays.asList(transactions), null);
    }

    @Test
    public void aSkippedTransactionDoesNotAbortTheRestOfTheBatch() {
        final LoanDTO loanDTO = batch(transaction("100", WAIVE_CHARGES), transaction("101", NOT_POSTED_TO_ODOO),
                transaction("102", REPAYMENT));

        // Every whitelisted transaction has entries; the middle one is skipped purely on its type.
        final List<JournalEntry> noEntries = new ArrayList<>();
        when(this.glJournalEntryRepository.findJournalEntriesByLoanTransactionId("L100")).thenReturn(noEntries);
        when(this.glJournalEntryRepository.findJournalEntriesByLoanTransactionId("L102")).thenReturn(noEntries);

        this.service.postHookForLoanJournalEntries(loanDTO);

        verify(this.glJournalEntryRepository).findJournalEntriesByLoanTransactionId("L100");
        verify(this.glJournalEntryRepository).findJournalEntriesByLoanTransactionId("L102");
    }

    @Test
    public void aTransactionWithNoJournalEntriesDoesNotAbortTheRestOfTheBatch() {
        final LoanDTO loanDTO = batch(transaction("200", REPAYMENT), transaction("201", REPAYMENT));

        when(this.glJournalEntryRepository.findJournalEntriesByLoanTransactionId("L200")).thenReturn(new ArrayList<>());
        when(this.glJournalEntryRepository.findJournalEntriesByLoanTransactionId("L201")).thenReturn(new ArrayList<>());

        this.service.postHookForLoanJournalEntries(loanDTO);

        verify(this.glJournalEntryRepository).findJournalEntriesByLoanTransactionId("L201");
    }
}
