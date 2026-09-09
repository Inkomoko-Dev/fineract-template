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
package org.apache.fineract.infrastructure.africastalking.voice.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceIvrMenuDefinitionData;
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceIvrMenuOptionData;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuDefinition;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuDefinitionRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOption;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceIvrMenuReadPlatformService {

    private final VoiceIvrMenuDefinitionRepository menuDefinitionRepository;
    private final VoiceIvrMenuOptionRepository menuOptionRepository;

    @Transactional(readOnly = true)
    public List<VoiceIvrMenuDefinitionData> retrieveMenuDefinitions(final String menuKey) {
        final List<VoiceIvrMenuDefinition> definitions = StringUtils.isBlank(menuKey)
                ? menuDefinitionRepository.findAllByOrderByMenuKeyAscLanguageCodeAsc()
                : menuDefinitionRepository.findByMenuKeyOrderByLanguageCodeAsc(menuKey);
        return definitions.stream().map(this::mapDefinition).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VoiceIvrMenuOptionData> retrieveMenuOptions(final String menuKey, final String languageCode) {
        final List<VoiceIvrMenuOption> options;
        if (StringUtils.isNotBlank(menuKey) && StringUtils.isNotBlank(languageCode)) {
            options = menuOptionRepository.findByMenuKeyAndLanguageCodeOrderByOptionDigitAsc(menuKey, languageCode);
        } else if (StringUtils.isNotBlank(menuKey)) {
            options = menuOptionRepository.findByMenuKeyOrderByLanguageCodeAscOptionDigitAsc(menuKey);
        } else if (StringUtils.isNotBlank(languageCode)) {
            options = menuOptionRepository.findByLanguageCodeOrderByMenuKeyAscOptionDigitAsc(languageCode);
        } else {
            options = menuOptionRepository.findAllByOrderByMenuKeyAscLanguageCodeAscOptionDigitAsc();
        }
        return options.stream().map(this::mapOption).collect(Collectors.toList());
    }

    private VoiceIvrMenuDefinitionData mapDefinition(final VoiceIvrMenuDefinition definition) {
        final VoiceIvrMenuDefinitionData data = new VoiceIvrMenuDefinitionData();
        data.setId(definition.getId());
        data.setMenuKey(definition.getMenuKey());
        data.setLanguageCode(definition.getLanguageCode());
        data.setPromptText(definition.getPromptText());
        data.setParentMenuKey(definition.getParentMenuKey());
        data.setEnabled(definition.isEnabled());
        return data;
    }

    private VoiceIvrMenuOptionData mapOption(final VoiceIvrMenuOption option) {
        final VoiceIvrMenuOptionData data = new VoiceIvrMenuOptionData();
        data.setId(option.getId());
        data.setMenuKey(option.getMenuKey());
        data.setLanguageCode(option.getLanguageCode());
        data.setOptionDigit(option.getOptionDigit());
        data.setOptionLabel(option.getOptionLabel());
        data.setActionType(option.getActionType());
        data.setActionTarget(option.getActionTarget());
        data.setEnabled(option.isEnabled());
        return data;
    }
}
