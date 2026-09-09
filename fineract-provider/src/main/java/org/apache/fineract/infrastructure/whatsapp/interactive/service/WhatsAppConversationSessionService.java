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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSessionRepository;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppConversationSessionService {

    private final WhatsAppConversationSessionRepository sessionRepository;
    private final WhatsAppInteractiveSettingsProvider settings;

    @Transactional(readOnly = true)
    public Optional<WhatsAppConversationSession> findResumableSession(final String phoneNumber,
            final WhatsAppConversationType conversationType) {
        final List<WhatsAppConversationSession> sessions = sessionRepository
                .findByPhoneNumberAndConversationTypeOrderByLastActivityAtDesc(phoneNumber, conversationType);
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        final WhatsAppConversationSession session = sessions.get(0);
        if (isTerminal(session.getSessionStatus())) {
            return Optional.empty();
        }
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        if (session.isExpired(now)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Transactional
    public WhatsAppConversationSession startOrResume(final String phoneNumber, final WhatsAppConversationType conversationType,
            final Client client, final Staff staff) {
        final Optional<WhatsAppConversationSession> existing = findResumableSession(phoneNumber, conversationType);
        if (existing.isPresent()) {
            final WhatsAppConversationSession session = existing.get();
            session.touchActivity(calculateExpiresAt());
            return sessionRepository.save(session);
        }
        final WhatsAppConversationSession session = WhatsAppConversationSession.startNew(phoneNumber, conversationType, client, staff,
                calculateExpiresAt());
        return sessionRepository.save(session);
    }

    @Transactional
    public WhatsAppConversationSession save(final WhatsAppConversationSession session) {
        session.touchActivity(calculateExpiresAt());
        return sessionRepository.save(session);
    }

    @Transactional
    public void markExpired(final WhatsAppConversationSession session) {
        session.setSessionStatus(WhatsAppSessionStatus.EXPIRED);
        sessionRepository.save(session);
    }

    @Transactional
    public void markOptedOut(final WhatsAppConversationSession session) {
        session.setSessionStatus(WhatsAppSessionStatus.OPTED_OUT);
        sessionRepository.save(session);
    }

    private LocalDateTime calculateExpiresAt() {
        return DateUtils.getLocalDateTimeOfTenant().plusMinutes(settings.getSessionTimeoutMinutes());
    }

    private boolean isTerminal(final WhatsAppSessionStatus status) {
        return status == WhatsAppSessionStatus.CLOSED || status == WhatsAppSessionStatus.EXPIRED
                || status == WhatsAppSessionStatus.OPTED_OUT;
    }
}
