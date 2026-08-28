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
package org.apache.fineract.accounting.provisioning.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;

@SuppressWarnings("unused")
public class ProvisioningEntryData {

    private Long id;

    private Boolean journalEntry;

    private Long createdById;

    private String createdUser;

    LocalDate createdDate;

    Long modifiedById;

    private String modifiedUser;

    private BigDecimal reservedAmount;

    private LocalDate provisioningDate;

    private LocalDateTime executedAt;

    private String runScope;

    private Collection<ProvisioningEntryCriteriaVersionData> criteriaVersions;

    private Collection<LoanProductProvisioningEntryData> provisioningEntries;

    public ProvisioningEntryData(final Long id, final Collection<LoanProductProvisioningEntryData> provisioningEntries) {
        this.provisioningEntries = provisioningEntries;
        this.id = id;
    }

    public ProvisioningEntryData(Long id, Boolean journalEntry, Long createdById, String createdUser, LocalDate createdDate,
            Long modifiedById, String modifiedUser, BigDecimal totalReservedAmount) {
        this(id, journalEntry, createdById, createdUser, createdDate, modifiedById, modifiedUser, totalReservedAmount, createdDate, null,
                null, null);
    }

    public ProvisioningEntryData(Long id, Boolean journalEntry, Long createdById, String createdUser, LocalDate createdDate,
            Long modifiedById, String modifiedUser, BigDecimal totalReservedAmount, LocalDate provisioningDate, LocalDateTime executedAt,
            String runScope, Collection<ProvisioningEntryCriteriaVersionData> criteriaVersions) {
        this.id = id;
        this.journalEntry = journalEntry;
        this.createdById = createdById;
        this.createdUser = createdUser;
        this.modifiedById = modifiedById;
        this.modifiedUser = modifiedUser;
        this.createdDate = createdDate;
        this.reservedAmount = totalReservedAmount;
        this.provisioningDate = provisioningDate;
        this.executedAt = executedAt;
        this.runScope = runScope;
        this.criteriaVersions = criteriaVersions;
    }

    public void setEntries(Collection<LoanProductProvisioningEntryData> provisioningEntries) {
        this.provisioningEntries = provisioningEntries;
    }

    public Long getId() {
        return this.id;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Boolean getJournalEntry() {
        return this.journalEntry;
    }

    public Boolean isJournalEntry() {
        return this.journalEntry;
    }

    public String getCreatedUser() {
        return this.createdUser;
    }

    public Long getCreatedById() {
        return this.createdById;
    }

    public Long getModifiedById() {
        return this.modifiedById;
    }

    public String getModifiedUser() {
        return this.modifiedUser;
    }

    public BigDecimal getReservedAmount() {
        return this.reservedAmount;
    }

    public LocalDate getProvisioningDate() {
        return this.provisioningDate;
    }

    public LocalDateTime getExecutedAt() {
        return this.executedAt;
    }

    public String getRunScope() {
        return this.runScope;
    }

    public Collection<ProvisioningEntryCriteriaVersionData> getCriteriaVersions() {
        return this.criteriaVersions;
    }

    public Collection<LoanProductProvisioningEntryData> getProvisioningEntries() {
        return this.provisioningEntries;
    }
}
