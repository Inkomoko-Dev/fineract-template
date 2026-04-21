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
package org.apache.fineract.organisation.provisioning.data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.apache.fineract.accounting.glaccount.data.GLAccountData;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductData;

@SuppressWarnings("unused")
public final class ProvisioningCriteriaData implements Comparable<ProvisioningCriteriaData>, Serializable {

    private final Long criteriaId;
    private final String criteriaName;
    private final String createdBy;
    private final Collection<LoanProductData> loanProducts;
    private Collection<LoanProductData> selectedLoanProducts;
    private final Collection<ProvisioningCriteriaDefinitionData> definitions;
    private final Collection<ProvisioningCategoryData> categories;
    private final Collection<GLAccountData> glAccounts;
    private final Long activeVersionId;
    private final Integer versionNo;
    private final LocalDate effectiveFrom;

    private ProvisioningCriteriaData(final Long criteriaId, final String criteriaName, final Collection<LoanProductData> loanProducts,
            Collection<ProvisioningCriteriaDefinitionData> definitions, Collection<ProvisioningCategoryData> categories,
            Collection<GLAccountData> glAccounts, final String createdBy, final Long activeVersionId, final Integer versionNo,
            final LocalDate effectiveFrom) {
        this.criteriaId = criteriaId;
        this.criteriaName = criteriaName;
        this.loanProducts = loanProducts;
        this.definitions = definitions;
        this.categories = categories;
        this.glAccounts = glAccounts;
        this.createdBy = createdBy;
        this.activeVersionId = activeVersionId;
        this.versionNo = versionNo;
        this.effectiveFrom = effectiveFrom;
    }

    private ProvisioningCriteriaData(ProvisioningCriteriaData data, final Collection<LoanProductData> loanProducts,
            Collection<ProvisioningCategoryData> categories, Collection<GLAccountData> glAccounts) {
        this.criteriaId = data.criteriaId;
        this.criteriaName = data.criteriaName;
        this.selectedLoanProducts = data.loanProducts;
        this.loanProducts = loanProducts;
        this.loanProducts.removeAll(selectedLoanProducts);
        this.definitions = data.definitions;
        this.categories = categories;
        this.glAccounts = glAccounts;
        this.createdBy = data.createdBy;
        this.activeVersionId = data.activeVersionId;
        this.versionNo = data.versionNo;
        this.effectiveFrom = data.effectiveFrom;
    }

    public static ProvisioningCriteriaData toLookup(final Long criteriaId, final String criteriaName,
            final Collection<LoanProductData> loanProducts, final List<ProvisioningCriteriaDefinitionData> definitions,
            final Long activeVersionId, final Integer versionNo, final LocalDate effectiveFrom) {
        Collection<GLAccountData> glAccounts = null;
        Collection<ProvisioningCategoryData> categories = null;
        String createdBy = null;
        return new ProvisioningCriteriaData(criteriaId, criteriaName, loanProducts, definitions, categories, glAccounts, createdBy,
                activeVersionId, versionNo, effectiveFrom);
    }

    public static ProvisioningCriteriaData toLookup(final Long criteriaId, final String criteriaName, String createdBy) {
        Collection<GLAccountData> glAccounts = null;
        Collection<LoanProductData> loanProducts = null;
        List<ProvisioningCriteriaDefinitionData> definitions = null;
        Collection<ProvisioningCategoryData> categories = null;
        Long activeVersionId = null;
        Integer versionNo = null;
        LocalDate effectiveFrom = null;
        return new ProvisioningCriteriaData(criteriaId, criteriaName, loanProducts, definitions, categories, glAccounts, createdBy,
                activeVersionId, versionNo, effectiveFrom);
    }

    public static ProvisioningCriteriaData toTemplate(final Collection<ProvisioningCategoryData> categories,
            final Collection<ProvisioningCriteriaDefinitionData> definitions, final Collection<LoanProductData> loanProducts,
            final Collection<GLAccountData> glAccounts) {
        Long criteriaId = null;
        String criteriaName = null;
        String createdBy = null;
        Long activeVersionId = null;
        Integer versionNo = null;
        LocalDate effectiveFrom = null;
        return new ProvisioningCriteriaData(criteriaId, criteriaName, loanProducts, definitions, categories, glAccounts, createdBy,
                activeVersionId, versionNo, effectiveFrom);
    }

    public static ProvisioningCriteriaData toTemplate(final ProvisioningCriteriaData data,
            final Collection<ProvisioningCategoryData> categories, final Collection<LoanProductData> loanProducts,
            final Collection<GLAccountData> glAccounts) {
        return new ProvisioningCriteriaData(data, loanProducts, categories, glAccounts);
    }

    public Long getCriteriaId() {
        return this.criteriaId;
    }

    public String getCriteriaName() {
        return this.criteriaName;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Collection<LoanProductData> getLoanProducts() {
        return this.loanProducts;
    }

    public Collection<LoanProductData> getSelectedLoanProducts() {
        return this.selectedLoanProducts;
    }

    public Collection<ProvisioningCriteriaDefinitionData> getDefinitions() {
        return this.definitions;
    }

    public Collection<ProvisioningCategoryData> getCategories() {
        return this.categories;
    }

    public Collection<GLAccountData> getGlAccounts() {
        return this.glAccounts;
    }

    public Long getActiveVersionId() {
        return this.activeVersionId;
    }

    public Integer getVersionNo() {
        return this.versionNo;
    }

    public LocalDate getEffectiveFrom() {
        return this.effectiveFrom;
    }

    @Override
    public int compareTo(ProvisioningCriteriaData obj) {
        if (obj == null) {
            return -1;
        }
        return obj.criteriaId.compareTo(this.criteriaId);
    }
}
