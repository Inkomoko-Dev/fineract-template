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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AfricasTalkingWhatsAppServiceTest {

    @Mock
    private AfricasTalkingClient africasTalkingClient;
    @Mock
    private CommunicationMessageRepository communicationMessageRepository;
    @Mock
    private RecipientResolutionService recipientResolutionService;
    @Mock
    private PhoneNumberNormalizer phoneNumberNormalizer;
    @Mock
    private CommunicationMessageDispatchService communicationMessageDispatchService;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;
    @Mock
    private FromJsonHelper fromJsonHelper;

    private AfricasTalkingWhatsAppService whatsAppService;

    @BeforeEach
    void setUp() {
        whatsAppService = new AfricasTalkingWhatsAppService(africasTalkingClient, communicationMessageRepository,
                recipientResolutionService, phoneNumberNormalizer, communicationMessageDispatchService, clientRepositoryWrapper,
                staffRepositoryWrapper, fromJsonHelper);
    }

    @Test
    void updatesDeliveredStatusFromWebhook() {
        final CommunicationMessage message = new CommunicationMessage();
        message.setExternalId("ATX_99");
        message.setStatus(CommunicationMessageStatus.SENT);
        when(communicationMessageRepository.findByExternalId("ATX_99")).thenReturn(Optional.of(message));

        whatsAppService.processStatusUpdate("{\"messageId\":\"ATX_99\",\"status\":\"DELIVERED\"}");

        assertEquals(CommunicationMessageStatus.DELIVERED, message.getStatus());
        verify(communicationMessageRepository).save(message);
    }

    @Test
    void storesInboundMessage() {
        when(recipientResolutionService.resolve("+254712345678"))
                .thenReturn(ResolvedRecipientData.client("+254712345678", 12L));
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(12L)).thenReturn(mock(Client.class));
        when(communicationMessageRepository.findByExternalId("ATX_IN_1")).thenReturn(Optional.empty());
        when(communicationMessageRepository.save(any(CommunicationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        whatsAppService.processInboundMessage("{\"from\":\"+254712345678\",\"message\":\"Hi\",\"messageId\":\"ATX_IN_1\"}");

        verify(communicationMessageRepository).save(any(CommunicationMessage.class));
    }
}
