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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceCallQueueStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallQueueEntry;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallQueueEntryRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceCallQueueService {

    private final VoiceCallQueueEntryRepository queueEntryRepository;

    @Transactional
    public VoiceCallQueueEntry enqueue(final VoiceIvrSession session, final String departmentCode, final Long supportTicketId) {
        final int position = (int) queueEntryRepository.countByDepartmentCodeAndStatus(departmentCode, VoiceCallQueueStatus.WAITING) + 1;
        final VoiceCallQueueEntry entry = VoiceCallQueueEntry.waiting(session.getExternalSessionId(), session.getId(),
                session.getCallerNumber(), session.getClient(), departmentCode, position, supportTicketId);
        return queueEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public int resolveQueuePosition(final VoiceIvrSession session) {
        return queueEntryRepository
                .findFirstByExternalSessionIdAndStatusOrderByCreatedDateDesc(session.getExternalSessionId(), VoiceCallQueueStatus.WAITING)
                .map(this::calculateLivePosition).orElse(1);
    }

    @Transactional
    public void markConnecting(final VoiceCallQueueEntry entry) {
        entry.setStatus(VoiceCallQueueStatus.CONNECTING);
        entry.setLastModifiedDate(DateUtils.getLocalDateTimeOfTenant());
        queueEntryRepository.save(entry);
    }

    @Transactional
    public void markConnected(final VoiceIvrSession session) {
        markConnected(session.getExternalSessionId());
    }

    @Transactional
    public void markConnected(final String externalSessionId) {
        queueEntryRepository
                .findFirstByExternalSessionIdAndStatusOrderByCreatedDateDesc(externalSessionId, VoiceCallQueueStatus.CONNECTING)
                .ifPresent(entry -> {
                    entry.setStatus(VoiceCallQueueStatus.CONNECTED);
                    entry.setConnectedAt(DateUtils.getLocalDateTimeOfTenant());
                    entry.setLastModifiedDate(DateUtils.getLocalDateTimeOfTenant());
                    queueEntryRepository.save(entry);
                });
    }

    @Transactional
    public void markCompleted(final String externalSessionId) {
        queueEntryRepository.findFirstByExternalSessionIdAndStatusInOrderByCreatedDateDesc(externalSessionId,
                List.of(VoiceCallQueueStatus.CONNECTING, VoiceCallQueueStatus.CONNECTED)).ifPresent(entry -> {
                    entry.setStatus(VoiceCallQueueStatus.COMPLETED);
                    entry.setLastModifiedDate(DateUtils.getLocalDateTimeOfTenant());
                    queueEntryRepository.save(entry);
                });
    }

    @Transactional
    public void abandonWaitingEntries(final VoiceIvrSession session) {
        queueEntryRepository
                .findFirstByExternalSessionIdAndStatusOrderByCreatedDateDesc(session.getExternalSessionId(), VoiceCallQueueStatus.WAITING)
                .ifPresent(entry -> {
                    entry.setStatus(VoiceCallQueueStatus.ABANDONED);
                    entry.setLastModifiedDate(DateUtils.getLocalDateTimeOfTenant());
                    queueEntryRepository.save(entry);
                });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<VoiceCallQueueEntry> findWaitingEntry(final VoiceIvrSession session) {
        return queueEntryRepository.findFirstByExternalSessionIdAndStatusOrderByCreatedDateDesc(session.getExternalSessionId(),
                VoiceCallQueueStatus.WAITING);
    }

    private int calculateLivePosition(final VoiceCallQueueEntry entry) {
        final List<VoiceCallQueueEntry> waiting = queueEntryRepository.findByDepartmentCodeAndStatusOrderByCreatedDateAsc(
                entry.getDepartmentCode(), VoiceCallQueueStatus.WAITING);
        int position = 1;
        for (final VoiceCallQueueEntry waitingEntry : waiting) {
            if (waitingEntry.getId().equals(entry.getId())) {
                return position;
            }
            position++;
        }
        return entry.getQueuePosition();
    }
}
