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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSettingRepository;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppClientAuthServiceTest {

    @Mock
    private WhatsAppConversationSessionService sessionService;
    @Mock
    private WhatsAppOtpService otpService;
    @Mock
    private WhatsAppReplyService replyService;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private WhatsAppLoanSelfServiceGate loanSelfServiceGate;
    @Mock
    private WhatsAppOtpSmsDeliveryService otpSmsDeliveryService;
    @Mock
    private WhatsAppAdvisorEscalationService advisorEscalationService;
    @Mock
    private WhatsAppInteractiveSettingRepository settingRepository;

    private WhatsAppSessionContextSerializer contextSerializer;
    private WhatsAppClientAuthService authService;
    private PhoneNumberNormalizer phoneNumberNormalizer;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        final AfricasTalkingProperties atProperties = new AfricasTalkingProperties();
        atProperties.getPhone().setDefaultCountryCode("254");
        phoneNumberNormalizer = new PhoneNumberNormalizer(atProperties);
        final WhatsAppInteractiveProperties properties = new WhatsAppInteractiveProperties();
        properties.setDefaultLanguage("en");
        properties.setAuthValidityMinutes(30);
        properties.setMaxOtpAttempts(3);
        final WhatsAppInteractiveSettingsProvider settings = new WhatsAppInteractiveSettingsProvider(properties, settingRepository);
        contextSerializer = new WhatsAppSessionContextSerializer();
        authService = new WhatsAppClientAuthService(sessionService, contextSerializer, otpService, replyService, clientRepositoryWrapper,
                settings, loanSelfServiceGate, phoneNumberNormalizer, otpSmsDeliveryService, advisorEscalationService);
    }

    @Test
    void mismatchMobileDoesNotIssueOtpOrAuthenticate() {
        final WhatsAppConversationSession session = session("+254711111111", WhatsAppAuthStep.IDENTIFY_CLIENT);
        final Client otherClient = org.mockito.Mockito.mock(Client.class);
        when(otherClient.mobileNo()).thenReturn("+254722222222");
        when(clientRepositoryWrapper.getClientByAccountNumber("ACC-B")).thenReturn(otherClient);

        authService.handleAuthenticating(session, "ACC-B", RecipientType.CLIENT, null, null);

        verify(otpService, never()).issueOtp(anyString(), any());
        verify(otpSmsDeliveryService, never()).sendOtp(anyString(), anyString(), anyString());
        assertFalse(session.isAuthenticated());
        final ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(replyService).sendTransactionalReply(eq("+254711111111"), eq(RecipientType.CLIENT), isNull(), isNull(), reply.capture());
        assertFalse(reply.getValue().toLowerCase().contains("acc-b"));
        assertFalse(reply.getValue().matches("(?s).*\\d{4,}.*"));
    }

    @Test
    void matchingMobileSendsSmsAndRedactsWhatsAppReply() {
        final WhatsAppConversationSession session = session("+254711111111", WhatsAppAuthStep.IDENTIFY_CLIENT);
        final Client client = org.mockito.Mockito.mock(Client.class);
        when(client.getId()).thenReturn(10L);
        when(client.mobileNo()).thenReturn("0711111111");
        when(clientRepositoryWrapper.getClientByAccountNumber("ACC-A")).thenReturn(client);
        when(otpService.issueOtp(eq("+254711111111"), eq(10L))).thenReturn("654321");
        when(otpSmsDeliveryService.sendOtp(eq("+254711111111"), eq("654321"), eq("en"))).thenReturn(true);

        authService.handleAuthenticating(session, "ACC-A", RecipientType.CLIENT, null, null);

        verify(otpSmsDeliveryService).sendOtp("+254711111111", "654321", "en");
        final ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(replyService).sendTransactionalReply(eq("+254711111111"), eq(RecipientType.CLIENT), isNull(), isNull(), reply.capture());
        assertFalse(reply.getValue().contains("654321"));
        assertFalse(session.isAuthenticated());
    }

    @Test
    void exhaustedOtpAttemptsEscalateInsteadOfInfiniteInvalid() {
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setAuthStep(WhatsAppAuthStep.VERIFY_OTP.name());
        context.setCandidateClientId(10L);
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+254711111111");
        session.setLanguageCode("en");
        session.setSessionStatus(WhatsAppSessionStatus.AUTHENTICATING);
        session.setSessionContext(contextSerializer.toJson(context));

        final Client client = org.mockito.Mockito.mock(Client.class);
        when(client.mobileNo()).thenReturn("+254711111111");
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(10L)).thenReturn(client);
        when(otpService.verifyOtp("+254711111111", "000000")).thenReturn(false);
        when(otpService.isAttemptsExhausted("+254711111111")).thenReturn(true);
        when(advisorEscalationService.escalate(eq(session), eq(client), eq("AUTH_LOCKOUT"), isNull(), eq("en")))
                .thenReturn("Advisor handoff WA-1");

        authService.handleAuthenticating(session, "000000", RecipientType.CLIENT, null, null);

        verify(advisorEscalationService).escalate(session, client, "AUTH_LOCKOUT", null, "en");
        verify(replyService).sendTransactionalReply(eq("+254711111111"), eq(RecipientType.CLIENT), isNull(), isNull(),
                contains("Advisor handoff"));
        verify(replyService, never()).sendTransactionalReply(anyString(), any(), any(), any(),
                eq(WhatsAppInteractiveMessages.otpInvalid("en")));
    }

    private WhatsAppConversationSession session(final String phone, final WhatsAppAuthStep step) {
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setAuthStep(step.name());
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber(phone);
        session.setLanguageCode("en");
        session.setSessionStatus(WhatsAppSessionStatus.AUTHENTICATING);
        session.setSessionContext(contextSerializer.toJson(context));
        return session;
    }
}
