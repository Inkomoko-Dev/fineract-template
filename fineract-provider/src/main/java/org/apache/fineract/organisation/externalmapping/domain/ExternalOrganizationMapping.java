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
package org.apache.fineract.organisation.externalmapping.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_external_organization_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ext_org_mapping_office_system", columnNames = { "office_id", "external_system" }) })
public class ExternalOrganizationMapping extends AbstractPersistableCustom {

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "external_system", nullable = false, length = 50)
    private String externalSystem;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "external_name", nullable = false, length = 150)
    private String externalName;

    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ExternalOrganizationMapping() {}

    public ExternalOrganizationMapping(final Long officeId, final String externalSystem, final String externalId,
            final String externalName, final String currencyCode, final boolean active) {
        this.officeId = officeId;
        this.externalSystem = externalSystem;
        this.externalId = externalId;
        this.externalName = externalName;
        this.currencyCode = currencyCode;
        this.active = active;
    }

    public void update(final Long officeId, final String externalSystem, final String externalId, final String externalName,
            final String currencyCode, final boolean active) {
        this.officeId = officeId;
        this.externalSystem = externalSystem;
        this.externalId = externalId;
        this.externalName = externalName;
        this.currencyCode = currencyCode;
        this.active = active;
    }

    public Long getOfficeId() {
        return officeId;
    }

    public String getExternalSystem() {
        return externalSystem;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getExternalName() {
        return externalName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public boolean isActive() {
        return active;
    }

    public String groupingKey() {
        final String currency = currencyCode == null || currencyCode.isBlank() ? "" : currencyCode;
        return externalId + "::" + currency;
    }
}
