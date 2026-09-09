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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.RandomOTPGenerator;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppAuthChallenge;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppAuthChallengeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppOtpService {

    private final WhatsAppAuthChallengeRepository authChallengeRepository;
    private final WhatsAppInteractiveSettingsProvider settings;

    @Transactional
    public String issueOtp(final String phoneNumber, final Long clientId) {
        final String otp = new RandomOTPGenerator(settings.getOtpLength()).generate();
        final WhatsAppAuthChallenge challenge = new WhatsAppAuthChallenge();
        challenge.setPhoneNumber(phoneNumber);
        challenge.setClientId(clientId);
        challenge.setOtpHash(hashOtp(phoneNumber, otp));
        challenge.setAttemptCount(0);
        challenge.setExpiresAt(DateUtils.getLocalDateTimeOfTenant().plusMinutes(settings.getOtpValidityMinutes()));
        challenge.setCreatedDate(DateUtils.getLocalDateTimeOfTenant());
        authChallengeRepository.save(challenge);
        return otp;
    }

    @Transactional
    public boolean verifyOtp(final String phoneNumber, final String otpCandidate) {
        if (StringUtils.isBlank(otpCandidate)) {
            return false;
        }
        final WhatsAppAuthChallenge challenge = authChallengeRepository.findFirstByPhoneNumberAndVerifiedFalseOrderByCreatedDateDesc(phoneNumber)
                .orElse(null);
        if (challenge == null) {
            return false;
        }
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        if (challenge.getExpiresAt().isBefore(now)) {
            return false;
        }
        if (challenge.getAttemptCount() >= settings.getMaxOtpAttempts()) {
            return false;
        }
        challenge.setAttemptCount(challenge.getAttemptCount() + 1);
        final boolean matches = challenge.getOtpHash().equals(hashOtp(phoneNumber, otpCandidate.trim()));
        if (matches) {
            challenge.setVerified(true);
        }
        authChallengeRepository.save(challenge);
        return matches;
    }

    private String hashOtp(final String phoneNumber, final String otp) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest((phoneNumber + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
