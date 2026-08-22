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

import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "x_partner_client_mapping_history")
public class PartnerClientMappingHistory extends AbstractPersistableCustom {

    @ManyToOne
    @JoinColumn(name = "mapping_id", nullable = false)
    private PartnerClientMapping mapping;

    @OneToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "partner_code", nullable = false)
    private String partnerCode;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "previous_partner_code")
    private String previousPartnerCode;

    @Column(name = "new_partner_code")
    private String newPartnerCode;

    @Column(name = "changed_date", nullable = false)
    private LocalDateTime changedDate;

    @ManyToOne
    @JoinColumn(name = "changed_by", nullable = false)
    private AppUser changedBy;

    @Column(name = "reason")
    private String reason;

    protected PartnerClientMappingHistory() {
        // for JPA
    }

    public PartnerClientMappingHistory(final PartnerClientMapping mapping, final Client client, final String partnerCode,
            final String actionType, final String previousPartnerCode, final String newPartnerCode, final AppUser changedBy,
            final String reason) {
        this.mapping = mapping;
        this.client = client;
        this.partnerCode = partnerCode;
        this.actionType = actionType;
        this.previousPartnerCode = previousPartnerCode;
        this.newPartnerCode = newPartnerCode;
        this.changedDate = LocalDateTime.now(ZoneId.systemDefault());
        this.changedBy = changedBy;
        this.reason = reason;
    }

    public static PartnerClientMappingHistory createAssignment(final PartnerClientMapping mapping, final AppUser changedBy,
            final String reason) {
        return new PartnerClientMappingHistory(mapping, mapping.getClient(), mapping.getPartnerCode(), "ASSIGN", null,
                mapping.getPartnerCode(), changedBy, reason);
    }

    public static PartnerClientMappingHistory createReassignment(final PartnerClientMapping mapping, final String previousPartnerCode,
            final String newPartnerCode, final AppUser changedBy, final String reason) {
        return new PartnerClientMappingHistory(mapping, mapping.getClient(), newPartnerCode, "REASSIGN", previousPartnerCode,
                newPartnerCode, changedBy, reason);
    }

    public static PartnerClientMappingHistory createDeactivation(final PartnerClientMapping mapping, final AppUser changedBy,
            final String reason) {
        return new PartnerClientMappingHistory(mapping, mapping.getClient(), mapping.getPartnerCode(), "DEACTIVATE",
                mapping.getPartnerCode(), null, changedBy, reason);
    }

    public Long getMappingId() {
        return this.mapping != null ? this.mapping.getId() : null;
    }

    public Long getClientId() {
        return this.client != null ? this.client.getId() : null;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public String getActionType() {
        return actionType;
    }

    public String getPreviousPartnerCode() {
        return previousPartnerCode;
    }

    public String getNewPartnerCode() {
        return newPartnerCode;
    }

    public LocalDateTime getChangedDate() {
        return changedDate;
    }

    public Long getChangedById() {
        return this.changedBy != null ? this.changedBy.getId() : null;
    }

    public AppUser getChangedBy() {
        return changedBy;
    }

    public String getChangedByName() {
        return this.changedBy != null ? this.changedBy.getUsername() : null;
    }

    public String getReason() {
        return reason;
    }
}
