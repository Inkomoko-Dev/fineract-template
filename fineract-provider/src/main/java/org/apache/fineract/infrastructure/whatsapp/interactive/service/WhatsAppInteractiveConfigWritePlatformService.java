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
package org.apache.fineract.infrastructure.whatsapp.interactive.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConfigArea;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSetting;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppInteractiveConfigWritePlatformService {

    private static final Map<WhatsAppConfigArea, Set<String>> ALLOWED_KEYS = Map.of(
            WhatsAppConfigArea.SESSION, Set.of("sessionTimeoutMinutes"),
            WhatsAppConfigArea.LANGUAGE, Set.of("defaultLanguage"),
            WhatsAppConfigArea.CONSENT, Set.of("consentTermsVersion", "consentAcceptKeywords"),
            WhatsAppConfigArea.OPT_OUT, Set.of("optOutKeywords"),
            WhatsAppConfigArea.OPT_IN, Set.of("optInKeywords"),
            WhatsAppConfigArea.NAVIGATION, Set.of("mainMenuKeywords", "backMenuKeywords", "mainMenuKey", "staffSelfServiceEnabled",
                    "staffMainMenuKey"),
            WhatsAppConfigArea.AUTHENTICATION, Set.of("authValidityMinutes", "pinAuthEnabled"),
            WhatsAppConfigArea.OTP, Set.of("otpLength", "otpValidityMinutes", "maxOtpAttempts"),
            WhatsAppConfigArea.SLA, Set.of("slaFirstResponseHours"),
            WhatsAppConfigArea.OPERATIONAL_NOTIFICATIONS,
            Set.of("notifyOnTicketCreated", "notifyOnSlaBreach", "operationalNotifyUserIds"));

    private final FromJsonHelper fromJsonHelper;
    private final WhatsAppInteractiveSettingRepository settingRepository;
    private final WhatsAppInteractiveSettingsProvider settingsProvider;

    @Transactional
    public CommandProcessingResult updateConfigArea(final String areaCode, final String json) {
        final WhatsAppConfigArea area = WhatsAppConfigArea.valueOf(areaCode);
        final Set<String> allowedKeys = ALLOWED_KEYS.get(area);
        if (allowedKeys == null || allowedKeys.isEmpty()) {
            throw new IllegalArgumentException("Config area is managed externally: " + areaCode);
        }
        final JsonElement element = fromJsonHelper.parse(json);
        final JsonObject settings = element.getAsJsonObject().getAsJsonObject("settings");
        if (settings == null) {
            throw new IllegalArgumentException("settings object is required");
        }
        for (final String key : settings.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("Unsupported setting for area " + areaCode + ": " + key);
            }
            final String value = toSettingValue(settings.get(key));
            upsertSetting(key, value, area);
        }
        settingsProvider.reload();
        return new CommandProcessingResultBuilder().build();
    }

    private void upsertSetting(final String key, final String value, final WhatsAppConfigArea area) {
        final WhatsAppInteractiveSetting setting = settingRepository.findBySettingKey(key).orElseGet(WhatsAppInteractiveSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting.setConfigArea(area);
        settingRepository.save(setting);
    }

    private String toSettingValue(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            final JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return Boolean.toString(primitive.getAsBoolean());
            }
            return primitive.getAsString();
        }
        return element.toString();
    }
}
