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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSetting;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppInteractiveSettingsProvider {

    private final WhatsAppInteractiveProperties properties;
    private final WhatsAppInteractiveSettingRepository settingRepository;

    private volatile Map<String, String> overrides = Map.of();
    private volatile boolean overridesLoaded;

    @Transactional(readOnly = true)
    public void reload() {
        final List<WhatsAppInteractiveSetting> settings = settingRepository.findAll();
        final Map<String, String> loaded = new HashMap<>();
        for (final WhatsAppInteractiveSetting setting : settings) {
            loaded.put(setting.getSettingKey(), setting.getSettingValue());
        }
        overrides = loaded;
        overridesLoaded = true;
    }

    private void ensureOverridesLoaded() {
        if (overridesLoaded || ThreadLocalContextUtil.getTenant() == null) {
            return;
        }
        try {
            reload();
        } catch (RuntimeException ex) {
            overrides = Map.of();
            overridesLoaded = true;
        }
    }

    public int getSessionTimeoutMinutes() {
        return getInt("sessionTimeoutMinutes", properties.getSessionTimeoutMinutes());
    }

    public String getDefaultLanguage() {
        return getString("defaultLanguage", properties.getDefaultLanguage());
    }

    public String getConsentTermsVersion() {
        return getString("consentTermsVersion", properties.getConsentTermsVersion());
    }

    public String getOptOutKeywords() {
        return getString("optOutKeywords", properties.getOptOutKeywords());
    }

    public String getOptInKeywords() {
        return getString("optInKeywords", properties.getOptInKeywords());
    }

    public String getConsentAcceptKeywords() {
        return getString("consentAcceptKeywords", properties.getConsentAcceptKeywords());
    }

    public String getMainMenuKeywords() {
        return getString("mainMenuKeywords", properties.getMainMenuKeywords());
    }

    public String getBackMenuKeywords() {
        return getString("backMenuKeywords", properties.getBackMenuKeywords());
    }

    public String getMainMenuKey() {
        return getString("mainMenuKey", properties.getMainMenuKey());
    }

    public int getAuthValidityMinutes() {
        return getInt("authValidityMinutes", properties.getAuthValidityMinutes());
    }

    public int getOtpLength() {
        return getInt("otpLength", properties.getOtpLength());
    }

    public int getOtpValidityMinutes() {
        return getInt("otpValidityMinutes", properties.getOtpValidityMinutes());
    }

    public int getMaxOtpAttempts() {
        return getInt("maxOtpAttempts", properties.getMaxOtpAttempts());
    }

    public boolean isPinAuthEnabled() {
        return getBoolean("pinAuthEnabled", properties.isPinAuthEnabled());
    }

    public int getSlaFirstResponseHours() {
        return getInt("slaFirstResponseHours", properties.getSlaFirstResponseHours());
    }

    public boolean isNotifyOnTicketCreated() {
        return getBoolean("notifyOnTicketCreated", properties.isNotifyOnTicketCreated());
    }

    public boolean isNotifyOnSlaBreach() {
        return getBoolean("notifyOnSlaBreach", properties.isNotifyOnSlaBreach());
    }

    public String getOperationalNotifyUserIds() {
        return getString("operationalNotifyUserIds", properties.getOperationalNotifyUserIds());
    }

    public boolean isStaffSelfServiceEnabled() {
        return getBoolean("staffSelfServiceEnabled", properties.isStaffSelfServiceEnabled());
    }

    public String getStaffMainMenuKey() {
        return getString("staffMainMenuKey", properties.getStaffMainMenuKey());
    }

    public String resolveMainMenuKey(final WhatsAppConversationType conversationType) {
        return conversationType == WhatsAppConversationType.STAFF ? getStaffMainMenuKey() : getMainMenuKey();
    }

    private String getString(final String key, final String defaultValue) {
        ensureOverridesLoaded();
        final String override = overrides.get(key);
        return StringUtils.isNotBlank(override) ? override : defaultValue;
    }

    private int getInt(final String key, final int defaultValue) {
        ensureOverridesLoaded();
        final String override = overrides.get(key);
        return NumberUtils.isParsable(override) ? Integer.parseInt(override) : defaultValue;
    }

    private boolean getBoolean(final String key, final boolean defaultValue) {
        ensureOverridesLoaded();
        final String override = overrides.get(key);
        if (StringUtils.isBlank(override)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(override);
    }
}
