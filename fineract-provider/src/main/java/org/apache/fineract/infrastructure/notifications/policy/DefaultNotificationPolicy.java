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
        return denialCode(command.getChannel(), command.getPhoneNumber(), command.getPurpose()) == null;
    }

    @Override
    public boolean isAllowed(final NotificationChannel channel, final String phoneNumber, final NotificationPurpose purpose) {
        return denialCode(channel, phoneNumber, purpose) == null;
    }

    @Override
    public String denialCode(final NotificationCommand command) {
        return denialCode(command.getChannel(), command.getPhoneNumber(), command.getPurpose());
    }

    @Override
    public String denialCode(final NotificationChannel channel, final String phoneNumber, final NotificationPurpose purpose) {
        if (channel != NotificationChannel.WHATSAPP || phoneNumber == null) {
            return null;
        }
        if (purpose == NotificationPurpose.TRANSACTIONAL) {
            return null;
        }
        if (whatsAppOptOutService.isOptedOut(phoneNumber)) {
            return "OPTED_OUT";
        }
        if (purpose == NotificationPurpose.CAMPAIGN) {
            // Campaign broadcasts: allow when no consent row exists; still honor opt-out above and explicit denial.
            return whatsAppConsentService.hasActiveConsentOrNotRecorded(phoneNumber) ? null : "CONSENT_DENIED";
        }
        if (purpose == NotificationPurpose.HOOK || purpose == NotificationPurpose.AD_HOC) {
            // Business-initiated hook / ad-hoc requires an explicit consent row.
            return whatsAppConsentService.hasActiveConsent(phoneNumber) ? null : "CONSENT_REQUIRED";
        }
        return null;
    }
}
