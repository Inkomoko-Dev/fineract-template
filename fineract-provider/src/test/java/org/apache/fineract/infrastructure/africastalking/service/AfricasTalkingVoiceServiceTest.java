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

import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLog;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLogRepository;
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

    private AfricasTalkingProperties properties;
    private AfricasTalkingVoiceService voiceService;

    @BeforeEach
    void setUp() {
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
                recipientResolutionService, phoneNumberNormalizer, clientRepositoryWrapper, staffRepositoryWrapper);
    }

    @Test
    void returnsMainMenuWhenNoDtmfDigits() {
        final String xml = voiceService.buildIvrResponse("sessionId=ATV_1&callerNumber=%2B254700000099");
        assertTrue(xml.contains("<GetDigits"));
    }

    @Test
    void routesLoansSelectionToDial() {
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
}
