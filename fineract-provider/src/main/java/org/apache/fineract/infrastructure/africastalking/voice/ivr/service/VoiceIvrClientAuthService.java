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

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.service.VoiceXmlBuilder;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppOtpService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSessionContextSerializer;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class VoiceIvrClientAuthService {

    private final VoiceIvrSessionService sessionService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final WhatsAppOtpService otpService;
    private final VoiceIvrOtpNotificationService otpNotificationService;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final WhatsAppInteractiveSettingsProvider settings;
    private final VoiceIvrLoanSelfServiceGate loanSelfServiceGate;

    public VoiceIvrClientAuthService(final VoiceIvrSessionService sessionService,
            final WhatsAppSessionContextSerializer contextSerializer, final WhatsAppOtpService otpService,
            final VoiceIvrOtpNotificationService otpNotificationService, final ClientRepositoryWrapper clientRepositoryWrapper,
            final WhatsAppInteractiveSettingsProvider settings, @Lazy final VoiceIvrLoanSelfServiceGate loanSelfServiceGate) {
        this.sessionService = sessionService;
        this.contextSerializer = contextSerializer;
        this.otpService = otpService;
        this.otpNotificationService = otpNotificationService;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.settings = settings;
        this.loanSelfServiceGate = loanSelfServiceGate;
    }

    @Transactional
    public String beginAuthentication(final VoiceIvrSession session, final Client matchedClient, final String pendingLoanAction) {
        final String language = resolveLanguage(session);
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setPendingLoanAction(pendingLoanAction);
        context.setAuthStep(WhatsAppAuthStep.IDENTIFY_CLIENT.name());
        if (matchedClient != null) {
            context.setCandidateClientId(matchedClient.getId());
            session.setSessionContext(contextSerializer.toJson(context));
            session.setSessionStatus(VoiceIvrSessionStatus.AUTHENTICATING);
            sessionService.save(session);
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.confirmIdentityPrompt(language, matchedClient.getDisplayName()));
        }
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(VoiceIvrSessionStatus.AUTHENTICATING);
        sessionService.save(session);
        return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.enterClientAccountPrompt(language));
    }

    @Transactional
    public String handleAuthInput(final VoiceIvrSession session, final String input) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final String authStep = context.getAuthStep();
        if (WhatsAppAuthStep.VERIFY_OTP.name().equals(authStep)) {
            return handleOtpVerification(session, input, context);
        }
        if (WhatsAppAuthStep.IDENTIFY_CLIENT.name().equals(authStep)) {
            return handleIdentifyClient(session, input, context);
        }
        return VoiceXmlBuilder.buildInvalidSelection();
    }

    @Transactional(readOnly = true)
    public boolean isSessionAuthenticated(final VoiceIvrSession session) {
        if (!session.isAuthenticated()) {
            return false;
        }
        if (session.getAuthExpiresAt() == null) {
            return false;
        }
        return session.getAuthExpiresAt().isAfter(DateUtils.getLocalDateTimeOfTenant());
    }

    @Transactional(readOnly = true)
    public String getAuthReprompt(final VoiceIvrSession session) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final String language = resolveLanguage(session);
        if (WhatsAppAuthStep.VERIFY_OTP.name().equals(context.getAuthStep())) {
            return VoiceIvrMessages.otpSentPrompt(language);
        }
        if (context.getCandidateClientId() != null) {
            final Client client = clientRepositoryWrapper.findOneWithNotFoundDetection(context.getCandidateClientId());
            return VoiceIvrMessages.confirmIdentityPrompt(language, client.getDisplayName());
        }
        return VoiceIvrMessages.enterClientAccountPrompt(language);
    }

    private String handleIdentifyClient(final VoiceIvrSession session, final String input, final WhatsAppSessionContext context) {
        final String language = resolveLanguage(session);
        Client resolvedClient = null;
        if ("1".equals(input.trim()) && context.getCandidateClientId() != null) {
            resolvedClient = clientRepositoryWrapper.findOneWithNotFoundDetection(context.getCandidateClientId());
        } else if (StringUtils.isNotBlank(input)) {
            try {
                resolvedClient = clientRepositoryWrapper.getClientByAccountNumber(input.trim());
            } catch (final ClientNotFoundException ignored) {
                resolvedClient = null;
            }
        }
        if (resolvedClient == null) {
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.identityNotFound(language));
        }
        context.setCandidateClientId(resolvedClient.getId());
        session.setClient(resolvedClient);
        return issueOtpAndPrompt(session, context, language);
    }

    private String handleOtpVerification(final VoiceIvrSession session, final String input, final WhatsAppSessionContext context) {
        final String language = resolveLanguage(session);
        if (!otpService.verifyOtp(session.getCallerNumber(), input)) {
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.otpInvalid(language));
        }
        markAuthenticated(session);
        context.setAuthStep(null);
        session.setSessionContext(contextSerializer.toJson(context));
        sessionService.save(session);
        if (StringUtils.isNotBlank(context.getPendingLoanAction())) {
            return loanSelfServiceGate.continueAfterAuthentication(session, context.getPendingLoanAction(), language);
        }
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        sessionService.save(session);
        return VoiceXmlBuilder.buildSay(VoiceIvrMessages.authSuccess(language));
    }

    private String issueOtpAndPrompt(final VoiceIvrSession session, final WhatsAppSessionContext context, final String language) {
        final String otp = otpService.issueOtp(session.getCallerNumber(), context.getCandidateClientId());
        if (!otpNotificationService.sendOtp(session.getCallerNumber(), otp, language)) {
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.otpDeliveryFailed(language));
        }
        context.setAuthStep(WhatsAppAuthStep.VERIFY_OTP.name());
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(VoiceIvrSessionStatus.AUTHENTICATING);
        sessionService.save(session);
        return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.otpSentPrompt(language));
    }

    private void markAuthenticated(final VoiceIvrSession session) {
        final LocalDateTime authExpiresAt = DateUtils.getLocalDateTimeOfTenant().plusMinutes(settings.getAuthValidityMinutes());
        session.setAuthenticated(true);
        session.setAuthExpiresAt(authExpiresAt);
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
    }

    private String resolveLanguage(final VoiceIvrSession session) {
        return StringUtils.defaultIfBlank(session.getLanguageCode(), settings.getDefaultLanguage());
    }
}
