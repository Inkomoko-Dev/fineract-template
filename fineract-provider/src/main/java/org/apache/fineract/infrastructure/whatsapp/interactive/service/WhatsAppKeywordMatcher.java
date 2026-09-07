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

import java.util.Arrays;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppKeywordMatcher {

    private final WhatsAppInteractiveSettingsProvider settings;

    public WhatsAppKeywordMatcher(final WhatsAppInteractiveSettingsProvider settings) {
        this.settings = settings;
    }

    public boolean matchesOptOut(final String text) {
        return matchesAny(text, settings.getOptOutKeywords());
    }

    public boolean matchesOptIn(final String text) {
        return matchesAny(text, settings.getOptInKeywords());
    }

    public boolean matchesConsentAccept(final String text) {
        return matchesAny(text, settings.getConsentAcceptKeywords());
    }

    public boolean matchesMainMenu(final String text) {
        return matchesAny(text, settings.getMainMenuKeywords());
    }

    public boolean matchesBackMenu(final String text) {
        return matchesAny(text, settings.getBackMenuKeywords());
    }

    private boolean matchesAny(final String text, final String commaSeparatedKeywords) {
        if (StringUtils.isBlank(text) || StringUtils.isBlank(commaSeparatedKeywords)) {
            return false;
        }
        final String normalized = text.trim().toUpperCase(Locale.ENGLISH);
        return Arrays.stream(commaSeparatedKeywords.split(",")).map(String::trim).filter(StringUtils::isNotBlank)
                .anyMatch(keyword -> normalized.equals(keyword.toUpperCase(Locale.ENGLISH)));
    }
}
