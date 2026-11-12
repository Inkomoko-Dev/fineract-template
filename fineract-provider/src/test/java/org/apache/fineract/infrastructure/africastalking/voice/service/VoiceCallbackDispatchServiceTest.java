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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingVoiceService;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceCallbackRequestStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequest;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequestRepository;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoiceCallbackDispatchServiceTest {

    @Mock
    private VoiceCallbackRequestRepository callbackRequestRepository;
    @Mock
    private AfricasTalkingVoiceService voiceService;

    private VoiceCallbackDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        dispatchService = new VoiceCallbackDispatchService(callbackRequestRepository, voiceService);
    }

    @Test
    void dispatchesPendingCallbackAsOutboundCall() {
        final VoiceCallbackRequest request = VoiceCallbackRequest.pending("ATV_1", 1L, "+254700000099", null, "AFTER_HOURS", "en", 10L,
                null);
        when(callbackRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(voiceService.initiateOutboundCall(any())).thenReturn(CommandProcessingResult.resourceResult(99L, null));
        when(callbackRequestRepository.save(request)).thenReturn(request);

        final CommandProcessingResult result = dispatchService.dispatchCallback(5L);

        assertThat(result.resourceId()).isEqualTo(99L);
        assertThat(request.getStatus()).isEqualTo(VoiceCallbackRequestStatus.SCHEDULED);
        assertThat(request.getOutboundCallLogId()).isEqualTo(99L);
        verify(voiceService).initiateOutboundCall(any());
    }

    @Test
    void dispatchesDueCallbacksFromScheduledJob() {
        final VoiceCallbackRequest request = VoiceCallbackRequest.pending("ATV_2", 2L, "+254700000088", null, "CALLBACK", "en", 11L, null);
        ReflectionTestUtils.setField(request, "id", 6L);
        when(callbackRequestRepository.findDueForDispatch(any(), any())).thenReturn(List.of(request));
        when(callbackRequestRepository.findById(6L)).thenReturn(Optional.of(request));
        when(voiceService.initiateOutboundCall(any())).thenReturn(CommandProcessingResult.resourceResult(100L, null));
        when(callbackRequestRepository.save(request)).thenReturn(request);

        final int dispatched = dispatchService.dispatchDueCallbacks();

        assertThat(dispatched).isEqualTo(1);
        verify(voiceService).initiateOutboundCall(any());
    }

    @Test
    void continuesScheduledJobWhenSingleCallbackFails() {
        final VoiceCallbackRequest failing = VoiceCallbackRequest.pending("ATV_3", 3L, "+254700000077", null, "CALLBACK", "en", 12L, null);
        final VoiceCallbackRequest succeeding = VoiceCallbackRequest.pending("ATV_4", 4L, "+254700000066", null, "CALLBACK", "en", 13L,
                null);
        ReflectionTestUtils.setField(failing, "id", 7L);
        ReflectionTestUtils.setField(succeeding, "id", 8L);
        when(callbackRequestRepository.findDueForDispatch(any(), any())).thenReturn(List.of(failing, succeeding));
        when(callbackRequestRepository.findById(7L)).thenReturn(Optional.empty());
        when(callbackRequestRepository.findById(8L)).thenReturn(Optional.of(succeeding));
        when(voiceService.initiateOutboundCall(any())).thenReturn(CommandProcessingResult.resourceResult(101L, null));
        when(callbackRequestRepository.save(succeeding)).thenReturn(succeeding);

        final int dispatched = dispatchService.dispatchDueCallbacks();

        assertThat(dispatched).isEqualTo(1);
        verify(voiceService).initiateOutboundCall(any());
    }

    @Test
    void skipsScheduledJobWhenNoCallbacksAreDue() {
        when(callbackRequestRepository.findDueForDispatch(any(), any())).thenReturn(List.of());

        final int dispatched = dispatchService.dispatchDueCallbacks();

        assertThat(dispatched).isZero();
        verify(voiceService, never()).initiateOutboundCall(any());
    }
}
