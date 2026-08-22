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
package org.apache.fineract.portfolio.client.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "x_partner_client_mapping")
public class PartnerClientMapping extends AbstractPersistableCustom {

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "partner_code", nullable = false)
    private String partnerCode;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @ManyToOne
    @JoinColumn(name = "assigned_by", nullable = false)
    private AppUser assignedBy;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate;

    protected PartnerClientMapping() {
        // for JPA
    }

    public PartnerClientMapping(final Client client, final String partnerCode, final LocalDate assignedDate, final AppUser assignedBy) {
        this.client = client;
        this.partnerCode = partnerCode;
        this.assignedDate = assignedDate;
        this.assignedBy = assignedBy;
        this.isActive = true;
        this.createdDate = LocalDateTime.now(ZoneId.systemDefault());
        this.updatedDate = LocalDateTime.now(ZoneId.systemDefault());
    }

    public static PartnerClientMapping create(final Client client, final String partnerCode, final AppUser assignedBy) {
        return new PartnerClientMapping(client, partnerCode, LocalDate.now(ZoneId.systemDefault()), assignedBy);
    }

    public void deactivate() {
        this.isActive = false;
        this.updatedDate = LocalDateTime.now(ZoneId.systemDefault());
    }

    public void activate() {
        this.isActive = true;
        this.updatedDate = LocalDateTime.now(ZoneId.systemDefault());
    }

    public void updatePartnerCode(final String newPartnerCode) {
        this.partnerCode = newPartnerCode;
        this.updatedDate = LocalDateTime.now(ZoneId.systemDefault());
    }

    public Long getClientId() {
        return this.client != null ? this.client.getId() : null;
    }

    public Client getClient() {
        return client;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public AppUser getAssignedBy() {
        return assignedBy;
    }

    public Long getAssignedById() {
        return this.assignedBy != null ? this.assignedBy.getId() : null;
    }

    public boolean isActive() {
        return isActive;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }
}
