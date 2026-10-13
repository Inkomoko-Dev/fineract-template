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
package org.apache.fineract.accounting.provisioning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.data.JournalEntryData;
import org.apache.fineract.accounting.journalentry.service.JournalEntryReadPlatformService;
import org.apache.fineract.accounting.provisioning.data.ProvisioningEntryData;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatch;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournal;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournalLine;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournalRepository;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchRepository;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchStatus;
import org.apache.fineract.infrastructure.Odoo.OdooService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionBatchServiceImplTest {

    private static final LocalDate PERIOD = LocalDate.of(2026, 9, 30);

    @Mock
    private ProvisionBatchRepository provisionBatchRepository;
    @Mock
    private ProvisionBatchJournalRepository provisionBatchJournalRepository;
    @Mock
    private ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService;
    @Mock
    private JournalEntryReadPlatformService journalEntryReadPlatformService;
    @Mock
    private OdooService odooService;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private ProvisioningEntryData history;

    private ProvisionBatchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProvisionBatchServiceImpl(provisionBatchRepository, provisionBatchJournalRepository,
                provisioningEntriesReadPlatformService, journalEntryReadPlatformService, odooService,
                configurationDomainService);
        when(history.getId()).thenReturn(271L);
        when(history.getCreatedDate()).thenReturn(PERIOD);
        when(provisioningEntriesReadPlatformService.findLatestProvisioningHistory()).thenReturn(Optional.of(history));
        when(provisionBatchRepository.findByAccountingPeriod(PERIOD)).thenReturn(Optional.empty());
        when(provisionBatchRepository.saveAndFlush(any(ProvisionBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(odooService.buildProvisioningJournalEntryPayload(any(), anyBoolean())).thenReturn("{}");
        lenient().when(provisionBatchRepository.findFirstByStatusOrderByAccountingPeriodDesc(any()))
                .thenReturn(Optional.empty());
        lenient().when(provisionBatchRepository.findFirstByStatusNotOrderByAccountingPeriodDesc(any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void mergesRwandaOfficesIntoOneCompanyJournalAndSkipsEthiopia() {
        when(journalEntryReadPlatformService.retrieveAllByTransactionId("P271")).thenReturn(List.of(
                credit(1L, 5L, "Inkomoko - Ethiopia", "2601-3649-Ethiopia_Watch/Special Mention-loan loss allowance",
                        3649L, "1000.00", "ETB"),
                debit(2L, 5L, "Inkomoko - Ethiopia", "6306-3657-Ethiopia_Collective Credit loss provision",
                        3657L, "1000.00", "ETB"),
                credit(3L, 73L, "Kirhe", "2606-3744-Rwanda Capital_Loss-loan loss allowance", 3744L, "677610.00",
                        "RWF"),
                debit(4L, 73L, "Kirhe", "6306-3591-Rwanda Capital_Collective Credit loss provision", 3591L,
                        "684000.00", "RWF"),
                credit(5L, 22L, "Southern Province", "2606-3744-Rwanda Capital_Loss-loan loss allowance", 3744L,
                        "43600.00", "RWF"),
                debit(6L, 22L, "Southern Province", "6306-3591-Rwanda Capital_Collective Credit loss provision", 3591L,
                        "43600.00", "RWF"),
                credit(7L, 19L, "Eastern Province", "2606-3744-Rwanda Capital_Loss-loan loss allowance", 3744L,
                        "756391.68", "RWF"),
                debit(8L, 19L, "Eastern Province", "6306-3591-Rwanda Capital_Collective Credit loss provision", 3591L,
                        "756391.68", "RWF"),
                credit(9L, 117L, "Inkomoko - Chad", "2605-6225-Chad Doubtful-loan loss allowance", 6225L, "1213.70",
                        "XAF"),
                debit(10L, 117L, "Inkomoko - Chad", "6306-4061-Chad Collective Credit loss provision", 4061L, "5130589.79",
                        "XAF")));

        service.generateProvisionBatch();

        final ProvisionBatch batch = capturedGeneratedBatch();
        assertEquals(2, batch.getJournals().size());

        final ProvisionBatchJournal rwanda = journalNamed(batch, "Rwanda Capital");
        assertFalse(rwanda.isReversal());
        assertEquals("RWF", rwanda.getCurrencyCode());
        assertEquals("Loan Loss Provision Entry ID: 271 ; CBS ID " + rwanda.getJournalReference(), rwanda.getReference());
        assertEquals(new BigDecimal("1483991.68"), lineAmount(rwanda, "6306-3591-Rwanda Capital_Collective Credit loss provision",
                "DEBIT"));
        assertEquals(new BigDecimal("1477601.68"), lineAmount(rwanda, "2606-3744-Rwanda Capital_Loss-loan loss allowance",
                "CREDIT"));

        final ProvisionBatchJournal chad = journalNamed(batch, "Chad");
        assertFalse(chad.isReversal());
        assertEquals("XAF", chad.getCurrencyCode());
        assertEquals(new BigDecimal("5130589.79"), lineAmount(chad, "6306-4061-Chad Collective Credit loss provision",
                "DEBIT"));
        assertTrue(batch.getJournals().stream().noneMatch(journal -> "Ethiopia".equals(journal.getOfficeName())));
    }

    @Test
    void keepsKenyaCapitalSeparateFromKenya() {
        when(journalEntryReadPlatformService.retrieveAllByTransactionId("P271")).thenReturn(List.of(
                credit(1L, 2L, "Nairobi", "2601-1001-Kenya_Watch/Special Mention-loan loss allowance", 1001L, "10.00",
                        "KES"),
                debit(2L, 2L, "Nairobi", "6306-1002-Kenya_Collective Credit loss provision", 1002L, "10.00", "KES"),
                credit(3L, 40L, "Capital Nairobi", "2601-2001-Kenya Capital_Watch/Special Mention-loan loss allowance",
                        2001L, "25.00", "KES"),
                debit(4L, 40L, "Capital Nairobi", "6306-2002-Kenya Capital_Collective Credit loss provision", 2002L,
                        "25.00", "KES")));

        service.generateProvisionBatch();

        final ProvisionBatch batch = capturedGeneratedBatch();
        assertEquals(2, batch.getJournals().size());
        assertEquals(new BigDecimal("10.00"), lineAmount(journalNamed(batch, "Kenya"),
                "6306-1002-Kenya_Collective Credit loss provision", "DEBIT"));
        assertEquals(new BigDecimal("25.00"), lineAmount(journalNamed(batch, "Kenya Capital"),
                "6306-2002-Kenya Capital_Collective Credit loss provision", "DEBIT"));
    }

    @Test
    void reversesPreviousPostedOfficeJournalsAsOneCompanyReversal() {
        final ProvisionBatch previous = new ProvisionBatch("PROV-2026-08-31-H270", LocalDate.of(2026, 8, 31),
                ProvisionBatchStatus.POSTED, 270L);
        previous.addJournal(postedOriginal("PROV-2026-08-31-H270-O73-RWF", 73L, "Kirhe", "RWF", 270L,
                "6306-3591-Rwanda Capital_Collective Credit loss provision", "DEBIT", "684000.00"));
        previous.addJournal(postedOriginal("PROV-2026-08-31-H270-O22-RWF", 22L, "Southern Province", "RWF", 270L,
                "6306-3591-Rwanda Capital_Collective Credit loss provision", "DEBIT", "43600.00"));
        final ProvisionBatchJournal previousReversal = postedOriginal("PROV-2026-08-31-H270-REV-O73-RWF", 73L, "Kirhe",
                "RWF", 269L, "6306-3591-Rwanda Capital_Collective Credit loss provision", "CREDIT", "500.00");
        previousReversal.setReversal(true);
        previous.addJournal(previousReversal);

        when(provisionBatchRepository.findFirstByStatusOrderByAccountingPeriodDesc(ProvisionBatchStatus.POSTED))
                .thenReturn(Optional.of(previous));
        when(provisionBatchRepository.findByIdWithJournals(any())).thenReturn(Optional.of(previous));
        when(journalEntryReadPlatformService.retrieveAllByTransactionId("P271")).thenReturn(List.of(
                debit(8L, 73L, "Kirhe", "6306-3591-Rwanda Capital_Collective Credit loss provision", 3591L, "10.00",
                        "RWF"),
                credit(9L, 73L, "Kirhe", "2606-3744-Rwanda Capital_Loss-loan loss allowance", 3744L, "10.00", "RWF")));

        service.generateProvisionBatch();

        final ProvisionBatch batch = capturedGeneratedBatch();
        assertEquals(ProvisionBatchStatus.REVERSED, previous.getStatus());
        final ProvisionBatchJournal reversal = batch.getJournals().stream()
                .filter(ProvisionBatchJournal::isReversal)
                .findFirst()
                .orElseThrow();
        assertEquals("Rwanda Capital", reversal.getOfficeName());
        assertTrue(reversal.getReference().startsWith("Reversal of Loan Loss Provision Entry ID: 270"));
        assertEquals(new BigDecimal("727600.00"), lineAmount(reversal,
                "6306-3591-Rwanda Capital_Collective Credit loss provision", "CREDIT"));
        assertEquals(1, batch.getJournals().stream().filter(journal -> !journal.isReversal()).count());
    }

    private ProvisionBatch capturedGeneratedBatch() {
        final ArgumentCaptor<ProvisionBatch> captor = ArgumentCaptor.forClass(ProvisionBatch.class);
        verify(provisionBatchRepository, atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getAllValues().stream()
                .filter(batch -> "PROV-2026-09-30-H271".equals(batch.getBatchReference()))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private static ProvisionBatchJournal journalNamed(final ProvisionBatch batch, final String company) {
        return batch.getJournals().stream()
                .filter(journal -> company.equals(journal.getOfficeName()))
                .findFirst()
                .orElseThrow();
    }

    private static BigDecimal lineAmount(final ProvisionBatchJournal journal, final String glCode, final String type) {
        final ProvisionBatchJournalLine line = journal.getJournalLines().stream()
                .filter(item -> glCode.equals(item.getGlAccountCode()) && type.equals(item.getEntryType()))
                .findFirst()
                .orElseThrow();
        return "DEBIT".equals(type) ? line.getDebitAmount() : line.getCreditAmount();
    }

    private static ProvisionBatchJournal postedOriginal(final String reference, final Long officeId, final String officeName,
            final String currency, final Long historyId, final String glCode, final String type, final String amount) {
        final ProvisionBatchJournal journal = new ProvisionBatchJournal(reference, reference, "ref",
                LocalDate.of(2026, 8, 31), currency);
        journal.setOfficeId(officeId);
        journal.setOfficeName(officeName);
        journal.setReversal(false);
        journal.setProvisioningHistoryId(historyId);
        journal.setStatus(ProvisionBatchStatus.POSTED);
        final boolean debit = "DEBIT".equals(type);
        final BigDecimal money = new BigDecimal(amount);
        journal.addJournalLine(new ProvisionBatchJournalLine(1L, glCode, type,
                debit ? money : BigDecimal.ZERO, debit ? BigDecimal.ZERO : money, LocalDate.of(2026, 8, 31)));
        journal.setTotalDebit(debit ? money : BigDecimal.ZERO);
        journal.setTotalCredit(debit ? BigDecimal.ZERO : money);
        return journal;
    }

    private static JournalEntryData debit(final Long id, final Long officeId, final String officeName, final String glCode,
            final Long glAccountId, final String amount, final String currency) {
        return line(id, officeId, officeName, glCode, glAccountId, "DEBIT", amount, currency);
    }

    private static JournalEntryData credit(final Long id, final Long officeId, final String officeName, final String glCode,
            final Long glAccountId, final String amount, final String currency) {
        return line(id, officeId, officeName, glCode, glAccountId, "CREDIT", amount, currency);
    }

    private static JournalEntryData line(final Long id, final Long officeId, final String officeName, final String glCode,
            final Long glAccountId, final String entryType, final String amount, final String currency) {
        return new JournalEntryData(id, officeId, officeName, glCode, glAccountId, glCode, null, PERIOD,
                new EnumOptionData("DEBIT".equals(entryType) ? 2L : 1L, entryType, entryType), new BigDecimal(amount),
                "P271", false, null, null, null, null, null, null, false, null, null, null, false, null,
                new CurrencyData(currency), false, false, null);
    }
}
