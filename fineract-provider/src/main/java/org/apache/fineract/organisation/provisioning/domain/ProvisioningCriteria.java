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
package org.apache.fineract.organisation.provisioning.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.audit.AuditChangeRecorder;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;
import org.apache.fineract.organisation.provisioning.constants.ProvisioningCriteriaConstants;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "m_provisioning_criteria", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "criteria_name" }, name = "criteria_name") })
public class ProvisioningCriteria extends AbstractAuditableCustom {

    @Column(name = "criteria_name", nullable = false)
    private String criteriaName;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "criteria", orphanRemoval = true, fetch = FetchType.EAGER)
    Set<ProvisioningCriteriaVersion> provisioningCriteriaVersions = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "criteria", orphanRemoval = true, fetch = FetchType.EAGER)
    Set<LoanProductProvisionCriteria> loanProductMapping = new HashSet<>();

    public String getCriteriaName() {
        return this.criteriaName;
    }

    public void setCriteriaName(String criteriaName) {
        this.criteriaName = criteriaName;
    }

    protected ProvisioningCriteria() {

    }

    public ProvisioningCriteria(String criteriaName, AppUser createdBy, LocalDateTime createdDate, AppUser lastModifiedBy,
            LocalDateTime lastModifiedDate) {
        this.criteriaName = criteriaName;
        setCreatedBy(createdBy.getId());
        setCreatedDate(createdDate);
        setLastModifiedBy(lastModifiedBy.getId());
        setLastModifiedDate(lastModifiedDate);
    }

    public void setProvisioningCriteriaVersions(Set<ProvisioningCriteriaVersion> provisioningCriteriaVersions) {
        this.provisioningCriteriaVersions.clear();
        this.provisioningCriteriaVersions.addAll(provisioningCriteriaVersions);
    }

    public void setLoanProductProvisioningCriteria(Set<LoanProductProvisionCriteria> loanProductMapping) {
        this.loanProductMapping.clear();
        this.loanProductMapping.addAll(loanProductMapping);
    }

    public Map<String, Object> update(JsonCommand command, List<LoanProduct> loanProducts) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>(7);
        if (command.isChangeInStringParameterNamed(ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM, criteriaName)) {
            final String valueAsInput = command.stringValueOfParameterNamed(ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM);
            AuditChangeRecorder.recordChange(actualChanges, ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM, this.criteriaName,
                    valueAsInput);
            this.criteriaName = valueAsInput;
        }

        if (command.parameterExists(ProvisioningCriteriaConstants.JSON_LOANPRODUCTS_PARAM)) {
            final List<Long> beforeProductIds = mappedLoanProductIds();
            Set<LoanProductProvisionCriteria> temp = new HashSet<>();
            Set<LoanProduct> productsTemp = new HashSet<>();

            for (LoanProductProvisionCriteria mapping : loanProductMapping) {
                if (!loanProducts.contains(mapping.getLoanProduct())) {
                    temp.add(mapping);
                } else {
                    productsTemp.add(mapping.getLoanProduct());
                }
            }
            loanProductMapping.removeAll(temp);

            for (LoanProduct loanProduct : loanProducts) {
                if (!productsTemp.contains(loanProduct)) {
                    this.loanProductMapping.add(new LoanProductProvisionCriteria(this, loanProduct));
                }
            }
            final List<Long> afterProductIds = mappedLoanProductIds();
            AuditChangeRecorder.recordChange(actualChanges, ProvisioningCriteriaConstants.JSON_LOANPRODUCTS_PARAM, beforeProductIds,
                    afterProductIds);
        }
        return actualChanges;
    }

    public List<Long> mappedLoanProductIds() {
        return this.loanProductMapping.stream().map(mapping -> mapping.getLoanProduct().getId()).sorted().collect(Collectors.toList());
    }

    public List<Map<String, Object>> snapshotLatestDefinitions() {
        final ProvisioningCriteriaVersion latest = getLatestVersion();
        if (latest == null) {
            return List.of();
        }
        final List<Map<String, Object>> bands = new ArrayList<>();
        for (ProvisioningCriteriaDefinition definition : latest.getDefinitionsInDisplayOrder()) {
            final Map<String, Object> band = new LinkedHashMap<>();
            band.put(ProvisioningCriteriaConstants.JSON_CATEOGRYID_PARAM,
                    definition.getProvisioningCategory() == null ? null : definition.getProvisioningCategory().getId());
            band.put("categoryCode", definition.getCategoryCode());
            band.put(ProvisioningCriteriaConstants.JSON_MINIMUM_AGE_PARAM, definition.getMinimumAge());
            band.put(ProvisioningCriteriaConstants.JSON_MAXIMUM_AGE_PARAM, definition.getMaximumAge());
            band.put(ProvisioningCriteriaConstants.JSON_PROVISIONING_PERCENTAGE_PARAM, definition.getProvisioningPercentage());
            bands.add(band);
        }
        return bands;
    }

    public Map<String, Object> auditSnapshot() {
        final Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put(ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM, this.criteriaName);
        snapshot.put(ProvisioningCriteriaConstants.JSON_LOANPRODUCTS_PARAM, mappedLoanProductIds());
        final ProvisioningCriteriaVersion latest = getLatestVersion();
        if (latest != null) {
            snapshot.put(ProvisioningCriteriaConstants.VERSION_NO_PARAM, latest.getVersionNo());
            snapshot.put(ProvisioningCriteriaConstants.JSON_EFFECTIVE_FROM_PARAM, latest.getEffectiveFrom());
            snapshot.put(ProvisioningCriteriaConstants.JSON_POLICY_CHANGE_REASON_PARAM, latest.getPolicyChangeReason());
        }
        snapshot.put(ProvisioningCriteriaConstants.JSON_PROVISIONING_DEFINITIONS_PARAM, snapshotLatestDefinitions());
        return snapshot;
    }

    public Set<ProvisioningCriteriaVersion> getProvisioningCriteriaVersions() {
        return this.provisioningCriteriaVersions;
    }

    public ProvisioningCriteriaVersion getLatestVersion() {
        return this.provisioningCriteriaVersions.stream().max((left, right) -> Integer.compare(left.getVersionNo(), right.getVersionNo()))
                .orElse(null);
    }
}
