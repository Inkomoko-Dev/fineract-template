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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLog;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLogRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrAuthConstants;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallQueueEntry;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSessionContextSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoiceRecordingConsentServiceTest {

    @Mock
    private VoiceIvrSessionService sessionService;
    @Mock
    private VoiceCallQueueService queueService;
    @Mock
    private VoiceIvrDialTargetResolver dialTargetResolver;
    @Mock
    private VoiceCallLogRepository voiceCallLogRepository;
    @Mock
    private WhatsAppSessionContextSerializer contextSerializer;

    private AfricasTalkingProperties properties;
    private VoiceRecordingConsentService consentService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        properties = new AfricasTalkingProperties();
        properties.getVoice().setRecordingConsentRequired(true);
        consentService = new VoiceRecordingConsentService(sessionService, queueService, dialTargetResolver, voiceCallLogRepository,
                contextSerializer, properties);
    }

    @Test
    void beginsRecordingConsentPromptBeforeConnecting() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setExternalSessionId("ATV_CONSENT");
        session.setLanguageCode("en");
        final VoiceCallQueueEntry entry = new VoiceCallQueueEntry();
        ReflectionTestUtils.setField(entry, "id", 9L);
        final WhatsAppSessionContext context = WhatsAppSessionContext.empty();
        when(contextSerializer.fromJson(any())).thenReturn(context);
        when(contextSerializer.toJson(any())).thenReturn("{}");
        when(sessionService.save(session)).thenReturn(session);
        when(voiceCallLogRepository.findByExternalSessionId("ATV_CONSENT")).thenReturn(Optional.of(new VoiceCallLog()));

        final String xml = consentService.beginConsent(session, "LOANS", "Loans", entry, "en");

        assertThat(xml).contains("<GetDigits");
        assertThat(xml).contains("Press 1 to consent");
        assertThat(session.getSessionStatus()).isEqualTo(VoiceIvrSessionStatus.RECORDING_CONSENT);
        assertThat(context.getPendingAction()).isEqualTo(VoiceIvrAuthConstants.PENDING_RECORDING_CONSENT);
    }

    @Test
    void connectsWithRecordingWhenCallerConsents() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setExternalSessionId("ATV_CONSENT_2");
        session.setLanguageCode("en");
        final WhatsAppSessionContext context = new WhatsAppSessionContext();
        context.setPendingAction(VoiceIvrAuthConstants.PENDING_RECORDING_CONSENT);
        context.setPendingDepartmentCode("LOANS");
        context.setPendingDepartmentLabel("Loans");
        context.setPendingQueueEntryId(9L);
        session.setSessionContext("{}");
        final VoiceCallLog callLog = new VoiceCallLog();
        when(contextSerializer.fromJson(any())).thenReturn(context);
        when(contextSerializer.toJson(any())).thenReturn("{}");
        when(dialTargetResolver.resolve("LOANS")).thenReturn("+254700000001");
        when(voiceCallLogRepository.findByExternalSessionId("ATV_CONSENT_2")).thenReturn(Optional.of(callLog));
        when(voiceCallLogRepository.save(callLog)).thenReturn(callLog);

        final String xml = consentService.handleConsentInput(session, "1");

        assertThat(xml).contains("record=\"true\"");
        assertThat(callLog.getRecordingConsentGiven()).isTrue();
        verify(queueService).markConnectingById(9L);
        verify(sessionService).markClosed(session);
    }

    @Test
    void connectsWithoutRecordingWhenCallerDeclines() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setExternalSessionId("ATV_CONSENT_3");
        session.setLanguageCode("en");
        final WhatsAppSessionContext context = new WhatsAppSessionContext();
        context.setPendingAction(VoiceIvrAuthConstants.PENDING_RECORDING_CONSENT);
        context.setPendingDepartmentCode("LOANS");
        context.setPendingDepartmentLabel("Loans");
        final VoiceCallLog callLog = new VoiceCallLog();
        when(contextSerializer.fromJson(any())).thenReturn(context);
        when(contextSerializer.toJson(any())).thenReturn("{}");
        when(dialTargetResolver.resolve("LOANS")).thenReturn("+254700000001");
        when(voiceCallLogRepository.findByExternalSessionId("ATV_CONSENT_3")).thenReturn(Optional.of(callLog));
        when(voiceCallLogRepository.save(callLog)).thenReturn(callLog);

        final String xml = consentService.handleConsentInput(session, "2");

        assertThat(xml).contains("record=\"false\"");
        assertThat(callLog.getRecordingConsentGiven()).isFalse();
    }
}
