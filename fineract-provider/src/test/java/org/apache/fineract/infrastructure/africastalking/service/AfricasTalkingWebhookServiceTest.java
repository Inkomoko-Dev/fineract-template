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

import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEvent;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEventRepository;
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

    private AfricasTalkingWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new AfricasTalkingWebhookService(webhookVerifier, webhookEventRepository);
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
}
