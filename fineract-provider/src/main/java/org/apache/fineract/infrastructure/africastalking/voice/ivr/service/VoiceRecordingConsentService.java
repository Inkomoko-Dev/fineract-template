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
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLog;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLogRepository;
import org.apache.fineract.infrastructure.africastalking.service.VoiceXmlBuilder;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrAuthConstants;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallQueueEntry;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSessionContextSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceRecordingConsentService {

    private final VoiceIvrSessionService sessionService;
    private final VoiceCallQueueService queueService;
    private final VoiceIvrDialTargetResolver dialTargetResolver;
    private final VoiceCallLogRepository voiceCallLogRepository;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final AfricasTalkingProperties properties;

    @Transactional
    public String beginConsent(final VoiceIvrSession session, final String departmentCode, final String departmentLabel,
            final VoiceCallQueueEntry queueEntry, final String languageCode) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        context.setPendingAction(VoiceIvrAuthConstants.PENDING_RECORDING_CONSENT);
        context.setPendingDepartmentCode(departmentCode);
        context.setPendingDepartmentLabel(departmentLabel);
        context.setPendingQueueEntryId(queueEntry != null ? queueEntry.getId() : null);
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(VoiceIvrSessionStatus.RECORDING_CONSENT);
        sessionService.save(session);
        markConsentRequired(session);
        return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.recordingConsentPrompt(languageCode));
    }

    @Transactional(readOnly = true)
    public String reprompt(final VoiceIvrSession session) {
        return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.recordingConsentPrompt(resolveLanguage(session)));
    }

    @Transactional
    public String handleConsentInput(final VoiceIvrSession session, final String input) {
        final String language = resolveLanguage(session);
        if (!"1".equals(input) && !"2".equals(input)) {
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.recordingConsentInvalid(language));
        }
        final boolean consentGiven = "1".equals(input);
        final boolean recordCall = consentGiven;
        persistConsent(session, consentGiven);
        return completePendingConnection(session, language, recordCall);
    }

    private String completePendingConnection(final VoiceIvrSession session, final String languageCode, final boolean recordCall) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final String departmentCode = context.getPendingDepartmentCode();
        final String departmentLabel = StringUtils.defaultIfBlank(context.getPendingDepartmentLabel(), departmentCode);
        final String phoneNumber = dialTargetResolver.resolve(departmentCode);
        if (StringUtils.isBlank(phoneNumber)) {
            queueService.abandonWaitingEntries(session);
            clearPendingRoute(session, context);
            return VoiceXmlBuilder.buildUnavailableDepartment(departmentLabel);
        }
        queueService.markConnectingById(context.getPendingQueueEntryId());
        clearPendingRoute(session, context);
        sessionService.markClosed(session);
        return VoiceXmlBuilder.buildConnectingDial(VoiceIvrMessages.connectingToAgent(languageCode, departmentLabel), phoneNumber,
                recordCall);
    }

    private void clearPendingRoute(final VoiceIvrSession session, final WhatsAppSessionContext context) {
        context.setPendingAction(null);
        context.setPendingDepartmentCode(null);
        context.setPendingDepartmentLabel(null);
        context.setPendingQueueEntryId(null);
        session.setSessionContext(contextSerializer.toJson(context));
        sessionService.save(session);
    }

    private void markConsentRequired(final VoiceIvrSession session) {
        voiceCallLogRepository.findByExternalSessionId(session.getExternalSessionId()).ifPresent(callLog -> {
            callLog.setRecordingConsentRequired(properties.getVoice().isRecordingConsentRequired());
            voiceCallLogRepository.save(callLog);
        });
    }

    private void persistConsent(final VoiceIvrSession session, final boolean consentGiven) {
        voiceCallLogRepository.findByExternalSessionId(session.getExternalSessionId()).ifPresent(callLog -> {
            callLog.setRecordingConsentRequired(true);
            callLog.setRecordingConsentGiven(consentGiven);
            voiceCallLogRepository.save(callLog);
        });
    }

    private String resolveLanguage(final VoiceIvrSession session) {
        return StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getVoiceIvr().getDefaultLanguage());
    }
}
