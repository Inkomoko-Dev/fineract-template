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

import com.google.gson.JsonElement;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrMenuActionType;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuDefinition;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuDefinitionRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOption;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOptionRepository;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceIvrMenuWritePlatformService {

    private final FromJsonHelper fromJsonHelper;
    private final VoiceIvrMenuDefinitionRepository menuDefinitionRepository;
    private final VoiceIvrMenuOptionRepository menuOptionRepository;

    @Transactional
    public CommandProcessingResult createMenuOption(final String json) {
        final JsonElement element = fromJsonHelper.parse(json);
        final VoiceIvrMenuOption option = new VoiceIvrMenuOption();
        option.setMenuKey(fromJsonHelper.extractStringNamed("menuKey", element));
        option.setLanguageCode(fromJsonHelper.extractStringNamed("languageCode", element));
        option.setOptionDigit(fromJsonHelper.extractIntegerNamed("optionDigit", element, Locale.getDefault()));
        option.setOptionLabel(fromJsonHelper.extractStringNamed("optionLabel", element));
        option.setActionType(VoiceIvrMenuActionType.valueOf(fromJsonHelper.extractStringNamed("actionType", element)));
        option.setActionTarget(fromJsonHelper.extractStringNamed("actionTarget", element));
        if (fromJsonHelper.parameterExists("enabled", element)) {
            option.setEnabled(fromJsonHelper.extractBooleanNamed("enabled", element));
        }
        final VoiceIvrMenuOption saved = menuOptionRepository.save(option);
        return new CommandProcessingResultBuilder().withEntityId(saved.getId()).build();
    }

    @Transactional
    public CommandProcessingResult updateMenuOption(final Long optionId, final String json) {
        final VoiceIvrMenuOption option = menuOptionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Voice IVR menu option not found: " + optionId));
        final JsonElement element = fromJsonHelper.parse(json);
        if (fromJsonHelper.parameterExists("optionLabel", element)) {
            option.setOptionLabel(fromJsonHelper.extractStringNamed("optionLabel", element));
        }
        if (fromJsonHelper.parameterExists("optionDigit", element)) {
            option.setOptionDigit(fromJsonHelper.extractIntegerNamed("optionDigit", element, Locale.getDefault()));
        }
        if (fromJsonHelper.parameterExists("actionType", element)) {
            option.setActionType(VoiceIvrMenuActionType.valueOf(fromJsonHelper.extractStringNamed("actionType", element)));
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
        final VoiceIvrMenuDefinition definition = menuDefinitionRepository.findByMenuKeyAndLanguageCode(menuKey, languageCode)
                .orElseGet(() -> {
                    final VoiceIvrMenuDefinition created = new VoiceIvrMenuDefinition();
                    created.setMenuKey(menuKey);
                    created.setLanguageCode(languageCode);
                    return created;
                });
        final JsonElement element = fromJsonHelper.parse(json);
        if (fromJsonHelper.parameterExists("promptText", element)) {
            definition.setPromptText(fromJsonHelper.extractStringNamed("promptText", element));
        }
        if (fromJsonHelper.parameterExists("parentMenuKey", element)) {
            final String parentMenuKey = fromJsonHelper.extractStringNamed("parentMenuKey", element);
            definition.setParentMenuKey(StringUtils.isBlank(parentMenuKey) ? null : parentMenuKey);
        }
        if (fromJsonHelper.parameterExists("enabled", element)) {
            definition.setEnabled(fromJsonHelper.extractBooleanNamed("enabled", element));
        }
        final VoiceIvrMenuDefinition saved = menuDefinitionRepository.save(definition);
        return new CommandProcessingResultBuilder().withEntityId(saved.getId()).build();
    }
}
