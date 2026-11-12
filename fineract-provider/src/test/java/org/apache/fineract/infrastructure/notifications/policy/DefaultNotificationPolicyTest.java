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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.notifications.constants.NotificationChannel;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppConsentService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppOptOutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultNotificationPolicyTest {

    @Mock
    private WhatsAppOptOutService whatsAppOptOutService;

    @Mock
    private WhatsAppConsentService whatsAppConsentService;

    @InjectMocks
    private DefaultNotificationPolicy policy;

    @Test
    void campaignAllowsWhenConsentNotRecorded() {
        when(whatsAppOptOutService.isOptedOut("+254700000001")).thenReturn(false);
        when(whatsAppConsentService.hasActiveConsentOrNotRecorded("+254700000001")).thenReturn(true);

        assertTrue(policy.isAllowed(NotificationChannel.WHATSAPP, "+254700000001", NotificationPurpose.CAMPAIGN));
    }

    @Test
    void hookRequiresExplicitConsent() {
        when(whatsAppOptOutService.isOptedOut("+254700000002")).thenReturn(false);
        when(whatsAppConsentService.hasActiveConsent("+254700000002")).thenReturn(false);

        assertFalse(policy.isAllowed(NotificationChannel.WHATSAPP, "+254700000002", NotificationPurpose.HOOK));
        assertEquals("CONSENT_REQUIRED", policy.denialCode(NotificationChannel.WHATSAPP, "+254700000002", NotificationPurpose.HOOK));
    }

    @Test
    void adHocRequiresExplicitConsent() {
        when(whatsAppOptOutService.isOptedOut("+254700000003")).thenReturn(false);
        when(whatsAppConsentService.hasActiveConsent("+254700000003")).thenReturn(true);

        assertTrue(policy.isAllowed(NotificationChannel.WHATSAPP, "+254700000003", NotificationPurpose.AD_HOC));
        assertNull(policy.denialCode(NotificationChannel.WHATSAPP, "+254700000003", NotificationPurpose.AD_HOC));
    }

    @Test
    void optedOutBlocksAdHoc() {
        when(whatsAppOptOutService.isOptedOut("+254700000004")).thenReturn(true);

        assertFalse(policy.isAllowed(NotificationChannel.WHATSAPP, "+254700000004", NotificationPurpose.AD_HOC));
        assertEquals("OPTED_OUT", policy.denialCode(NotificationChannel.WHATSAPP, "+254700000004", NotificationPurpose.AD_HOC));
    }
}
