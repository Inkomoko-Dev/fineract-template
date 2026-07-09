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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
