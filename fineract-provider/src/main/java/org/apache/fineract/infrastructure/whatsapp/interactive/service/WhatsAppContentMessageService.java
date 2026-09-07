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
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppContentMessage;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppContentMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppContentMessageService {

    private final WhatsAppContentMessageRepository contentMessageRepository;
    private final WhatsAppInteractiveSettingsProvider settings;

    @Transactional(readOnly = true)
    public String resolveBody(final String contentKey, final String languageCode) {
        final String language = StringUtils.defaultIfBlank(languageCode, settings.getDefaultLanguage());
        return contentMessageRepository.findByContentKeyAndLanguageCodeAndEnabledTrue(contentKey, language)
                .map(WhatsAppContentMessage::getBodyText)
                .orElseGet(() -> contentMessageRepository.findByContentKeyAndLanguageCodeAndEnabledTrue(contentKey,
                        settings.getDefaultLanguage()).map(WhatsAppContentMessage::getBodyText).orElse(null));
    }
}
