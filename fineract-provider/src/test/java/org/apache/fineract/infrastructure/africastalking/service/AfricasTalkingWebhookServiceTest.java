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
package org.apache.fineract.infrastructure.africastalking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEvent;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEventRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSessionRepository;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AfricasTalkingWebhookServiceTest {

    @Mock
    private AfricasTalkingWebhookVerifier webhookVerifier;

    @Mock
    private CommunicationWebhookEventRepository webhookEventRepository;

    @Mock
    private VoiceIvrSessionRepository voiceIvrSessionRepository;

    private AfricasTalkingWebhookService webhookService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        webhookService = new AfricasTalkingWebhookService(webhookVerifier, webhookEventRepository, voiceIvrSessionRepository);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void extractsEventIdFromJsonPayload() {
        final String payload = "{\"id\":\"ATX_123\",\"status\":\"Success\"}";
        final String eventId = webhookService.extractEventId(payload, AfricasTalkingClient.sha256Hex(payload));
        assertEquals("ATX_123", eventId);
    }

    @Test
    void storesNewWebhookEvent() {
        final String payload = "{\"id\":\"ATX_456\"}";
        when(webhookEventRepository.existsByEventId("ATX_456")).thenReturn(false);
        when(webhookEventRepository.save(any(CommunicationWebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final var result = webhookService.processWebhook("WHATSAPP_STATUS", payload, null, "AfricasTalking/v1");

        assertTrue(!result.duplicate());
        final ArgumentCaptor<CommunicationWebhookEvent> captor = ArgumentCaptor.forClass(CommunicationWebhookEvent.class);
        verify(webhookEventRepository).save(captor.capture());
        assertEquals("ATX_456", captor.getValue().getEventId());
    }

    @Test
    void skipsDuplicateWebhookEvent() {
        final String payload = "{\"id\":\"ATX_789\"}";
        when(webhookEventRepository.existsByEventId("ATX_789")).thenReturn(true);

        final var result = webhookService.processWebhook("WHATSAPP_STATUS", payload, null, "AfricasTalking/v1");

        assertTrue(result.duplicate());
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void masksOtpDigitsInVoiceCallbackEventIdWhileAuthenticating() {
        final String payload = "sessionId=ATV_AUTH&dtmfDigits=654321&callSessionState=Completed";
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.AUTHENTICATING);
        when(voiceIvrSessionRepository.findByExternalSessionIdOrderByLastActivityAtDesc("ATV_AUTH")).thenReturn(List.of(session));

        final String eventId = webhookService.extractEventId(payload, AfricasTalkingClient.sha256Hex(payload));

        assertTrue(eventId.startsWith("ATV_AUTH:OTP:"));
        assertTrue(eventId.endsWith(":Completed"));
        assertTrue(!eventId.contains("654321"));
    }

    @Test
    void distinctAuthDtmfAttemptsProduceDistinctEventIds() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.AUTHENTICATING);
        when(voiceIvrSessionRepository.findByExternalSessionIdOrderByLastActivityAtDesc("ATV_AUTH")).thenReturn(List.of(session));

        final String first = "sessionId=ATV_AUTH&dtmfDigits=111111&callSessionState=Completed";
        final String second = "sessionId=ATV_AUTH&dtmfDigits=222222&callSessionState=Completed";

        final String firstId = webhookService.extractEventId(first, AfricasTalkingClient.sha256Hex(first));
        final String secondId = webhookService.extractEventId(second, AfricasTalkingClient.sha256Hex(second));

        assertTrue(!firstId.equals(secondId));
        assertTrue(!firstId.contains("111111"));
        assertTrue(!secondId.contains("222222"));
    }
}
