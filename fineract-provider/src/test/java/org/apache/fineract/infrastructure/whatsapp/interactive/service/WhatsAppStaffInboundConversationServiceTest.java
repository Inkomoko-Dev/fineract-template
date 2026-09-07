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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppStaffInboundConversationServiceTest {

    @Mock
    private WhatsAppKeywordMatcher keywordMatcher;
    @Mock
    private WhatsAppOptOutService optOutService;
    @Mock
    private WhatsAppConversationSessionService sessionService;
    @Mock
    private WhatsAppMenuNavigationService menuNavigationService;
    @Mock
    private WhatsAppStaffInboundMessageRouter staffMessageRouter;
    @Mock
    private WhatsAppReplyService replyService;
    @Mock
    private WhatsAppStaffAccessService staffAccessService;
    @Mock
    private WhatsAppInteractiveSettingsProvider settings;

    private WhatsAppStaffInboundConversationService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppStaffInboundConversationService(keywordMatcher, optOutService, sessionService, menuNavigationService,
                staffMessageRouter, replyService, staffAccessService, settings);
    }

    @Test
    void deniesAccessWhenStaffCannotUseSelfService() {
        final Staff staff = org.mockito.Mockito.mock(Staff.class);
        when(staffAccessService.canAccessStaffSelfService(staff)).thenReturn(false);

        service.handleInbound("+250788123456", "hello", RecipientType.STAFF, staff);

        verify(replyService).sendTransactionalReply(eq("+250788123456"), eq(RecipientType.STAFF), eq(null), eq(staff), any(String.class));
        verify(sessionService, never()).startOrResume(any(), any(), any(), any());
    }

    @Test
    void routesLanguageSelectionToStaffMainMenu() {
        final Staff staff = org.mockito.Mockito.mock(Staff.class);
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+250788123456");
        session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
        when(staffAccessService.canAccessStaffSelfService(staff)).thenReturn(true);
        when(keywordMatcher.matchesOptOut("1")).thenReturn(false);
        when(keywordMatcher.matchesOptIn("1")).thenReturn(false);
        when(sessionService.startOrResume("+250788123456", WhatsAppConversationType.STAFF, null, staff)).thenReturn(session);
        when(settings.getStaffMainMenuKey()).thenReturn("STAFF_MAIN");

        service.handleInbound("+250788123456", "1", RecipientType.STAFF, staff);

        verify(menuNavigationService).navigateToMenu(session, "STAFF_MAIN", RecipientType.STAFF, null, staff, false);
    }
}
