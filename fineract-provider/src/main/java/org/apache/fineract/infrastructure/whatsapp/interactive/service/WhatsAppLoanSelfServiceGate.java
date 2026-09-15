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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppLoanSelfServiceGate {

    private final WhatsAppClientAuthService clientAuthService;
    private final WhatsAppLoanSelfService loanSelfService;
    private final WhatsAppReplyService replyService;
    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final WhatsAppInteractiveSettingsProvider settings;

    @Transactional
    public void beginLoanService(final WhatsAppConversationSession session, final String loanActionTarget, final RecipientType recipientType,
            final Client webhookClient, final Staff staff) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
        if (!clientAuthService.isSessionAuthenticated(session)) {
            clientAuthService.startIdentifyClient(session, webhookClient, recipientType, webhookClient, staff, loanActionTarget);
            return;
        }
        continueAfterAuthentication(session, loanActionTarget, recipientType, webhookClient, staff, language);
    }

    @Transactional
    public void continueAfterAuthentication(final WhatsAppConversationSession session, final String loanActionTarget,
            final RecipientType recipientType, final Client webhookClient, final Staff staff, final String language) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final Long clientId = resolveAuthenticatedClientId(session, context, webhookClient);
        if (clientId == null) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.loanClientNotFound(language));
            return;
        }
        final List<Loan> loans = loanSelfService.findSelfServiceLoans(clientId);
        if (loans.isEmpty()) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.noActiveLoans(language));
            return;
        }
        context.setPendingLoanAction(loanActionTarget);
        context.setCandidateClientId(clientId);
        if (loans.size() == 1) {
            deliverLoanService(session, loans.get(0).getId(), loanActionTarget, recipientType, webhookClient, staff, language);
            return;
        }
        context.setAuthStep(WhatsAppAuthStep.SELECT_LOAN.name());
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.AWAITING_INPUT);
        sessionService.save(session);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                loanSelfService.buildLoanSelectionMenu(loans, language));
    }

    @Transactional
    public void handleLoanSelection(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client webhookClient, final Staff staff) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final Long clientId = resolveAuthenticatedClientId(session, context, webhookClient);
        if (clientId == null) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.loanClientNotFound(language));
            return;
        }
        final List<Loan> loans = loanSelfService.findSelfServiceLoans(clientId);
        final Loan selected = loanSelfService.resolveLoanSelection(loans, body);
        if (selected == null) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.invalidSelection(language));
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    loanSelfService.buildLoanSelectionMenu(loans, language));
            return;
        }
        deliverLoanService(session, selected.getId(), context.getPendingLoanAction(), recipientType, webhookClient, staff, language);
    }

    @Transactional
    public void handleAuthenticatedFollowUp(final WhatsAppConversationSession session, final RecipientType recipientType,
            final Client webhookClient, final Staff staff) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
        if (StringUtils.isNotBlank(context.getPendingLoanAction())) {
            continueAfterAuthentication(session, context.getPendingLoanAction(), recipientType, webhookClient, staff, language);
        } else {
            session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
            sessionService.save(session);
        }
    }

    private void deliverLoanService(final WhatsAppConversationSession session, final Long loanId, final String loanActionTarget,
            final RecipientType recipientType, final Client webhookClient, final Staff staff, final String language) {
        final String response = loanSelfService.buildLoanServiceResponse(loanId, loanActionTarget, language);
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        context.setSelectedLoanId(loanId);
        context.setAuthStep(null);
        context.setPendingLoanAction(null);
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
        sessionService.save(session);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                response + "\n" + WhatsAppInteractiveMessages.navigationHint(language));
    }

    private Long resolveAuthenticatedClientId(final WhatsAppConversationSession session, final WhatsAppSessionContext context,
            final Client webhookClient) {
        if (context.getCandidateClientId() != null) {
            return context.getCandidateClientId();
        }
        if (session.getClient() != null) {
            return session.getClient().getId();
        }
        if (webhookClient != null) {
            return webhookClient.getId();
        }
        return null;
    }
}
