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
package org.apache.fineract.infrastructure.notifications.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingVoiceService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.infrastructure.notifications.data.NotificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceNotificationChannelTest {

    @Mock
    private AfricasTalkingVoiceService voiceService;

    private VoiceNotificationChannel channel;

    @BeforeEach
    void setUp() {
        channel = new VoiceNotificationChannel(voiceService);
    }

    @Test
    void initiatesOutboundCallForVoiceNotification() {
        when(voiceService.initiateOutboundCall(any())).thenReturn(CommandProcessingResult.resourceResult(55L, null));

        final NotificationResult result = channel.send(NotificationCommand.outboundVoice(NotificationPurpose.TRANSACTIONAL,
                "+254700000001", RecipientType.CLIENT, null, null, "REMINDER", true));

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getResourceId()).isEqualTo(55L);
        verify(voiceService).initiateOutboundCall(any());
    }
}
