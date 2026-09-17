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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOption;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceIvrMenuRenderer {

    private final VoiceIvrMenuOptionRepository menuOptionRepository;
    private final VoiceIvrMenuDefinitionService menuDefinitionService;

    @Transactional(readOnly = true)
    public String buildMenuPrompt(final String menuKey, final String languageCode) {
        final String intro = StringUtils.defaultString(menuDefinitionService.resolvePrompt(menuKey, languageCode));
        final List<VoiceIvrMenuOption> options = menuOptionRepository
                .findByMenuKeyAndLanguageCodeAndEnabledTrueOrderByOptionDigitAsc(menuKey, languageCode);
        final StringBuilder builder = new StringBuilder(intro.trim());
        for (int index = 0; index < options.size(); index++) {
            final VoiceIvrMenuOption option = options.get(index);
            if (index == 0 && builder.length() > 0) {
                builder.append(' ');
            } else if (index > 0) {
                builder.append(", ");
            }
            builder.append("Press ").append(option.getOptionDigit()).append(" for ").append(option.getOptionLabel());
        }
        if (!options.isEmpty()) {
            builder.append('.');
        }
        return builder.toString().trim();
    }
}
