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

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.service.VoiceXmlBuilder;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallQueueEntry;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOption;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceAgentRoutingService {

    private final VoiceAdvisorEscalationService advisorEscalationService;
    private final VoiceCallQueueService queueService;
    private final VoiceIvrDialTargetResolver dialTargetResolver;
    private final VoiceIvrSessionService sessionService;
    private final VoiceRecordingConsentService recordingConsentService;
    private final AfricasTalkingProperties properties;

    @Transactional
    public String joinQueue(final VoiceIvrSession session, final VoiceIvrMenuOption option, final String languageCode) {
        final String departmentCode = option.getActionTarget();
        final WhatsAppSupportTicket ticket = advisorEscalationService.escalate(session, departmentCode, languageCode);
        final VoiceCallQueueEntry entry = queueService.enqueue(session, departmentCode, ticket.getId());
        session.setSessionStatus(VoiceIvrSessionStatus.QUEUED);
        sessionService.save(session);
        if (entry.getQueuePosition() <= 1) {
            return connectToDepartment(session, option, languageCode, entry);
        }
        return VoiceXmlBuilder.buildQueueHold(VoiceIvrMessages.queuePosition(languageCode, entry.getQueuePosition()));
    }

    @Transactional
    public String advanceQueuedCall(final VoiceIvrSession session, final String languageCode) {
        final int position = queueService.resolveQueuePosition(session);
        if (position > 1) {
            return VoiceXmlBuilder.buildQueueHold(VoiceIvrMessages.queuePosition(languageCode, position));
        }
        final Optional<VoiceCallQueueEntry> waitingEntry = queueService.findWaitingEntry(session);
        if (waitingEntry.isEmpty()) {
            return VoiceXmlBuilder.buildInvalidSelection();
        }
        final VoiceIvrMenuOption option = new VoiceIvrMenuOption();
        option.setActionTarget(waitingEntry.get().getDepartmentCode());
        option.setOptionLabel(waitingEntry.get().getDepartmentCode());
        return connectToDepartment(session, option, languageCode, waitingEntry.get());
    }

    @Transactional
    public String transferToDepartment(final VoiceIvrSession session, final VoiceIvrMenuOption option, final String languageCode) {
        final WhatsAppSupportTicket ticket = advisorEscalationService.escalate(session, option.getActionTarget(), languageCode);
        final VoiceCallQueueEntry entry = queueService.enqueue(session, option.getActionTarget(), ticket.getId());
        return connectToDepartment(session, option, languageCode, entry);
    }

    private String connectToDepartment(final VoiceIvrSession session, final VoiceIvrMenuOption option, final String languageCode,
            final VoiceCallQueueEntry entry) {
        final String phoneNumber = dialTargetResolver.resolve(option.getActionTarget());
        if (StringUtils.isBlank(phoneNumber)) {
            queueService.abandonWaitingEntries(session);
            return VoiceXmlBuilder.buildUnavailableDepartment(option.getOptionLabel());
        }
        if (properties.getVoice().isRecordingConsentRequired()) {
            return recordingConsentService.beginConsent(session, option.getActionTarget(), option.getOptionLabel(), entry, languageCode);
        }
        queueService.markConnecting(entry);
        sessionService.markClosed(session);
        return VoiceXmlBuilder.buildConnectingDial(VoiceIvrMessages.connectingToAgent(languageCode, option.getOptionLabel()), phoneNumber);
    }
}
