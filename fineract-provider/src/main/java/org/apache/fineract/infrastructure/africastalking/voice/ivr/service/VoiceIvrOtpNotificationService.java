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

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingClient;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveMessages;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceIvrOtpNotificationService {

    private final AfricasTalkingClient africasTalkingClient;
    private final AfricasTalkingProperties properties;

    public void sendOtp(final String phoneNumber, final String otp, final String languageCode) {
        if (!properties.isConfigured()) {
            log.warn("Skipping voice IVR OTP delivery because AfricasTalking is not configured");
            return;
        }
        try {
            africasTalkingClient.sendWhatsAppMessage(phoneNumber, WhatsAppInteractiveMessages.otpIssued(languageCode, otp));
        } catch (final IOException e) {
            log.warn("Failed to deliver voice IVR OTP via WhatsApp", e);
        }
    }
}
