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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.campaigns.sms.data.MessageGatewayConfigurationData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.springframework.stereotype.Service;

/**
 * When MESSAGE_GATEWAY whatsapp_whitelist_enabled is true, only allow WhatsApp to numbers in whatsapp_whitelist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppPhoneWhitelistService {

    public static final String BLOCKED_ERROR_MESSAGE = "WhatsApp not sent: recipient is not on the QA whitelist";

    private final ExternalServicesPropertiesReadPlatformService propertiesReadPlatformService;

    public boolean isWhitelistEnforced() {
        final MessageGatewayConfigurationData config = this.propertiesReadPlatformService.getSMSGateway();
        return config != null && config.isWhatsappWhitelistEnabled();
    }

    public boolean isAllowed(final String phoneNumber) {
        final MessageGatewayConfigurationData config = this.propertiesReadPlatformService.getSMSGateway();
        if (config == null || !config.isWhatsappWhitelistEnabled()) {
            return true;
        }
        final Set<String> whitelist = parseWhitelist(config.getWhatsappWhitelist());
        if (whitelist.isEmpty()) {
            log.warn("WhatsApp whitelist enforcement is enabled but whatsapp_whitelist is empty; blocking all WhatsApp messages");
        }
        return isAllowed(phoneNumber, whitelist);
    }

    Set<String> parseWhitelist(final String rawWhitelist) {
        if (StringUtils.isBlank(rawWhitelist)) {
            return Set.of();
        }
        return Arrays.stream(rawWhitelist.split("[,;\\s]+")).map(this::normalizePhoneNumber).filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    String normalizePhoneNumber(final String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        String trimmed = phoneNumber.trim().replace(" ", "");
        if (trimmed.startsWith("00")) {
            trimmed = "+" + trimmed.substring(2);
        }
        return trimmed;
    }

    private boolean isAllowed(final String phoneNumber, final Set<String> whitelist) {
        final String normalized = normalizePhoneNumber(phoneNumber);
        if (StringUtils.isBlank(normalized) || whitelist.isEmpty()) {
            return false;
        }
        final String digitsOnly = digitsOnly(normalized);
        for (final String allowed : whitelist) {
            if (normalized.equalsIgnoreCase(allowed) || digitsOnly.equals(digitsOnly(allowed))) {
                return true;
            }
        }
        return false;
    }

    private String digitsOnly(final String value) {
        return value.replaceAll("\\D", "");
    }
}
