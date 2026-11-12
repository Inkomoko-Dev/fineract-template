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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppOtpService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSessionContextSerializer;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceIvrClientAuthServiceTest {

    @Mock
    private VoiceIvrSessionService sessionService;
    @Mock
    private WhatsAppOtpService otpService;
    @Mock
    private VoiceIvrOtpNotificationService otpNotificationService;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private WhatsAppInteractiveSettingsProvider settings;
    @Mock
    private VoiceIvrLoanSelfServiceGate loanSelfServiceGate;
    @Mock
    private PhoneNumberNormalizer phoneNumberNormalizer;
    @Mock
    private VoiceIvrDialTargetResolver dialTargetResolver;

    private WhatsAppSessionContextSerializer contextSerializer;
    private VoiceIvrClientAuthService authService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        contextSerializer = new WhatsAppSessionContextSerializer();
        authService = new VoiceIvrClientAuthService(sessionService, contextSerializer, otpService, otpNotificationService,
                clientRepositoryWrapper, settings, loanSelfServiceGate, phoneNumberNormalizer, dialTargetResolver);
    }

    @Test
    void beginsAuthenticationForMatchedClient() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setLanguageCode("en");
        session.setCallerNumber("+254700000099");
        final Client client = org.mockito.Mockito.mock(Client.class);
        when(client.getId()).thenReturn(10L);
        when(client.getDisplayName()).thenReturn("Jane Client");
        when(sessionService.save(session)).thenReturn(session);

        final String xml = authService.beginAuthentication(session, client, "LOAN_BALANCE");

        assertThat(xml).contains("Press 1 to confirm");
        assertThat(session.getSessionStatus()).isEqualTo(VoiceIvrSessionStatus.AUTHENTICATING);
    }

    @Test
    void verifiesOtpAgainstRegisteredMobileAndContinuesLoanService() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setLanguageCode("en");
        session.setCallerNumber("+254700000099");
        final Client client = org.mockito.Mockito.mock(Client.class);
        when(client.mobileNo()).thenReturn("+254700000099");
        session.setClient(client);
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setAuthStep(WhatsAppAuthStep.VERIFY_OTP.name());
        context.setPendingLoanAction("LOAN_BALANCE");
        context.setCandidateClientId(10L);
        session.setSessionContext(contextSerializer.toJson(context));
        when(settings.getDefaultLanguage()).thenReturn("en");
        when(settings.getAuthValidityMinutes()).thenReturn(30);
        when(phoneNumberNormalizer.normalize("+254700000099")).thenReturn("+254700000099");
        when(otpService.verifyOtp("+254700000099", "123456")).thenReturn(true);
        when(loanSelfServiceGate.continueAfterAuthentication(session, "LOAN_BALANCE", "en")).thenReturn("<Response><Say/></Response>");
        when(sessionService.save(session)).thenReturn(session);

        final String xml = authService.handleAuthInput(session, "123456");

        assertThat(xml).contains("<Say");
        verify(otpService).verifyOtp("+254700000099", "123456");
        verify(loanSelfServiceGate).continueAfterAuthentication(session, "LOAN_BALANCE", "en");
        assertThat(session.isAuthenticated()).isTrue();
    }

    @Test
    void rejectsAccountNumberWhoseRegisteredMobileIsNotCaller() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setLanguageCode("en");
        session.setCallerNumber("+254700000001");
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setAuthStep(WhatsAppAuthStep.IDENTIFY_CLIENT.name());
        session.setSessionContext(contextSerializer.toJson(context));
        final Client otherClient = org.mockito.Mockito.mock(Client.class);
        when(otherClient.getId()).thenReturn(99L);
        when(otherClient.mobileNo()).thenReturn("+254700000099");
        when(settings.getDefaultLanguage()).thenReturn("en");
        when(clientRepositoryWrapper.getClientByAccountNumber("000000099")).thenReturn(otherClient);
        when(phoneNumberNormalizer.normalize("+254700000099")).thenReturn("+254700000099");
        when(phoneNumberNormalizer.normalize("+254700000001")).thenReturn("+254700000001");
        when(sessionService.save(session)).thenReturn(session);

        final String xml = authService.handleAuthInput(session, "000000099");

        assertThat(xml).contains("not registered to the phone number");
        assertThat(session.isAuthenticated()).isFalse();
        verify(otpService, never()).issueOtp(anyString(), any());
        verify(otpNotificationService, never()).sendOtp(anyString(), anyString(), anyString());
    }

    @Test
    void issuesOtpToRegisteredMobileWhenCallerMatches() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setLanguageCode("en");
        session.setCallerNumber("+254700000099");
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setAuthStep(WhatsAppAuthStep.IDENTIFY_CLIENT.name());
        session.setSessionContext(contextSerializer.toJson(context));
        final Client client = org.mockito.Mockito.mock(Client.class);
        when(client.getId()).thenReturn(10L);
        when(client.mobileNo()).thenReturn("0700000099");
        when(settings.getDefaultLanguage()).thenReturn("en");
        when(clientRepositoryWrapper.getClientByAccountNumber("000000010")).thenReturn(client);
        when(phoneNumberNormalizer.normalize("0700000099")).thenReturn("+254700000099");
        when(phoneNumberNormalizer.normalize("+254700000099")).thenReturn("+254700000099");
        when(otpService.issueOtp("+254700000099", 10L)).thenReturn("654321");
        when(otpNotificationService.sendOtp("+254700000099", "654321", "en")).thenReturn(true);
        when(sessionService.save(session)).thenReturn(session);

        final String xml = authService.handleAuthInput(session, "000000010");

        assertThat(xml).contains("verification code");
        verify(otpService).issueOtp(eq("+254700000099"), eq(10L));
        verify(otpNotificationService).sendOtp("+254700000099", "654321", "en");
    }

    @Test
    void transfersToSupportAfterMaxOtpFailures() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setLanguageCode("en");
        session.setCallerNumber("+254700000099");
        final Client client = org.mockito.Mockito.mock(Client.class);
        when(client.mobileNo()).thenReturn("+254700000099");
        session.setClient(client);
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        context.setAuthStep(WhatsAppAuthStep.VERIFY_OTP.name());
        context.setCandidateClientId(10L);
        session.setSessionContext(contextSerializer.toJson(context));
        when(settings.getDefaultLanguage()).thenReturn("en");
        when(phoneNumberNormalizer.normalize("+254700000099")).thenReturn("+254700000099");
        when(otpService.verifyOtp("+254700000099", "000000")).thenReturn(false);
        when(otpService.isAttemptsExhausted("+254700000099")).thenReturn(true);
        when(dialTargetResolver.resolve(org.mockito.ArgumentMatchers.eq("SUPPORT"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("+254711000000");
        when(sessionService.save(session)).thenReturn(session);

        final String xml = authService.handleAuthInput(session, "000000");

        assertThat(xml).contains("Too many incorrect");
        assertThat(xml).contains("<Dial");
        assertThat(session.getSessionStatus()).isEqualTo(VoiceIvrSessionStatus.CLOSED);
        assertThat(session.isAuthenticated()).isFalse();
    }
}
