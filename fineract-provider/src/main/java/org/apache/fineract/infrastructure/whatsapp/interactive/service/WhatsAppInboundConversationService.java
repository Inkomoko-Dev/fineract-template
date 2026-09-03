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
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOption;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOptionRepository;
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
    private final WhatsAppMenuRenderer menuRenderer;
    private final WhatsAppMenuOptionRepository menuOptionRepository;
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
        switch (session.getSessionStatus()) {
            case LANGUAGE_SELECTION -> session = handleLanguageSelection(session, normalizedBody, recipientType, client, staff);
            case CONSENT_PENDING -> session = handleConsent(session, normalizedBody, recipientType, client, staff);
            case MAIN_MENU -> handleMainMenuSelection(session, normalizedBody, recipientType, client, staff);
            default -> {
                session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
                session.setCurrentMenuKey("LANGUAGE");
                sessionService.save(session);
                sendLanguagePrompt(phoneNumber, recipientType, client, staff);
            }
        }
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
            session.setCurrentMenuKey("MAIN");
            sessionService.save(session);
            sendMainMenu(session, recipientType, client, staff);
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
            session.setCurrentMenuKey("MAIN");
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.consentAccepted(language));
            sendMainMenu(session, recipientType, client, staff);
        } else {
            consentService.recordConsent(session.getPhoneNumber(), client, false, "WHATSAPP_SESSION", null);
            session.setSessionStatus(WhatsAppSessionStatus.CLOSED);
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.consentDeclined(language));
        }
        return session;
    }

    private void handleMainMenuSelection(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client client, final Staff staff) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        if (!NumberUtils.isDigits(body.trim())) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.invalidSelection(language));
            sendMainMenu(session, recipientType, client, staff);
            return;
        }
        final int optionNumber = Integer.parseInt(body.trim());
        final WhatsAppMenuOption option = menuOptionRepository
                .findByMenuKeyAndLanguageCodeAndOptionNumberAndEnabledTrue(session.getCurrentMenuKey(), language, optionNumber)
                .orElse(null);
        if (option == null) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.invalidSelection(language));
            sendMainMenu(session, recipientType, client, staff);
            return;
        }
        dispatchMenuAction(session, option, recipientType, client, staff, language);
    }

    private void dispatchMenuAction(final WhatsAppConversationSession session, final WhatsAppMenuOption option,
            final RecipientType recipientType, final Client client, final Staff staff, final String language) {
        switch (option.getActionType()) {
            case LOAN_SERVICE -> replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.loanServicePending(language));
            case CONTENT -> replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.contentPending(language, option.getActionTarget()));
            case ADVISOR_HANDOFF -> {
                session.setSessionStatus(WhatsAppSessionStatus.ESCALATED);
                sessionService.save(session);
                replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                        WhatsAppInteractiveMessages.advisorHandoff(language));
            }
            default -> replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.invalidSelection(language));
        }
    }

    private void sendLanguagePrompt(final String phoneNumber, final RecipientType recipientType, final Client client, final Staff staff) {
        replyService.sendTransactionalReply(phoneNumber, recipientType, client, staff, WhatsAppInteractiveMessages.welcomeLanguagePrompt());
    }

    private void sendMainMenu(final WhatsAppConversationSession session, final RecipientType recipientType, final Client client,
            final Staff staff) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        final String menu = menuRenderer.renderMenu("MAIN", language, WhatsAppInteractiveMessages.mainMenuHeader(language));
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff, menu);
    }

    private String resolveLanguageChoice(final String body) {
        return switch (body.trim()) {
            case "1" -> "en";
            case "2" -> "rw";
            default -> null;
        };
    }
}
