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
package org.apache.fineract.infrastructure.notifications.channel;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingVoiceService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.infrastructure.notifications.data.NotificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceNotificationChannel {

    private final AfricasTalkingVoiceService voiceService;

    @Transactional
    public NotificationResult send(final NotificationCommand command) {
        final JsonObject payload = new JsonObject();
        payload.addProperty("phoneNumber", command.getPhoneNumber());
        if (command.getClient() != null) {
            payload.addProperty("clientId", command.getClient().getId());
        }
        if (command.getStaff() != null) {
            payload.addProperty("staffId", command.getStaff().getId());
        }
        payload.addProperty("callPurpose", StringUtils.defaultIfBlank(command.getCallPurpose(), "NOTIFICATION"));
        payload.addProperty("recordingConsentRequired", command.isRecordingConsentRequired());
        final CommandProcessingResult result = voiceService.initiateOutboundCall(payload.toString());
        return NotificationResult.accepted(result.resourceId());
    }
}
