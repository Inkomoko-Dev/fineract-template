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
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "x_partner_client_verification_audit")
public class PartnerClientVerificationAudit extends AbstractPersistableCustom {

    @Column(name = "national_id_masked")
    private String nationalIdMasked;

    @Column(name = "phone_number_masked")
    private String phoneNumberMasked;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "source_system")
    private String sourceSystem;

    @Column(name = "client_account_no")
    private String clientAccountNo;

    @Column(name = "is_registered")
    private Boolean isRegistered;

    @Column(name = "verification_status")
    private String verificationStatus;

    @Column(name = "eligibility_status")
    private String eligibilityStatus;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "verification_timestamp")
    private LocalDateTime verificationTimestamp;

    @Column(name = "tenant_id")
    private String tenantId;

    protected PartnerClientVerificationAudit() {
        // for JPA
    }

    public PartnerClientVerificationAudit(final String nationalIdMasked, final String phoneNumberMasked,
            final String fullName, final String sourceSystem, final String clientAccountNo,
            final Boolean isRegistered, final String verificationStatus, final String eligibilityStatus,
            final String remarks, final String tenantId) {
        this.nationalIdMasked = nationalIdMasked;
        this.phoneNumberMasked = phoneNumberMasked;
        this.fullName = fullName;
        this.sourceSystem = sourceSystem;
        this.clientAccountNo = clientAccountNo;
        this.isRegistered = isRegistered;
        this.verificationStatus = verificationStatus;
        this.eligibilityStatus = eligibilityStatus;
        this.remarks = remarks;
        this.verificationTimestamp = LocalDateTime.now(ZoneId.systemDefault());
        this.tenantId = tenantId;
    }

    public static PartnerClientVerificationAudit create(final String nationalIdMasked, final String phoneNumberMasked,
            final String fullName, final String sourceSystem, final String clientAccountNo,
            final Boolean isRegistered, final String verificationStatus, final String eligibilityStatus,
            final String remarks, final String tenantId) {
        return new PartnerClientVerificationAudit(nationalIdMasked, phoneNumberMasked, fullName, sourceSystem,
                clientAccountNo, isRegistered, verificationStatus, eligibilityStatus, remarks, tenantId);
    }

    public String getNationalIdMasked() {
        return nationalIdMasked;
    }

    public String getPhoneNumberMasked() {
        return phoneNumberMasked;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getClientAccountNo() {
        return clientAccountNo;
    }

    public Boolean getIsRegistered() {
        return isRegistered;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public String getEligibilityStatus() {
        return eligibilityStatus;
    }

    public String getRemarks() {
        return remarks;
    }

    public LocalDateTime getVerificationTimestamp() {
        return verificationTimestamp;
    }

    public String getTenantId() {
        return tenantId;
    }
}