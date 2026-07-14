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
package org.apache.fineract.infrastructure.africastalking.data;

import java.time.LocalDateTime;
import lombok.Getter;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLog;

@Getter
public class VoiceCallLogData {

    private final Long id;
    private final String externalSessionId;
    private final CommunicationDirection direction;
    private final String callerNumber;
    private final String destinationNumber;
    private final Long clientId;
    private final Long staffId;
    private final String status;
    private final Integer durationSeconds;
    private final String recordingUrl;
    private final String dtmfDigits;
    private final LocalDateTime createdDate;

    private VoiceCallLogData(final Long id, final String externalSessionId, final CommunicationDirection direction,
            final String callerNumber, final String destinationNumber, final Long clientId, final Long staffId, final String status,
            final Integer durationSeconds, final String recordingUrl, final String dtmfDigits, final LocalDateTime createdDate) {
        this.id = id;
        this.externalSessionId = externalSessionId;
        this.direction = direction;
        this.callerNumber = callerNumber;
        this.destinationNumber = destinationNumber;
        this.clientId = clientId;
        this.staffId = staffId;
        this.status = status;
        this.durationSeconds = durationSeconds;
        this.recordingUrl = recordingUrl;
        this.dtmfDigits = dtmfDigits;
        this.createdDate = createdDate;
    }

    public static VoiceCallLogData instance(final Long id, final String externalSessionId, final CommunicationDirection direction,
            final String callerNumber, final String destinationNumber, final Long clientId, final Long staffId, final String status,
            final Integer durationSeconds, final String recordingUrl, final String dtmfDigits, final LocalDateTime createdDate) {
        return new VoiceCallLogData(id, externalSessionId, direction, callerNumber, destinationNumber, clientId, staffId, status,
                durationSeconds, recordingUrl, dtmfDigits, createdDate);
    }

    public static VoiceCallLogData fromEntity(final VoiceCallLog callLog) {
        return instance(callLog.getId(), callLog.getExternalSessionId(), callLog.getDirection(), callLog.getCallerNumber(),
                callLog.getDestinationNumber(), callLog.getClient() != null ? callLog.getClient().getId() : null,
                callLog.getStaff() != null ? callLog.getStaff().getId() : null, callLog.getStatus(), callLog.getDurationSeconds(),
                callLog.getRecordingUrl(), callLog.getDtmfDigits(), callLog.getCreatedDate());
    }
}
