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
package org.apache.fineract.portfolio.loanproduct.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "m_disbursement_provider_appuser_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uk_disbursement_provider_appuser", columnNames = { "appuser_id" }) })
public class DisbursementProviderAppUserMapping extends AbstractPersistableCustom {

    @ManyToOne(optional = false)
    @JoinColumn(name = "appuser_id", nullable = false)
    private AppUser appUser;

    @Column(name = "disbursement_provider_code", length = 50, nullable = false)
    private String disbursementProviderCode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected DisbursementProviderAppUserMapping() {}

    public DisbursementProviderAppUserMapping(final AppUser appUser, final String disbursementProviderCode, final boolean active) {
        this.appUser = appUser;
        this.disbursementProviderCode = disbursementProviderCode;
        this.active = active;
    }

    public AppUser getAppUser() {
        return this.appUser;
    }

    public String getDisbursementProviderCode() {
        return this.disbursementProviderCode;
    }

    public boolean isActive() {
        return this.active;
    }
}
