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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingClient;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingClient.AfricasTalkingApiResponse;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrAuthConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceIvrOtpNotificationServiceTest {

    @Mock
    private AfricasTalkingClient africasTalkingClient;

    @Mock
    private AfricasTalkingProperties properties;

    private VoiceIvrOtpNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new VoiceIvrOtpNotificationService(africasTalkingClient, properties);
    }

    @Test
    void deliversOtpViaSmsOnly() throws IOException {
        when(properties.isConfigured()).thenReturn(true);
        when(africasTalkingClient.sendSms(eq("+254700000099"), anyString()))
                .thenReturn(new AfricasTalkingApiResponse(201, "{\"SMSMessageData\":{\"Recipients\":[{\"status\":\"Success\",\"statusCode\":100}]}}"));

        final boolean sent = notificationService.sendOtp("+254700000099", "654321", "en");

        assertThat(sent).isTrue();
        assertThat(VoiceIvrAuthConstants.OTP_DELIVERY_CHANNEL).isEqualTo("SMS");
        verify(africasTalkingClient).sendSms(eq("+254700000099"), anyString());
    }

    @Test
    void propagatesSmsDeliveryFailure() throws IOException {
        when(properties.isConfigured()).thenReturn(true);
        when(africasTalkingClient.sendSms(eq("+254700000099"), anyString()))
                .thenReturn(new AfricasTalkingApiResponse(201, "{\"SMSMessageData\":{\"Recipients\":[{\"status\":\"Failed\",\"statusCode\":403}]}}"));

        assertThat(notificationService.sendOtp("+254700000099", "654321", "en")).isFalse();
    }

    @Test
    void skipsWhenNotConfigured() throws IOException {
        when(properties.isConfigured()).thenReturn(false);

        assertThat(notificationService.sendOtp("+254700000099", "654321", "en")).isFalse();
        verify(africasTalkingClient, never()).sendSms(anyString(), anyString());
    }
}
