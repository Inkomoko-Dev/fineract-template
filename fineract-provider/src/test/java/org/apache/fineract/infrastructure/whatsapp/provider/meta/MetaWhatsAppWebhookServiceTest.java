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
package org.apache.fineract.infrastructure.whatsapp.provider.meta;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEventRepository;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.africastalking.service.RecipientResolutionService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInboundConversationService;
import org.apache.fineract.infrastructure.whatsapp.provider.CommunicationMessageStatusUpdater;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppWebhookServiceTest {

    @Mock
    private CommunicationMessageRepository communicationMessageRepository;
    @Mock
    private CommunicationWebhookEventRepository webhookEventRepository;
    @Mock
    private RecipientResolutionService recipientResolutionService;
    @Mock
    private PhoneNumberNormalizer phoneNumberNormalizer;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;
    @Mock
    private WhatsAppInboundConversationService inboundConversationService;
    @Mock
    private CommunicationMessageStatusUpdater statusUpdater;

    @InjectMocks
    private MetaWhatsAppWebhookService service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
    }

    @Test
    void processesTextInboundAndStatusCallbacks() {
        when(phoneNumberNormalizer.normalize("+254712345678")).thenReturn("+254712345678");
        when(recipientResolutionService.resolve("+254712345678"))
                .thenReturn(ResolvedRecipientData.unknown("+254712345678"));
        when(communicationMessageRepository.findByExternalId("wamid.in")).thenReturn(Optional.empty());
        when(webhookEventRepository.existsByEventId("META_INBOUND:wamid.in")).thenReturn(false);
        when(webhookEventRepository.existsByEventId("META_STATUS:wamid.out:DELIVERED")).thenReturn(false);
        when(statusUpdater.applyProviderStatus("wamid.out", "delivered", null)).thenReturn(true);

        final String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "254712345678",
                          "id": "wamid.in",
                          "type": "text",
                          "text": { "body": "1" }
                        }],
                        "statuses": [{
                          "id": "wamid.out",
                          "status": "delivered"
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.processWebhookPayload(payload);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository).save(captor.capture());
        verify(inboundConversationService).handleInbound(eq("+254712345678"), eq("1"), eq(RecipientType.UNKNOWN), any(), any());
        verify(statusUpdater).applyProviderStatus("wamid.out", "delivered", null);
        verify(webhookEventRepository, atLeastOnce()).save(any());
        org.junit.jupiter.api.Assertions.assertEquals("META", captor.getValue().getProvider());
    }

    @Test
    void doesNotClaimStatusEventWhenOutboundMessageMissing() {
        when(webhookEventRepository.existsByEventId("META_STATUS:wamid.missing:DELIVERED")).thenReturn(false);
        when(statusUpdater.applyProviderStatus("wamid.missing", "delivered", null)).thenReturn(false);

        final String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "statuses": [{
                          "id": "wamid.missing",
                          "status": "delivered"
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.processWebhookPayload(payload);

        verify(statusUpdater).applyProviderStatus("wamid.missing", "delivered", null);
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void ignoresDuplicateInboundByExternalId() {
        when(webhookEventRepository.existsByEventId("META_INBOUND:wamid.dup")).thenReturn(true);

        final String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "254712345678",
                          "id": "wamid.dup",
                          "type": "text",
                          "text": { "body": "hi" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.processWebhookPayload(payload);

        verify(communicationMessageRepository, never()).save(any());
        verify(inboundConversationService, never()).handleInbound(any(), any(), any(), any(), any());
    }
}
