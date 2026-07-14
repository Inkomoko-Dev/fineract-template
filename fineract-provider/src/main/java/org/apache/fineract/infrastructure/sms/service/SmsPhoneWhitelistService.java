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
package org.apache.fineract.infrastructure.sms.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.campaigns.sms.data.MessageGatewayConfigurationData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.apache.fineract.infrastructure.sms.data.SmsMessageApiQueueResourceData;
import org.apache.fineract.infrastructure.sms.domain.SmsMessage;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageStatusType;
import org.springframework.stereotype.Service;

/**
 * When MESSAGE_GATEWAY sms_whitelist_enabled is true, only allow SMS to numbers in sms_whitelist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsPhoneWhitelistService {

    public static final String BLOCKED_ERROR_MESSAGE = "SMS not sent: recipient is not on the QA whitelist";

    private final ExternalServicesPropertiesReadPlatformService propertiesReadPlatformService;
    private final SmsMessageRepository smsMessageRepository;

    public boolean isWhitelistEnforced() {
        MessageGatewayConfigurationData config = this.propertiesReadPlatformService.getSMSGateway();
        return config != null && config.isSmsWhitelistEnabled();
    }

    public Collection<SmsMessageApiQueueResourceData> filterAllowedOrMarkBlocked(
            Collection<SmsMessageApiQueueResourceData> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        MessageGatewayConfigurationData config = this.propertiesReadPlatformService.getSMSGateway();
        if (config == null || !config.isSmsWhitelistEnabled()) {
            return messages;
        }

        Set<String> whitelist = parseWhitelist(config.getSmsWhitelist());
        if (whitelist.isEmpty()) {
            log.warn("SMS whitelist enforcement is enabled but sms_whitelist is empty; blocking all SMS");
        }

        return messages.stream().map(message -> {
            if (isAllowed(message.getPhoneNumber(), whitelist)) {
                return message;
            }
            markBlocked(message.getInternalId());
            log.info("Blocked outbound SMS id {} to non-whitelisted number", message.getInternalId());
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public boolean isAllowed(String phoneNumber) {
        MessageGatewayConfigurationData config = this.propertiesReadPlatformService.getSMSGateway();
        if (config == null || !config.isSmsWhitelistEnabled()) {
            return true;
        }
        return isAllowed(phoneNumber, parseWhitelist(config.getSmsWhitelist()));
    }

    Set<String> parseWhitelist(String rawWhitelist) {
        if (StringUtils.isBlank(rawWhitelist)) {
            return Set.of();
        }
        return Arrays.stream(rawWhitelist.split("[,;\\s]+")).map(this::normalizePhoneNumber)
                .filter(StringUtils::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        String trimmed = phoneNumber.trim().replace(" ", "");
        if (trimmed.startsWith("00")) {
            trimmed = "+" + trimmed.substring(2);
        }
        return trimmed;
    }

    private boolean isAllowed(String phoneNumber, Set<String> whitelist) {
        String normalized = normalizePhoneNumber(phoneNumber);
        if (StringUtils.isBlank(normalized) || whitelist.isEmpty()) {
            return false;
        }
        String digitsOnly = digitsOnly(normalized);
        for (String allowed : whitelist) {
            if (normalized.equalsIgnoreCase(allowed) || digitsOnly.equals(digitsOnly(allowed))) {
                return true;
            }
        }
        return false;
    }

    private String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }

    private void markBlocked(Long internalId) {
        if (internalId == null) {
            return;
        }
        Optional<SmsMessage> optionalSms = smsMessageRepository.findById(internalId);
        if (optionalSms.isEmpty()) {
            return;
        }
        SmsMessage sms = optionalSms.get();
        sms.setStatusType(SmsMessageStatusType.FAILED.getValue());
        sms.setErrorMessage(BLOCKED_ERROR_MESSAGE);
        smsMessageRepository.saveAndFlush(sms);
    }
}
