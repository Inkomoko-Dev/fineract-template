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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceVoicemailStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceVoicemail;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceVoicemailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceVoicemailServiceTest {

    @Mock
    private VoiceVoicemailRepository voicemailRepository;
    @Mock
    private VoiceIvrSessionService sessionService;

    private VoiceVoicemailService voicemailService;

    @BeforeEach
    void setUp() {
        voicemailService = new VoiceVoicemailService(voicemailRepository, sessionService);
    }

    @Test
    void storesRecordingFromVoiceEvent() {
        final VoiceVoicemail voicemail = VoiceVoicemail.pending("ATV_1", 1L, "+254700000099", null, "AFTER_HOURS");
        when(voicemailRepository.findFirstByExternalSessionIdAndStatusOrderByCreatedDateDesc("ATV_1", VoiceVoicemailStatus.PENDING))
                .thenReturn(Optional.of(voicemail));
        when(voicemailRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        voicemailService.completeFromRecordingEvent("ATV_1", "https://recordings.example/v1.mp3", 45);

        final ArgumentCaptor<VoiceVoicemail> captor = ArgumentCaptor.forClass(VoiceVoicemail.class);
        verify(voicemailRepository).save(captor.capture());
        assertThat(captor.getValue().getRecordingUrl()).isEqualTo("https://recordings.example/v1.mp3");
        assertThat(captor.getValue().getStatus()).isEqualTo(VoiceVoicemailStatus.STORED);
    }
}
