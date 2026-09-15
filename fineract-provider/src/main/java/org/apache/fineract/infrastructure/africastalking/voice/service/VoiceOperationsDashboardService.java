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

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceOperationsDashboardData;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceCallQueueStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceCallbackRequestStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceOperationsDashboardService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public VoiceOperationsDashboardData retrieveDashboard() {
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        final LocalDateTime startOfDay = DateUtils.getLocalDateOfTenant().atStartOfDay();
        final VoiceOperationsDashboardData data = new VoiceOperationsDashboardData();
        data.setActiveIvrSessions(count(
                "select count(*) from voice_ivr_session where session_status not in (?, ?) and (expires_at is null or expires_at > ?)",
                VoiceIvrSessionStatus.CLOSED.name(), VoiceIvrSessionStatus.LANGUAGE_SELECTION.name(), now));
        data.setQueuedCallers(count("select count(*) from voice_call_queue_entry where status = ?", VoiceCallQueueStatus.WAITING.name()));
        data.setPendingCallbacks(count("select count(*) from voice_callback_request where status in (?, ?)",
                VoiceCallbackRequestStatus.PENDING.name(), VoiceCallbackRequestStatus.SCHEDULED.name()));
        data.setVoicemailsToday(count("select count(*) from voice_voicemail where created_date >= ?", startOfDay));
        data.setInboundCallsToday(count("select count(*) from voice_call_log where direction = ? and created_date >= ?",
                CommunicationDirection.INBOUND.name(), startOfDay));
        data.setOutboundCallsToday(count("select count(*) from voice_call_log where direction = ? and created_date >= ?",
                CommunicationDirection.OUTBOUND.name(), startOfDay));
        data.setOpenVoiceTickets(count(
                "select count(*) from whatsapp_support_ticket where voice_ivr_session_id is not null and status in (?, ?, ?)",
                WhatsAppTicketStatus.OPEN.name(), WhatsAppTicketStatus.ASSIGNED.name(), WhatsAppTicketStatus.IN_PROGRESS.name()));
        return data;
    }

    private long count(final String sql, final Object... params) {
        final Long result = jdbcTemplate.queryForObject(sql, Long.class, params);
        return result == null ? 0L : result;
    }
}
