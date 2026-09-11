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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrMenuActionType;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuDefinition;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuDefinitionRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOption;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceIvrMenuRendererTest {

    @Mock
    private VoiceIvrMenuOptionRepository menuOptionRepository;
    @Mock
    private VoiceIvrMenuDefinitionRepository menuDefinitionRepository;

    private VoiceIvrMenuRenderer renderer;

    @BeforeEach
    void setUp() {
        final VoiceIvrMenuDefinitionService definitionService = new VoiceIvrMenuDefinitionService(menuDefinitionRepository);
        renderer = new VoiceIvrMenuRenderer(menuOptionRepository, definitionService);
    }

    @Test
    void buildsVoicePromptFromMenuDefinitionAndOptions() {
        final VoiceIvrMenuDefinition definition = new VoiceIvrMenuDefinition();
        definition.setPromptText("Welcome to Inkomoko.");
        org.mockito.Mockito.when(menuDefinitionRepository.findByMenuKeyAndLanguageCodeAndEnabledTrue("MAIN", "en"))
                .thenReturn(java.util.Optional.of(definition));
        final VoiceIvrMenuOption loans = new VoiceIvrMenuOption();
        loans.setOptionDigit(1);
        loans.setOptionLabel("Loans");
        loans.setActionType(VoiceIvrMenuActionType.DIAL);
        final VoiceIvrMenuOption support = new VoiceIvrMenuOption();
        support.setOptionDigit(2);
        support.setOptionLabel("Client Support");
        support.setActionType(VoiceIvrMenuActionType.DIAL);
        org.mockito.Mockito.when(menuOptionRepository.findByMenuKeyAndLanguageCodeAndEnabledTrueOrderByOptionDigitAsc("MAIN", "en"))
                .thenReturn(List.of(loans, support));

        final String prompt = renderer.buildMenuPrompt("MAIN", "en");

        assertThat(prompt).isEqualTo("Welcome to Inkomoko. Press 1 for Loans, Press 2 for Client Support.");
    }
}
