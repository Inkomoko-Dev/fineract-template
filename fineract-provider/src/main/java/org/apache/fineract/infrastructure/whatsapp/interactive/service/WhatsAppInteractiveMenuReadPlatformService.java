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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppMenuDefinitionData;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppMenuOptionData;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuDefinition;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuDefinitionRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOption;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppInteractiveMenuReadPlatformService {

    private final WhatsAppMenuDefinitionRepository menuDefinitionRepository;
    private final WhatsAppMenuOptionRepository menuOptionRepository;

    @Transactional(readOnly = true)
    public List<WhatsAppMenuDefinitionData> retrieveMenuDefinitions(final String menuKey) {
        final List<WhatsAppMenuDefinition> definitions = menuKey == null ? menuDefinitionRepository.findByEnabledTrueOrderByMenuKeyAscLanguageCodeAsc()
                : menuDefinitionRepository.findByMenuKeyAndEnabledTrueOrderByLanguageCodeAsc(menuKey);
        return definitions.stream().map(this::mapDefinition).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WhatsAppMenuOptionData> retrieveMenuOptions(final String menuKey, final String languageCode) {
        final List<WhatsAppMenuOption> options;
        if (StringUtils.isNotBlank(menuKey) && StringUtils.isNotBlank(languageCode)) {
            options = menuOptionRepository.findByMenuKeyAndLanguageCodeOrderByOptionNumberAsc(menuKey, languageCode);
        } else if (StringUtils.isNotBlank(menuKey)) {
            options = menuOptionRepository.findByMenuKeyOrderByLanguageCodeAscOptionNumberAsc(menuKey);
        } else if (StringUtils.isNotBlank(languageCode)) {
            options = menuOptionRepository.findByLanguageCodeOrderByMenuKeyAscOptionNumberAsc(languageCode);
        } else {
            options = menuOptionRepository.findAllByOrderByMenuKeyAscLanguageCodeAscOptionNumberAsc();
        }
        return options.stream().map(this::mapOption).collect(Collectors.toList());
    }

    private WhatsAppMenuDefinitionData mapDefinition(final WhatsAppMenuDefinition definition) {
        final WhatsAppMenuDefinitionData data = new WhatsAppMenuDefinitionData();
        data.setId(definition.getId());
        data.setMenuKey(definition.getMenuKey());
        data.setLanguageCode(definition.getLanguageCode());
        data.setHeaderText(definition.getHeaderText());
        data.setParentMenuKey(definition.getParentMenuKey());
        data.setEnabled(definition.isEnabled());
        return data;
    }

    private WhatsAppMenuOptionData mapOption(final WhatsAppMenuOption option) {
        final WhatsAppMenuOptionData data = new WhatsAppMenuOptionData();
        data.setId(option.getId());
        data.setMenuKey(option.getMenuKey());
        data.setLanguageCode(option.getLanguageCode());
        data.setOptionNumber(option.getOptionNumber());
        data.setOptionLabel(option.getOptionLabel());
        data.setActionType(option.getActionType());
        data.setActionTarget(option.getActionTarget());
        data.setEnabled(option.isEnabled());
        return data;
    }
}
