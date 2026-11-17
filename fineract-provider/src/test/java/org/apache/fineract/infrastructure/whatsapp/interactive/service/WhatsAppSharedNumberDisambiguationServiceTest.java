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
package org.apache.fineract.infrastructure.whatsapp.interactive.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSettingRepository;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppSharedNumberDisambiguationServiceTest {

    @Mock
    private WhatsAppConversationSessionService sessionService;
    @Mock
    private WhatsAppReplyService replyService;
    @Mock
    private WhatsAppInboundConversationService inboundConversationService;
    @Mock
    private WhatsAppStaffInboundConversationService staffInboundConversationService;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;
    @Mock
    private WhatsAppInteractiveSettingRepository settingRepository;

    private WhatsAppSessionContextSerializer contextSerializer;
    private WhatsAppSharedNumberDisambiguationService service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        contextSerializer = new WhatsAppSessionContextSerializer();
        final WhatsAppInteractiveProperties properties = new WhatsAppInteractiveProperties();
        properties.setDefaultLanguage("en");
        final WhatsAppInteractiveSettingsProvider settings = new WhatsAppInteractiveSettingsProvider(properties, settingRepository);
        service = new WhatsAppSharedNumberDisambiguationService(sessionService, contextSerializer, replyService, inboundConversationService,
                staffInboundConversationService, settings, clientRepositoryWrapper, staffRepositoryWrapper);
    }

    @Test
    void firstMultiClientMessageSendsPromptOnly() {
        final WhatsAppConversationSession session = newSession();
        when(sessionService.startOrResume(eq("+254711111111"), eq(WhatsAppConversationType.CLIENT_SELF_SERVICE), isNull(), isNull()))
                .thenReturn(session);
        when(sessionService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.handleAmbiguousInbound("+254711111111", "hello",
                ResolvedRecipientData.multiClient("+254711111111", List.of(10L, 11L)));

        verify(replyService).sendTransactionalReply(eq("+254711111111"), eq(RecipientType.MULTI_CLIENT), isNull(), isNull(),
                eq(WhatsAppInteractiveMessages.sharedNumberMultiClientPrompt("en")));
        verify(inboundConversationService, never()).handleInbound(any(), any(), any(), any(), any());
    }

    @Test
    void multiClientFollowUpWithAccountRoutesToClientInbound() {
        final WhatsAppConversationSession session = newSession();
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setPendingAction(WhatsAppSessionContext.PENDING_DISAMBIGUATE_MULTI_CLIENT);
        context.setCandidateClientIds(List.of(10L, 11L));
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.AWAITING_INPUT);

        when(sessionService.startOrResume(eq("+254711111111"), eq(WhatsAppConversationType.CLIENT_SELF_SERVICE), isNull(), isNull()))
                .thenReturn(session);
        when(sessionService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        final Client client = org.mockito.Mockito.mock(Client.class);
        when(client.getId()).thenReturn(10L);
        when(clientRepositoryWrapper.getClientByAccountNumber("ACC-10")).thenReturn(client);

        service.handleAmbiguousInbound("+254711111111", "ACC-10",
                ResolvedRecipientData.multiClient("+254711111111", List.of(10L, 11L)));

        verify(inboundConversationService).handleInbound(eq("+254711111111"), eq("__DISAMBIGUATED__"), eq(RecipientType.CLIENT), eq(client),
                isNull());
        verify(replyService, never()).sendTransactionalReply(any(), eq(RecipientType.MULTI_CLIENT), any(), any(), any());
    }

    private WhatsAppConversationSession newSession() {
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+254711111111");
        session.setConversationType(WhatsAppConversationType.CLIENT_SELF_SERVICE);
        session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
        session.setSessionContext(contextSerializer.toJson(WhatsAppSessionContext.empty()));
        return session;
    }
}
