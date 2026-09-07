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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.service.VoiceXmlBuilder;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveMessages;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppLoanSelfService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSessionContextSerializer;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceIvrLoanSelfServiceGate {

    private final VoiceIvrClientAuthService clientAuthService;
    private final WhatsAppLoanSelfService loanSelfService;
    private final VoiceIvrSessionService sessionService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final WhatsAppInteractiveSettingsProvider settings;
    private final AfricasTalkingProperties properties;

    @Transactional(readOnly = true)
    public String buildLoanSelectionPrompt(final Long clientId, final String language) {
        final List<Loan> loans = loanSelfService.findSelfServiceLoans(clientId);
        return VoiceIvrMessages.sanitizeForSpeech(loanSelfService.buildLoanSelectionMenu(loans, language));
    }

    @Transactional
    public String beginLoanService(final VoiceIvrSession session, final String loanActionTarget) {
        final Client matchedClient = session.getClient();
        if (!clientAuthService.isSessionAuthenticated(session)) {
            return clientAuthService.beginAuthentication(session, matchedClient, loanActionTarget);
        }
        return continueAfterAuthentication(session, loanActionTarget, resolveLanguage(session));
    }

    @Transactional
    public String continueAfterAuthentication(final VoiceIvrSession session, final String loanActionTarget, final String language) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final Long clientId = resolveAuthenticatedClientId(session, context);
        if (clientId == null) {
            return VoiceXmlBuilder.buildSay(WhatsAppInteractiveMessages.loanClientNotFound(language));
        }
        final List<Loan> loans = loanSelfService.findSelfServiceLoans(clientId);
        if (loans.isEmpty()) {
            return VoiceXmlBuilder.buildSay(WhatsAppInteractiveMessages.noActiveLoans(language));
        }
        context.setPendingLoanAction(loanActionTarget);
        context.setCandidateClientId(clientId);
        if (loans.size() == 1) {
            return deliverLoanService(session, loans.get(0).getId(), loanActionTarget, language);
        }
        context.setAuthStep(WhatsAppAuthStep.SELECT_LOAN.name());
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(VoiceIvrSessionStatus.AWAITING_INPUT);
        sessionService.save(session);
        return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.sanitizeForSpeech(loanSelfService.buildLoanSelectionMenu(loans, language)));
    }

    @Transactional
    public String handleLoanSelection(final VoiceIvrSession session, final String input) {
        final String language = resolveLanguage(session);
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final Long clientId = resolveAuthenticatedClientId(session, context);
        if (clientId == null) {
            return VoiceXmlBuilder.buildSay(WhatsAppInteractiveMessages.loanClientNotFound(language));
        }
        final List<Loan> loans = loanSelfService.findSelfServiceLoans(clientId);
        final Loan selected = loanSelfService.resolveLoanSelection(loans, input);
        if (selected == null) {
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.sanitizeForSpeech(loanSelfService.buildLoanSelectionMenu(loans, language)));
        }
        return deliverLoanService(session, selected.getId(), context.getPendingLoanAction(), language);
    }

    private String deliverLoanService(final VoiceIvrSession session, final Long loanId, final String loanActionTarget,
            final String language) {
        final String response = loanSelfService.buildLoanServiceResponse(loanId, loanActionTarget, language);
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        context.setSelectedLoanId(loanId);
        context.setAuthStep(null);
        context.setPendingLoanAction(null);
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        session.setCurrentMenuKey(properties.getVoiceIvr().getMainMenuKey());
        sessionService.save(session);
        return VoiceXmlBuilder.buildSay(VoiceIvrMessages.sanitizeForSpeech(response));
    }

    private Long resolveAuthenticatedClientId(final VoiceIvrSession session, final WhatsAppSessionContext context) {
        if (context.getCandidateClientId() != null) {
            return context.getCandidateClientId();
        }
        if (session.getClient() != null) {
            return session.getClient().getId();
        }
        return null;
    }

    private String resolveLanguage(final VoiceIvrSession session) {
        return StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
    }
}
