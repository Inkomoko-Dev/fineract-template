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

import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.service.JournalEntryReadPlatformService;
import org.apache.fineract.accounting.provisioning.domain.ProvisioningEntry;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchRepository;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatch;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchStatus;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournalLine;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournal;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchEntry;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public ProvisionBatchServiceImpl(ProvisionBatchRepository provisionBatchRepository, ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService, JournalEntryReadPlatformService provisioningJournalSourceReadService) {
        this.provisionBatchRepository = provisionBatchRepository;
        this.provisioningEntriesReadPlatformService = provisioningEntriesReadPlatformService;
        this.provisioningJournalSourceReadService = provisioningJournalSourceReadService;
    }

    @Override
    @CronTarget(jobName = JobName.PROCESS_AND_POST_PROVISION_JOURNAL_ENTRY)
    public void generateAndPostProvisionEntriesToOdoo(){
        generateProvisionBatch();
    }

    @Override
    @Transactional
    public void generateProvisionBatch() {

        final Optional<ProvisioningEntry> latestHistory =
                provisioningEntriesReadPlatformService.findLatestProvisioningHistory();

        if (latestHistory.isEmpty()) {
            LOG.info("No m_provisioning_history rows found - nothing to process.");
            return;
        }

        final ProvisioningEntry history = latestHistory.get();
        final LocalDate accountingPeriod = history.getCreatedDate();

        if (provisionBatchRepository.findByAccountingPeriod(accountingPeriod).isPresent()) {
            LOG.info("A provisioning batch already exists for accounting period {} - skipping "
                    + "(provisioning history id {}).", accountingPeriod, history.getId());
            return;
        }

        final List<JournalEntry> sourceLines =
                provisioningJournalSourceReadService.retrieveAllByTransactionId("P"+history.getId());

        if (sourceLines.isEmpty()) {
            LOG.info("No unposted provisioning journal entries found for provisioning history {} "
                    + "- nothing to process.", history.getId());
            return;
        }

        final String batchReference = buildBatchReference(accountingPeriod, history.getId());
        final ProvisionBatch batch = new ProvisionBatch(batchReference, accountingPeriod, ProvisionBatchStatus.CREATED);

        final Map<String, List<JournalEntry>> grouped = sourceLines.stream()
                .collect(Collectors.groupingBy(
                        line -> line.getCurrencyCode() + "::" + line.getGlAccount().getGlCode(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int journalCount = 0;
        for (final List<JournalEntry> lines : grouped.values()) {
            final JournalEntry first = lines.get(0);
            final String journalReference = buildJournalReference(batchReference, first.getCurrencyCode(), first.getGlAccount().getGlCode());

            final ProvisionBatchJournal journal = new ProvisionBatchJournal(
                    journalReference,
                    journalReference,
                    buildJournalOdooReference(accountingPeriod, first.getGlAccount().getGlCode(), first.getCurrencyCode()),
                    accountingPeriod,
                    first.getCurrencyCode()
            );

            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;

            for (final JournalEntry line : lines) {
                final boolean isDebit = line.getType() == 1 ? true: false;
                final BigDecimal debitAmount = isDebit ? line.getAmount() : BigDecimal.ZERO;
                final BigDecimal creditAmount = isDebit ? BigDecimal.ZERO : line.getAmount();

                final ProvisionBatchJournalLine journalLine = new ProvisionBatchJournalLine(
                        line.getGlAccount().getId(),
                        line.getGlAccount().getGlCode(),
                        isDebit ? "DEBIT" : "CREDIT",
                        debitAmount,
                        creditAmount,
                        line.getTransactionDate() != null ? line.getTransactionDate() : accountingPeriod
                );
                journal.addJournalLine(journalLine);
                journal.incrementEntryCount();

                totalDebit = totalDebit.add(debitAmount);
                totalCredit = totalCredit.add(creditAmount);

                final ProvisionBatchEntry batchEntry = new ProvisionBatchEntry(line.getId(), line.getCurrencyCode());
                batch.addEntry(batchEntry);
            }

            journal.setTotalDebit(totalDebit);
            journal.setTotalCredit(totalCredit);

            batch.addJournal(journal);
            journalCount++;
        }

        provisionBatchRepository.saveAndFlush(batch);

        LOG.info("Generated provisioning batch '{}' for accounting period {}: {} source entries "
                        + "aggregated into {} journal(s) (provisioning history id {}).",
                batchReference, accountingPeriod, sourceLines.size(), journalCount, history.getId());
    }

    private String buildBatchReference(final LocalDate accountingPeriod, final Long historyId) {
        return "PROV-" + accountingPeriod + "-H" + historyId;
    }

    private String buildJournalReference(final String batchReference, final String currencyCode, final String glAccountCode) {
        return batchReference + "-" + currencyCode + "-" + glAccountCode;
    }

    private String buildJournalOdooReference(final LocalDate accountingPeriod, final String glAccountCode, final String currencyCode) {
        return "Provisioning " + accountingPeriod + " - GL " + glAccountCode + " (" + currencyCode + ")";
    }

}
