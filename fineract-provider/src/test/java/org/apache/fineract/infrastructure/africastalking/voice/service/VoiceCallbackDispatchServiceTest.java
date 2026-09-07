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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
