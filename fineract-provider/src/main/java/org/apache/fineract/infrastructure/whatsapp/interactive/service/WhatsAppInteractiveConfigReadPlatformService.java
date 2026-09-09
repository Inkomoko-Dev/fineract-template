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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConfigArea;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppInteractiveConfigAreaData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppInteractiveConfigReadPlatformService {

    private final WhatsAppInteractiveSettingsProvider settingsProvider;

    @Transactional(readOnly = true)
    public List<WhatsAppInteractiveConfigAreaData> retrieveConfigAreas() {
        final List<WhatsAppInteractiveConfigAreaData> areas = new ArrayList<>();
        areas.add(area(WhatsAppConfigArea.SESSION, "Session management", false, null, sessionSettings()));
        areas.add(area(WhatsAppConfigArea.LANGUAGE, "Language defaults", false, null, languageSettings()));
        areas.add(area(WhatsAppConfigArea.CONSENT, "Consent capture", false, null, consentSettings()));
        areas.add(area(WhatsAppConfigArea.OPT_OUT, "Opt-out keywords", false, null, optOutSettings()));
        areas.add(area(WhatsAppConfigArea.OPT_IN, "Opt-in keywords", false, null, optInSettings()));
        areas.add(area(WhatsAppConfigArea.NAVIGATION, "Menu navigation", false, null, navigationSettings()));
        areas.add(area(WhatsAppConfigArea.AUTHENTICATION, "Client authentication", false, null, authSettings()));
        areas.add(area(WhatsAppConfigArea.OTP, "OTP verification", false, null, otpSettings()));
        areas.add(area(WhatsAppConfigArea.SLA, "Advisor SLA", false, null, slaSettings()));
        areas.add(area(WhatsAppConfigArea.BUSINESS_HOURS, "Business hours", true, "/whatsapp/interactive/business-hours", Map.of()));
        areas.add(area(WhatsAppConfigArea.MENUS, "Interactive menus", true, "/whatsapp/interactive/menus/definitions", Map.of()));
        areas.add(area(WhatsAppConfigArea.ADVISOR_INBOX, "Advisor inbox", true, "/whatsapp/interactive/tickets", Map.of()));
        areas.add(area(WhatsAppConfigArea.OPERATIONAL_NOTIFICATIONS, "Operational notifications", false, null, operationalSettings()));
        return areas;
    }

    private WhatsAppInteractiveConfigAreaData area(final WhatsAppConfigArea area, final String label, final boolean managedExternally,
            final String externalEndpoint, final Map<String, Object> areaSettings) {
        final WhatsAppInteractiveConfigAreaData data = new WhatsAppInteractiveConfigAreaData();
        data.setArea(area);
        data.setLabel(label);
        data.setManagedExternally(managedExternally);
        data.setExternalEndpoint(externalEndpoint);
        data.setSettings(areaSettings);
        return data;
    }

    private Map<String, Object> sessionSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("sessionTimeoutMinutes", settingsProvider.getSessionTimeoutMinutes());
        return areaSettings;
    }

    private Map<String, Object> languageSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("defaultLanguage", settingsProvider.getDefaultLanguage());
        return areaSettings;
    }

    private Map<String, Object> consentSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("consentTermsVersion", settingsProvider.getConsentTermsVersion());
        areaSettings.put("consentAcceptKeywords", settingsProvider.getConsentAcceptKeywords());
        return areaSettings;
    }

    private Map<String, Object> optOutSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("optOutKeywords", settingsProvider.getOptOutKeywords());
        return areaSettings;
    }

    private Map<String, Object> optInSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("optInKeywords", settingsProvider.getOptInKeywords());
        return areaSettings;
    }

    private Map<String, Object> navigationSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("mainMenuKeywords", settingsProvider.getMainMenuKeywords());
        areaSettings.put("backMenuKeywords", settingsProvider.getBackMenuKeywords());
        areaSettings.put("mainMenuKey", settingsProvider.getMainMenuKey());
        areaSettings.put("staffSelfServiceEnabled", settingsProvider.isStaffSelfServiceEnabled());
        areaSettings.put("staffMainMenuKey", settingsProvider.getStaffMainMenuKey());
        return areaSettings;
    }

    private Map<String, Object> authSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("authValidityMinutes", settingsProvider.getAuthValidityMinutes());
        areaSettings.put("pinAuthEnabled", settingsProvider.isPinAuthEnabled());
        return areaSettings;
    }

    private Map<String, Object> otpSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("otpLength", settingsProvider.getOtpLength());
        areaSettings.put("otpValidityMinutes", settingsProvider.getOtpValidityMinutes());
        areaSettings.put("maxOtpAttempts", settingsProvider.getMaxOtpAttempts());
        return areaSettings;
    }

    private Map<String, Object> slaSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("slaFirstResponseHours", settingsProvider.getSlaFirstResponseHours());
        return areaSettings;
    }

    private Map<String, Object> operationalSettings() {
        final Map<String, Object> areaSettings = new LinkedHashMap<>();
        areaSettings.put("notifyOnTicketCreated", settingsProvider.isNotifyOnTicketCreated());
        areaSettings.put("notifyOnSlaBreach", settingsProvider.isNotifyOnSlaBreach());
        areaSettings.put("operationalNotifyUserIds", settingsProvider.getOperationalNotifyUserIds());
        return areaSettings;
    }
}
