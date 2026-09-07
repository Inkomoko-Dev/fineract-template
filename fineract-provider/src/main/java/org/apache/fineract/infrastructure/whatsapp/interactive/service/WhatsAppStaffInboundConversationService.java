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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppStaffInboundConversationService {

    private final WhatsAppKeywordMatcher keywordMatcher;
    private final WhatsAppOptOutService optOutService;
    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppMenuNavigationService menuNavigationService;
    private final WhatsAppStaffInboundMessageRouter staffMessageRouter;
    private final WhatsAppReplyService replyService;
    private final WhatsAppStaffAccessService staffAccessService;
    private final WhatsAppInteractiveSettingsProvider settings;

    @Transactional
    public void handleInbound(final String phoneNumber, final String messageBody, final RecipientType recipientType, final Staff staff) {
        if (StringUtils.isBlank(phoneNumber) || StringUtils.isBlank(messageBody)) {
            return;
        }
        if (!staffAccessService.canAccessStaffSelfService(staff)) {
            replyService.sendTransactionalReply(phoneNumber, recipientType, null, staff,
                    staff == null ? WhatsAppInteractiveMessages.staffAccessDenied("en")
                            : WhatsAppInteractiveMessages.staffChannelStub());
            return;
        }
        final String normalizedBody = messageBody.trim();
        if (keywordMatcher.matchesOptOut(normalizedBody)) {
            optOutService.recordOptOut(phoneNumber, normalizedBody);
            sessionService.findResumableSession(phoneNumber, WhatsAppConversationType.STAFF).ifPresent(sessionService::markOptedOut);
            replyService.sendTransactionalReply(phoneNumber, recipientType, null, staff, WhatsAppInteractiveMessages.optOutConfirmation());
            return;
        }
        if (keywordMatcher.matchesOptIn(normalizedBody)) {
            handleOptIn(phoneNumber, recipientType, staff);
            return;
        }

        WhatsAppConversationSession session = sessionService.startOrResume(phoneNumber, WhatsAppConversationType.STAFF, null, staff);
        if (handleGlobalNavigation(session, normalizedBody, recipientType, staff)) {
            return;
        }

        switch (session.getSessionStatus()) {
            case LANGUAGE_SELECTION -> session = handleLanguageSelection(session, normalizedBody, recipientType, staff);
            case MAIN_MENU -> staffMessageRouter.routeMenuSelection(session, normalizedBody, recipientType, null, staff);
            case AWAITING_INPUT -> staffMessageRouter.handleAwaitingInput(session, normalizedBody, recipientType, null, staff);
            default -> {
                session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
                session.setCurrentMenuKey("LANGUAGE");
                sessionService.save(session);
                sendLanguagePrompt(phoneNumber, recipientType, staff);
            }
        }
    }

    private boolean handleGlobalNavigation(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Staff staff) {
        if (session.getSessionStatus() == WhatsAppSessionStatus.LANGUAGE_SELECTION) {
            return false;
        }
        if (keywordMatcher.matchesMainMenu(body)) {
            menuNavigationService.navigateToMainMenu(session, recipientType, null, staff);
            return true;
        }
        if (keywordMatcher.matchesBackMenu(body) || "0".equals(body)) {
            menuNavigationService.navigateBack(session, recipientType, null, staff);
            return true;
        }
        return false;
    }

    private void handleOptIn(final String phoneNumber, final RecipientType recipientType, final Staff staff) {
        optOutService.recordOptIn(phoneNumber, "START");
        final WhatsAppConversationSession session = sessionService.startOrResume(phoneNumber, WhatsAppConversationType.STAFF, null, staff);
        session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
        session.setCurrentMenuKey("LANGUAGE");
        sessionService.save(session);
        replyService.sendTransactionalReply(phoneNumber, recipientType, null, staff, WhatsAppInteractiveMessages.staffOptInWelcome());
    }

    private WhatsAppConversationSession handleLanguageSelection(final WhatsAppConversationSession session, final String body,
            final RecipientType recipientType, final Staff staff) {
        final String language = resolveLanguageChoice(body);
        if (language == null) {
            sendLanguagePrompt(session.getPhoneNumber(), recipientType, staff);
            return session;
        }
        session.setLanguageCode(language);
        session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
        session.setCurrentMenuKey(settings.getStaffMainMenuKey());
        sessionService.save(session);
        menuNavigationService.navigateToMenu(session, settings.getStaffMainMenuKey(), recipientType, null, staff, false);
        return session;
    }

    private void sendLanguagePrompt(final String phoneNumber, final RecipientType recipientType, final Staff staff) {
        replyService.sendTransactionalReply(phoneNumber, recipientType, null, staff, WhatsAppInteractiveMessages.welcomeLanguagePrompt());
    }

    private String resolveLanguageChoice(final String body) {
        return switch (body.trim()) {
            case "1" -> "en";
            case "2" -> "rw";
            default -> null;
        };
    }
}
