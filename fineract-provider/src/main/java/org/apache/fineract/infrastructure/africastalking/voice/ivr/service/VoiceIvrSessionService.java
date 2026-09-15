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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSessionRepository;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceIvrSessionService {

    private final VoiceIvrSessionRepository sessionRepository;
    private final AfricasTalkingProperties properties;

    @Transactional(readOnly = true)
    public Optional<VoiceIvrSession> findResumableSession(final String externalSessionId) {
        final List<VoiceIvrSession> sessions = sessionRepository.findByExternalSessionIdOrderByLastActivityAtDesc(externalSessionId);
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        final VoiceIvrSession session = sessions.get(0);
        if (session.getSessionStatus() == VoiceIvrSessionStatus.CLOSED) {
            return Optional.empty();
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.LANGUAGE_SELECTION && StringUtils.isBlank(session.getLanguageCode())) {
            return Optional.of(session);
        }
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        if (session.isExpired(now)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Transactional
    public VoiceIvrSession startOrResume(final String externalSessionId, final String callerNumber, final Client client,
            final Staff staff) {
        final Optional<VoiceIvrSession> existing = findResumableSession(externalSessionId);
        if (existing.isPresent()) {
            final VoiceIvrSession session = existing.get();
            session.touchActivity(calculateExpiresAt());
            return sessionRepository.save(session);
        }
        final VoiceIvrSession session = VoiceIvrSession.startNew(externalSessionId, callerNumber, client, staff,
                properties.getVoiceIvr().getLanguageMenuKey(), calculateExpiresAt());
        return sessionRepository.save(session);
    }

    @Transactional
    public VoiceIvrSession save(final VoiceIvrSession session) {
        session.touchActivity(calculateExpiresAt());
        return sessionRepository.save(session);
    }

    @Transactional
    public void markClosed(final VoiceIvrSession session) {
        session.setSessionStatus(VoiceIvrSessionStatus.CLOSED);
        sessionRepository.save(session);
    }

    private LocalDateTime calculateExpiresAt() {
        return DateUtils.getLocalDateTimeOfTenant().plusMinutes(properties.getVoiceIvr().getSessionTimeoutMinutes());
    }
}
