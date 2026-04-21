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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_provision_category", uniqueConstraints = { @UniqueConstraint(columnNames = { "category_name" }, name = "category_name") })
public class ProvisioningCategory extends AbstractPersistableCustom {

    @Column(name = "category_name", nullable = false, unique = true)
    private String categoryName;

    @Column(name = "description")
    private String categoryDescription;

    @Column(name = "category_code", nullable = false, unique = true)
    private String categoryCode;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ProvisioningCategory() {

    }

    private ProvisioningCategory(String categoryName, String categoryDescription, String categoryCode, Integer displayOrder,
            boolean active) {
        this.categoryName = categoryName;
        this.categoryDescription = categoryDescription;
        this.categoryCode = categoryCode;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public static ProvisioningCategory fromJson(JsonCommand jsonCommand) {
        final String categoryName = jsonCommand.stringValueOfParameterNamed("categoryname");
        final String description = jsonCommand.stringValueOfParameterNamed("categorydescription");
        final String categoryCode = jsonCommand.stringValueOfParameterNamed("categorycode");
        final Integer displayOrder = jsonCommand.integerValueOfParameterNamed("displayorder");
        final Boolean active = jsonCommand.booleanObjectValueOfParameterNamed("active");
        return new ProvisioningCategory(categoryName, description, categoryCode, displayOrder, active == null || active);
    }

    public Map<String, Object> update(JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>(5);
        final String nameParamName = "categoryname";
        if (command.isChangeInStringParameterNamed(nameParamName, this.categoryName)) {
            final String newValue = command.stringValueOfParameterNamed(nameParamName);
            actualChanges.put(nameParamName, newValue);
            this.categoryName = newValue;
        }

        final String descriptionParamName = "categorydescription";
        if (command.isChangeInStringParameterNamed(descriptionParamName, this.categoryDescription)) {
            final String newValue = command.stringValueOfParameterNamed(descriptionParamName);
            actualChanges.put(descriptionParamName, newValue);
            this.categoryDescription = newValue;
        }

        final String codeParamName = "categorycode";
        if (command.isChangeInStringParameterNamed(codeParamName, this.categoryCode)) {
            final String newValue = command.stringValueOfParameterNamed(codeParamName);
            actualChanges.put(codeParamName, newValue);
            this.categoryCode = newValue;
        }

        final String displayOrderParamName = "displayorder";
        if (command.isChangeInIntegerParameterNamed(displayOrderParamName, this.displayOrder)) {
            final Integer newValue = command.integerValueOfParameterNamed(displayOrderParamName);
            actualChanges.put(displayOrderParamName, newValue);
            this.displayOrder = newValue;
        }

        final String activeParamName = "active";
        if (command.isChangeInBooleanParameterNamed(activeParamName, this.active)) {
            final boolean newValue = command.booleanPrimitiveValueOfParameterNamed(activeParamName);
            actualChanges.put(activeParamName, newValue);
            this.active = newValue;
        }
        return actualChanges;
    }

    public String getCategoryName() {
        return this.categoryName;
    }

    public String getCategoryDescription() {
        return this.categoryDescription;
    }

    public String getCategoryCode() {
        return this.categoryCode;
    }

    public Integer getDisplayOrder() {
        return this.displayOrder;
    }

    public boolean isActive() {
        return this.active;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ProvisioningCategory)) {
            return false;
        }
        ProvisioningCategory pc = (ProvisioningCategory) obj;
        return Objects.equals(pc.getCategoryCode(), this.categoryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.categoryCode);
    }
}
