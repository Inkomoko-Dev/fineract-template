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
package org.apache.fineract.organisation.externalmapping.data;

public class ExternalOrganizationMappingData {

    private final Long id;
    private final Long officeId;
    private final String officeName;
    private final String externalSystem;
    private final String externalId;
    private final String externalName;
    private final String currencyCode;
    private final boolean active;

    public ExternalOrganizationMappingData(final Long id, final Long officeId, final String officeName, final String externalSystem,
            final String externalId, final String externalName, final String currencyCode, final boolean active) {
        this.id = id;
        this.officeId = officeId;
        this.officeName = officeName;
        this.externalSystem = externalSystem;
        this.externalId = externalId;
        this.externalName = externalName;
        this.currencyCode = currencyCode;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Long getOfficeId() {
        return officeId;
    }

    public String getOfficeName() {
        return officeName;
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
}
