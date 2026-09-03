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

import java.time.LocalDateTime;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppClientAuthService {

    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final WhatsAppOtpService otpService;
    private final WhatsAppReplyService replyService;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final WhatsAppInteractiveProperties properties;
    private final WhatsAppLoanSelfServiceGate loanSelfServiceGate;

    public WhatsAppClientAuthService(final WhatsAppConversationSessionService sessionService,
            final WhatsAppSessionContextSerializer contextSerializer, final WhatsAppOtpService otpService,
            final WhatsAppReplyService replyService, final ClientRepositoryWrapper clientRepositoryWrapper,
            final WhatsAppInteractiveProperties properties, @Lazy final WhatsAppLoanSelfServiceGate loanSelfServiceGate) {
        this.sessionService = sessionService;
        this.contextSerializer = contextSerializer;
        this.otpService = otpService;
        this.replyService = replyService;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.properties = properties;
        this.loanSelfServiceGate = loanSelfServiceGate;
    }

    @Transactional
    public void startIdentifyClient(final WhatsAppConversationSession session, final Client matchedClient, final RecipientType recipientType,
            final Client client, final Staff staff, final String pendingLoanAction) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setPendingLoanAction(pendingLoanAction);
        context.setAuthStep(WhatsAppAuthStep.IDENTIFY_CLIENT.name());
        if (matchedClient != null) {
            context.setCandidateClientId(matchedClient.getId());
            session.setSessionContext(contextSerializer.toJson(context));
            session.setSessionStatus(WhatsAppSessionStatus.AUTHENTICATING);
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                    WhatsAppInteractiveMessages.confirmIdentityPrompt(language, matchedClient.getDisplayName()));
            return;
        }
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.AUTHENTICATING);
        sessionService.save(session);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff,
                WhatsAppInteractiveMessages.enterClientAccountPrompt(language));
    }

    @Transactional
    public void handleAuthenticating(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client webhookClient, final Staff staff) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final String authStep = context.getAuthStep();
        if (WhatsAppAuthStep.VERIFY_OTP.name().equals(authStep)) {
            handleOtpVerification(session, body, recipientType, webhookClient, staff, context);
            return;
        }
        if (WhatsAppAuthStep.IDENTIFY_CLIENT.name().equals(authStep)) {
            handleIdentifyClient(session, body, recipientType, webhookClient, staff, context);
        }
    }

    @Transactional
    public void markAuthenticated(final WhatsAppConversationSession session) {
        final LocalDateTime authExpiresAt = DateUtils.getLocalDateTimeOfTenant().plusMinutes(properties.getAuthValidityMinutes());
        session.setAuthenticated(true);
        session.setAuthExpiresAt(authExpiresAt);
        sessionService.save(session);
    }

    @Transactional(readOnly = true)
    public boolean isSessionAuthenticated(final WhatsAppConversationSession session) {
        if (!session.isAuthenticated()) {
            return false;
        }
        if (session.getAuthExpiresAt() == null) {
            return false;
        }
        return session.getAuthExpiresAt().isAfter(DateUtils.getLocalDateTimeOfTenant());
    }

    private void handleIdentifyClient(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client webhookClient, final Staff staff, final WhatsAppSessionContext context) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        Client resolvedClient = null;
        if ("1".equals(body.trim()) && context.getCandidateClientId() != null) {
            resolvedClient = clientRepositoryWrapper.findOneWithNotFoundDetection(context.getCandidateClientId());
        } else if (StringUtils.isNotBlank(body)) {
            try {
                resolvedClient = clientRepositoryWrapper.getClientByAccountNumber(body.trim());
            } catch (final ClientNotFoundException ignored) {
                resolvedClient = null;
            }
        }
        if (resolvedClient == null) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.identityNotFound(language));
            return;
        }
        context.setCandidateClientId(resolvedClient.getId());
        issueOtpAndPrompt(session, recipientType, webhookClient, staff, context, language);
    }

    private void handleOtpVerification(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client webhookClient, final Staff staff, final WhatsAppSessionContext context) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        if (!otpService.verifyOtp(session.getPhoneNumber(), body)) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.otpInvalid(language));
            return;
        }
        markAuthenticated(session);
        context.setAuthStep(null);
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.AUTHENTICATED);
        sessionService.save(session);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                WhatsAppInteractiveMessages.authSuccess(language));
        if (StringUtils.isNotBlank(context.getPendingLoanAction())) {
            loanSelfServiceGate.continueAfterAuthentication(session, context.getPendingLoanAction(), recipientType, webhookClient, staff,
                    language);
        }
    }

    private void issueOtpAndPrompt(final WhatsAppConversationSession session, final RecipientType recipientType, final Client webhookClient,
            final Staff staff, final WhatsAppSessionContext context, final String language) {
        final String otp = otpService.issueOtp(session.getPhoneNumber(), context.getCandidateClientId());
        context.setAuthStep(WhatsAppAuthStep.VERIFY_OTP.name());
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.AUTHENTICATING);
        sessionService.save(session);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                WhatsAppInteractiveMessages.otpIssued(language, otp));
    }
}
