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
package org.apache.fineract.infrastructure.whatsapp.interactive.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.client.domain.Client;

@Entity
@Table(name = "whatsapp_consent_record")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppConsentRecord extends AbstractPersistableCustom {

    @Column(name = "phone_number", length = 50, nullable = false)
    private String phoneNumber;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "consent_granted", nullable = false)
    private boolean consentGranted;

    @Column(name = "consent_source", length = 50)
    private String consentSource;

    @Column(name = "terms_version", length = 50)
    private String termsVersion;

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    public static WhatsAppConsentRecord record(final String phoneNumber, final Client client, final boolean granted, final String source,
            final String termsVersion, final Long recordedByUserId) {
        final WhatsAppConsentRecord record = new WhatsAppConsentRecord();
        record.phoneNumber = phoneNumber;
        record.client = client;
        record.consentGranted = granted;
        record.consentSource = source;
        record.termsVersion = termsVersion;
        record.recordedByUserId = recordedByUserId;
        record.createdDate = DateUtils.getLocalDateTimeOfTenant();
        return record;
    }
}
