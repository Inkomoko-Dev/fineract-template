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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppInboundConversationService {

    private final WhatsAppKeywordMatcher keywordMatcher;
    private final WhatsAppOptOutService optOutService;
    private final WhatsAppConsentService consentService;
    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppMenuNavigationService menuNavigationService;
    private final WhatsAppInboundMessageRouter messageRouter;
    private final WhatsAppClientAuthService clientAuthService;
    private final WhatsAppLoanSelfServiceGate loanSelfServiceGate;
    private final WhatsAppReplyService replyService;
    private final WhatsAppInteractiveProperties properties;

    @Transactional
    public void handleInbound(final String phoneNumber, final String messageBody, final RecipientType recipientType, final Client client,
            final Staff staff) {
        if (StringUtils.isBlank(phoneNumber) || StringUtils.isBlank(messageBody)) {
            return;
        }
        final String normalizedBody = messageBody.trim();
        final WhatsAppConversationType conversationType = WhatsAppConversationType.fromRecipient(recipientType == RecipientType.STAFF);

        if (keywordMatcher.matchesOptOut(normalizedBody)) {
            handleOptOut(phoneNumber, normalizedBody, recipientType, client, staff);
            return;
        }
        if (keywordMatcher.matchesOptIn(normalizedBody)) {
            handleOptIn(phoneNumber, normalizedBody, recipientType, client, staff, conversationType);
            return;
        }

        if (conversationType == WhatsAppConversationType.STAFF) {
            replyService.sendTransactionalReply(phoneNumber, recipientType, client, staff, WhatsAppInteractiveMessages.staffChannelStub());
            return;
        }

        WhatsAppConversationSession session = sessionService.startOrResume(phoneNumber, conversationType, client, staff);
        if (handleGlobalNavigation(session, normalizedBody, recipientType, client, staff)) {
            return;
        }

        switch (session.getSessionStatus()) {
            case LANGUAGE_SELECTION -> session = handleLanguageSelection(session, normalizedBody, recipientType, client, staff);
            case CONSENT_PENDING -> session = handleConsent(session, normalizedBody, recipientType, client, staff);
            case MAIN_MENU -> messageRouter.routeMenuSelection(session, normalizedBody, recipientType, client, staff);
            case AWAITING_INPUT -> messageRouter.handleAwaitingInput(session, normalizedBody, recipientType, client, staff);
            case AUTHENTICATING -> clientAuthService.handleAuthenticating(session, normalizedBody, recipientType, client, staff);
            case AUTHENTICATED -> loanSelfServiceGate.handleAuthenticatedFollowUp(session, recipientType, client, staff);
            default -> {
                session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
                session.setCurrentMenuKey("LANGUAGE");
                sessionService.save(session);
                sendLanguagePrompt(phoneNumber, recipientType, client, staff);
            }
        }
    }

    private boolean handleGlobalNavigation(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client client, final Staff staff) {
        if (session.getSessionStatus() == WhatsAppSessionStatus.LANGUAGE_SELECTION
                || session.getSessionStatus() == WhatsAppSessionStatus.CONSENT_PENDING) {
            return false;
        }
        if (keywordMatcher.matchesMainMenu(body)) {
            menuNavigationService.navigateToMainMenu(session, recipientType, client, staff);
            return true;
        }
        if (keywordMatcher.matchesBackMenu(body) || "0".equals(body)) {
            menuNavigationService.navigateBack(session, recipientType, client, staff);
            return true;
        }
        return false;
    }

    private void handleOptOut(final String phoneNumber, final String keyword, final RecipientType recipientType, final Client client,
            final Staff staff) {
        optOutService.recordOptOut(phoneNumber, keyword);
        sessionService.findResumableSession(phoneNumber, WhatsAppConversationType.CLIENT_SELF_SERVICE).ifPresent(sessionService::markOptedOut);
        replyService.sendTransactionalReply(phoneNumber, recipientType, client, staff, WhatsAppInteractiveMessages.optOutConfirmation());
    }

    private void handleOptIn(final String phoneNumber, final String keyword, final RecipientType recipientType, final Client client,
            final Staff staff, final WhatsAppConversationType conversationType) {
        optOutService.recordOptIn(phoneNumber, keyword);
        final WhatsAppConversationSession session = sessionService.startOrResume(phoneNumber, conversationType, client, staff);
        session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
        session.setCurrentMenuKey("LANGUAGE");
        sessionService.save(session);
        replyService.sendTransactionalReply(phoneNumber, recipientType, client, staff, WhatsAppInteractiveMessages.optInWelcome());
    }

    private WhatsAppConversationSession handleLanguageSelection(final WhatsAppConversationSession session, final String body,
            final RecipientType recipientType, final Client client, final Staff staff) {
        final String language = resolveLanguageChoice(body);
        if (language == null) {
            sendLanguagePrompt(session.getPhoneNumber(), recipientType, client, staff);
            return session;
        }
        session.setLanguageCode(language);
        if (consentService.hasActiveConsent(session.getPhoneNumber())) {
            session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
            session.setCurrentMenuKey(properties.getMainMenuKey());
            sessionService.save(session);
            menuNavigationService.navigateToMenu(session, properties.getMainMenuKey(), recipientType, client, staff, false);
        } else {
            session.setSessionStatus(WhatsAppSessionStatus.CONSENT_PENDING);
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.consentPrompt(language));
        }
        return session;
    }

    private WhatsAppConversationSession handleConsent(final WhatsAppConversationSession session, final String body,
            final RecipientType recipientType, final Client client, final Staff staff) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        if (keywordMatcher.matchesConsentAccept(body)) {
            consentService.recordConsent(session.getPhoneNumber(), client, true, "WHATSAPP_SESSION", null);
            session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
            session.setCurrentMenuKey(properties.getMainMenuKey());
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.consentAccepted(language));
            menuNavigationService.navigateToMenu(session, properties.getMainMenuKey(), recipientType, client, staff, false);
        } else {
            consentService.recordConsent(session.getPhoneNumber(), client, false, "WHATSAPP_SESSION", null);
            session.setSessionStatus(WhatsAppSessionStatus.CLOSED);
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.consentDeclined(language));
        }
        return session;
    }

    private void sendLanguagePrompt(final String phoneNumber, final RecipientType recipientType, final Client client, final Staff staff) {
        replyService.sendTransactionalReply(phoneNumber, recipientType, client, staff, WhatsAppInteractiveMessages.welcomeLanguagePrompt());
    }

    private String resolveLanguageChoice(final String body) {
        return switch (body.trim()) {
            case "1" -> "en";
            case "2" -> "rw";
            default -> null;
        };
    }
}
