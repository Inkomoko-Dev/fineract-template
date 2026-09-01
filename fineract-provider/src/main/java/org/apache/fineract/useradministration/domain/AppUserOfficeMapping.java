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
package org.apache.fineract.useradministration.domain;

import java.util.Objects;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.organisation.office.domain.Office;

@Entity
@Table(name = "m_appuser_office", uniqueConstraints = @UniqueConstraint(columnNames = { "appuser_id",
        "office_id" }, name = "unique_appuser_office"))
public class AppUserOfficeMapping extends AbstractPersistableCustom {

    @ManyToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "appuser_id", nullable = false)
    private AppUser appUser;

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    protected AppUserOfficeMapping() {

    }

    public AppUserOfficeMapping(final AppUser appUser, final Office office) {
        this.appUser = appUser;
        this.office = office;
    }

    public AppUser getAppUser() {
        return this.appUser;
    }

    public Office getOffice() {
        return this.office;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AppUserOfficeMapping)) {
            return false;
        }
        final AppUserOfficeMapping that = (AppUserOfficeMapping) obj;
        return this.office != null && that.office != null && Objects.equals(this.office.getId(), that.office.getId());
    }

    @Override
    public int hashCode() {
        return this.office == null ? 0 : Objects.hashCode(this.office.getId());
    }
}
