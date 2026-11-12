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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.service.VoiceXmlBuilder;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceVoicemailStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceVoicemail;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceVoicemailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceVoicemailService {

    private final VoiceVoicemailRepository voicemailRepository;
    private final VoiceIvrSessionService sessionService;

    @Transactional
    public String beginRecording(final VoiceIvrSession session, final String departmentCode, final String languageCode) {
        final VoiceVoicemail voicemail = VoiceVoicemail.pending(session.getExternalSessionId(), session.getId(), session.getCallerNumber(),
                session.getClient(), departmentCode);
        voicemailRepository.save(voicemail);
        session.setSessionStatus(VoiceIvrSessionStatus.VOICEMAIL);
        sessionService.save(session);
        return VoiceXmlBuilder.buildRecordVoicemail(VoiceIvrMessages.voicemailPrompt(languageCode));
    }

    @Transactional
    public void completeFromRecordingEvent(final String externalSessionId, final String recordingUrl, final Integer durationSeconds) {
        if (StringUtils.isBlank(externalSessionId) || StringUtils.isBlank(recordingUrl)) {
            return;
        }
        voicemailRepository.findFirstByExternalSessionIdAndStatusOrderByCreatedDateDesc(externalSessionId, VoiceVoicemailStatus.PENDING)
                .ifPresent(voicemail -> {
                    voicemail.setRecordingUrl(recordingUrl);
                    voicemail.setDurationSeconds(durationSeconds);
                    voicemail.setStatus(VoiceVoicemailStatus.STORED);
                    voicemailRepository.save(voicemail);
                });
    }

    @Transactional
    public String completeSession(final VoiceIvrSession session, final String languageCode) {
        sessionService.markClosed(session);
        return VoiceXmlBuilder.buildSay(VoiceIvrMessages.voicemailThankYou(languageCode));
    }
}
