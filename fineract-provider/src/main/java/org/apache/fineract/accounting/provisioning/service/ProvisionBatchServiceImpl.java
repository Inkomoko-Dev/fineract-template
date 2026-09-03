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

import com.google.gson.JsonObject;
import org.apache.fineract.accounting.journalentry.data.JournalEntryData;
import org.apache.fineract.accounting.journalentry.service.JournalEntryReadPlatformService;
import org.apache.fineract.accounting.provisioning.data.ProvisioningEntryData;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatch;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchEntry;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournal;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournalLine;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournalRepository;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchRepository;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchStatus;
import org.apache.fineract.infrastructure.Odoo.OdooService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Groups CBS provisioning journal lines into Odoo-ready journals and posts them.
 *
 * <p>Grouping is by <strong>office + currency</strong>, not currency alone. Two
 * countries can share a currency (e.g. KES) while each office has its own unique
 * mapped GLs; office keeps those journals separate so Odoo can resolve the
 * correct company from the GL mapping on each line.</p>
 *
 * <p>Each new period also mirrors the previous period's <em>original</em>
 * (non-reversal) office journals with debit/credit swapped, so provision
 * reversals are part of the same outbound payload set Odoo posts to GL.</p>
 */
@Service
public class ProvisionBatchServiceImpl implements ProvisionBatchService {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisionBatchServiceImpl.class);

    private final ProvisionBatchRepository provisionBatchRepository;
    private final ProvisionBatchJournalRepository provisionBatchJournalRepository;
    private final ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService;
    private final JournalEntryReadPlatformService provisioningJournalSourceReadService;
    private final OdooService odooService;
    private final ConfigurationDomainService configurationDomainService;

    public ProvisionBatchServiceImpl(ProvisionBatchRepository provisionBatchRepository,
            ProvisionBatchJournalRepository provisionBatchJournalRepository,
            ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService,
            JournalEntryReadPlatformService provisioningJournalSourceReadService, OdooService odooService,
            ConfigurationDomainService configurationDomainService) {
        this.provisionBatchRepository = provisionBatchRepository;
        this.provisionBatchJournalRepository = provisionBatchJournalRepository;
        this.provisioningEntriesReadPlatformService = provisioningEntriesReadPlatformService;
        this.provisioningJournalSourceReadService = provisioningJournalSourceReadService;
        this.odooService = odooService;
        this.configurationDomainService = configurationDomainService;
    }

    @Override
    @CronTarget(jobName = JobName.PROCESS_AND_POST_PROVISION_JOURNAL_ENTRY)
    public void generateAndPostProvisionEntriesToOdoo() {
        generateProvisionBatch();
        postPendingProvisionJournals();
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
            LOG.info("A provisioning batch already exists for accounting period {} - skipping generation "
                    + "(provisioning history id {}). Pending journals may still be posted.", accountingPeriod,
                    history.getId());
            return;
        }

        final List<JournalEntryData> sourceLines =
                provisioningJournalSourceReadService.retrieveAllByTransactionId("P" + history.getId());

        LOG.info("source lines: {}", sourceLines.size());
        if (sourceLines.isEmpty()) {
            LOG.info("No provisioning journal entries found for provisioning history {} "
                    + "- nothing to process.", history.getId());
            return;
        }

        final String batchReference = buildBatchReference(accountingPeriod, history.getId());
        final ProvisionBatch batch = new ProvisionBatch(batchReference, accountingPeriod, ProvisionBatchStatus.CREATED,
                history.getId());

        int journalCount = 0;
        int entryCount = 0;

        // ------------------------------------------------------------------
        // 1. Reverse only the previous batch's ORIGINAL office journals.
        //    Reversal journals from that batch must not be reversed again, or
        //    older periods would be re-posted to Odoo.
        // ------------------------------------------------------------------
        final Optional<ProvisionBatch> previousBatch =
                provisionBatchRepository.findFirstByStatusNotOrderByAccountingPeriodDesc(ProvisionBatchStatus.REVERSED);

        if (previousBatch.isPresent()) {
            final ProvisionBatch reversed = previousBatch.get();
            LOG.info("Reversing original journals of provision batch '{}' (accounting period {}) into new batch '{}'.",
                    reversed.getBatchReference(), reversed.getAccountingPeriod(), batchReference);

            for (final ProvisionBatchJournal originalJournal : reversed.getJournals()) {
                if (originalJournal.isReversal()) {
                    continue;
                }
                // Only reverse journals that actually reached Odoo. Unposted
                // originals are abandoned with the reversed batch so they are
                // not posted later alongside a mirrored reversal.
                if (!ProvisionBatchStatus.POSTED.name().equals(originalJournal.getStatus())) {
                    LOG.info("Skipping reversal of unposted provision journal '{}' (status={}).",
                            originalJournal.getJournalReference(), originalJournal.getStatus());
                    continue;
                }
                final ProvisionBatchJournal reversalJournal = buildReversalJournal(batchReference, accountingPeriod,
                        history.getId(), originalJournal);
                batch.addJournal(reversalJournal);
                journalCount++;
                entryCount += reversalJournal.getEntryCount();
            }

            batch.setReversalOfBatch(reversed);
            reversed.setStatus(ProvisionBatchStatus.REVERSED);
            reversed.setReversedAt(LocalDateTime.now(ZoneId.systemDefault()));
            provisionBatchRepository.saveAndFlush(reversed);
        }

        // ------------------------------------------------------------------
        // 2. Aggregate current-period CBS lines by office + currency.
        //    Same currency across countries stays split by office; GLs on each
        //    line are already unique per country mapping.
        // ------------------------------------------------------------------
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
                    buildJournalOdooReference(history.getId(), journalReference),
                    accountingPeriod,
                    currencyCode
            );
            journal.setOfficeId(officeId);
            journal.setOfficeName(officeName);
            journal.setReversal(false);
            journal.setProvisioningHistoryId(history.getId());

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

            batch.addJournal(journal);
            journalCount++;
            entryCount += officeLines.size();
        }

        batch.setEntryCount(entryCount);
        batch.setJournalCount(journalCount);
        batch.setTotalDebit(sumTotalDebit(batch));
        batch.setTotalCredit(sumTotalCredit(batch));

        // Persist first so journal/line ids exist, then build Odoo payloads.
        provisionBatchRepository.saveAndFlush(batch);
        for (final ProvisionBatchJournal journal : batch.getJournals()) {
            journal.setPayloadJson(odooService.buildProvisioningJournalEntryPayload(journal, journal.isReversal()));
        }
        provisionBatchRepository.saveAndFlush(batch);

        LOG.info("Generated provisioning batch '{}' for accounting period {}: {} source entries "
                        + "aggregated into {} journal(s) across {} office/currency group(s) "
                        + "(provisioning history id {}).",
                batchReference, accountingPeriod, sourceLines.size(), journalCount,
                byOfficeAndCurrency.size(), history.getId());
    }

    @Override
    @Transactional
    public void postPendingProvisionJournals() {
        if (!this.configurationDomainService.isOdooIntegrationEnabled()) {
            LOG.info("Odoo integration is disabled - skipping provision journal posting.");
            return;
        }

        final List<ProvisionBatchJournal> pending = provisionBatchJournalRepository.findByStatusIn(
                Arrays.asList(ProvisionBatchStatus.CREATED.name(), ProvisionBatchStatus.FAILED.name()));

        if (pending.isEmpty()) {
            LOG.info("No pending provision journals to post to Odoo.");
            return;
        }

        LOG.info("Posting {} pending provision journal(s) to Odoo.", pending.size());

        for (final ProvisionBatchJournal journal : pending) {
            final ProvisionBatch parentBatch = journal.getBatch();
            if (parentBatch != null && ProvisionBatchStatus.REVERSED.equals(parentBatch.getStatus())) {
                LOG.info("Skipping journal '{}' because its batch '{}' is already REVERSED.",
                        journal.getJournalReference(), parentBatch.getBatchReference());
                continue;
            }
            if (ProvisionBatchStatus.POSTED.name().equals(journal.getStatus())) {
                continue;
            }

            final Long batchId = parentBatch != null ? parentBatch.getId() : null;
            try {
                if (journal.getPayloadJson() == null || journal.getPayloadJson().isBlank()) {
                    journal.setPayloadJson(
                            odooService.buildProvisioningJournalEntryPayload(journal, journal.isReversal()));
                }

                final JsonObject response = odooService.postProvisioningJournalEntry(journal);
                if (response == null) {
                    journal.setStatus(ProvisionBatchStatus.FAILED);
                    journal.setFailureReason("Empty response from Odoo/Celery");
                    provisionBatchJournalRepository.saveAndFlush(journal);
                    refreshBatchStatus(batchId);
                    continue;
                }

                final boolean success = odooService.getBooleanField(response, "success")
                        || odooService.getBooleanField(response, "ack");
                final String message = odooService.getStringField(response, "message");
                final String odooJournalId = firstNonBlank(
                        odooService.getStringField(response, "journal_entry_no"),
                        odooService.getStringField(response, "odoo_journal_id"),
                        odooService.getStringField(response, "id"));

                if (success) {
                    journal.setStatus(ProvisionBatchStatus.POSTED);
                    journal.setPostedAt(LocalDateTime.now(ZoneId.systemDefault()));
                    journal.setFailureReason(null);
                    if (odooJournalId != null) {
                        journal.setOdooJournalId(odooJournalId);
                    }
                    LOG.info("Posted provision journal '{}' to Odoo (reversal={}, officeId={}, currency={}).",
                            journal.getJournalReference(), journal.isReversal(), journal.getOfficeId(),
                            journal.getCurrencyCode());
                } else {
                    journal.setStatus(ProvisionBatchStatus.FAILED);
                    journal.setFailureReason(message != null ? message : String.valueOf(response));
                    LOG.error("Odoo rejected provision journal '{}': {}", journal.getJournalReference(),
                            journal.getFailureReason());
                }
            } catch (final Exception ex) {
                journal.setStatus(ProvisionBatchStatus.FAILED);
                journal.setFailureReason(ex.getMessage());
                LOG.error("Failed to post provision journal '{}' to Odoo", journal.getJournalReference(), ex);
            }
            provisionBatchJournalRepository.saveAndFlush(journal);
            refreshBatchStatus(batchId);
        }
    }

    private void refreshBatchStatus(final Long batchId) {
        if (batchId == null) {
            return;
        }
        provisionBatchRepository.findById(batchId).ifPresent(managed -> {
            if (ProvisionBatchStatus.REVERSED.equals(managed.getStatus())) {
                return;
            }
            final List<ProvisionBatchJournal> journals = provisionBatchJournalRepository.findByBatchId(batchId);
            final boolean anyFailed = journals.stream()
                    .anyMatch(j -> ProvisionBatchStatus.FAILED.name().equals(j.getStatus()));
            final boolean allPosted = !journals.isEmpty() && journals.stream()
                    .allMatch(j -> ProvisionBatchStatus.POSTED.name().equals(j.getStatus()));

            if (allPosted) {
                managed.setStatus(ProvisionBatchStatus.POSTED);
                managed.setPostedAt(LocalDateTime.now(ZoneId.systemDefault()));
                managed.setFailureReason(null);
            } else if (anyFailed) {
                managed.setStatus(ProvisionBatchStatus.FAILED);
                final String reasons = journals.stream()
                        .filter(j -> ProvisionBatchStatus.FAILED.name().equals(j.getStatus()))
                        .map(j -> j.getJournalReference() + ": " + j.getFailureReason())
                        .collect(Collectors.joining(" | "));
                managed.setFailureReason(reasons);
            }
            provisionBatchRepository.saveAndFlush(managed);
        });
    }

    private ProvisionBatchJournal buildReversalJournal(final String batchReference, final LocalDate accountingPeriod,
            final Long currentHistoryId, final ProvisionBatchJournal original) {

        final String journalReference = buildReversalJournalReference(batchReference, original.getOfficeId(),
                original.getCurrencyCode());
        final Long originalHistoryId = original.getProvisioningHistoryId() != null
                ? original.getProvisioningHistoryId()
                : (original.getBatch() != null ? original.getBatch().getProvisioningHistoryId() : null);

        final ProvisionBatchJournal reversalJournal = new ProvisionBatchJournal(
                journalReference,
                journalReference,
                buildReversalJournalOdooReference(originalHistoryId, original.getJournalReference()),
                accountingPeriod,
                original.getCurrencyCode()
        );
        reversalJournal.setOfficeId(original.getOfficeId());
        reversalJournal.setOfficeName(original.getOfficeName());
        reversalJournal.setReversal(true);
        reversalJournal.setReversedJournalId(original.getId());
        // Narration links to the batch/history being reversed; currentHistoryId is the run that triggers it.
        reversalJournal.setProvisioningHistoryId(
                originalHistoryId != null ? originalHistoryId : currentHistoryId);

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

    private String buildJournalOdooReference(final Long historyId, final String journalReference) {
        return "Loan Loss Provision Entry ID: " + historyId + " ; CBS ID " + journalReference;
    }

    private String buildReversalJournalReference(final String batchReference, final Long officeId,
            final String currencyCode) {
        return batchReference + "-REV-O" + officeId + "-" + currencyCode;
    }

    private String buildReversalJournalOdooReference(final Long originalHistoryId, final String originalJournalReference) {
        return "Reversal of Loan Loss Provision Entry ID: " + originalHistoryId + " ; CBS ID " + originalJournalReference;
    }

    private String firstNonBlank(final String... values) {
        if (values == null) {
            return null;
        }
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

}
