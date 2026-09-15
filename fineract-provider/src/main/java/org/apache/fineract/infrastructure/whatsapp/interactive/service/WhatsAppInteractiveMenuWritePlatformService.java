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

import com.google.gson.JsonElement;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppMenuActionType;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuDefinition;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuDefinitionRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOption;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppMenuOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppInteractiveMenuWritePlatformService {

    private final FromJsonHelper fromJsonHelper;
    private final WhatsAppMenuDefinitionRepository menuDefinitionRepository;
    private final WhatsAppMenuOptionRepository menuOptionRepository;

    @Transactional
    public CommandProcessingResult createMenuOption(final String json) {
        final JsonElement element = fromJsonHelper.parse(json);
        final WhatsAppMenuOption option = new WhatsAppMenuOption();
        option.setMenuKey(fromJsonHelper.extractStringNamed("menuKey", element));
        option.setLanguageCode(fromJsonHelper.extractStringNamed("languageCode", element));
        option.setOptionNumber(fromJsonHelper.extractIntegerNamed("optionNumber", element, Locale.getDefault()));
        option.setOptionLabel(fromJsonHelper.extractStringNamed("optionLabel", element));
        final String actionType = fromJsonHelper.extractStringNamed("actionType", element);
        option.setActionType(WhatsAppMenuActionType.valueOf(actionType));
        option.setActionTarget(fromJsonHelper.extractStringNamed("actionTarget", element));
        if (fromJsonHelper.parameterExists("enabled", element)) {
            option.setEnabled(fromJsonHelper.extractBooleanNamed("enabled", element));
        }
        final WhatsAppMenuOption saved = menuOptionRepository.save(option);
        return new CommandProcessingResultBuilder().withEntityId(saved.getId()).build();
    }

    @Transactional
    public CommandProcessingResult updateMenuOption(final Long optionId, final String json) {
        final WhatsAppMenuOption option = menuOptionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp menu option not found: " + optionId));
        final JsonElement element = fromJsonHelper.parse(json);
        if (fromJsonHelper.parameterExists("optionLabel", element)) {
            option.setOptionLabel(fromJsonHelper.extractStringNamed("optionLabel", element));
        }
        if (fromJsonHelper.parameterExists("optionNumber", element)) {
            option.setOptionNumber(fromJsonHelper.extractIntegerNamed("optionNumber", element, Locale.getDefault()));
        }
        if (fromJsonHelper.parameterExists("actionType", element)) {
            option.setActionType(WhatsAppMenuActionType.valueOf(fromJsonHelper.extractStringNamed("actionType", element)));
        }
        if (fromJsonHelper.parameterExists("actionTarget", element)) {
            option.setActionTarget(fromJsonHelper.extractStringNamed("actionTarget", element));
        }
        if (fromJsonHelper.parameterExists("enabled", element)) {
            option.setEnabled(fromJsonHelper.extractBooleanNamed("enabled", element));
        }
        menuOptionRepository.save(option);
        return new CommandProcessingResultBuilder().withEntityId(optionId).build();
    }

    @Transactional
    public CommandProcessingResult deleteMenuOption(final Long optionId) {
        menuOptionRepository.deleteById(optionId);
        return new CommandProcessingResultBuilder().withEntityId(optionId).build();
    }

    @Transactional
    public CommandProcessingResult updateMenuDefinition(final String menuKey, final String languageCode, final String json) {
        final WhatsAppMenuDefinition definition = menuDefinitionRepository.findByMenuKeyAndLanguageCode(menuKey, languageCode)
                .orElseGet(() -> {
                    final WhatsAppMenuDefinition created = new WhatsAppMenuDefinition();
                    created.setMenuKey(menuKey);
                    created.setLanguageCode(languageCode);
                    return created;
                });
        final JsonElement element = fromJsonHelper.parse(json);
        if (fromJsonHelper.parameterExists("headerText", element)) {
            definition.setHeaderText(fromJsonHelper.extractStringNamed("headerText", element));
        }
        if (fromJsonHelper.parameterExists("parentMenuKey", element)) {
            final String parentMenuKey = fromJsonHelper.extractStringNamed("parentMenuKey", element);
            definition.setParentMenuKey(StringUtils.isBlank(parentMenuKey) ? null : parentMenuKey);
        }
        if (fromJsonHelper.parameterExists("enabled", element)) {
            definition.setEnabled(fromJsonHelper.extractBooleanNamed("enabled", element));
        }
        final WhatsAppMenuDefinition saved = menuDefinitionRepository.save(definition);
        return new CommandProcessingResultBuilder().withEntityId(saved.getId()).build();
    }
}
