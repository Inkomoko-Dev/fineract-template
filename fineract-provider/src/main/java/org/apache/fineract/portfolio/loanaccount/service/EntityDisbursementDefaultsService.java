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
package org.apache.fineract.portfolio.loanaccount.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.journalentry.data.JournalData;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.EntityDisbursementDefaultsConfiguration;
import org.apache.fineract.portfolio.loanaccount.data.EntityDisbursementDefaultsResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.springframework.stereotype.Service;

/**
 * Generic service for entity-specific disbursement defaults.
 * 
 * Configuration format (JSON):
 * [
 *   {
 *     "entityName": "Kenya Capital",
 *     "officeNames": ["Inkomoko Kenya Capital"],
 *     "defaultDepartmentName": "Investment",
 *     "budgetCodeName": "InvestmentsBudget",
 *     "budgetLocationPrefix": "Investments - "
 *   }
 * ]
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityDisbursementDefaultsService {

    public static final String CONFIG_ENABLED = "entity-disbursement-defaults-enabled";
    public static final String CONFIG_ENTITIES = "entity-disbursement-defaults-config";

    private static final String DEPARTMENT_CODE_NAME = "Department";
    private static final DateTimeFormatter BUDGET_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final ConfigurationReadPlatformService configurationReadPlatformService;
    private final CodeValueRepositoryWrapper codeValueRepository;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        try {
            return configurationReadPlatformService.retrieveGlobalConfiguration(CONFIG_ENABLED).isEnabled();
        } catch (Exception ex) {
            return true;
        }
    }

    public List<EntityDisbursementDefaultsConfiguration> getConfigurations() {
        try {
            final GlobalConfigurationPropertyData config = configurationReadPlatformService
                    .retrieveGlobalConfiguration(CONFIG_ENTITIES);
            if (config != null && StringUtils.isNotBlank(config.getDescription())) {
                return objectMapper.readValue(config.getDescription(),
                        new TypeReference<List<EntityDisbursementDefaultsConfiguration>>() {});
            }
        } catch (Exception ex) {
            log.warn("Failed to parse entity disbursement defaults configuration: {}", ex.getMessage(), ex);
        }
        return new ArrayList<>();
    }

    public EntityDisbursementDefaultsConfiguration findConfigurationForOffice(final String officeName) {
        if (!isEnabled() || StringUtils.isBlank(officeName)) {
            return null;
        }
        for (final EntityDisbursementDefaultsConfiguration config : getConfigurations()) {
            if (config.matchesOfficeName(officeName)) {
                return config;
            }
        }
        return null;
    }

    public EntityDisbursementDefaultsConfiguration findConfigurationForLoan(final Loan loan) {
        if (!isEnabled() || loan == null) {
            return null;
        }
        // Prefer explicit loan entity office (m_loan.office_id), then client office.
        EntityDisbursementDefaultsConfiguration config = findConfigurationForOffice(loan.getOffice());
        if (config == null) {
            final Client client = loan.client();
            if (client != null) {
                config = findConfigurationForOffice(client.getOffice());
            }
        }
        return config;
    }

    public EntityDisbursementDefaultsConfiguration findConfigurationForOffice(final Office office) {
        return office != null ? findConfigurationForOffice(office.getName()) : null;
    }

    public EntityDisbursementDefaultsResult resolve(final Loan loan, final LocalDate disbursementDate) {
        return resolve(loan, null, disbursementDate);
    }

    public EntityDisbursementDefaultsResult resolve(final Loan loan, final Office journalOffice,
            final LocalDate disbursementDate) {
        final EntityDisbursementDefaultsConfiguration loanConfig = findConfigurationForLoan(loan);
        final EntityDisbursementDefaultsConfiguration journalConfig = findConfigurationForOffice(journalOffice);
        
        final EntityDisbursementDefaultsConfiguration config = loanConfig != null ? loanConfig : journalConfig;
        if (config == null) {
            return EntityDisbursementDefaultsResult.notApplicable();
        }

        final LocalDate effectiveDate = disbursementDate != null ? disbursementDate : DateUtils.getBusinessLocalDate();
        final CodeValue department = codeValueRepository.findOneByCodeNameAndLabelWithNotFoundDetection(
                DEPARTMENT_CODE_NAME, config.getDefaultDepartmentName());
        final String budgetLocation = formatBudgetLocation(config.getBudgetLocationPrefix(), effectiveDate);
        final boolean budgetReviewRequired = !isBudgetConfigured(config.getBudgetCodeName(), budgetLocation);
        
        return EntityDisbursementDefaultsResult.applicable(config.getEntityName(), department, budgetLocation,
                budgetReviewRequired);
    }

    public EntityDisbursementDefaultsResult applyDisbursementDefaults(final Loan loan, final LocalDate disbursementDate,
            final JsonCommand command, final Map<String, Object> changes) {
        final EntityDisbursementDefaultsResult defaults = resolve(loan, disbursementDate);
        if (!defaults.isApplicable()) {
            log.debug("CGLT-653: Skipping entity disbursement defaults for loan {} (office='{}')",
                    loan != null ? loan.getId() : null,
                    loan != null && loan.getOffice() != null ? loan.getOffice().getName() : null);
            return defaults;
        }

        applyDepartmentDefault(loan, command, changes, defaults);
        applyBudgetDefaults(loan, disbursementDate, command, changes, defaults);

        log.info(
                "CGLT-653: Applied entity disbursement defaults for loan {} (entity='{}', department='{}')",
                loan.getId(), defaults.getEntityName(), defaults.getDepartmentName());
        return defaults;
    }

    /**
     * Enrich the CBS→Odoo journal payload for entity-specific disbursements.
     * Odoo/Celery historically uses {@code location} as the budget analytic; department is sent as an explicit field.
     */
    public void enrichOdooJournalData(final JournalData journalData, final Loan loan,
            final LoanTransaction loanTransaction, final Office journalOffice) {
        if (journalData == null || loanTransaction == null || !loanTransaction.isDisbursement()) {
            return;
        }

        final EntityDisbursementDefaultsResult defaults = resolve(loan, journalOffice,
                loanTransaction.getTransactionDate());
        if (!defaults.isApplicable()) {
            log.info(
                    "CGLT-653: Odoo payload not enriched for loanTxn {} / loan {} (no matching entity). loanOffice='{}', journalOffice='{}'",
                    loanTransaction.getId(), loan != null ? loan.getId() : null,
                    loan != null && loan.getOffice() != null ? loan.getOffice().getName() : null,
                    journalOffice != null ? journalOffice.getName() : null);
            return;
        }

        String budgetLocation = findPersistedBudgetLocation(loan, loanTransaction);
        Boolean budgetReviewRequired = findPersistedBudgetReviewRequired(loan, loanTransaction);

        if (StringUtils.isBlank(budgetLocation)) {
            budgetLocation = defaults.getBudgetLocation();
            budgetReviewRequired = defaults.isBudgetReviewRequired();
        }
        if (budgetReviewRequired == null) {
            budgetReviewRequired = defaults.isBudgetReviewRequired();
        }

        if (StringUtils.isNotBlank(budgetLocation)) {
            journalData.setLocation(budgetLocation);
        }
        journalData.setBudgetReviewRequired(budgetReviewRequired);

        final String departmentName = getEffectiveDepartmentName(defaults, loan);
        journalData.setDepartment(departmentName);

        log.info(
                "CGLT-653: Enriched Odoo journal for loanTxn {} / loan {} with entity='{}', department='{}', location='{}', budgetReviewRequired={}",
                loanTransaction.getId(), loan.getId(), defaults.getEntityName(), departmentName, budgetLocation,
                budgetReviewRequired);
    }

    public String formatBudgetLocation(final String prefix, final LocalDate disbursementDate) {
        final String actualPrefix = StringUtils.isNotBlank(prefix) ? prefix : "Investments - ";
        return actualPrefix + BUDGET_MONTH_FORMATTER.format(disbursementDate);
    }

    private void applyDepartmentDefault(final Loan loan, final JsonCommand command, final Map<String, Object> changes,
            final EntityDisbursementDefaultsResult defaults) {
        // Explicit request override wins; otherwise force configured department for the entity
        // (department is required at application, so "only if null" never ran in practice).
        if (command != null && command.parameterExists(LoanApiConstants.DEPARTMENT_PARAM)) {
            final Long departmentId = command.longValueOfParameterNamed(LoanApiConstants.DEPARTMENT_PARAM);
            if (departmentId != null) {
                final CodeValue department = codeValueRepository.findOneWithNotFoundDetection(departmentId);
                loan.updateDepartment(department);
                changes.put(LoanApiConstants.DEPARTMENT_PARAM, departmentId);
            }
            return;
        }
        if (defaults.getDepartment() != null) {
            final CodeValue current = loan.getDepartment();
            if (current == null || !defaults.getDepartment().getId().equals(current.getId())) {
                loan.updateDepartment(defaults.getDepartment());
                changes.put(LoanApiConstants.DEPARTMENT_PARAM, defaults.getDepartment().getId());
            }
        }
    }

    private void applyBudgetDefaults(final Loan loan, final LocalDate disbursementDate, final JsonCommand command,
            final Map<String, Object> changes, final EntityDisbursementDefaultsResult defaults) {
        String budgetLocation = defaults.getBudgetLocation();
        Boolean budgetReviewRequired = defaults.isBudgetReviewRequired();

        if (command != null && command.parameterExists(LoanApiConstants.BUDGET_LOCATION_PARAM)) {
            final String overrideLocation = command.stringValueOfParameterNamedAllowingNull(LoanApiConstants.BUDGET_LOCATION_PARAM);
            if (StringUtils.isNotBlank(overrideLocation)) {
                budgetLocation = overrideLocation.trim();
                budgetReviewRequired = !isBudgetConfigured(getBudgetCodeNameForEntity(defaults.getEntityName()),
                        budgetLocation);
            }
        }

        applyBudgetToDisbursementDetails(loan, disbursementDate, budgetLocation, budgetReviewRequired);
        changes.put(LoanApiConstants.BUDGET_LOCATION_PARAM, budgetLocation);
        changes.put(LoanApiConstants.BUDGET_REVIEW_REQUIRED_PARAM, budgetReviewRequired);
    }

    private void applyBudgetToDisbursementDetails(final Loan loan, final LocalDate disbursementDate,
            final String budgetLocation, final Boolean budgetReviewRequired) {
        if (loan.getDisbursementDetails() == null || loan.getDisbursementDetails().isEmpty()) {
            return;
        }
        LoanDisbursementDetails targetDetail = null;
        for (final LoanDisbursementDetails detail : loan.getDisbursementDetails()) {
            if (disbursementDate != null && (disbursementDate.equals(detail.getActualDisbursementDate())
                    || disbursementDate.equals(detail.getExpectedDisbursementDate()))) {
                targetDetail = detail;
                break;
            }
        }
        if (targetDetail == null) {
            targetDetail = loan.getDisbursementDetails().iterator().next();
        }
        targetDetail.setBudgetLocation(budgetLocation);
        targetDetail.setBudgetReviewRequired(budgetReviewRequired);
    }

    private String findPersistedBudgetLocation(final Loan loan, final LoanTransaction loanTransaction) {
        final LoanDisbursementDetails detail = findMatchingDisbursementDetail(loan, loanTransaction);
        return detail != null ? detail.getBudgetLocation() : null;
    }

    private Boolean findPersistedBudgetReviewRequired(final Loan loan, final LoanTransaction loanTransaction) {
        final LoanDisbursementDetails detail = findMatchingDisbursementDetail(loan, loanTransaction);
        return detail != null ? detail.getBudgetReviewRequired() : null;
    }

    private LoanDisbursementDetails findMatchingDisbursementDetail(final Loan loan,
            final LoanTransaction loanTransaction) {
        if (loan == null || loan.getDisbursementDetails() == null || loan.getDisbursementDetails().isEmpty()) {
            return null;
        }
        LoanDisbursementDetails byDate = null;
        for (final LoanDisbursementDetails detail : loan.getDisbursementDetails()) {
            if (detail.getActualDisbursementDate() != null
                    && detail.getActualDisbursementDate().equals(loanTransaction.getTransactionDate())) {
                if (detail.getPrincipal() != null && loanTransaction.getAmount(loan.getCurrency()) != null
                        && detail.getPrincipal().compareTo(loanTransaction.getAmount(loan.getCurrency()).getAmount()) == 0) {
                    return detail;
                }
                if (byDate == null) {
                    byDate = detail;
                }
            }
            if (StringUtils.isNotBlank(detail.getBudgetLocation()) && byDate == null) {
                byDate = detail;
            }
        }
        return byDate != null ? byDate : loan.getDisbursementDetails().iterator().next();
    }

    private boolean isBudgetConfigured(final String budgetCodeName, final String budgetLocation) {
        return codeValueRepository.findOneByCodeNameAndLabelOptional(budgetCodeName, budgetLocation) != null;
    }

    private String getBudgetCodeNameForEntity(final String entityName) {
        for (final EntityDisbursementDefaultsConfiguration config : getConfigurations()) {
            if (entityName.equals(config.getEntityName())) {
                return config.getBudgetCodeName();
            }
        }
        return "InvestmentsBudget"; // fallback
    }

    private String getEffectiveDepartmentName(final EntityDisbursementDefaultsResult defaults, final Loan loan) {
        if (defaults.getDepartmentName() != null) {
            return defaults.getDepartmentName();
        }
        if (loan.getDepartment() != null) {
            return loan.getDepartment().label();
        }
        return null;
    }
}