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
package org.apache.fineract.infrastructure.notifications.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.notifications.channel.VoiceNotificationChannel;
import org.apache.fineract.infrastructure.notifications.channel.WhatsAppNotificationChannel;
import org.apache.fineract.infrastructure.notifications.constants.NotificationChannel;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.infrastructure.notifications.data.NotificationResult;
import org.apache.fineract.infrastructure.notifications.policy.NotificationPolicy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationPolicy notificationPolicy;
    private final WhatsAppNotificationChannel whatsAppNotificationChannel;
    private final VoiceNotificationChannel voiceNotificationChannel;

    public NotificationResult send(final NotificationCommand command) {
        if (!notificationPolicy.isAllowed(command)) {
            return NotificationResult.rejected("Outbound notification blocked by policy (consent/opt-out).");
        }
        if (command.getChannel() == NotificationChannel.WHATSAPP) {
            return whatsAppNotificationChannel.send(command);
        }
        if (command.getChannel() == NotificationChannel.VOICE) {
            return voiceNotificationChannel.send(command);
        }
        return NotificationResult.rejected("Unsupported notification channel: " + command.getChannel());
    }
}
