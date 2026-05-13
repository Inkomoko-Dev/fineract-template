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
package org.apache.fineract.organisation.provisioning.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.provisioning.constants.ProvisioningCriteriaConstants;
import org.apache.fineract.organisation.provisioning.constants.ProvisioningGlobalConfigurationConstants;
import org.apache.fineract.organisation.provisioning.domain.LoanProductProvisionCriteria;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCategory;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCategoryRepository;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteria;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaDefinition;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaVersion;
import org.apache.fineract.organisation.provisioning.exception.ProvisioningCriteriaOverlappingDefinitionException;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningCriteriaAssembler {

    private final FromJsonHelper fromApiJsonHelper;
    private final ProvisioningCategoryRepository provisioningCategoryRepository;
    private final LoanProductRepository loanProductRepository;
    private final GLAccountRepository glAccountRepository;
    private final PlatformSecurityContext platformSecurityContext;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProvisioningCriteriaAssembler(final FromJsonHelper fromApiJsonHelper,
            final ProvisioningCategoryRepository provisioningCategoryRepository, final LoanProductRepository loanProductRepository,
            final GLAccountRepository glAccountRepository, final PlatformSecurityContext platformSecurityContext,
            final JdbcTemplate jdbcTemplate) {
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.provisioningCategoryRepository = provisioningCategoryRepository;
        this.loanProductRepository = loanProductRepository;
        this.glAccountRepository = glAccountRepository;
        this.platformSecurityContext = platformSecurityContext;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LoanProduct> parseLoanProducts(final JsonElement jsonElement) {
        List<LoanProduct> loanProducts = new ArrayList<>();
        if (fromApiJsonHelper.parameterExists(ProvisioningCriteriaConstants.JSON_LOANPRODUCTS_PARAM, jsonElement)) {
            JsonArray jsonloanProducts = this.fromApiJsonHelper.extractJsonArrayNamed(ProvisioningCriteriaConstants.JSON_LOANPRODUCTS_PARAM,
                    jsonElement);
            for (JsonElement element : jsonloanProducts) {
                Long productId = this.fromApiJsonHelper.extractLongNamed("id", element.getAsJsonObject());
                loanProducts.add(loanProductRepository.findById(productId).orElse(null));
            }
        } else {
            loanProducts = loanProductRepository.findAll();
        }
        return loanProducts;
    }

    private void validateRange(List<ProvisioningCriteriaDefinition> definitions) {
        if (definitions.isEmpty()) {
            throw new PlatformDataIntegrityException("error.msg.provisioningcriteria.no.definitions",
                    "At least one provisioning bucket must be configured");
        }

        definitions.sort(Comparator.comparing(ProvisioningCriteriaDefinition::getMinimumAge));

        validateNoOverlaps(definitions);
        for (int i = 0; i < definitions.size(); i++) {
            ProvisioningCriteriaDefinition definition = definitions.get(i);
            if (definition.getMaximumAge() == null && i < definitions.size() - 1) {
                throw new PlatformDataIntegrityException("error.msg.provisioningcriteria.open.ended.bucket.not.last",
                        "Only the last provisioning bucket may be open ended");
            }
        }

        if (relaxContiguousProvisioningBands()) {
            return;
        }

        if (definitions.get(0).getMinimumAge() != 0L) {
            throw new PlatformDataIntegrityException("error.msg.provisioningcriteria.range.must.start.at.zero",
                    "Provisioning bucket ranges must start at 0 days");
        }

        Long expectedMinimumAge = 0L;
        for (int i = 0; i < definitions.size(); i++) {
            ProvisioningCriteriaDefinition definition = definitions.get(i);
            if (!definition.getMinimumAge().equals(expectedMinimumAge)) {
                throw new PlatformDataIntegrityException("error.msg.provisioningcriteria.gapped.ranges",
                        "Provisioning bucket ranges must be continuous without gaps");
            }
            if (definition.getMaximumAge() != null) {
                expectedMinimumAge = definition.getMaximumAge() + 1;
            }
        }
    }

    private void validateNoOverlaps(List<ProvisioningCriteriaDefinition> definitions) {
        for (int i = 0; i < definitions.size(); i++) {
            for (int j = i + 1; j < definitions.size(); j++) {
                if (definitions.get(i).isOverlapping(definitions.get(j))) {
                    throw new ProvisioningCriteriaOverlappingDefinitionException();
                }
            }
        }
    }

    private boolean relaxContiguousProvisioningBands() {
        try {
            final String sql = "SELECT COALESCE(enabled, false) FROM c_configuration WHERE name = ?";
            RowMapper<Boolean> toBool = (rs, rn) -> rs.getBoolean(1);
            return Boolean.TRUE.equals(
                    jdbcTemplate.queryForObject(sql, toBool, ProvisioningGlobalConfigurationConstants.RELAX_CONTIGUOUS_AGING_BANDS));
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    public ProvisioningCriteria fromParsedJson(final JsonElement jsonElement) {
        ProvisioningCriteria provisioningCriteria = createCriteria(jsonElement);
        ProvisioningCriteriaVersion version = createProvisioningCriteriaVersion(provisioningCriteria, 1, jsonElement);
        List<LoanProduct> loanProducts = parseLoanProducts(jsonElement);

        Set<LoanProductProvisionCriteria> mapping = new HashSet<>();
        for (LoanProduct loanProduct : loanProducts) {
            mapping.add(new LoanProductProvisionCriteria(provisioningCriteria, loanProduct));
        }
        Set<ProvisioningCriteriaVersion> versions = new HashSet<>();
        versions.add(version);
        provisioningCriteria.setProvisioningCriteriaVersions(versions);
        provisioningCriteria.setLoanProductProvisioningCriteria(mapping);
        return provisioningCriteria;
    }

    public ProvisioningCriteriaVersion createProvisioningCriteriaVersion(final ProvisioningCriteria criteria, final int versionNo,
            final JsonElement jsonElement) {
        return createProvisioningCriteriaVersion(criteria, versionNo, parseEffectiveFrom(jsonElement), jsonElement);
    }

    public ProvisioningCriteriaVersion createProvisioningCriteriaVersion(final ProvisioningCriteria criteria, final int versionNo,
            final LocalDate effectiveFrom, final JsonElement jsonElement) {
        ProvisioningCriteriaVersion version = new ProvisioningCriteriaVersion(criteria, versionNo, effectiveFrom, null,
                platformSecurityContext.authenticatedUser(), DateUtils.getLocalDateTimeOfSystem(),
                platformSecurityContext.authenticatedUser(), DateUtils.getLocalDateTimeOfSystem());
        final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(jsonElement.getAsJsonObject());
        List<ProvisioningCriteriaDefinition> definitions = new ArrayList<>();
        JsonArray jsonProvisioningCriteria = this.fromApiJsonHelper
                .extractJsonArrayNamed(ProvisioningCriteriaConstants.JSON_PROVISIONING_DEFINITIONS_PARAM, jsonElement);
        for (JsonElement element : jsonProvisioningCriteria) {
            JsonObject jsonObject = element.getAsJsonObject();
            definitions.add(createProvisioningCriteriaDefinitions(jsonObject, locale, criteria, version));
        }
        validateRange(definitions);
        version.setDefinitions(new HashSet<>(definitions));
        if (this.fromApiJsonHelper.parameterExists(ProvisioningCriteriaConstants.JSON_POLICY_CHANGE_REASON_PARAM, jsonElement)) {
            final String reason = this.fromApiJsonHelper
                    .extractStringNamed(ProvisioningCriteriaConstants.JSON_POLICY_CHANGE_REASON_PARAM, jsonElement);
            if (StringUtils.isNotBlank(reason)) {
                version.setPolicyChangeReason(reason.trim());
            }
        }
        return version;
    }

    private ProvisioningCriteria createCriteria(final JsonElement jsonElement) {
        final String criteriaName = this.fromApiJsonHelper.extractStringNamed(ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM,
                jsonElement);

        ProvisioningCriteria criteria = new ProvisioningCriteria(criteriaName, platformSecurityContext.authenticatedUser(),
                DateUtils.getLocalDateTimeOfSystem(), platformSecurityContext.authenticatedUser(), DateUtils.getLocalDateTimeOfSystem());
        return criteria;
    }

    private ProvisioningCriteriaDefinition createProvisioningCriteriaDefinitions(JsonObject jsonObject, Locale locale,
            ProvisioningCriteria criteria, ProvisioningCriteriaVersion criteriaVersion) {
        Long categoryId = this.fromApiJsonHelper.extractLongNamed(ProvisioningCriteriaConstants.JSON_CATEOGRYID_PARAM, jsonObject);
        Long minimumAge = this.fromApiJsonHelper.extractLongNamed(ProvisioningCriteriaConstants.JSON_MINIMUM_AGE_PARAM, jsonObject);
        Long maximumAge = this.fromApiJsonHelper.extractLongNamed(ProvisioningCriteriaConstants.JSON_MAXIMUM_AGE_PARAM, jsonObject);
        BigDecimal provisioningpercentage = this.fromApiJsonHelper
                .extractBigDecimalNamed(ProvisioningCriteriaConstants.JSON_PROVISIONING_PERCENTAGE_PARAM, jsonObject, locale);
        Long liabilityAccountId = this.fromApiJsonHelper.extractLongNamed(ProvisioningCriteriaConstants.JSON_LIABILITY_ACCOUNT_PARAM,
                jsonObject);
        Long expenseAccountId = this.fromApiJsonHelper.extractLongNamed(ProvisioningCriteriaConstants.JSON_EXPENSE_ACCOUNT_PARAM,
                jsonObject);

        ProvisioningCategory provisioningCategory = provisioningCategoryRepository.findById(categoryId).orElse(null);
        GLAccount liabilityAccount = glAccountRepository.findById(liabilityAccountId).orElse(null);
        GLAccount expenseAccount = glAccountRepository.findById(expenseAccountId).orElse(null);
        if (provisioningCategory == null || !provisioningCategory.isActive()) {
            throw new PlatformDataIntegrityException("error.msg.provisioningcategory.invalid",
                    "The selected provisioning category is invalid or inactive");
        }
        if (provisioningpercentage.compareTo(BigDecimal.ZERO) < 0 || provisioningpercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new PlatformDataIntegrityException("error.msg.provisioningcriteria.invalid.percentage",
                    "Provisioning percentage must be between 0 and 100");
        }
        return ProvisioningCriteriaDefinition.newProvisioningCriteriaDefinition(criteria, criteriaVersion, provisioningCategory,
                minimumAge, maximumAge, provisioningpercentage, liabilityAccount, expenseAccount);
    }

    public LocalDate parseEffectiveFrom(final JsonElement jsonElement) {
        if (this.fromApiJsonHelper.parameterExists(ProvisioningCriteriaConstants.JSON_EFFECTIVE_FROM_PARAM, jsonElement)) {
            return this.fromApiJsonHelper.extractLocalDateNamed(ProvisioningCriteriaConstants.JSON_EFFECTIVE_FROM_PARAM, jsonElement);
        }
        return DateUtils.getBusinessLocalDate();
    }
}
