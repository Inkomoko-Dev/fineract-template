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
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingVoiceService;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceCallbackRequestStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequest;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequestRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.service.VoiceBusinessHoursService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceCallbackDispatchService {

    private static final List<VoiceCallbackRequestStatus> DISPATCHABLE_STATUSES = List.of(VoiceCallbackRequestStatus.PENDING,
            VoiceCallbackRequestStatus.SCHEDULED, VoiceCallbackRequestStatus.RESCHEDULED);
    private static final int OVERDUE_HOURS = 24;

    private final VoiceCallbackRequestRepository callbackRequestRepository;
    private final AfricasTalkingVoiceService voiceService;
    private final VoiceBusinessHoursService businessHoursService;

    @Transactional
    public CommandProcessingResult dispatchCallback(final Long callbackRequestId) {
        if (!businessHoursService.isWithinBusinessHours()) {
            throw new IllegalStateException("Voice callbacks can only be dispatched during business hours");
        }
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
        request.setStatus(VoiceCallbackRequestStatus.IN_PROGRESS);
        request.setOutboundCallLogId(result.resourceId());
        request.setLastModifiedDate(DateUtils.getLocalDateTimeOfTenant());
        callbackRequestRepository.save(request);
        return result;
    }

    public int dispatchDueCallbacks() {
        if (!businessHoursService.isWithinBusinessHours()) {
            log.info("Skipping voice callback dispatch outside business hours");
            escalateOverdueCallbacks();
            return 0;
        }
        escalateOverdueCallbacks();
        final List<VoiceCallbackRequest> dueCallbacks = callbackRequestRepository.findDueForDispatch(DISPATCHABLE_STATUSES,
                DateUtils.getLocalDateTimeOfTenant());
        int dispatched = 0;
        for (final VoiceCallbackRequest request : dueCallbacks) {
            try {
                dispatchCallback(request.getId());
                dispatched++;
            } catch (final RuntimeException ex) {
                log.warn("Failed to dispatch voice callback request {}", request.getId(), ex);
            }
        }
        return dispatched;
    }

    private void escalateOverdueCallbacks() {
        final LocalDateTime cutoff = DateUtils.getLocalDateTimeOfTenant().minusHours(OVERDUE_HOURS);
        final List<VoiceCallbackRequest> dueCallbacks = callbackRequestRepository.findDueForDispatch(DISPATCHABLE_STATUSES, cutoff);
        for (final VoiceCallbackRequest request : dueCallbacks) {
            if (request.getScheduledAt() != null && request.getScheduledAt().isBefore(cutoff)
                    && request.getStatus() != VoiceCallbackRequestStatus.ESCALATED) {
                request.setStatus(VoiceCallbackRequestStatus.ESCALATED);
                request.setLastModifiedDate(DateUtils.getLocalDateTimeOfTenant());
                callbackRequestRepository.save(request);
                log.warn("Voice callback request {} is overdue (scheduledAt={})", request.getId(), request.getScheduledAt());
            }
        }
    }
}
