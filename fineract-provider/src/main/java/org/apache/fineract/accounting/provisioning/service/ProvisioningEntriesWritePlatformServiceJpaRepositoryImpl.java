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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.accounting.producttoaccountmapping.domain.PortfolioProductType;
import org.apache.fineract.accounting.provisioning.data.LoanProvisioningCandidateData;
import org.apache.fineract.accounting.provisioning.data.ProvisioningEntryData;
import org.apache.fineract.accounting.provisioning.domain.LoanProductProvisioningEntry;
import org.apache.fineract.accounting.provisioning.domain.ProvisioningClassificationType;
import org.apache.fineract.accounting.provisioning.domain.ProvisioningEntry;
import org.apache.fineract.accounting.provisioning.domain.ProvisioningEntryRepository;
import org.apache.fineract.accounting.provisioning.exception.NoProvisioningCriteriaDefinitionFound;
import org.apache.fineract.accounting.provisioning.exception.ProvisioningEntryAlreadyCreatedException;
import org.apache.fineract.accounting.provisioning.exception.ProvisioningEntryNotfoundException;
import org.apache.fineract.accounting.provisioning.exception.ProvisioningJournalEntriesCannotbeCreatedException;
import org.apache.fineract.accounting.provisioning.serialization.ProvisioningEntriesDefinitionJsonDeserializer;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.hooks.event.HookEvent;
import org.apache.fineract.infrastructure.hooks.event.HookEventSource;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.provisioning.data.ProvisioningCriteriaData;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaDefinition;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaVersion;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaVersionRepository;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCategory;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCategoryRepository;
import org.apache.fineract.organisation.provisioning.service.ProvisioningCriteriaReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProvisioningEntriesWritePlatformServiceJpaRepositoryImpl implements ProvisioningEntriesWritePlatformService {

    private final ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService;
    private final ProvisioningCriteriaReadPlatformService provisioningCriteriaReadPlatformService;
    private final LoanProductRepository loanProductRepository;
    private final GLAccountRepository glAccountRepository;
    private final OfficeRepositoryWrapper officeRepositoryWrapper;
    private final ProvisioningCategoryRepository provisioningCategoryRepository;
    private final PlatformSecurityContext platformSecurityContext;
    private final ProvisioningEntryRepository provisioningEntryRepository;
    private final JournalEntryWritePlatformService journalEntryWritePlatformService;
    private final ProvisioningEntriesDefinitionJsonDeserializer fromApiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final LoanRepository loanRepository;
    private final ProvisioningCriteriaVersionRepository provisioningCriteriaVersionRepository;

    @Override
    public CommandProcessingResult createProvisioningJournalEntries(Long provisioningEntryId, JsonCommand command) {
        ProvisioningEntry requestedEntry = this.provisioningEntryRepository.findById(provisioningEntryId)
                .orElseThrow(() -> new ProvisioningEntryNotfoundException(provisioningEntryId));

        ProvisioningEntryData exisProvisioningEntryData = this.provisioningEntriesReadPlatformService
                .retrieveExistingProvisioningIdDateWithJournals();
        revertAndAddJournalEntries(exisProvisioningEntryData, requestedEntry);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(requestedEntry.getId()).build();
    }

    private void revertAndAddJournalEntries(ProvisioningEntryData existingEntryData, ProvisioningEntry requestedEntry) {
        if (existingEntryData != null) {
            validateForCreateJournalEntry(existingEntryData, requestedEntry);
            this.journalEntryWritePlatformService.revertProvisioningJournalEntries(requestedEntry.getCreatedDate(),
                    existingEntryData.getId(), PortfolioProductType.PROVISIONING.getValue());
        }
        if (!hasJournalableEntries(requestedEntry)) {
            requestedEntry.setJournalEntryCreated(Boolean.FALSE);
        } else {
            requestedEntry.setJournalEntryCreated(Boolean.TRUE);
        }

        this.provisioningEntryRepository.saveAndFlush(requestedEntry);
        if (hasJournalableEntries(requestedEntry)) {
            this.journalEntryWritePlatformService.createProvisioningJournalEntries(requestedEntry);
        }
    }

    private boolean hasJournalableEntries(ProvisioningEntry requestedEntry) {
        return requestedEntry.getLoanProductProvisioningEntries() != null
                && requestedEntry.getLoanProductProvisioningEntries().stream()
                        .anyMatch(entry -> entry.getClassificationType() == ProvisioningClassificationType.PROVISION_BUCKET);
    }

    private void validateForCreateJournalEntry(ProvisioningEntryData existingEntry, ProvisioningEntry requested) {
        LocalDate existingDate = existingEntry.getCreatedDate();
        LocalDate requestedDate = requested.getCreatedDate();
        if (existingDate.isAfter(requestedDate) || existingDate.compareTo(requestedDate) == 0 ? Boolean.TRUE : Boolean.FALSE) {
            throw new ProvisioningJournalEntriesCannotbeCreatedException(existingEntry.getCreatedDate(), requestedDate);
        }
    }

    private boolean isJournalEntriesRequired(JsonCommand command) {
        boolean bool = false;
        if (this.fromApiJsonHelper.parameterExists("createjournalentries", command.parsedJson())) {
            JsonObject jsonObject = command.parsedJson().getAsJsonObject();
            bool = jsonObject.get("createjournalentries").getAsBoolean();
        }
        return bool;
    }

    private LocalDate parseDate(JsonCommand command) {
        return this.fromApiJsonHelper.extractLocalDateNamed("date", command.parsedJson());
    }

    @Override
    @CronTarget(jobName = JobName.GENERATE_LOANLOSS_PROVISIONING)
    public void generateLoanLossProvisioningAmount() {
//        LocalDate currentDate = DateUtils.getBusinessLocalDate();
        LocalDate lastDayOfMonth = DateUtils.getLastDayOfPreviousMonth();
        boolean addJournalEntries = true;
        try {
            Collection<ProvisioningCriteriaData> criteriaCollection = this.provisioningCriteriaReadPlatformService
                    .retrieveAllProvisioningCriterias();
            if (criteriaCollection == null || criteriaCollection.size() == 0) {
                return;
                // FIXME: Do we need to throw
                // NoProvisioningCriteriaDefinitionFound()?
            }
            log.info("Provisioning job started");
            ProvisioningEntry requestedEntry = createProvsioningEntry(lastDayOfMonth, addJournalEntries);
            postWebHook(requestedEntry);
            log.info("Provisioning job complete");
        } catch (ProvisioningEntryAlreadyCreatedException peace) {
            log.error("Provisioning Entry already created", peace);
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            log.error("Problem occurred in generateLoanLossProvisioningAmount function", dve);
        }
    }

    @Override
    public CommandProcessingResult createProvisioningEntries(JsonCommand command) {
        this.fromApiJsonDeserializer.validateForCreate(command.json());
        LocalDate createdDate = parseDate(command);
        boolean addJournalEntries = isJournalEntriesRequired(command);
        try {
            Collection<ProvisioningCriteriaData> criteriaCollection = this.provisioningCriteriaReadPlatformService
                    .retrieveAllProvisioningCriterias();
            if (criteriaCollection == null || criteriaCollection.size() == 0) {
                throw new NoProvisioningCriteriaDefinitionFound();
            }
            ProvisioningEntry requestedEntry = createProvsioningEntry(createdDate, addJournalEntries);
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(requestedEntry.getId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException e) {
            return CommandProcessingResult.empty();
        }
    }

    private ProvisioningEntry createProvsioningEntry(LocalDate date, boolean addJournalEntries) {
        ProvisioningEntry existingEntry = this.provisioningEntryRepository.findByProvisioningEntryDate(date);
        if (existingEntry != null) {
            throw new ProvisioningEntryAlreadyCreatedException(existingEntry.getId(), existingEntry.getCreatedDate());
        }
        AppUser currentUser = this.platformSecurityContext.authenticatedUser();
        AppUser lastModifiedBy = null;
        LocalDate lastModifiedDate = null;
        Set<LoanProductProvisioningEntry> nullEntries = null;
        ProvisioningEntry requestedEntry = new ProvisioningEntry(currentUser, date, lastModifiedBy, lastModifiedDate, nullEntries);
        Collection<LoanProductProvisioningEntry> entries = generateLoanProvisioningEntry(requestedEntry, date);
        requestedEntry.setProvisioningEntries(entries);
        if (addJournalEntries) {
            ProvisioningEntryData exisProvisioningEntryData = this.provisioningEntriesReadPlatformService
                    .retrieveExistingProvisioningIdDateWithJournals();
            revertAndAddJournalEntries(exisProvisioningEntryData, requestedEntry);
        } else {
            this.provisioningEntryRepository.saveAndFlush(requestedEntry);
        }
        return requestedEntry;
    }

    @Override
    public CommandProcessingResult reCreateProvisioningEntries(Long provisioningEntryId, JsonCommand command) {
        ProvisioningEntry requestedEntry = this.provisioningEntryRepository.findById(provisioningEntryId)
                .orElseThrow(() -> new ProvisioningEntryNotfoundException(provisioningEntryId));
        Map<Long, Long> criteriaVersionIds = new HashMap<>();
        requestedEntry.getLoanProductProvisioningEntries().forEach(entry -> criteriaVersionIds.put(entry.getCriteriaId(),
                entry.getCriteriaVersion().getId()));
        requestedEntry.getLoanProductProvisioningEntries().clear();
        this.provisioningEntryRepository.saveAndFlush(requestedEntry);
        Collection<LoanProductProvisioningEntry> entries = generateLoanProvisioningEntry(requestedEntry, requestedEntry.getCreatedDate(),
                criteriaVersionIds);
        requestedEntry.setProvisioningEntries(entries);
        this.provisioningEntryRepository.saveAndFlush(requestedEntry);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(requestedEntry.getId()).build();
    }

    private Collection<LoanProductProvisioningEntry> generateLoanProvisioningEntry(ProvisioningEntry parent, LocalDate date) {
        return generateLoanProvisioningEntry(parent, date, new HashMap<>());
    }

    private Collection<LoanProductProvisioningEntry> generateLoanProvisioningEntry(ProvisioningEntry parent, LocalDate date,
            Map<Long, Long> criteriaVersionIds) {
        Collection<LoanProvisioningCandidateData> entries = this.provisioningEntriesReadPlatformService
                .retrieveLoanProductsProvisioningData(date);
        Map<Long, ProvisioningCriteriaVersion> resolvedVersions = resolveCriteriaVersions(entries, date, criteriaVersionIds);
        Map<ProvisioningEntryAggregationKey, LoanProductProvisioningEntry> provisioningEntries = new HashMap<>();
        for (LoanProvisioningCandidateData data : entries) {
            LoanProduct loanProduct = this.loanProductRepository.findById(data.getProductId()).orElseThrow();
            Office office = this.officeRepositoryWrapper.findOneWithNotFoundDetection(data.getOfficeId());
            MonetaryCurrency currency = loanProduct.getPrincipalAmount().getCurrency();
            ProvisioningCriteriaVersion criteriaVersion = resolvedVersions.get(data.getCriteriaId());
            if (criteriaVersion == null) {
                throw new PlatformDataIntegrityException("error.msg.provisioningcriteria.version.not.found",
                        "No provisioning configuration version is available for criteria " + data.getCriteriaId() + " on " + date);
            }
            Loan loan = this.loanRepository.getReferenceById(data.getLoanId());

            if (isWrittenOff(data)) {
                ProvisioningEntryAggregationKey writtenOffKey = new ProvisioningEntryAggregationKey(data.getCriteriaId(),
                        criteriaVersion.getId(), null, office.getId(), loanProduct.getId(), data.getCurrencyCode(), null,
                        ProvisioningClassificationType.WRITTEN_OFF_PORTFOLIO, data.getOverdueInDays(), null, null);
                LoanProductProvisioningEntry writtenOffEntry = provisioningEntries.get(writtenOffKey);
                if (writtenOffEntry == null) {
                    writtenOffEntry = new LoanProductProvisioningEntry(loanProduct, office, data.getCurrencyCode(), null, criteriaVersion,
                            null, ProvisioningClassificationType.WRITTEN_OFF_PORTFOLIO, data.getOverdueInDays(), BigDecimal.ZERO, null,
                            null, data.getCriteriaId());
                    writtenOffEntry.setProvisioningEntry(parent);
                    provisioningEntries.put(writtenOffKey, writtenOffEntry);
                }
                writtenOffEntry.addLoan(loan, data.getAccountNo(), safeAmount(data.getOutstandingBalance()), BigDecimal.ZERO);
                continue;
            }

            ProvisioningCriteriaDefinition definition = findMatchingDefinition(criteriaVersion, data.getOverdueInDays());
            GLAccount liabilityAccount = glAccountRepository.findById(definition.getLiabilityAccount().getId()).orElseThrow();
            GLAccount expenseAccount = glAccountRepository.findById(definition.getExpenseAccount().getId()).orElseThrow();
            ProvisioningCategory provisioningCategory = provisioningCategoryRepository
                    .findById(definition.getProvisioningCategory().getId()).orElse(null);
            Money money = Money.of(currency, safeAmount(data.getOutstandingBalance()));
            Money amountToReserve = money.percentageOf(definition.getProvisioningPercentage(), MoneyHelper.getRoundingMode());

            ProvisioningEntryAggregationKey key = new ProvisioningEntryAggregationKey(data.getCriteriaId(), criteriaVersion.getId(),
                    definition.getId(), office.getId(), loanProduct.getId(), data.getCurrencyCode(),
                    provisioningCategory == null ? null : provisioningCategory.getId(), ProvisioningClassificationType.PROVISION_BUCKET,
                    data.getOverdueInDays(), liabilityAccount.getId(), expenseAccount.getId());

            LoanProductProvisioningEntry entry = provisioningEntries.get(key);
            if (entry == null) {
                entry = new LoanProductProvisioningEntry(loanProduct, office, data.getCurrencyCode(), provisioningCategory,
                        criteriaVersion, definition, ProvisioningClassificationType.PROVISION_BUCKET, data.getOverdueInDays(),
                        amountToReserve.getAmount(), liabilityAccount, expenseAccount, data.getCriteriaId());
                entry.setProvisioningEntry(parent);
                provisioningEntries.put(key, entry);
            } else {
                entry.addReservedAmount(amountToReserve.getAmount());
            }
            entry.addLoan(loan, data.getAccountNo(), safeAmount(data.getOutstandingBalance()), amountToReserve.getAmount());
        }
        return provisioningEntries.values();
    }

    private Map<Long, ProvisioningCriteriaVersion> resolveCriteriaVersions(Collection<LoanProvisioningCandidateData> entries, LocalDate date,
            Map<Long, Long> criteriaVersionIds) {
        Map<Long, ProvisioningCriteriaVersion> resolved = new HashMap<>();
        for (LoanProvisioningCandidateData entry : entries) {
            if (resolved.containsKey(entry.getCriteriaId())) {
                continue;
            }
            ProvisioningCriteriaVersion version;
            Long explicitVersionId = criteriaVersionIds.get(entry.getCriteriaId());
            if (explicitVersionId != null) {
                version = this.provisioningCriteriaVersionRepository.findById(explicitVersionId)
                        .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.provisioningcriteria.version.not.found",
                                "Provisioning criteria version " + explicitVersionId + " was not found"));
            } else {
                List<ProvisioningCriteriaVersion> versions = this.provisioningCriteriaVersionRepository
                        .findByCriteriaIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(entry.getCriteriaId(), date);
                version = versions.stream().filter(candidate -> candidate.isEffectiveFor(date)).findFirst()
                        .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.provisioningcriteria.version.not.found",
                                "No effective provisioning criteria version exists for criteria " + entry.getCriteriaId() + " on " + date));
            }
            resolved.put(entry.getCriteriaId(), version);
        }
        return resolved;
    }

    private ProvisioningCriteriaDefinition findMatchingDefinition(ProvisioningCriteriaVersion criteriaVersion, Long overdueInDays) {
        return criteriaVersion.getDefinitionsInDisplayOrder().stream().filter(definition -> definition.matches(overdueInDays)).findFirst()
                .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.provisioningcriteria.no.match.for.overdue.range",
                        "No provisioning bucket matches " + overdueInDays + " days in arrears for criteria version "
                                + criteriaVersion.getId()));
    }

    private boolean isWrittenOff(LoanProvisioningCandidateData data) {
        return Integer.valueOf(601).equals(data.getLoanStatusId());
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public void postWebHook(ProvisioningEntry requestedEntry) {
        AppUser currentUser = this.platformSecurityContext.authenticatedUser();
        // Build the payload
        JsonObject payload = new JsonObject();
        provisioningPayLoad(requestedEntry, payload, currentUser);

        FineractContext context = ThreadLocalContextUtil.getContext();
        // Create the HookEvent
        HookEvent hookEvent = new HookEvent(new HookEventSource("PROVISIONENTRIES", "CREATE"), payload.toString(), currentUser, context);
        // Publish the event
        eventPublisher.publishEvent(hookEvent);
    }

    private static void provisioningPayLoad(ProvisioningEntry requestedEntry, JsonObject payload, AppUser currentUser) {
        payload.addProperty("createdByName", currentUser.getUsername());

        JsonObject request = new JsonObject();
        request.addProperty("createjournalentries", true);
        request.addProperty("locale", "en");
        request.addProperty("dateFormat", "dd MMMM yyyy");
        request.addProperty("date", requestedEntry.getCreatedDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));
        payload.add("request", request);

        payload.addProperty("createdBy", requestedEntry.getCreatedBy().getId());
        payload.addProperty("entityName", "PROVISIONENTRIES");

        JsonObject response = new JsonObject();
        response.addProperty("resourceId", requestedEntry.getId());
        payload.add("response", response);

        payload.addProperty("createdByFullName", currentUser.getDisplayName());
        payload.addProperty("actionName", "CREATE");
    }

    private static final class ProvisioningEntryAggregationKey {

        private final Long criteriaId;
        private final Long criteriaVersionId;
        private final Long criteriaDefinitionId;
        private final Long officeId;
        private final Long productId;
        private final String currencyCode;
        private final Long categoryId;
        private final ProvisioningClassificationType classificationType;
        private final Long overdueInDays;
        private final Long liabilityAccountId;
        private final Long expenseAccountId;

        private ProvisioningEntryAggregationKey(Long criteriaId, Long criteriaVersionId, Long criteriaDefinitionId, Long officeId,
                Long productId, String currencyCode, Long categoryId, ProvisioningClassificationType classificationType,
                Long overdueInDays, Long liabilityAccountId, Long expenseAccountId) {
            this.criteriaId = criteriaId;
            this.criteriaVersionId = criteriaVersionId;
            this.criteriaDefinitionId = criteriaDefinitionId;
            this.officeId = officeId;
            this.productId = productId;
            this.currencyCode = currencyCode;
            this.categoryId = categoryId;
            this.classificationType = classificationType;
            this.overdueInDays = overdueInDays;
            this.liabilityAccountId = liabilityAccountId;
            this.expenseAccountId = expenseAccountId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ProvisioningEntryAggregationKey)) {
                return false;
            }
            ProvisioningEntryAggregationKey other = (ProvisioningEntryAggregationKey) obj;
            return Objects.equals(this.criteriaId, other.criteriaId) && Objects.equals(this.criteriaVersionId, other.criteriaVersionId)
                    && Objects.equals(this.criteriaDefinitionId, other.criteriaDefinitionId)
                    && Objects.equals(this.officeId, other.officeId) && Objects.equals(this.productId, other.productId)
                    && Objects.equals(this.currencyCode, other.currencyCode) && Objects.equals(this.categoryId, other.categoryId)
                    && this.classificationType == other.classificationType
                    && Objects.equals(this.overdueInDays, other.overdueInDays)
                    && Objects.equals(this.liabilityAccountId, other.liabilityAccountId)
                    && Objects.equals(this.expenseAccountId, other.expenseAccountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.criteriaId, this.criteriaVersionId, this.criteriaDefinitionId, this.officeId, this.productId,
                    this.currencyCode, this.categoryId, this.classificationType, this.overdueInDays, this.liabilityAccountId,
                    this.expenseAccountId);
        }
    }
}
