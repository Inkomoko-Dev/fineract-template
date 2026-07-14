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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunicationMessageDispatchServiceTest {

    @Mock
    private AfricasTalkingClient africasTalkingClient;

    @Mock
    private CommunicationMessageRepository communicationMessageRepository;

    @InjectMocks
    private CommunicationMessageDispatchService dispatchService;

    @Test
    void marksMessageSentWhenProviderReturnsSuccess() throws Exception {
        final CommunicationMessage message = CommunicationMessage.pendingOutbound(CommunicationChannel.WHATSAPP, "+254712345678",
                RecipientType.CLIENT, null, null, "Hello");
        when(africasTalkingClient.sendWhatsAppMessage("+254712345678", "Hello"))
                .thenReturn(new AfricasTalkingClient.AfricasTalkingApiResponse(200, "{\"messageId\":\"ATX_1\"}"));

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.SENT, message.getStatus());
        assertEquals("ATX_1", message.getExternalId());
        verify(communicationMessageRepository).save(message);
    }

    @Test
    void dispatchesTemplateMessageWhenTemplateNameSet() throws Exception {
        final CommunicationMessage message = CommunicationMessage.pendingOutboundTemplate("+254712345678", RecipientType.CLIENT, null,
                null, "payment_due_today", "en", "[\"John\",\"1000\"]", "Payment reminder", 5L);
        when(africasTalkingClient.sendWhatsAppTemplate("+254712345678", "payment_due_today", "en", List.of("John", "1000")))
                .thenReturn(new AfricasTalkingClient.AfricasTalkingApiResponse(200, "{\"messageId\":\"ATX_2\"}"));

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.SENT, message.getStatus());
        assertEquals("ATX_2", message.getExternalId());
        verify(africasTalkingClient).sendWhatsAppTemplate("+254712345678", "payment_due_today", "en", List.of("John", "1000"));
        verify(africasTalkingClient, never()).sendWhatsAppMessage(any(), any());
        verify(communicationMessageRepository).save(message);
    }

    @Test
    void dispatchesFreeFormMessageWhenTemplateNameBlank() throws Exception {
        final CommunicationMessage message = CommunicationMessage.pendingOutbound(CommunicationChannel.WHATSAPP, "+254712345678",
                RecipientType.CLIENT, null, null, "Hello");
        when(africasTalkingClient.sendWhatsAppMessage("+254712345678", "Hello"))
                .thenReturn(new AfricasTalkingClient.AfricasTalkingApiResponse(200, "{\"messageId\":\"ATX_3\"}"));

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.SENT, message.getStatus());
        verify(africasTalkingClient).sendWhatsAppMessage("+254712345678", "Hello");
        verify(africasTalkingClient, never()).sendWhatsAppTemplate(any(), any(), any(), anyList());
        verify(communicationMessageRepository).save(message);
    }
}
