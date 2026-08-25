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

import org.apache.fineract.accounting.journalentry.data.JournalEntryData;
import org.apache.fineract.accounting.journalentry.service.JournalEntryReadPlatformService;
import org.apache.fineract.accounting.provisioning.data.ProvisioningEntryData;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatch;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchEntry;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournal;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournalLine;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchRepository;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchStatus;
import org.apache.fineract.infrastructure.Odoo.OdooService;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class ProvisionBatchServiceImpl implements ProvisionBatchService {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisionBatchServiceImpl.class);

    private final ProvisionBatchRepository provisionBatchRepository;
    private final ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService;
    private final JournalEntryReadPlatformService provisioningJournalSourceReadService;
    private final OdooService odooService;

    public ProvisionBatchServiceImpl(ProvisionBatchRepository provisionBatchRepository, ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService, JournalEntryReadPlatformService provisioningJournalSourceReadService, OdooService odooService) {
        this.provisionBatchRepository = provisionBatchRepository;
        this.provisioningEntriesReadPlatformService = provisioningEntriesReadPlatformService;
        this.provisioningJournalSourceReadService = provisioningJournalSourceReadService;
        this.odooService = odooService;
    }

    @Override
    @CronTarget(jobName = JobName.PROCESS_AND_POST_PROVISION_JOURNAL_ENTRY)
    public void generateAndPostProvisionEntriesToOdoo(){
        generateProvisionBatch();
    }

    @Override
    @Transactional
    public void generateProvisionBatch() {

        final Optional<ProvisioningEntryData> latestHistory =
                provisioningEntriesReadPlatformService.findLatestProvisioningHistory();

        if (latestHistory.isEmpty()) {
            LOG.info("No m_provisioning_history rows found - nothing to process.");
            return;
        }

        final ProvisioningEntryData history = latestHistory.get();
        LOG.info("latest history {}", history.getId());
        final LocalDate accountingPeriod = history.getCreatedDate();

        if (provisionBatchRepository.findByAccountingPeriod(accountingPeriod).isPresent()) {
            LOG.info("A provisioning batch already exists for accounting period {} - skipping "
                    + "(provisioning history id {}).", accountingPeriod, history.getId());
            return;
        }

        final List<JournalEntryData> sourceLines =
                provisioningJournalSourceReadService.retrieveAllByTransactionId("P" + history.getId());

        LOG.info("source lines: {}", sourceLines.size());
        if (sourceLines.isEmpty()) {
            LOG.info("No unposted provisioning journal entries found for provisioning history {} "
                    + "- nothing to process.", history.getId());
            return;
        }

        final String batchReference = buildBatchReference(accountingPeriod, history.getId());
        final ProvisionBatch batch = new ProvisionBatch(batchReference, accountingPeriod, ProvisionBatchStatus.CREATED);

        int journalCount = 0;
        int entryCount = 0;

        // ------------------------------------------------------------------
        // 1. Every new provisioning history supersedes the one before it, so
        //    the previous still-active batch (if any) gets reversed into THIS
        //    batch: one mirrored journal per journal it had, debit/credit
        //    swapped, so the reversal can be posted to Odoo alongside the new
        //    figures below.
        // ------------------------------------------------------------------
        final Optional<ProvisionBatch> previousBatch =
                provisionBatchRepository.findFirstByStatusNotOrderByAccountingPeriodDesc(ProvisionBatchStatus.REVERSED);

        if (previousBatch.isPresent()) {
            final ProvisionBatch reversed = previousBatch.get();
            LOG.info("Reversing provision batch '{}' (accounting period {}) into new batch '{}'.",
                    reversed.getBatchReference(), reversed.getAccountingPeriod(), batchReference);

            for (final ProvisionBatchJournal originalJournal : reversed.getJournals()) {
                final ProvisionBatchJournal reversalJournal = buildReversalJournal(batchReference, accountingPeriod, originalJournal);
                batch.addJournal(reversalJournal);
                journalCount++;
                entryCount += reversalJournal.getEntryCount();
            }

            batch.setReversalOfBatch(reversed);
            reversed.setStatus(ProvisionBatchStatus.REVERSED);
            reversed.setReversedAt(LocalDateTime.now(ZoneId.systemDefault()));
            provisionBatchRepository.saveAndFlush(reversed);
        }


        final Map<String, List<JournalEntryData>> byOfficeAndCurrency = sourceLines.stream()
                .collect(Collectors.groupingBy(
                        line -> line.getOfficeId() + "::" + line.getCurrency().getCode(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (final List<JournalEntryData> officeLines : byOfficeAndCurrency.values()) {
            final JournalEntryData first = officeLines.get(0);
            final String currencyCode = first.getCurrency().getCode();
            final Long officeId = first.getOfficeId();
            final String officeName = first.getOfficeName();

            final String journalReference = buildJournalReference(batchReference, officeId, currencyCode);

            final ProvisionBatchJournal journal = new ProvisionBatchJournal(
                    journalReference,
                    journalReference,
                    buildJournalOdooReference(accountingPeriod, officeName, currencyCode),
                    accountingPeriod,
                    currencyCode
            );
            journal.setOfficeId(officeId);
            journal.setOfficeName(officeName);

            final Map<String, List<JournalEntryData>> byAccountAndType = new LinkedHashMap<>();
            for (final JournalEntryData line : officeLines) {
                final String key = line.getGlAccountCode() + "::" + line.getEntryType().getValue();
                byAccountAndType.computeIfAbsent(key, k -> new ArrayList<>()).add(line);

                journal.incrementEntryCount();
                batch.addEntry(new ProvisionBatchEntry(line.getId(), currencyCode));
            }

            BigDecimal journalTotalDebit = BigDecimal.ZERO;
            BigDecimal journalTotalCredit = BigDecimal.ZERO;

            for (final List<JournalEntryData> accountLines : byAccountAndType.values()) {
                final JournalEntryData accountFirst = accountLines.get(0);
                final boolean isDebit = "DEBIT".equals(accountFirst.getEntryType().getValue());
                final BigDecimal sum = accountLines.stream()
                        .map(JournalEntryData::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                journal.addJournalLine(new ProvisionBatchJournalLine(
                        accountFirst.getGlAccountId(), accountFirst.getGlAccountCode(),
                        isDebit ? "DEBIT" : "CREDIT",
                        isDebit ? sum : BigDecimal.ZERO,
                        isDebit ? BigDecimal.ZERO : sum,
                        accountingPeriod));

                if (isDebit) {
                    journalTotalDebit = journalTotalDebit.add(sum);
                } else {
                    journalTotalCredit = journalTotalCredit.add(sum);
                }
            }

            journal.setTotalDebit(journalTotalDebit);
            journal.setTotalCredit(journalTotalCredit);
            journal.setPayloadJson(odooService.buildProvisioningJournalEntryPayload(journal, false));

            batch.addJournal(journal);
            journalCount++;
            entryCount += officeLines.size();
        }

        batch.setEntryCount(entryCount);
        batch.setJournalCount(journalCount);
        batch.setTotalDebit(sumTotalDebit(batch));
        batch.setTotalCredit(sumTotalCredit(batch));

        provisionBatchRepository.saveAndFlush(batch);

        LOG.info("Generated provisioning batch '{}' for accounting period {}: {} source entries "
                        + "aggregated into {} journal(s) (provisioning history id {}).",
                batchReference, accountingPeriod, sourceLines.size(), journalCount, history.getId());
    }


    private ProvisionBatchJournal buildReversalJournal(final String batchReference, final LocalDate accountingPeriod,
            final ProvisionBatchJournal original) {

        final String journalReference = buildReversalJournalReference(batchReference, original.getOfficeId(), original.getCurrencyCode());

        final ProvisionBatchJournal reversalJournal = new ProvisionBatchJournal(
                journalReference,
                journalReference,
                buildReversalJournalOdooReference(accountingPeriod, original),
                accountingPeriod,
                original.getCurrencyCode()
        );
        reversalJournal.setOfficeId(original.getOfficeId());
        reversalJournal.setOfficeName(original.getOfficeName());

        for (final ProvisionBatchJournalLine originalLine : original.getJournalLines()) {
            final boolean wasDebit = originalLine.getDebitAmount().compareTo(BigDecimal.ZERO) != 0;
            final ProvisionBatchJournalLine reversalLine = new ProvisionBatchJournalLine(
                    originalLine.getGlAccountId(),
                    originalLine.getGlAccountCode(),
                    wasDebit ? "CREDIT" : "DEBIT",
                    originalLine.getCreditAmount(),
                    originalLine.getDebitAmount(),
                    accountingPeriod
            );
            reversalJournal.addJournalLine(reversalLine);
            reversalJournal.incrementEntryCount();
        }

        reversalJournal.setTotalDebit(original.getTotalCredit());
        reversalJournal.setTotalCredit(original.getTotalDebit());
        reversalJournal.setPayloadJson(odooService.buildProvisioningJournalEntryPayload(reversalJournal, true));

        return reversalJournal;
    }

    private BigDecimal sumTotalDebit(final ProvisionBatch batch) {
        return batch.getJournals().stream()
                .map(ProvisionBatchJournal::getTotalDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumTotalCredit(final ProvisionBatch batch) {
        return batch.getJournals().stream()
                .map(ProvisionBatchJournal::getTotalCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildBatchReference(final LocalDate accountingPeriod, final Long historyId) {
        return "PROV-" + accountingPeriod + "-H" + historyId;
    }

    private String buildJournalReference(final String batchReference, final Long officeId, final String currencyCode) {
        return batchReference + "-O" + officeId + "-" + currencyCode;
    }

    private String buildJournalOdooReference(final LocalDate accountingPeriod, final String officeName, final String currencyCode) {
        return "Provisioning " + accountingPeriod + " - " + (officeName != null ? officeName : "Office") + " (" + currencyCode + ")";
    }

    private String buildReversalJournalReference(final String batchReference, final Long officeId, final String currencyCode) {
        return batchReference + "-REV-O" + officeId + "-" + currencyCode;
    }

    private String buildReversalJournalOdooReference(final LocalDate accountingPeriod, final ProvisionBatchJournal original) {
        return "Reversal of " + original.getReference() + " (posted " + accountingPeriod + ")";
    }

}
