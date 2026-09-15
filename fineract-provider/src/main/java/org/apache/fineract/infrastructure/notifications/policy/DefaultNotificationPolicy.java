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
package org.apache.fineract.infrastructure.notifications.policy;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.notifications.constants.NotificationChannel;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppConsentService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppOptOutService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultNotificationPolicy implements NotificationPolicy {

    private final WhatsAppOptOutService whatsAppOptOutService;
    private final WhatsAppConsentService whatsAppConsentService;

    @Override
    public boolean isAllowed(final NotificationCommand command) {
        return isAllowed(command.getChannel(), command.getPhoneNumber(), command.getPurpose());
    }

    @Override
    public boolean isAllowed(final NotificationChannel channel, final String phoneNumber, final NotificationPurpose purpose) {
        if (channel != NotificationChannel.WHATSAPP || phoneNumber == null) {
            return true;
        }
        if (purpose == NotificationPurpose.TRANSACTIONAL) {
            return true;
        }
        if (whatsAppOptOutService.isOptedOut(phoneNumber)) {
            return false;
        }
        if (purpose == NotificationPurpose.CAMPAIGN || purpose == NotificationPurpose.HOOK || purpose == NotificationPurpose.AD_HOC) {
            return whatsAppConsentService.hasActiveConsent(phoneNumber);
        }
        return true;
    }
}
