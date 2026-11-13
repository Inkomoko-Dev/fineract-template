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
package org.apache.fineract.infrastructure.africastalking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLog;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLogRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSessionRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.service.VoiceCallQueueService;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.service.VoiceIvrCallbackService;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.service.VoiceVoicemailService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AfricasTalkingVoiceServiceTest {

    @Mock
    private AfricasTalkingClient africasTalkingClient;
    @Mock
    private VoiceCallLogRepository voiceCallLogRepository;
    @Mock
    private RecipientResolutionService recipientResolutionService;
    @Mock
    private PhoneNumberNormalizer phoneNumberNormalizer;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;
    @Mock
    private VoiceIvrCallbackService voiceIvrCallbackService;
    @Mock
    private VoiceVoicemailService voiceVoicemailService;
    @Mock
    private org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequestRepository callbackRequestRepository;
    @Mock
    private org.apache.fineract.infrastructure.africastalking.voice.ivr.service.VoiceCallbackRequestService voiceCallbackRequestService;
    @Mock
    private VoiceCallQueueService voiceCallQueueService;
    @Mock
    private VoiceIvrSessionRepository voiceIvrSessionRepository;
    @Mock
    private org.apache.fineract.infrastructure.africastalking.voice.ivr.service.VoiceIvrSessionService voiceIvrSessionService;

    private AfricasTalkingProperties properties;
    private AfricasTalkingVoiceService voiceService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        properties = new AfricasTalkingProperties();
        properties.setUsername("Inkomoko-Capital");
        properties.setApiKey("test-key");
        properties.getVoice().setCallerId("+254711082867");
        properties.getVoice().setLoansDepartmentNumber("+254700000001");
        properties.getVoice().setSupportDepartmentNumber("+254700000002");
        properties.getVoice().setInternalDepartmentNumber("+254700000003");
        properties.getVoice().setBusinessHoursStart("00:00");
        properties.getVoice().setBusinessHoursEnd("23:59");
        voiceService = new AfricasTalkingVoiceService(africasTalkingClient, properties, voiceCallLogRepository,
                recipientResolutionService, phoneNumberNormalizer, clientRepositoryWrapper, staffRepositoryWrapper,
                voiceIvrCallbackService, voiceVoicemailService, callbackRequestRepository, voiceCallbackRequestService,
                voiceCallQueueService, voiceIvrSessionRepository, voiceIvrSessionService);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void returnsMainMenuWhenNoDtmfDigits() {
        when(voiceIvrCallbackService.handleInbound("sessionId=ATV_1&callerNumber=%2B254700000099"))
                .thenReturn("<?xml version=\"1.0\"?><Response><GetDigits/></Response>");
        final String xml = voiceService.buildIvrResponse("sessionId=ATV_1&callerNumber=%2B254700000099");
        assertTrue(xml.contains("<GetDigits"));
    }

    @Test
    void routesLoansSelectionToDial() {
        when(voiceIvrCallbackService.handleInbound("sessionId=ATV_1&dtmfDigits=1"))
                .thenReturn("<?xml version=\"1.0\"?><Response><Dial phoneNumbers=\"+254700000001\"/></Response>");
        final String xml = voiceService.buildIvrResponse("sessionId=ATV_1&dtmfDigits=1");
        assertTrue(xml.contains("phoneNumbers=\"+254700000001\""));
    }

    @Test
    void persistsInboundCallback() {
        when(recipientResolutionService.resolve(any())).thenReturn(ResolvedRecipientData.unknown("+254700000099"));
        when(voiceCallLogRepository.findByExternalSessionId("ATV_99")).thenReturn(Optional.empty());
        when(voiceCallLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        voiceService.processInboundCallback("sessionId=ATV_99&callerNumber=%2B254700000099&destinationNumber=%2B254711082867");

        final ArgumentCaptor<VoiceCallLog> captor = ArgumentCaptor.forClass(VoiceCallLog.class);
        verify(voiceCallLogRepository).save(captor.capture());
        assertEquals(CommunicationDirection.INBOUND, captor.getValue().getDirection());
        assertEquals(AfricasTalkingConstants.CALL_STATUS_RINGING, captor.getValue().getStatus());
    }

    @Test
    void updatesCallLogFromVoiceEvent() {
        final VoiceCallLog existing = VoiceCallLog.inbound("ATV_55", "+254700000099", "+254711082867", null, null);
        when(voiceCallLogRepository.findByExternalSessionId("ATV_55")).thenReturn(Optional.of(existing));
        when(voiceCallLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        voiceService.processCallEvent("sessionId=ATV_55&callSessionState=Completed&durationInSeconds=42");

        assertEquals(AfricasTalkingConstants.CALL_STATUS_COMPLETED, existing.getStatus());
        assertEquals(42, existing.getDurationSeconds());
    }

    @Test
    void mapsBusyAndCreatesMissedCallCallback() {
        final VoiceCallLog existing = VoiceCallLog.inbound("ATV_BUSY", "+254700000099", "+254711082867", null, null);
        final var callback = org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequest.pending("ATV_BUSY",
                1L, "+254700000099", null, "MISSED_CALL", "en", null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(callback, "id", 77L);
        when(voiceCallLogRepository.findByExternalSessionId("ATV_BUSY")).thenReturn(Optional.of(existing));
        when(voiceCallLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(voiceIvrSessionRepository.findByExternalSessionIdOrderByLastActivityAtDesc("ATV_BUSY")).thenReturn(List.of());
        when(voiceCallbackRequestService.createSilentCallbackForCaller(any(), any(), any(), any(), any())).thenReturn(callback);

        voiceService.processCallEvent("sessionId=ATV_BUSY&callSessionState=Busy&direction=Inbound");

        assertEquals(AfricasTalkingConstants.CALL_STATUS_BUSY, existing.getStatus());
        assertEquals(77L, existing.getCallbackRequestId());
        verify(voiceCallbackRequestService).createSilentCallbackForCaller(any(), any(), any(), any(), any());
    }

    @Test
    void mapsNoAnswerAliasAndCreatesMissedCallCallback() {
        final VoiceCallLog existing = VoiceCallLog.inbound("ATV_NA", "+254700000099", "+254711082867", null, null);
        final var callback = org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallbackRequest.pending("ATV_NA", 1L,
                "+254700000099", null, "MISSED_CALL", "en", null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(callback, "id", 78L);
        when(voiceCallLogRepository.findByExternalSessionId("ATV_NA")).thenReturn(Optional.of(existing));
        when(voiceCallLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(voiceIvrSessionRepository.findByExternalSessionIdOrderByLastActivityAtDesc("ATV_NA")).thenReturn(List.of());
        when(voiceCallbackRequestService.createSilentCallbackForCaller(any(), any(), any(), any(), any())).thenReturn(callback);

        voiceService.processCallEvent("sessionId=ATV_NA&callSessionState=no-answer&direction=Inbound");

        assertEquals(AfricasTalkingConstants.CALL_STATUS_NO_ANSWER, existing.getStatus());
        assertEquals(78L, existing.getCallbackRequestId());
    }

    @Test
    void doesNotCreateMissedCallCallbackForCompleted() {
        final VoiceCallLog existing = VoiceCallLog.inbound("ATV_OK", "+254700000099", "+254711082867", null, null);
        when(voiceCallLogRepository.findByExternalSessionId("ATV_OK")).thenReturn(Optional.of(existing));
        when(voiceCallLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        voiceService.processCallEvent("sessionId=ATV_OK&callSessionState=Completed&durationInSeconds=10");

        assertEquals(AfricasTalkingConstants.CALL_STATUS_COMPLETED, existing.getStatus());
        assertNull(existing.getCallbackRequestId());
        verify(voiceCallbackRequestService, org.mockito.Mockito.never()).createSilentCallbackForCaller(any(), any(), any(), any(), any());
    }

    @Test
    void doesNotPersistDtmfWhileIvrSessionIsAuthenticating() {
        final VoiceCallLog existing = VoiceCallLog.inbound("ATV_OTP", "+254700000099", "+254711082867", null, null);
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setSessionStatus(VoiceIvrSessionStatus.AUTHENTICATING);
        when(voiceCallLogRepository.findByExternalSessionId("ATV_OTP")).thenReturn(Optional.of(existing));
        when(voiceIvrSessionRepository.findByExternalSessionIdOrderByLastActivityAtDesc("ATV_OTP")).thenReturn(List.of(session));
        when(voiceCallLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        voiceService.processInboundCallback("sessionId=ATV_OTP&callerNumber=%2B254700000099&dtmfDigits=654321");

        assertNull(existing.getDtmfDigits());
    }
}
