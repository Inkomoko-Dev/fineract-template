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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppMenuActionType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOption;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppInboundMessageRouterTest {

    @Mock
    private WhatsAppMenuOptionRepository menuOptionRepository;
    @Mock
    private WhatsAppReplyService replyService;
    @Mock
    private WhatsAppContentMessageService contentMessageService;
    @Mock
    private WhatsAppMenuNavigationService menuNavigationService;
    @Mock
    private WhatsAppConversationSessionService sessionService;
    @Mock
    private WhatsAppSessionContextSerializer contextSerializer;
    @Mock
    private WhatsAppInteractiveProperties properties;
    @Mock
    private WhatsAppLoanSelfServiceGate loanSelfServiceGate;

    private WhatsAppInboundMessageRouter router;

    @BeforeEach
    void setUp() {
        router = new WhatsAppInboundMessageRouter(menuOptionRepository, replyService, contentMessageService, menuNavigationService,
                sessionService, contextSerializer, properties, loanSelfServiceGate);
    }

    @Test
    void routesSubmenuSelection() {
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+254712345678");
        session.setCurrentMenuKey("MAIN");
        session.setLanguageCode("en");
        final WhatsAppMenuOption option = new WhatsAppMenuOption();
        option.setActionType(WhatsAppMenuActionType.SUBMENU);
        option.setActionTarget("LOAN");
        when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndOptionNumberAndEnabledTrue("MAIN", "en", 1))
                .thenReturn(java.util.Optional.of(option));

        router.routeMenuSelection(session, "1", RecipientType.CLIENT, null, null);

        verify(menuNavigationService).navigateToMenu(session, "LOAN", RecipientType.CLIENT, null, null, true);
    }

    @Test
    void deliversConfiguredContent() {
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+254712345678");
        session.setCurrentMenuKey("INFO");
        session.setLanguageCode("en");
        final WhatsAppMenuOption option = new WhatsAppMenuOption();
        option.setActionType(WhatsAppMenuActionType.CONTENT);
        option.setActionTarget("TRAINING");
        when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndOptionNumberAndEnabledTrue("INFO", "en", 1))
                .thenReturn(java.util.Optional.of(option));
        when(contentMessageService.resolveBody("TRAINING", "en")).thenReturn("Training info");

        router.routeMenuSelection(session, "1", RecipientType.CLIENT, null, null);

        verify(replyService).sendTransactionalReply(eq("+254712345678"), eq(RecipientType.CLIENT), eq(null), eq(null), any(String.class));
    }

    @Test
    void capturesOtherEnquiryInput() {
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+254712345678");
        session.setLanguageCode("en");
        session.setSessionStatus(WhatsAppSessionStatus.AWAITING_INPUT);
        final org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext ctx =
                org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext.empty();
        ctx.setPendingAction("ADVISOR_OTHER");
        when(contextSerializer.fromJson(null)).thenReturn(ctx);
        when(contextSerializer.toJson(any())).thenReturn("{}");
        when(sessionService.save(session)).thenReturn(session);

        router.handleAwaitingInput(session, "Need help with my loan", RecipientType.CLIENT, null, null);

        verify(sessionService).save(session);
        verify(replyService).sendTransactionalReply(eq("+254712345678"), eq(RecipientType.CLIENT), eq(null), eq(null), any(String.class));
    }
}
