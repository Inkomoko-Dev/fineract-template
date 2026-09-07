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
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceVoicemailStatus;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.client.domain.Client;

@Entity
@Table(name = "voice_voicemail")
@Getter
@Setter
@NoArgsConstructor
public class VoiceVoicemail extends AbstractPersistableCustom {

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

    @Column(name = "recording_url", length = 500)
    private String recordingUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private VoiceVoicemailStatus status;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    public static VoiceVoicemail pending(final String externalSessionId, final Long ivrSessionId, final String callerNumber,
            final Client client, final String departmentCode) {
        final VoiceVoicemail voicemail = new VoiceVoicemail();
        voicemail.externalSessionId = externalSessionId;
        voicemail.ivrSessionId = ivrSessionId;
        voicemail.callerNumber = callerNumber;
        voicemail.client = client;
        voicemail.departmentCode = departmentCode;
        voicemail.status = VoiceVoicemailStatus.PENDING;
        voicemail.createdDate = DateUtils.getLocalDateTimeOfTenant();
        return voicemail;
    }
}
