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
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOption;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOptionRepository;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppInboundMessageRouter {

    private static final String PENDING_ADVISOR_OTHER = "ADVISOR_OTHER";

    private final WhatsAppMenuOptionRepository menuOptionRepository;
    private final WhatsAppReplyService replyService;
    private final WhatsAppContentMessageService contentMessageService;
    private final WhatsAppMenuNavigationService menuNavigationService;
    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final WhatsAppInteractiveSettingsProvider settings;
    private final WhatsAppLoanSelfServiceGate loanSelfServiceGate;
    private final WhatsAppAdvisorEscalationService advisorEscalationService;

    @Transactional
    public void routeMenuSelection(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client client, final Staff staff) {
        if (session.getConversationType() == WhatsAppConversationType.STAFF) {
            return;
        }
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
        if (!NumberUtils.isDigits(body.trim())) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.invalidSelection(language));
            menuNavigationService.navigateToMenu(session, session.getCurrentMenuKey(), recipientType, client, staff, false);
            return;
        }
        final int optionNumber = Integer.parseInt(body.trim());
        final WhatsAppMenuOption option = menuOptionRepository
                .findByMenuKeyAndLanguageCodeAndOptionNumberAndEnabledTrue(session.getCurrentMenuKey(), language, optionNumber)
                .orElse(null);
        if (option == null) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.invalidSelection(language));
            menuNavigationService.navigateToMenu(session, session.getCurrentMenuKey(), recipientType, client, staff, false);
            return;
        }
        dispatchMenuAction(session, option, recipientType, client, staff, language);
    }

    @Transactional
    public void handleAwaitingInput(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client client, final Staff staff) {
        if (session.getConversationType() == WhatsAppConversationType.STAFF) {
            return;
        }
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (PENDING_ADVISOR_OTHER.equals(context.getPendingAction())) {
            final String reply = advisorEscalationService.escalate(session, client, "OTHER", body.trim(), language);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff, reply);
            return;
        }
        if (WhatsAppAuthStep.SELECT_LOAN.name().equals(context.getAuthStep())) {
            loanSelfServiceGate.handleLoanSelection(session, body, recipientType, client, staff);
            return;
        }
        session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
        sessionService.save(session);
        menuNavigationService.navigateToMainMenu(session, recipientType, client, staff);
    }

    private void dispatchMenuAction(final WhatsAppConversationSession session, final WhatsAppMenuOption option,
            final RecipientType recipientType, final Client client, final Staff staff, final String language) {
        switch (option.getActionType()) {
            case SUBMENU -> menuNavigationService.navigateToMenu(session, option.getActionTarget(), recipientType, client, staff, true);
            case LOAN_SERVICE -> loanSelfServiceGate.beginLoanService(session, option.getActionTarget(), recipientType, client, staff);
            case CONTENT -> sendContent(session, option, recipientType, client, staff, language);
            case ADVISOR_HANDOFF -> handleAdvisorHandoff(session, option, recipientType, client, staff, language);
            default -> replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.invalidSelection(language));
        }
    }

    private void sendContent(final WhatsAppConversationSession session, final WhatsAppMenuOption option, final RecipientType recipientType,
            final Client client, final Staff staff, final String language) {
        final String body = contentMessageService.resolveBody(option.getActionTarget(), language);
        if (StringUtils.isBlank(body)) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.contentPending(language, option.getActionTarget()));
            return;
        }
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                body + "\n" + WhatsAppInteractiveMessages.navigationHint(language));
    }

    private void handleAdvisorHandoff(final WhatsAppConversationSession session, final WhatsAppMenuOption option,
            final RecipientType recipientType, final Client client, final Staff staff, final String language) {
        if ("OTHER".equalsIgnoreCase(option.getActionTarget())) {
            final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
            context.setPendingAction(PENDING_ADVISOR_OTHER);
            session.setSessionContext(contextSerializer.toJson(context));
            session.setSessionStatus(WhatsAppSessionStatus.AWAITING_INPUT);
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.otherEnquiryPrompt(language));
            return;
        }
        final String category = StringUtils.defaultIfBlank(option.getActionTarget(), "ADVISOR");
        final String reply = advisorEscalationService.escalate(session, client, category, null, language);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff, reply);
    }
}
