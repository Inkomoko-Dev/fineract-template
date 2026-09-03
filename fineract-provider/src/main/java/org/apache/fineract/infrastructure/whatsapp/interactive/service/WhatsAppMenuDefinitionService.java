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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuDefinition;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppMenuDefinitionService {

    private final WhatsAppMenuDefinitionRepository menuDefinitionRepository;
    private final WhatsAppInteractiveProperties properties;

    @Transactional(readOnly = true)
    public String resolveHeader(final String menuKey, final String languageCode) {
        final String language = StringUtils.defaultIfBlank(languageCode, properties.getDefaultLanguage());
        return menuDefinitionRepository.findByMenuKeyAndLanguageCodeAndEnabledTrue(menuKey, language)
                .map(WhatsAppMenuDefinition::getHeaderText)
                .orElseGet(() -> menuDefinitionRepository.findByMenuKeyAndLanguageCodeAndEnabledTrue(menuKey, properties.getDefaultLanguage())
                        .map(WhatsAppMenuDefinition::getHeaderText).orElse(null));
    }

    @Transactional(readOnly = true)
    public String resolveParentMenuKey(final String menuKey, final String languageCode) {
        final String language = StringUtils.defaultIfBlank(languageCode, properties.getDefaultLanguage());
        return menuDefinitionRepository.findByMenuKeyAndLanguageCodeAndEnabledTrue(menuKey, language)
                .map(WhatsAppMenuDefinition::getParentMenuKey).orElse(null);
    }
}
