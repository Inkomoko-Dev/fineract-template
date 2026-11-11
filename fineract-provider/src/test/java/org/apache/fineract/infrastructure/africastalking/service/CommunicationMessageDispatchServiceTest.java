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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppProvider;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppProviderRouter;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppTransportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunicationMessageDispatchServiceTest {

    @Mock
    private WhatsAppProviderRouter whatsAppProviderRouter;

    @Mock
    private CommunicationMessageRepository communicationMessageRepository;

    @Mock
    private WhatsAppPhoneWhitelistService whatsAppPhoneWhitelistService;

    @Mock
    private AfricasTalkingProperties properties;

    @InjectMocks
    private CommunicationMessageDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        lenient().when(whatsAppPhoneWhitelistService.isAllowed(any())).thenReturn(true);
        final AfricasTalkingProperties.Dispatch dispatch = new AfricasTalkingProperties.Dispatch();
        dispatch.setMaxRetries(3);
        lenient().when(properties.getDispatch()).thenReturn(dispatch);
    }

    @Test
    void marksMessageSentWhenProviderReturnsSuccess() {
        final CommunicationMessage message = CommunicationMessage.pendingOutbound(CommunicationChannel.WHATSAPP, "+254712345678",
                RecipientType.CLIENT, null, null, "Hello");
        when(whatsAppProviderRouter.sendText("+254712345678", "Hello"))
                .thenReturn(WhatsAppTransportResult.success(WhatsAppProvider.META, 200, "{\"messages\":[{\"id\":\"wamid.1\"}]}", "wamid.1"));

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.SENT, message.getStatus());
        assertEquals("wamid.1", message.getExternalId());
        assertEquals("META", message.getProvider());
        verify(communicationMessageRepository).save(message);
    }

    @Test
    void dispatchesTemplateMessageWhenTemplateNameSet() {
        final CommunicationMessage message = CommunicationMessage.pendingOutboundTemplate("+254712345678", RecipientType.CLIENT, null, null,
                "payment_due_today", "en", "[\"John\",\"1000\"]", "Payment reminder", 5L);
        when(whatsAppProviderRouter.sendTemplate(eq("+254712345678"), eq("payment_due_today"), eq("en"), eq(List.of("John", "1000"))))
                .thenReturn(WhatsAppTransportResult.success(WhatsAppProvider.META, 200, "{}", "wamid.2"));

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.SENT, message.getStatus());
        assertEquals("wamid.2", message.getExternalId());
        verify(whatsAppProviderRouter).sendTemplate("+254712345678", "payment_due_today", "en", List.of("John", "1000"));
        verify(whatsAppProviderRouter, never()).sendText(any(), any());
        verify(communicationMessageRepository).save(message);
    }

    @Test
    void dispatchesFreeFormMessageWhenTemplateNameBlank() {
        final CommunicationMessage message = CommunicationMessage.pendingOutbound(CommunicationChannel.WHATSAPP, "+254712345678",
                RecipientType.CLIENT, null, null, "Hello");
        when(whatsAppProviderRouter.sendText("+254712345678", "Hello"))
                .thenReturn(WhatsAppTransportResult.success(WhatsAppProvider.AFRICASTALKING, 200, "{\"messageId\":\"ATX_3\"}", "ATX_3"));

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.SENT, message.getStatus());
        assertEquals("AFRICASTALKING", message.getProvider());
        verify(whatsAppProviderRouter).sendText("+254712345678", "Hello");
        verify(whatsAppProviderRouter, never()).sendTemplate(any(), any(), any(), anyList());
        verify(communicationMessageRepository).save(message);
    }

    @Test
    void marksMessageFailedWhenRecipientNotOnWhitelist() {
        final CommunicationMessage message = CommunicationMessage.pendingOutbound(CommunicationChannel.WHATSAPP, "+254799999999",
                RecipientType.CLIENT, null, null, "Hello");
        when(whatsAppPhoneWhitelistService.isAllowed("+254799999999")).thenReturn(false);

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.FAILED, message.getStatus());
        assertEquals(WhatsAppPhoneWhitelistService.BLOCKED_ERROR_MESSAGE, message.getStatusDetail());
        verify(whatsAppProviderRouter, never()).sendText(any(), any());
        verify(whatsAppProviderRouter, never()).sendTemplate(any(), any(), any(), anyList());
        verify(communicationMessageRepository).save(message);
    }

    @Test
    void marksTemplateFailedWithoutTextFallbackOnClientError() {
        final CommunicationMessage message = CommunicationMessage.pendingOutboundTemplate("+254712345678", RecipientType.CLIENT, null, null,
                "test", "en", "[\"Lucy\"]", "Lucy|1000", 9L);
        when(whatsAppProviderRouter.sendTemplate(eq("+254712345678"), eq("test"), eq("en"), eq(List.of("Lucy")))).thenReturn(
                WhatsAppTransportResult.failure(WhatsAppProvider.AFRICASTALKING, 400, "{\"category\":\"UNKNOWN\"}", "HTTP 400"));

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.FAILED, message.getStatus());
        verify(whatsAppProviderRouter).sendTemplate("+254712345678", "test", "en", List.of("Lucy"));
        verify(whatsAppProviderRouter, never()).sendText(any(), any());
        verify(communicationMessageRepository).save(message);
    }

    @Test
    void failsTemplateDispatchWhenBodyValuesContainBlank() {
        final CommunicationMessage message = CommunicationMessage.pendingOutboundTemplate("+254712345678", RecipientType.CLIENT, null, null,
                "test", "en", "[\"Lucy\",\"\"]", "Lucy|", 9L);

        dispatchService.dispatchMessage(message);

        assertEquals(CommunicationMessageStatus.FAILED, message.getStatus());
        verify(whatsAppProviderRouter, never()).sendTemplate(any(), any(), any(), anyList());
        verify(whatsAppProviderRouter, never()).sendText(any(), any());
        verify(communicationMessageRepository).save(message);
    }
}
