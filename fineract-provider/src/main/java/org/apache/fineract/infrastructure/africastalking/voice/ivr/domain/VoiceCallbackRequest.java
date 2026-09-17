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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceCallbackRequestStatus;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.client.domain.Client;

@Entity
@Table(name = "voice_callback_request")
@Getter
@Setter
@NoArgsConstructor
public class VoiceCallbackRequest extends AbstractPersistableCustom {

    @Column(name = "external_session_id", length = 100, nullable = false)
    private String externalSessionId;

    @Column(name = "ivr_session_id")
    private Long ivrSessionId;

    @Column(name = "caller_number", length = 50, nullable = false)
    private String callerNumber;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "department_code", length = 50)
    private String departmentCode;

    @Column(name = "language_code", length = 15)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private VoiceCallbackRequestStatus status;

    @Column(name = "support_ticket_id")
    private Long supportTicketId;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "outbound_call_log_id")
    private Long outboundCallLogId;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "lastmodified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    public static VoiceCallbackRequest pending(final String externalSessionId, final Long ivrSessionId, final String callerNumber,
            final Client client, final String departmentCode, final String languageCode, final Long supportTicketId,
            final LocalDateTime scheduledAt) {
        final VoiceCallbackRequest request = new VoiceCallbackRequest();
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        request.externalSessionId = externalSessionId;
        request.ivrSessionId = ivrSessionId;
        request.callerNumber = callerNumber;
        request.client = client;
        request.departmentCode = departmentCode;
        request.languageCode = languageCode;
        request.status = VoiceCallbackRequestStatus.PENDING;
        request.supportTicketId = supportTicketId;
        request.scheduledAt = scheduledAt;
        request.createdDate = now;
        request.lastModifiedDate = now;
        return request;
    }
}
