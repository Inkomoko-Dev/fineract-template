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

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PartnerClientData {

    private final Long id;
    private final String accountNo;
    private final String displayName;
    private final String firstname;
    private final String lastname;
    private final String mobileNo;
    private final String emailAddress;
    private final Integer status;
    private final LocalDate activationDate;
    private final LocalDate officeJoiningDate;
    private final Long officeId;
    private final String officeName;
    private final String partnerCode;
    private final LocalDate assignedDate;
    private final Long assignedById;
    private final String assignedByName;
    private final boolean isActive;
    private final LocalDateTime createdDate;
    private final LocalDateTime updatedDate;

    // History removed - handled separately via dedicated history endpoint

    public PartnerClientData(final Long id, final String accountNo, final String displayName, final String firstname,
            final String lastname, final String mobileNo, final String emailAddress, final Integer status, final LocalDate activationDate,
            final LocalDate officeJoiningDate, final Long officeId, final String officeName, final String partnerCode,
            final LocalDate assignedDate, final Long assignedById, final String assignedByName, final boolean isActive,
            final LocalDateTime createdDate, final LocalDateTime updatedDate) {
        this.id = id;
        this.accountNo = accountNo;
        this.displayName = displayName;
        this.firstname = firstname;
        this.lastname = lastname;
        this.mobileNo = mobileNo;
        this.emailAddress = emailAddress;
        this.status = status;
        this.activationDate = activationDate;
        this.officeJoiningDate = officeJoiningDate;
        this.officeId = officeId;
        this.officeName = officeName;
        this.partnerCode = partnerCode;
        this.assignedDate = assignedDate;
        this.assignedById = assignedById;
        this.assignedByName = assignedByName;
        this.isActive = isActive;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public Long getId() {
        return id;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public Integer getStatus() {
        return status;
    }

    public LocalDate getActivationDate() {
        return activationDate;
    }

    public LocalDate getOfficeJoiningDate() {
        return officeJoiningDate;
    }

    public Long getOfficeId() {
        return officeId;
    }

    public String getOfficeName() {
        return officeName;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public Long getAssignedById() {
        return assignedById;
    }

    public String getAssignedByName() {
        return assignedByName;
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
