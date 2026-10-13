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
import org.apache.fineract.accounting.provisioning.data.ProvisionBatchJournalData;
import org.apache.fineract.accounting.provisioning.data.ProvisionBatchJournalLineData;
import org.apache.fineract.accounting.provisioning.data.ProvisioningEntryData;
import org.apache.fineract.accounting.provisioning.exception.ProvisionBatchJournalNotFoundException;
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
import java.math.RoundingMode;
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
 * Groups CBS provisioning journal lines into company-level journals and posts them
 * to the external GL.
 *
 * <p>One journal is sent per company (country entity) and currency. Rwanda branches
 * that share Rwanda Capital GLs become a single journal; Kenya Capital stays
 * separate from Kenya MFI even though both use KES. Ethiopia is not posted.</p>
 *
 * <p>Each new period also mirrors the previous period's posted original
 * company journals with debit/credit swapped, so the prior-month reversal and
 * the current-month provision are posted in the same run.</p>
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

        final Optional<ProvisionBatch> previousBatch = findPreviousBatchToReverse(accountingPeriod);

        if (previousBatch.isPresent()) {
            final ProvisionBatch reversed = previousBatch.get();
            LOG.info("Reversing posted original journals of provision batch '{}' (accounting period {}) into new batch '{}'.",
                    reversed.getBatchReference(), reversed.getAccountingPeriod(), batchReference);

            final Map<String, List<ProvisionBatchJournal>> reversalsByCompany = groupPostedOriginalsByCompany(reversed);
            for (final List<ProvisionBatchJournal> originals : reversalsByCompany.values()) {
                final ProvisionBatchJournal reversalJournal = buildMergedReversalJournal(batchReference,
                        accountingPeriod, history.getId(), originals);
                batch.addJournal(reversalJournal);
                journalCount++;
                entryCount += reversalJournal.getEntryCount();
            }
        }

        final Map<String, List<JournalEntryData>> byCompanyAndCurrency = new LinkedHashMap<>();
        int skippedExcluded = 0;
        for (final JournalEntryData line : sourceLines) {
            final String companyKey = ProvisionPostingCompany.fromGlAccountCode(line.getGlAccountCode(),
                    line.getOfficeId());
            if (ProvisionPostingCompany.isExcluded(companyKey)) {
                skippedExcluded++;
                continue;
            }
            final String groupKey = ProvisionPostingCompany.groupingKey(companyKey, line.getCurrency().getCode());
            byCompanyAndCurrency.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(line);
        }

        if (skippedExcluded > 0) {
            LOG.info("Skipped {} Ethiopia provision journal line(s) for GL posting.", skippedExcluded);
        }

        for (final List<JournalEntryData> companyLines : byCompanyAndCurrency.values()) {
            final JournalEntryData first = companyLines.get(0);
            final String currencyCode = first.getCurrency().getCode();
            final String companyKey = ProvisionPostingCompany.fromGlAccountCode(first.getGlAccountCode(),
                    first.getOfficeId());
            final Long representativeOfficeId = companyLines.stream()
                    .map(JournalEntryData::getOfficeId)
                    .min(Long::compareTo)
                    .orElse(first.getOfficeId());

            final String journalReference = buildJournalReference(batchReference, companyKey, currencyCode);
            final ProvisionBatchJournal journal = new ProvisionBatchJournal(
                    journalReference,
                    journalReference,
                    buildJournalOdooReference(history.getId(), journalReference),
                    accountingPeriod,
                    currencyCode
            );
            journal.setOfficeId(representativeOfficeId);
            journal.setOfficeName(companyKey);
            journal.setReversal(false);
            journal.setProvisioningHistoryId(history.getId());
            addAggregatedSourceLines(journal, companyLines, accountingPeriod, batch, currencyCode);

            batch.addJournal(journal);
            journalCount++;
            entryCount += companyLines.size();
        }

        if (batch.getJournals().isEmpty()) {
            LOG.info("No country provision journals to send for provisioning history {} "
                    + "(source lines={}, skipped Ethiopia={}).", history.getId(), sourceLines.size(),
                    skippedExcluded);
            return;
        }

        if (previousBatch.isPresent()) {
            final ProvisionBatch reversed = previousBatch.get();
            batch.setReversalOfBatch(reversed);
            reversed.setStatus(ProvisionBatchStatus.REVERSED);
            reversed.setReversedAt(LocalDateTime.now(ZoneId.systemDefault()));
            provisionBatchRepository.saveAndFlush(reversed);
        }

        batch.setEntryCount(entryCount);
        batch.setJournalCount(journalCount);
        batch.setTotalDebit(sumTotalDebit(batch));
        batch.setTotalCredit(sumTotalCredit(batch));

        provisionBatchRepository.saveAndFlush(batch);
        for (final ProvisionBatchJournal journal : batch.getJournals()) {
            journal.setPayloadJson(odooService.buildProvisioningJournalEntryPayload(journal, journal.isReversal()));
        }
        provisionBatchRepository.saveAndFlush(batch);

        LOG.info("Generated provisioning batch '{}' for accounting period {}: {} source entries "
                        + "aggregated into {} journal(s) across {} company/currency group(s) "
                        + "(provisioning history id {}).",
                batchReference, accountingPeriod, sourceLines.size(), journalCount,
                byCompanyAndCurrency.size(), history.getId());
    }

    private Optional<ProvisionBatch> findPreviousBatchToReverse(final LocalDate currentAccountingPeriod) {
        Optional<ProvisionBatch> posted = provisionBatchRepository
                .findFirstByStatusOrderByAccountingPeriodDesc(ProvisionBatchStatus.POSTED);
        if (posted.isPresent() && !posted.get().getAccountingPeriod().isEqual(currentAccountingPeriod)) {
            return provisionBatchRepository.findByIdWithJournals(posted.get().getId());
        }

        final Optional<ProvisionBatch> anyActive = provisionBatchRepository
                .findFirstByStatusNotOrderByAccountingPeriodDesc(ProvisionBatchStatus.REVERSED);
        if (anyActive.isPresent() && !anyActive.get().getAccountingPeriod().isEqual(currentAccountingPeriod)) {
            return provisionBatchRepository.findByIdWithJournals(anyActive.get().getId());
        }
        return Optional.empty();
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

                final boolean success = isSuccessfulOdooResponse(response, odooService);
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

    private Map<String, List<ProvisionBatchJournal>> groupPostedOriginalsByCompany(final ProvisionBatch previous) {
        final Map<String, List<ProvisionBatchJournal>> byCompany = new LinkedHashMap<>();
        for (final ProvisionBatchJournal original : previous.getJournals()) {
            if (original.isReversal()) {
                continue;
            }
            if (!ProvisionBatchStatus.POSTED.name().equals(original.getStatus())) {
                LOG.info("Skipping reversal of unposted provision journal '{}' (status={}).",
                        original.getJournalReference(), original.getStatus());
                continue;
            }
            final String companyKey = companyKeyFromJournal(original);
            if (ProvisionPostingCompany.isExcluded(companyKey)) {
                LOG.info("Skipping reversal of Ethiopia provision journal '{}'.", original.getJournalReference());
                continue;
            }
            final String groupKey = ProvisionPostingCompany.groupingKey(companyKey, original.getCurrencyCode());
            byCompany.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(original);
        }
        return byCompany;
    }

    private String companyKeyFromJournal(final ProvisionBatchJournal journal) {
        if (journal.getJournalLines() != null && !journal.getJournalLines().isEmpty()) {
            return ProvisionPostingCompany.fromGlAccountCode(journal.getJournalLines().get(0).getGlAccountCode(),
                    journal.getOfficeId());
        }
        if (journal.getOfficeName() != null && !journal.getOfficeName().isBlank()) {
            return journal.getOfficeName();
        }
        return "OFFICE-" + journal.getOfficeId();
    }

    private void addAggregatedSourceLines(final ProvisionBatchJournal journal, final List<JournalEntryData> lines,
            final LocalDate accountingPeriod, final ProvisionBatch batch, final String currencyCode) {
        final Map<String, List<JournalEntryData>> byAccountAndType = new LinkedHashMap<>();
        for (final JournalEntryData line : lines) {
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
            final BigDecimal sum = roundMoney(accountLines.stream()
                    .map(JournalEntryData::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

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
    }

    private ProvisionBatchJournal buildMergedReversalJournal(final String batchReference,
            final LocalDate accountingPeriod, final Long currentHistoryId,
            final List<ProvisionBatchJournal> originals) {

        final ProvisionBatchJournal first = originals.get(0);
        final String companyKey = companyKeyFromJournal(first);
        final String currencyCode = first.getCurrencyCode();
        final String journalReference = buildReversalJournalReference(batchReference, companyKey, currencyCode);
        final Long originalHistoryId = first.getProvisioningHistoryId() != null
                ? first.getProvisioningHistoryId()
                : (first.getBatch() != null ? first.getBatch().getProvisioningHistoryId() : null);
        final String originalCbsId = originals.size() == 1
                ? first.getJournalReference()
                : buildJournalReference(first.getBatch() != null ? first.getBatch().getBatchReference() : batchReference,
                        companyKey, currencyCode);

        final ProvisionBatchJournal reversalJournal = new ProvisionBatchJournal(
                journalReference,
                journalReference,
                buildReversalJournalOdooReference(
                        originalHistoryId != null ? originalHistoryId : currentHistoryId, originalCbsId),
                accountingPeriod,
                currencyCode
        );
        reversalJournal.setOfficeId(first.getOfficeId());
        reversalJournal.setOfficeName(companyKey);
        reversalJournal.setReversal(true);
        reversalJournal.setReversedJournalId(first.getId());
        reversalJournal.setProvisioningHistoryId(originalHistoryId != null ? originalHistoryId : currentHistoryId);

        final Map<String, ProvisionBatchJournalLine> aggregated = new LinkedHashMap<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (final ProvisionBatchJournal original : originals) {
            for (final ProvisionBatchJournalLine originalLine : original.getJournalLines()) {
                final boolean wasDebit = originalLine.getDebitAmount().compareTo(BigDecimal.ZERO) != 0;
                final String reversalType = wasDebit ? "CREDIT" : "DEBIT";
                final BigDecimal reversalDebit = roundMoney(originalLine.getCreditAmount());
                final BigDecimal reversalCredit = roundMoney(originalLine.getDebitAmount());
                final String key = originalLine.getGlAccountCode() + "::" + reversalType;
                final ProvisionBatchJournalLine existing = aggregated.get(key);
                if (existing == null) {
                    final ProvisionBatchJournalLine reversalLine = new ProvisionBatchJournalLine(
                            originalLine.getGlAccountId(),
                            originalLine.getGlAccountCode(),
                            reversalType,
                            reversalDebit,
                            reversalCredit,
                            accountingPeriod
                    );
                    aggregated.put(key, reversalLine);
                } else {
                    existing.setDebitAmount(roundMoney(existing.getDebitAmount().add(reversalDebit)));
                    existing.setCreditAmount(roundMoney(existing.getCreditAmount().add(reversalCredit)));
                }
                reversalJournal.incrementEntryCount();
            }
        }

        for (final ProvisionBatchJournalLine reversalLine : aggregated.values()) {
            reversalJournal.addJournalLine(reversalLine);
            totalDebit = totalDebit.add(reversalLine.getDebitAmount());
            totalCredit = totalCredit.add(reversalLine.getCreditAmount());
        }
        reversalJournal.setTotalDebit(totalDebit);
        reversalJournal.setTotalCredit(totalCredit);
        return reversalJournal;
    }

    private BigDecimal roundMoney(final BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }
        return amount.setScale(2, RoundingMode.HALF_EVEN);
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

    private String buildJournalReference(final String batchReference, final String companyKey,
            final String currencyCode) {
        return batchReference + "-" + ProvisionPostingCompany.slug(companyKey) + "-" + currencyCode;
    }

    private String buildJournalOdooReference(final Long historyId, final String journalReference) {
        return "Loan Loss Provision Entry ID: " + historyId + " ; CBS ID " + journalReference;
    }

    private String buildReversalJournalReference(final String batchReference, final String companyKey,
            final String currencyCode) {
        return batchReference + "-REV-" + ProvisionPostingCompany.slug(companyKey) + "-" + currencyCode;
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

    private boolean isSuccessfulOdooResponse(final JsonObject response, final OdooService odooService) {
        if (response == null) {
            return false;
        }
        if (odooService.getBooleanField(response, "success") || odooService.getBooleanField(response, "ack")) {
            return true;
        }
        final String responseCode = odooService.getStringField(response, "responseCode");
        return "POSTED".equals(responseCode) || "EXISTING".equals(responseCode) || "REVERSED".equals(responseCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvisionBatchJournalData> retrieveProvisionBatchJournals(final String status, final Boolean reversal,
            final Long entryId, final LocalDate date) {
        final String normalizedStatus = normalizeStatus(status);
        final List<ProvisionBatchJournal> journals = entryId != null
                ? provisionBatchJournalRepository.findByBatchProvisioningHistoryIdOrderByEntryDateDescIdDesc(entryId)
                : provisionBatchJournalRepository.findAllByOrderByEntryDateDescIdDesc();
        return journals.stream()
                .filter(journal -> normalizedStatus == null || normalizedStatus.equals(journal.getStatus()))
                .filter(journal -> reversal == null || journal.isReversal() == reversal)
                .filter(journal -> date == null || date.equals(journal.getEntryDate()))
                .map(journal -> toJournalData(journal, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProvisionBatchJournalData retrieveProvisionBatchJournal(final Long journalId) {
        final ProvisionBatchJournal journal = provisionBatchJournalRepository.findDetailedById(journalId)
                .orElseThrow(() -> new ProvisionBatchJournalNotFoundException(journalId));
        return toJournalData(journal, true);
    }

    private static String normalizeStatus(final String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        final String normalized = status.trim().toUpperCase();
        if ("PENDING".equals(normalized)) {
            return ProvisionBatchStatus.CREATED.name();
        }
        return normalized;
    }

    private ProvisionBatchJournalData toJournalData(final ProvisionBatchJournal journal, final boolean includeLines) {
        final ProvisionBatch batch = journal.getBatch();
        final List<ProvisionBatchJournalLineData> lines = includeLines && journal.getJournalLines() != null
                ? journal.getJournalLines().stream().map(ProvisionBatchJournalLineData::from).collect(Collectors.toList())
                : null;
        return new ProvisionBatchJournalData(journal.getId(), batch != null ? batch.getId() : null,
                batch != null ? batch.getBatchReference() : null, journal.getJournalReference(), journal.getReference(),
                journal.getEntryDate(), journal.getCurrencyCode(), journal.getOfficeId(), journal.getOfficeName(),
                journal.isReversal(), journal.getStatus(), journal.getOdooJournalId(), journal.getTotalDebit(),
                journal.getTotalCredit(), journal.getProvisioningHistoryId(), journal.getReversedJournalId(),
                journal.getFailureReason(), journal.getPostedAt(), journal.getCreatedAt(), lines);
    }

}
