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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppAuthChallenge;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppAuthChallengeRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppOtpServiceTest {

    @Mock
    private WhatsAppAuthChallengeRepository authChallengeRepository;

    private WhatsAppOtpService otpService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        final WhatsAppInteractiveProperties properties = new WhatsAppInteractiveProperties();
        properties.setOtpLength(6);
        properties.setOtpValidityMinutes(5);
        properties.setMaxOtpAttempts(3);
        final WhatsAppInteractiveSettingsProvider settings = new WhatsAppInteractiveSettingsProvider(properties,
                org.mockito.Mockito.mock(WhatsAppInteractiveSettingRepository.class));
        otpService = new WhatsAppOtpService(authChallengeRepository, settings);
    }

    @Test
    void verifiesMatchingOtp() {
        final String phone = "+254712345678";
        final ArgumentCaptor<WhatsAppAuthChallenge> captor = ArgumentCaptor.forClass(WhatsAppAuthChallenge.class);
        when(authChallengeRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        final String otp = otpService.issueOtp(phone, 10L);
        final WhatsAppAuthChallenge saved = captor.getValue();
        when(authChallengeRepository.findFirstByPhoneNumberAndVerifiedFalseOrderByCreatedDateDesc(phone)).thenReturn(Optional.of(saved));

        assertTrue(otpService.verifyOtp(phone, otp));
    }

    @Test
    void rejectsInvalidOtp() {
        final String phone = "+254712345678";
        final WhatsAppAuthChallenge challenge = new WhatsAppAuthChallenge();
        challenge.setOtpHash("invalid");
        challenge.setAttemptCount(0);
        challenge.setExpiresAt(DateUtils.getLocalDateTimeOfTenant().plusMinutes(5));
        when(authChallengeRepository.findFirstByPhoneNumberAndVerifiedFalseOrderByCreatedDateDesc(phone)).thenReturn(Optional.of(challenge));
        when(authChallengeRepository.save(any())).thenReturn(challenge);

        assertFalse(otpService.verifyOtp(phone, "000000"));
    }
}
