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
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.africastalking.service.RecipientResolutionService;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrMenuActionType;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOption;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOptionRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSessionContextSerializer;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceIvrCallbackServiceTest {

    @Mock
    private VoiceIvrSessionService sessionService;
    @Mock
    private VoiceIvrMenuRenderer menuRenderer;
    @Mock
    private VoiceIvrMenuOptionRepository menuOptionRepository;
    @Mock
    private VoiceIvrCallerIdentificationService callerIdentificationService;
    @Mock
    private VoiceIvrClientAuthService clientAuthService;
    @Mock
    private VoiceIvrLoanSelfServiceGate loanSelfServiceGate;
    @Mock
    private VoiceAgentRoutingService agentRoutingService;
    @Mock
    private VoiceCallbackRequestService callbackRequestService;
    @Mock
    private VoiceVoicemailService voicemailService;
    @Mock
    private VoiceBusinessHoursService businessHoursService;
    @Mock
    private WhatsAppSessionContextSerializer contextSerializer;
    @Mock
    private RecipientResolutionService recipientResolutionService;
    @Mock
    private PhoneNumberNormalizer phoneNumberNormalizer;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;
    @Mock
    private VoiceRecordingConsentService recordingConsentService;

    private AfricasTalkingProperties properties;
    private VoiceIvrCallbackService callbackService;

    @BeforeEach
    void setUp() {
        properties = new AfricasTalkingProperties();
        callbackService = new VoiceIvrCallbackService(properties, sessionService, menuRenderer, menuOptionRepository,
                callerIdentificationService, clientAuthService, loanSelfServiceGate, agentRoutingService, callbackRequestService,
                voicemailService, businessHoursService, contextSerializer, recipientResolutionService, phoneNumberNormalizer,
                clientRepositoryWrapper, staffRepositoryWrapper, recordingConsentService);
        when(businessHoursService.isWithinBusinessHours()).thenReturn(true);
    }

    @Test
    void presentsLanguageMenuForNewSession() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.LANGUAGE_SELECTION);
        session.setCurrentMenuKey("LANGUAGE");
        when(phoneNumberNormalizer.normalize(any())).thenReturn("+254700000099");
        when(recipientResolutionService.resolve(any())).thenReturn(ResolvedRecipientData.unknown("+254700000099"));
        when(sessionService.startOrResume("ATV_1", "+254700000099", null, null)).thenReturn(session);
        when(menuRenderer.buildMenuPrompt("LANGUAGE", "en")).thenReturn("Welcome to Inkomoko. Press 1 for English, 2 for Kinyarwanda.");
        when(sessionService.save(session)).thenReturn(session);

        final String xml = callbackService.handleInbound("sessionId=ATV_1&callerNumber=%2B254700000099");

        assertThat(xml).contains("<GetDigits");
        assertThat(xml).contains("Press 1 for English");
    }

    @Test
    void appliesLanguageSelectionAndGreetsIdentifiedCaller() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.LANGUAGE_SELECTION);
        session.setCurrentMenuKey("LANGUAGE");
        final Client client = org.mockito.Mockito.mock(Client.class);
        session.setClient(client);
        final VoiceIvrMenuOption englishOption = new VoiceIvrMenuOption();
        englishOption.setActionType(VoiceIvrMenuActionType.LANGUAGE_SELECT);
        englishOption.setActionTarget("en");
        when(phoneNumberNormalizer.normalize(any())).thenReturn("+254700000099");
        when(recipientResolutionService.resolve(any())).thenReturn(ResolvedRecipientData.client("+254700000099", 10L));
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(10L)).thenReturn(client);
        when(sessionService.startOrResume("ATV_1", "+254700000099", client, null)).thenReturn(session);
        when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndOptionDigitAndEnabledTrue("LANGUAGE", "en", 1))
                .thenReturn(Optional.of(englishOption));
        when(callerIdentificationService.buildGreeting(session, "en")).thenReturn("Welcome back, Jane Client.");
        when(menuRenderer.buildMenuPrompt("MAIN", "en")).thenReturn("Welcome to Inkomoko. Press 1 for Loans.");
        when(sessionService.save(session)).thenReturn(session);

        final String xml = callbackService.handleInbound("sessionId=ATV_1&callerNumber=%2B254700000099&dtmfDigits=1");

        assertThat(xml).contains("Welcome back, Jane Client.");
        assertThat(xml).contains("Press 1 for Loans");
    }

    @Test
    void routesDialSelectionToAgentRouting() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        session.setLanguageCode("en");
        session.setCurrentMenuKey("MAIN");
        final VoiceIvrMenuOption option = new VoiceIvrMenuOption();
        option.setOptionLabel("Client Support");
        option.setActionType(VoiceIvrMenuActionType.DIAL);
        option.setActionTarget("SUPPORT");
        when(phoneNumberNormalizer.normalize(any())).thenReturn("+254700000099");
        when(recipientResolutionService.resolve(any())).thenReturn(ResolvedRecipientData.unknown("+254700000099"));
        when(sessionService.startOrResume("ATV_1", "+254700000099", null, null)).thenReturn(session);
        when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndOptionDigitAndEnabledTrue("MAIN", "en", 2))
                .thenReturn(Optional.of(option));
        when(agentRoutingService.transferToDepartment(session, option, "en"))
                .thenReturn("<Response><Dial phoneNumbers=\"+254700000002\"/></Response>");

        final String xml = callbackService.handleInbound("sessionId=ATV_1&callerNumber=%2B254700000099&dtmfDigits=2");

        assertThat(xml).contains("phoneNumbers=\"+254700000002\"");
    }

    @Test
    void routesLoanServiceSelectionToAuthGate() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        session.setLanguageCode("en");
        session.setCurrentMenuKey("LOAN");
        final VoiceIvrMenuOption option = new VoiceIvrMenuOption();
        option.setActionType(VoiceIvrMenuActionType.LOAN_SERVICE);
        option.setActionTarget("LOAN_BALANCE");
        when(phoneNumberNormalizer.normalize(any())).thenReturn("+254700000099");
        when(recipientResolutionService.resolve(any())).thenReturn(ResolvedRecipientData.unknown("+254700000099"));
        when(sessionService.startOrResume("ATV_1", "+254700000099", null, null)).thenReturn(session);
        when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndOptionDigitAndEnabledTrue("LOAN", "en", 1))
                .thenReturn(Optional.of(option));
        when(loanSelfServiceGate.beginLoanService(session, "LOAN_BALANCE")).thenReturn("<Response><GetDigits/></Response>");

        final String xml = callbackService.handleInbound("sessionId=ATV_1&callerNumber=%2B254700000099&dtmfDigits=1");

        assertThat(xml).contains("<GetDigits");
    }

    @Test
    void presentsAfterHoursMenuWhenClosed() {
        when(businessHoursService.isWithinBusinessHours()).thenReturn(false);
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.AFTER_HOURS);
        session.setLanguageCode("en");
        when(phoneNumberNormalizer.normalize(any())).thenReturn("+254700000099");
        when(recipientResolutionService.resolve(any())).thenReturn(ResolvedRecipientData.unknown("+254700000099"));
        when(sessionService.startOrResume("ATV_1", "+254700000099", null, null)).thenReturn(session);
        when(sessionService.save(session)).thenReturn(session);

        final String xml = callbackService.handleInbound("sessionId=ATV_1&callerNumber=%2B254700000099");

        assertThat(xml).contains("Press 1 for a callback");
    }

    @Test
    void schedulesCallbackAfterHours() {
        when(businessHoursService.isWithinBusinessHours()).thenReturn(false);
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.AFTER_HOURS);
        session.setLanguageCode("en");
        when(phoneNumberNormalizer.normalize(any())).thenReturn("+254700000099");
        when(recipientResolutionService.resolve(any())).thenReturn(ResolvedRecipientData.unknown("+254700000099"));
        when(sessionService.startOrResume("ATV_1", "+254700000099", null, null)).thenReturn(session);
        when(callbackRequestService.requestCallback(session, "AFTER_HOURS", "en")).thenReturn("<Response><Say>Callback received</Say></Response>");

        final String xml = callbackService.handleInbound("sessionId=ATV_1&callerNumber=%2B254700000099&dtmfDigits=1");

        assertThat(xml).contains("Callback received");
    }

    @Test
    void routesQueueSelectionToAgentRouting() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        session.setLanguageCode("en");
        session.setCurrentMenuKey("MAIN");
        final VoiceIvrMenuOption option = new VoiceIvrMenuOption();
        option.setActionType(VoiceIvrMenuActionType.QUEUE);
        option.setActionTarget("SUPPORT");
        when(phoneNumberNormalizer.normalize(any())).thenReturn("+254700000099");
        when(recipientResolutionService.resolve(any())).thenReturn(ResolvedRecipientData.unknown("+254700000099"));
        when(sessionService.startOrResume("ATV_1", "+254700000099", null, null)).thenReturn(session);
        when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndOptionDigitAndEnabledTrue("MAIN", "en", 2))
                .thenReturn(Optional.of(option));
        when(agentRoutingService.joinQueue(session, option, "en")).thenReturn("<Response><GetDigits>queue</GetDigits></Response>");

        final String xml = callbackService.handleInbound("sessionId=ATV_1&callerNumber=%2B254700000099&dtmfDigits=2");

        assertThat(xml).contains("queue");
    }
}
