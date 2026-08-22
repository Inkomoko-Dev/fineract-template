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
package org.apache.fineract.portfolio.client.data;

import java.time.LocalDateTime;

public class PartnerClientHistoryData {

    private final Long id;
    private final Long mappingId;
    private final Long clientId;
    private final String partnerCode;
    private final String actionType;
    private final String previousPartnerCode;
    private final String newPartnerCode;
    private final LocalDateTime changedDate;
    private final Long changedById;
    private final String changedByName;
    private final String reason;

    private PartnerClientHistoryData(final Long id, final Long mappingId, final Long clientId, final String partnerCode,
            final String actionType, final String previousPartnerCode, final String newPartnerCode, final LocalDateTime changedDate,
            final Long changedById, final String changedByName, final String reason) {
        this.id = id;
        this.mappingId = mappingId;
        this.clientId = clientId;
        this.partnerCode = partnerCode;
        this.actionType = actionType;
        this.previousPartnerCode = previousPartnerCode;
        this.newPartnerCode = newPartnerCode;
        this.changedDate = changedDate;
        this.changedById = changedById;
        this.changedByName = changedByName;
        this.reason = reason;
    }

    public static PartnerClientHistoryData from(final Long id, final Long mappingId, final Long clientId, final String partnerCode,
            final String actionType, final String previousPartnerCode, final String newPartnerCode, final LocalDateTime changedDate,
            final Long changedById, final String changedByName, final String reason) {
        return new PartnerClientHistoryData(id, mappingId, clientId, partnerCode, actionType, previousPartnerCode, newPartnerCode,
                changedDate, changedById, changedByName, reason);
    }

    public Long getId() {
        return id;
    }

    public Long getMappingId() {
        return mappingId;
    }

    public Long getClientId() {
        return clientId;
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
        return changedById;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public String getReason() {
        return reason;
    }
}
