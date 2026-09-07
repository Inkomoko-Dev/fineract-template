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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppInboundConversationServiceStaffDelegationTest {

    @Mock
    private WhatsAppKeywordMatcher keywordMatcher;
    @Mock
    private WhatsAppOptOutService optOutService;
    @Mock
    private WhatsAppConsentService consentService;
    @Mock
    private WhatsAppConversationSessionService sessionService;
    @Mock
    private WhatsAppMenuNavigationService menuNavigationService;
    @Mock
    private WhatsAppInboundMessageRouter messageRouter;
    @Mock
    private WhatsAppClientAuthService clientAuthService;
    @Mock
    private WhatsAppLoanSelfServiceGate loanSelfServiceGate;
    @Mock
    private WhatsAppReplyService replyService;
    @Mock
    private WhatsAppInteractiveSettingsProvider settings;
    @Mock
    private WhatsAppStaffInboundConversationService staffInboundConversationService;

    private WhatsAppInboundConversationService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppInboundConversationService(keywordMatcher, optOutService, consentService, sessionService,
                menuNavigationService, messageRouter, clientAuthService, loanSelfServiceGate, replyService, settings,
                staffInboundConversationService);
    }

    @Test
    void delegatesStaffInboundToStaffConversationService() {
        final Staff staff = org.mockito.Mockito.mock(Staff.class);

        service.handleInbound("+250788123456", "hello", RecipientType.STAFF, null, staff);

        verify(staffInboundConversationService).handleInbound("+250788123456", "hello", RecipientType.STAFF, staff);
        verifyNoInteractions(sessionService, messageRouter, consentService);
    }
}
