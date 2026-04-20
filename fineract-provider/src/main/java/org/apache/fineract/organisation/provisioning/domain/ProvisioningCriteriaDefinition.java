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

import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_provisioning_criteria_definition")
public class ProvisioningCriteriaDefinition extends AbstractPersistableCustom {

    @ManyToOne(optional = false)
    @JoinColumn(name = "criteria_id", referencedColumnName = "id", nullable = false)
    private ProvisioningCriteria criteria;

    @ManyToOne(optional = false)
    @JoinColumn(name = "criteria_version_id", referencedColumnName = "id", nullable = false)
    private ProvisioningCriteriaVersion criteriaVersion;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private ProvisioningCategory provisioningCategory;

    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "min_age", nullable = false)
    private Long minimumAge;

    @Column(name = "max_age")
    private Long maximumAge;

    @Column(name = "provision_percentage", nullable = false)
    private BigDecimal provisioningPercentage;

    @ManyToOne
    @JoinColumn(name = "liability_account", nullable = false)
    private GLAccount liabilityAccount;

    @ManyToOne
    @JoinColumn(name = "expense_account", nullable = false)
    private GLAccount expenseAccount;

    protected ProvisioningCriteriaDefinition() {

    }

    private ProvisioningCriteriaDefinition(ProvisioningCriteria criteria, ProvisioningCriteriaVersion criteriaVersion,
            ProvisioningCategory provisioningCategory, Long minimumAge, Long maximumAge, BigDecimal provisioningPercentage,
            GLAccount liabilityAccount, GLAccount expenseAccount) {
        this.criteria = criteria;
        this.criteriaVersion = criteriaVersion;
        this.provisioningCategory = provisioningCategory;
        this.categoryCode = provisioningCategory.getCategoryCode();
        this.categoryName = provisioningCategory.getCategoryName();
        this.displayOrder = provisioningCategory.getDisplayOrder();
        this.minimumAge = minimumAge;
        this.maximumAge = maximumAge;
        this.provisioningPercentage = provisioningPercentage;
        this.liabilityAccount = liabilityAccount;
        this.expenseAccount = expenseAccount;
    }

    public static ProvisioningCriteriaDefinition newProvisioningCriteriaDefinition(ProvisioningCriteria criteria,
            ProvisioningCriteriaVersion criteriaVersion, ProvisioningCategory provisioningCategory, Long minimumAge, Long maximumAge,
            BigDecimal provisioningPercentage, GLAccount liabilityAccount, GLAccount expenseAccount) {

        return new ProvisioningCriteriaDefinition(criteria, criteriaVersion, provisioningCategory, minimumAge, maximumAge,
                provisioningPercentage, liabilityAccount, expenseAccount);
    }

    public static ProvisioningCriteriaDefinition newPrivisioningCriteria(ProvisioningCriteria criteria,
            ProvisioningCriteriaVersion criteriaVersion, ProvisioningCategory provisioningCategory, Long minimumAge, Long maximumAge,
            BigDecimal provisioningPercentage, GLAccount liabilityAccount, GLAccount expenseAccount) {
        return new ProvisioningCriteriaDefinition(criteria, criteriaVersion, provisioningCategory, minimumAge, maximumAge,
                provisioningPercentage, liabilityAccount, expenseAccount);
    }

    public ProvisioningCategory getProvisioningCategory() {
        return this.provisioningCategory;
    }

    public String getCategoryCode() {
        return this.categoryCode;
    }

    public String getCategoryName() {
        return this.categoryName;
    }

    public Integer getDisplayOrder() {
        return this.displayOrder;
    }

    public Long getMinimumAge() {
        return this.minimumAge;
    }

    public Long getMaximumAge() {
        return this.maximumAge;
    }

    public BigDecimal getProvisioningPercentage() {
        return this.provisioningPercentage;
    }

    public GLAccount getLiabilityAccount() {
        return this.liabilityAccount;
    }

    public GLAccount getExpenseAccount() {
        return this.expenseAccount;
    }

    public ProvisioningCriteriaVersion getCriteriaVersion() {
        return this.criteriaVersion;
    }

    public boolean matches(Long overdueInDays) {
        if (overdueInDays == null) {
            return false;
        }
        if (this.maximumAge == null) {
            return this.minimumAge <= overdueInDays;
        }
        return this.minimumAge <= overdueInDays && overdueInDays <= this.maximumAge;
    }

    public boolean isOverlapping(ProvisioningCriteriaDefinition def) {
        long thisMaximumAge = this.maximumAge == null ? Long.MAX_VALUE : this.maximumAge;
        long otherMaximumAge = def.maximumAge == null ? Long.MAX_VALUE : def.maximumAge;
        return this.minimumAge <= otherMaximumAge && def.minimumAge <= thisMaximumAge;
    }
}
