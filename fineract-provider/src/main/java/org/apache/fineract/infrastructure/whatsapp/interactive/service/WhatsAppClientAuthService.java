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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
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
@Slf4j
public class WhatsAppClientAuthService {

    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final WhatsAppOtpService otpService;
    private final WhatsAppReplyService replyService;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final WhatsAppInteractiveSettingsProvider settings;
    private final WhatsAppLoanSelfServiceGate loanSelfServiceGate;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final WhatsAppOtpSmsDeliveryService otpSmsDeliveryService;
    private final WhatsAppAdvisorEscalationService advisorEscalationService;

    public WhatsAppClientAuthService(final WhatsAppConversationSessionService sessionService,
            final WhatsAppSessionContextSerializer contextSerializer, final WhatsAppOtpService otpService,
            final WhatsAppReplyService replyService, final ClientRepositoryWrapper clientRepositoryWrapper,
            final WhatsAppInteractiveSettingsProvider settings, @Lazy final WhatsAppLoanSelfServiceGate loanSelfServiceGate,
            final PhoneNumberNormalizer phoneNumberNormalizer, final WhatsAppOtpSmsDeliveryService otpSmsDeliveryService,
            final WhatsAppAdvisorEscalationService advisorEscalationService) {
        this.sessionService = sessionService;
        this.contextSerializer = contextSerializer;
        this.otpService = otpService;
        this.replyService = replyService;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.settings = settings;
        this.loanSelfServiceGate = loanSelfServiceGate;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.otpSmsDeliveryService = otpSmsDeliveryService;
        this.advisorEscalationService = advisorEscalationService;
    }

    @Transactional
    public void startIdentifyClient(final WhatsAppConversationSession session, final Client matchedClient, final RecipientType recipientType,
            final Client client, final Staff staff, final String pendingLoanAction) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
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
        final LocalDateTime authExpiresAt = DateUtils.getLocalDateTimeOfTenant().plusMinutes(settings.getAuthValidityMinutes());
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
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
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
        if (resolvedClient == null || !senderOwnsRegisteredMobile(session, resolvedClient)) {
            context.setCandidateClientId(null);
            session.setSessionContext(contextSerializer.toJson(context));
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.authVerificationFailed(language));
            return;
        }
        context.setCandidateClientId(resolvedClient.getId());
        issueOtpAndPrompt(session, recipientType, webhookClient, staff, context, language, resolvedClient);
    }

    private void handleOtpVerification(final WhatsAppConversationSession session, final String body, final RecipientType recipientType,
            final Client webhookClient, final Staff staff, final WhatsAppSessionContext context) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
        final String registeredMobile = resolveRegisteredMobileForSession(session, context);
        if (StringUtils.isBlank(registeredMobile)) {
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.authVerificationFailed(language));
            return;
        }
        if (!otpService.verifyOtp(registeredMobile, body)) {
            if (otpService.isAttemptsExhausted(registeredMobile)) {
                lockAndEscalate(session, recipientType, webhookClient, staff, context, language);
                return;
            }
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.authVerificationFailed(language));
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
            final Staff staff, final WhatsAppSessionContext context, final String language, final Client resolvedClient) {
        final String registeredMobile = phoneNumberNormalizer.normalize(resolvedClient.mobileNo());
        final String otp = otpService.issueOtp(registeredMobile, context.getCandidateClientId());
        final boolean delivered = otpSmsDeliveryService.sendOtp(registeredMobile, otp, language);
        if (!delivered) {
            context.setCandidateClientId(null);
            context.setAuthStep(WhatsAppAuthStep.IDENTIFY_CLIENT.name());
            session.setSessionContext(contextSerializer.toJson(context));
            sessionService.save(session);
            replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                    WhatsAppInteractiveMessages.otpDeliveryFailed(language));
            return;
        }
        context.setAuthStep(WhatsAppAuthStep.VERIFY_OTP.name());
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.AUTHENTICATING);
        sessionService.save(session);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff,
                WhatsAppInteractiveMessages.otpSentBySms(language));
    }

    private void lockAndEscalate(final WhatsAppConversationSession session, final RecipientType recipientType, final Client webhookClient,
            final Staff staff, final WhatsAppSessionContext context, final String language) {
        Client client = webhookClient;
        if (context.getCandidateClientId() != null) {
            try {
                client = clientRepositoryWrapper.findOneWithNotFoundDetection(context.getCandidateClientId());
            } catch (final RuntimeException ex) {
                log.warn("AUTH_LOCKOUT could not load candidate client {}", context.getCandidateClientId(), ex);
            }
        }
        context.setAuthStep(null);
        context.setCandidateClientId(null);
        session.setAuthenticated(false);
        session.setSessionContext(contextSerializer.toJson(context));
        sessionService.save(session);
        final String handoff = advisorEscalationService.escalate(session, client, "AUTH_LOCKOUT", null, language);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, webhookClient, staff, handoff);
    }

    private boolean senderOwnsRegisteredMobile(final WhatsAppConversationSession session, final Client client) {
        final String registered = phoneNumberNormalizer.normalize(client.mobileNo());
        final String sender = phoneNumberNormalizer.normalize(session.getPhoneNumber());
        return StringUtils.isNotBlank(registered) && StringUtils.isNotBlank(sender) && registered.equals(sender);
    }

    private String resolveRegisteredMobileForSession(final WhatsAppConversationSession session, final WhatsAppSessionContext context) {
        if (context.getCandidateClientId() != null) {
            try {
                final Client client = clientRepositoryWrapper.findOneWithNotFoundDetection(context.getCandidateClientId());
                if (senderOwnsRegisteredMobile(session, client)) {
                    return phoneNumberNormalizer.normalize(client.mobileNo());
                }
            } catch (final RuntimeException ex) {
                log.warn("Could not resolve registered mobile for candidate client {}", context.getCandidateClientId(), ex);
                return null;
            }
        }
        return null;
    }
}
