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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.KenyaCapitalDisbursementDefaultsResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KenyaCapitalDisbursementDefaultsService {

    public static final String CONFIG_ENABLED = "kenya-capital-disbursement-defaults-enabled";
    public static final String CONFIG_OFFICE_NAME = "kenya-capital-office-name";
    public static final String CONFIG_DEPARTMENT_NAME = "kenya-capital-default-department-name";
    public static final String CONFIG_BUDGET_CODE_NAME = "investments-budget-code-name";

    private static final String DEFAULT_OFFICE_NAME = "Inkomoko Kenya Capital";
    private static final String DEFAULT_DEPARTMENT_NAME = "Investment";
    private static final String DEFAULT_BUDGET_CODE_NAME = "InvestmentsBudget";
    private static final String DEPARTMENT_CODE_NAME = "Department";
    private static final String BUDGET_LOCATION_PREFIX = "Investments - ";
    private static final DateTimeFormatter BUDGET_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final ConfigurationReadPlatformService configurationReadPlatformService;
    private final CodeValueRepositoryWrapper codeValueRepository;

    public boolean isEnabled() {
        try {
            return configurationReadPlatformService.retrieveGlobalConfiguration(CONFIG_ENABLED).isEnabled();
        } catch (Exception ex) {
            return true;
        }
    }

    public boolean isKenyaCapitalLoan(final Loan loan) {
        if (!isEnabled() || loan == null) {
            return false;
        }
        final Office office = loan.getOffice();
        return office != null && getKenyaCapitalOfficeName().equalsIgnoreCase(office.getName());
    }

    public KenyaCapitalDisbursementDefaultsResult resolve(final Loan loan, final LocalDate disbursementDate) {
        if (!isKenyaCapitalLoan(loan)) {
            return KenyaCapitalDisbursementDefaultsResult.notApplicable();
        }
        final LocalDate effectiveDate = disbursementDate != null ? disbursementDate : DateUtils.getBusinessLocalDate();
        CodeValue department = loan.getDepartment();
        if (department == null) {
            department = codeValueRepository.findOneByCodeNameAndLabelWithNotFoundDetection(DEPARTMENT_CODE_NAME,
                    getDefaultDepartmentName());
        }
        final String budgetLocation = formatBudgetLocation(effectiveDate);
        final boolean budgetReviewRequired = !isBudgetConfigured(budgetLocation);
        return KenyaCapitalDisbursementDefaultsResult.applicable(department, budgetLocation, budgetReviewRequired);
    }

    public KenyaCapitalDisbursementDefaultsResult applyDisbursementDefaults(final Loan loan, final LocalDate disbursementDate,
            final JsonCommand command, final Map<String, Object> changes) {
        final KenyaCapitalDisbursementDefaultsResult defaults = resolve(loan, disbursementDate);
        if (!defaults.isKenyaCapital()) {
            return defaults;
        }

        applyDepartmentDefault(loan, command, changes, defaults);
        applyBudgetDefaults(loan, disbursementDate, command, changes, defaults);

        log.info(
                "CGLT-653: Applied Kenya Capital disbursement defaults for loan {} (department='{}', budgetLocation='{}', budgetReviewRequired={})",
                loan.getId(), defaults.getDepartmentName(), changes.get(LoanApiConstants.BUDGET_LOCATION_PARAM),
                changes.get(LoanApiConstants.BUDGET_REVIEW_REQUIRED_PARAM));
        return defaults;
    }

    public String formatBudgetLocation(final LocalDate disbursementDate) {
        return BUDGET_LOCATION_PREFIX + BUDGET_MONTH_FORMATTER.format(disbursementDate);
    }

    private void applyDepartmentDefault(final Loan loan, final JsonCommand command, final Map<String, Object> changes,
            final KenyaCapitalDisbursementDefaultsResult defaults) {
        if (command != null && command.parameterExists(LoanApiConstants.DEPARTMENT_PARAM)) {
            final Long departmentId = command.longValueOfParameterNamed(LoanApiConstants.DEPARTMENT_PARAM);
            if (departmentId != null) {
                final CodeValue department = codeValueRepository.findOneWithNotFoundDetection(departmentId);
                loan.updateDepartment(department);
                changes.put(LoanApiConstants.DEPARTMENT_PARAM, departmentId);
            }
            return;
        }
        if (loan.getDepartment() == null && defaults.getDepartment() != null) {
            loan.updateDepartment(defaults.getDepartment());
            changes.put(LoanApiConstants.DEPARTMENT_PARAM, defaults.getDepartment().getId());
        }
    }

    private void applyBudgetDefaults(final Loan loan, final LocalDate disbursementDate, final JsonCommand command,
            final Map<String, Object> changes, final KenyaCapitalDisbursementDefaultsResult defaults) {
        String budgetLocation = defaults.getBudgetLocation();
        Boolean budgetReviewRequired = defaults.isBudgetReviewRequired();

        if (command != null && command.parameterExists(LoanApiConstants.BUDGET_LOCATION_PARAM)) {
            final String overrideLocation = command.stringValueOfParameterNamedAllowingNull(LoanApiConstants.BUDGET_LOCATION_PARAM);
            if (StringUtils.isNotBlank(overrideLocation)) {
                budgetLocation = overrideLocation.trim();
                budgetReviewRequired = !isBudgetConfigured(budgetLocation);
            }
        }

        applyBudgetToDisbursementDetails(loan, disbursementDate, budgetLocation, budgetReviewRequired);
        changes.put(LoanApiConstants.BUDGET_LOCATION_PARAM, budgetLocation);
        changes.put(LoanApiConstants.BUDGET_REVIEW_REQUIRED_PARAM, budgetReviewRequired);
    }

    private void applyBudgetToDisbursementDetails(final Loan loan, final LocalDate disbursementDate, final String budgetLocation,
            final Boolean budgetReviewRequired) {
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

    private boolean isBudgetConfigured(final String budgetLocation) {
        return codeValueRepository.findOneByCodeNameAndLabelOptional(getBudgetCodeName(), budgetLocation) != null;
    }

    private String getKenyaCapitalOfficeName() {
        return getConfigString(CONFIG_OFFICE_NAME, DEFAULT_OFFICE_NAME);
    }

    private String getDefaultDepartmentName() {
        return getConfigString(CONFIG_DEPARTMENT_NAME, DEFAULT_DEPARTMENT_NAME);
    }

    private String getBudgetCodeName() {
        return getConfigString(CONFIG_BUDGET_CODE_NAME, DEFAULT_BUDGET_CODE_NAME);
    }

    private String getConfigString(final String configName, final String defaultValue) {
        try {
            final GlobalConfigurationPropertyData config = configurationReadPlatformService.retrieveGlobalConfiguration(configName);
            if (config != null && StringUtils.isNotBlank(config.getStringValue())) {
                return config.getStringValue().trim();
            }
        } catch (Exception ex) {
            log.warn("Failed to read configuration {}: {}", configName, ex.getMessage());
        }
        return defaultValue;
    }
}
