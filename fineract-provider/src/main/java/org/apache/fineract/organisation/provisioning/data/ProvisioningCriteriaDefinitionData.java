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
import java.math.BigDecimal;

public final class ProvisioningCriteriaDefinitionData implements Comparable<ProvisioningCriteriaDefinitionData>, Serializable {

    private final Long id;
    private final Long categoryId;
    private final String categoryCode;
    private final String categoryName;
    private final Integer displayOrder;
    private final Long minAge;
    private final Long maxAge;
    private final BigDecimal provisioningPercentage;
    private final Long liabilityAccount;
    private final String liabilityCode;
    private final String liabilityName;
    private final Long expenseAccount;
    private final String expenseCode;
    private final String expenseName;

    public ProvisioningCriteriaDefinitionData(Long id, Long categoryId, String categoryName, Long minAge, Long maxAge,
            BigDecimal provisioningPercentage, Long liabilityAccount, final String liabilityCode, String liabilityName, Long expenseAccount,
            final String expenseCode, final String expenseName) {
        this(id, categoryId, null, categoryName, null, minAge, maxAge, provisioningPercentage, liabilityAccount, liabilityCode,
                liabilityName, expenseAccount, expenseCode, expenseName);
    }

    public ProvisioningCriteriaDefinitionData(Long id, Long categoryId, String categoryCode, String categoryName, Integer displayOrder,
            Long minAge, Long maxAge, BigDecimal provisioningPercentage, Long liabilityAccount, final String liabilityCode,
            String liabilityName, Long expenseAccount, final String expenseCode, final String expenseName) {
        this.id = id;
        this.categoryId = categoryId;
        this.categoryCode = categoryCode;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.provisioningPercentage = provisioningPercentage;
        this.liabilityAccount = liabilityAccount;
        this.expenseAccount = expenseAccount;
        this.categoryName = categoryName;
        this.displayOrder = displayOrder;
        this.liabilityCode = liabilityCode;
        this.expenseCode = expenseCode;
        this.liabilityName = liabilityName;
        this.expenseName = expenseName;
    }

    public static ProvisioningCriteriaDefinitionData template(Long categoryId, String categoryName) {
        return new ProvisioningCriteriaDefinitionData(null, categoryId, null, categoryName, null, null, null, null, null, null, null,
                null, null, null);
    }

    public static ProvisioningCriteriaDefinitionData template(Long categoryId, String categoryCode, String categoryName,
            Integer displayOrder) {
        return new ProvisioningCriteriaDefinitionData(null, categoryId, categoryCode, categoryName, displayOrder, null, null, null, null,
                null, null, null, null, null);
    }

    public Long getId() {
        return this.id;
    }

    public Long getCategoryId() {
        return this.categoryId;
    }

    public String getCategoryName() {
        return this.categoryName;
    }

    public String getCategoryCode() {
        return this.categoryCode;
    }

    public Integer getDisplayOrder() {
        return this.displayOrder;
    }

    public Long getMinAge() {
        return this.minAge;
    }

    public Long getMaxAge() {
        return this.maxAge;
    }

    public BigDecimal getProvisioningPercentage() {
        return this.provisioningPercentage;
    }

    public Long getLiabilityAccount() {
        return this.liabilityAccount;
    }

    public Long getExpenseAccount() {
        return this.expenseAccount;
    }

    public String getLiabilityCode() {
        return this.liabilityCode;
    }

    public String getExpenseCode() {
        return this.expenseCode;
    }

    @Override
    public int compareTo(ProvisioningCriteriaDefinitionData obj) {
        if (obj == null) {
            return -1;
        }
        if (this.displayOrder != null && obj.displayOrder != null && !obj.displayOrder.equals(this.displayOrder)) {
            return obj.displayOrder.compareTo(this.displayOrder);
        }
        if (this.id == null || obj.id == null) {
            return 0;
        }
        return obj.id.compareTo(this.id);
    }

}
