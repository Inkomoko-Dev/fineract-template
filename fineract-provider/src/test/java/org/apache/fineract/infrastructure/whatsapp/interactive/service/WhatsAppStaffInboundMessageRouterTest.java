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
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOption;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppStaffInboundMessageRouterTest {

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
    private WhatsAppInteractiveSettingsProvider settings;
    @Mock
    private WhatsAppAdvisorEscalationService advisorEscalationService;

    private WhatsAppStaffInboundMessageRouter router;

    @BeforeEach
    void setUp() {
        router = new WhatsAppStaffInboundMessageRouter(menuOptionRepository, replyService, contentMessageService, menuNavigationService,
                sessionService, contextSerializer, settings, advisorEscalationService);
    }

    @Test
    void blocksLoanServiceActions() {
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+250788123456");
        session.setCurrentMenuKey("STAFF_MAIN");
        session.setLanguageCode("en");
        final WhatsAppMenuOption option = new WhatsAppMenuOption();
        option.setActionType(WhatsAppMenuActionType.LOAN_SERVICE);
        option.setActionTarget("LOAN_BALANCE");
        when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndOptionNumberAndEnabledTrue("STAFF_MAIN", "en", 1))
                .thenReturn(java.util.Optional.of(option));

        router.routeMenuSelection(session, "1", RecipientType.STAFF, null, null);

        verify(replyService).sendTransactionalReply(eq("+250788123456"), eq(RecipientType.STAFF), eq(null), eq(null), any(String.class));
    }

    @Test
    void deliversStaffContent() {
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+250788123456");
        session.setCurrentMenuKey("STAFF_MAIN");
        session.setLanguageCode("en");
        final WhatsAppMenuOption option = new WhatsAppMenuOption();
        option.setActionType(WhatsAppMenuActionType.CONTENT);
        option.setActionTarget("STAFF_HR");
        when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndOptionNumberAndEnabledTrue("STAFF_MAIN", "en", 1))
                .thenReturn(java.util.Optional.of(option));
        when(contentMessageService.resolveBody("STAFF_HR", "en")).thenReturn("HR contact details");

        router.routeMenuSelection(session, "1", RecipientType.STAFF, null, null);

        verify(replyService).sendTransactionalReply(eq("+250788123456"), eq(RecipientType.STAFF), eq(null), eq(null), any(String.class));
    }
}
