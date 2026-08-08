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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.campaigns.sms.data.MessageGatewayConfigurationData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppPhoneWhitelistServiceTest {

    @Mock
    private ExternalServicesPropertiesReadPlatformService propertiesReadPlatformService;

    @InjectMocks
    private WhatsAppPhoneWhitelistService whitelistService;

    private void stubConfig(final String whitelist, final boolean enabled) {
        when(propertiesReadPlatformService.getSMSGateway()).thenReturn(
                new MessageGatewayConfigurationData(null, null, null, 0, null, null, null, false, null, null, false, whitelist, enabled));
    }

    @Test
    void allowsEverythingWhenWhitelistDisabled() {
        stubConfig("+254708881885", false);

        assertTrue(whitelistService.isAllowed("+254799999999"));
    }

    @Test
    void allowsWhitelistedNumber() {
        stubConfig("+254708881885", true);

        assertTrue(whitelistService.isAllowed("+254708881885"));
    }

    @Test
    void matchesOnDigitsOnlySoFormattingDoesNotMatter() {
        stubConfig("+254708881885", true);

        assertTrue(whitelistService.isAllowed("254708881885"));
    }

    @Test
    void blocksNumberNotOnWhitelist() {
        stubConfig("+254708881885", true);

        assertFalse(whitelistService.isAllowed("+254799999999"));
    }

    @Test
    void blocksLocalFormatNumberBecauseMatchingIsDigitsOnly() {
        stubConfig("+254708881885", true);

        assertFalse(whitelistService.isAllowed("0708881885"));
    }

    @Test
    void blocksEverythingWhenWhitelistEnabledButEmpty() {
        stubConfig("", true);

        assertFalse(whitelistService.isAllowed("+254708881885"));
    }
}
