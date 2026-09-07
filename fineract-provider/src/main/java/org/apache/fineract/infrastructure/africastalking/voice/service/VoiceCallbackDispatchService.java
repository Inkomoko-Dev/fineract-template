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
package org.apache.fineract.infrastructure.africastalking.voice.service;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingVoiceService;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceCallbackRequestStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequest;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequestRepository;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceCallbackDispatchService {

    private final VoiceCallbackRequestRepository callbackRequestRepository;
    private final AfricasTalkingVoiceService voiceService;

    @Transactional
    public CommandProcessingResult dispatchCallback(final Long callbackRequestId) {
        final VoiceCallbackRequest request = callbackRequestRepository.findById(callbackRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Voice callback request not found: " + callbackRequestId));
        if (request.getStatus() == VoiceCallbackRequestStatus.COMPLETED || request.getStatus() == VoiceCallbackRequestStatus.CANCELLED) {
            throw new IllegalStateException("Voice callback request is already closed");
        }
        final JsonObject payload = new JsonObject();
        payload.addProperty("phoneNumber", request.getCallerNumber());
        payload.addProperty("callPurpose", "CALLBACK");
        payload.addProperty("recordingConsentRequired", true);
        payload.addProperty("callbackRequestId", callbackRequestId);
        final CommandProcessingResult result = voiceService.initiateOutboundCall(payload.toString());
        request.setStatus(VoiceCallbackRequestStatus.SCHEDULED);
        request.setOutboundCallLogId(result.resourceId());
        request.setLastModifiedDate(DateUtils.getLocalDateTimeOfTenant());
        callbackRequestRepository.save(request);
        return result;
    }
}
