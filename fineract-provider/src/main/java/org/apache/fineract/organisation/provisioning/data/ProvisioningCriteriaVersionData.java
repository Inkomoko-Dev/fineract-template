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
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class ProvisioningCriteriaVersionData implements Serializable {

    private final Long id;
    private final Long criteriaId;
    private final String criteriaName;
    private final Integer versionNo;
    private final LocalDate effectiveFrom;
    private final LocalDate retiredOn;
    private final String policyChangeReason;
    private final String createdBy;
    private final LocalDateTime createdDate;
    private final List<ProvisioningCriteriaDefinitionData> definitions;
    private final ProvisioningCriteriaVersionData previousVersion;

    public ProvisioningCriteriaVersionData(final Long id, final Long criteriaId, final String criteriaName, final Integer versionNo,
            final LocalDate effectiveFrom, final LocalDate retiredOn, final String policyChangeReason, final String createdBy,
            final LocalDateTime createdDate) {
        this(id, criteriaId, criteriaName, versionNo, effectiveFrom, retiredOn, policyChangeReason, createdBy, createdDate, null, null);
    }

    public ProvisioningCriteriaVersionData(final Long id, final Long criteriaId, final String criteriaName, final Integer versionNo,
            final LocalDate effectiveFrom, final LocalDate retiredOn, final String policyChangeReason, final String createdBy,
            final LocalDateTime createdDate, final List<ProvisioningCriteriaDefinitionData> definitions,
            final ProvisioningCriteriaVersionData previousVersion) {
        this.id = id;
        this.criteriaId = criteriaId;
        this.criteriaName = criteriaName;
        this.versionNo = versionNo;
        this.effectiveFrom = effectiveFrom;
        this.retiredOn = retiredOn;
        this.policyChangeReason = policyChangeReason;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.definitions = definitions;
        this.previousVersion = previousVersion;
    }
}
