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
package org.apache.fineract.infrastructure.whatsapp.provider;

import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.whatsapp.provider.config.WhatsAppProviderProperties;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppLanguageMapper {

    private static final Map<String, String> SHORT_TO_META = Map.of("en", "en_US", "rw", "rw_RW", "fr", "fr_FR", "sw", "sw_KE", "es",
            "es_ES");

    private final WhatsAppProviderProperties properties;

    public WhatsAppLanguageMapper(final WhatsAppProviderProperties properties) {
        this.properties = properties;
    }

    public String toMetaLanguageCode(final String languageCode) {
        if (StringUtils.isBlank(languageCode)) {
            return properties.getLanguage().getDefaultMetaCode();
        }
        final String trimmed = languageCode.trim();
        if (trimmed.contains("_")) {
            return trimmed;
        }
        return SHORT_TO_META.getOrDefault(trimmed.toLowerCase(Locale.ROOT), properties.getLanguage().getDefaultMetaCode());
    }

    public String toAfricaTalkingLanguageCode(final String languageCode) {
        if (StringUtils.isBlank(languageCode)) {
            return "en";
        }
        final String trimmed = languageCode.trim();
        final int underscore = trimmed.indexOf('_');
        return underscore > 0 ? trimmed.substring(0, underscore) : trimmed;
    }
}
