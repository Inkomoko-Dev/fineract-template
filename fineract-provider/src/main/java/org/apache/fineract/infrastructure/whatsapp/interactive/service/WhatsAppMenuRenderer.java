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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOption;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppMenuRenderer {

    private final WhatsAppMenuOptionRepository menuOptionRepository;

    @Transactional(readOnly = true)
    public String renderMenu(final String menuKey, final String languageCode, final String header) {
        final List<WhatsAppMenuOption> options = menuOptionRepository.findByMenuKeyAndLanguageCodeAndEnabledTrueOrderByOptionNumberAsc(menuKey,
                languageCode);
        final StringBuilder builder = new StringBuilder(header).append("\n");
        for (final WhatsAppMenuOption option : options) {
            builder.append(option.getOptionNumber()).append(". ").append(option.getOptionLabel()).append("\n");
        }
        return builder.toString().trim();
    }
}
