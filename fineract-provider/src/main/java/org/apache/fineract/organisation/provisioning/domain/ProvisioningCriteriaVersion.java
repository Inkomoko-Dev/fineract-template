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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "m_provisioning_criteria_version")
public class ProvisioningCriteriaVersion extends AbstractAuditableCustom {

    @ManyToOne(optional = false)
    @JoinColumn(name = "criteria_id", referencedColumnName = "id", nullable = false)
    private ProvisioningCriteria criteria;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "retired_on")
    private LocalDate retiredOn;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "criteriaVersion", orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<ProvisioningCriteriaDefinition> definitions = new HashSet<>();

    protected ProvisioningCriteriaVersion() {}

    public ProvisioningCriteriaVersion(ProvisioningCriteria criteria, Integer versionNo, LocalDate effectiveFrom, LocalDate retiredOn,
            AppUser createdBy, LocalDateTime createdDate, AppUser lastModifiedBy, LocalDateTime lastModifiedDate) {
        this.criteria = criteria;
        this.versionNo = versionNo;
        this.effectiveFrom = effectiveFrom;
        this.retiredOn = retiredOn;
        setCreatedBy(createdBy == null ? null : createdBy.getId());
        setCreatedDate(createdDate);
        setLastModifiedBy(lastModifiedBy == null ? null : lastModifiedBy.getId());
        setLastModifiedDate(lastModifiedDate);
    }

    public ProvisioningCriteria getCriteria() {
        return this.criteria;
    }

    public Integer getVersionNo() {
        return this.versionNo;
    }

    public LocalDate getEffectiveFrom() {
        return this.effectiveFrom;
    }

    public LocalDate getRetiredOn() {
        return this.retiredOn;
    }

    public void retireOn(LocalDate retiredOn) {
        this.retiredOn = retiredOn;
    }

    public void setDefinitions(Set<ProvisioningCriteriaDefinition> definitions) {
        this.definitions.clear();
        this.definitions.addAll(definitions);
    }

    public Set<ProvisioningCriteriaDefinition> getDefinitions() {
        return this.definitions;
    }

    public List<ProvisioningCriteriaDefinition> getDefinitionsInDisplayOrder() {
        List<ProvisioningCriteriaDefinition> ordered = new ArrayList<>(this.definitions);
        ordered.sort((left, right) -> {
            int byDisplayOrder = Integer.compare(left.getDisplayOrder(), right.getDisplayOrder());
            if (byDisplayOrder != 0) {
                return byDisplayOrder;
            }
            return Long.compare(left.getMinimumAge(), right.getMinimumAge());
        });
        return ordered;
    }

    public boolean isEffectiveFor(LocalDate businessDate) {
        if (businessDate == null) {
            return false;
        }
        boolean startsOnOrBeforeDate = !this.effectiveFrom.isAfter(businessDate);
        boolean notRetiredYet = this.retiredOn == null || !this.retiredOn.isBefore(businessDate);
        return startsOnOrBeforeDate && notRetiredYet;
    }
}
