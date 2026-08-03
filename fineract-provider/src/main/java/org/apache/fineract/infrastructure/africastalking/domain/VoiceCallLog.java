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
package org.apache.fineract.infrastructure.africastalking.domain;

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
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;

@Entity
@Table(name = "voice_call_log")
@Getter
@Setter
@NoArgsConstructor
public class VoiceCallLog extends AbstractPersistableCustom {

    @Column(name = "external_session_id", length = 100)
    private String externalSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20, nullable = false)
    private CommunicationDirection direction;

    @Column(name = "caller_number", length = 50)
    private String callerNumber;

    @Column(name = "destination_number", length = 50)
    private String destinationNumber;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "recording_url", length = 500)
    private String recordingUrl;

    @Column(name = "dtmf_digits", length = 50)
    private String dtmfDigits;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    public static VoiceCallLog inbound(final String externalSessionId, final String callerNumber, final String destinationNumber,
            final Client client, final Staff staff) {
        final VoiceCallLog callLog = new VoiceCallLog();
        callLog.externalSessionId = externalSessionId;
        callLog.direction = CommunicationDirection.INBOUND;
        callLog.callerNumber = callerNumber;
        callLog.destinationNumber = destinationNumber;
        callLog.client = client;
        callLog.staff = staff;
        callLog.status = AfricasTalkingConstants.CALL_STATUS_RINGING;
        callLog.createdDate = DateUtils.getLocalDateTimeOfTenant();
        return callLog;
    }

    public static VoiceCallLog outbound(final String externalSessionId, final String callerNumber, final String destinationNumber,
            final Client client, final Staff staff) {
        final VoiceCallLog callLog = new VoiceCallLog();
        callLog.externalSessionId = externalSessionId;
        callLog.direction = CommunicationDirection.OUTBOUND;
        callLog.callerNumber = callerNumber;
        callLog.destinationNumber = destinationNumber;
        callLog.client = client;
        callLog.staff = staff;
        callLog.status = AfricasTalkingConstants.CALL_STATUS_INITIATED;
        callLog.createdDate = DateUtils.getLocalDateTimeOfTenant();
        return callLog;
    }

    public void applyEventUpdate(final String status, final Integer durationSeconds, final String recordingUrl, final String dtmfDigits) {
        if (status != null) {
            this.status = status;
        }
        if (durationSeconds != null) {
            this.durationSeconds = durationSeconds;
        }
        if (recordingUrl != null) {
            this.recordingUrl = recordingUrl;
        }
        if (dtmfDigits != null) {
            this.dtmfDigits = dtmfDigits;
        }
    }
}
