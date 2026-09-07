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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppInteractiveDashboardData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppInteractiveDashboardService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public WhatsAppInteractiveDashboardData retrieveDashboard() {
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        final LocalDateTime startOfDay = DateUtils.getLocalDateOfTenant().atStartOfDay();
        final WhatsAppInteractiveDashboardData data = new WhatsAppInteractiveDashboardData();
        data.setActiveSessions(count(
                "select count(*) from whatsapp_conversation_session where (expires_at is null or expires_at > ?) and session_status not in ('CLOSED')",
                now));
        data.setClientActiveSessions(count(
                "select count(*) from whatsapp_conversation_session where (expires_at is null or expires_at > ?) and session_status not in ('CLOSED') and conversation_type = ?",
                now, WhatsAppConversationType.CLIENT_SELF_SERVICE.name()));
        data.setStaffActiveSessions(count(
                "select count(*) from whatsapp_conversation_session where (expires_at is null or expires_at > ?) and session_status not in ('CLOSED') and conversation_type = ?",
                now, WhatsAppConversationType.STAFF.name()));
        data.setOpenTickets(count("select count(*) from whatsapp_support_ticket where status in (?, ?, ?)",
                WhatsAppTicketStatus.OPEN.name(), WhatsAppTicketStatus.ASSIGNED.name(), WhatsAppTicketStatus.IN_PROGRESS.name()));
        data.setSlaBreachedTickets(count(
                "select count(*) from whatsapp_support_ticket where sla_due_at < ? and first_response_at is null and status not in (?, ?)",
                now, WhatsAppTicketStatus.RESOLVED.name(), WhatsAppTicketStatus.CLOSED.name()));
        data.setInboundMessagesToday(count(
                "select count(*) from communication_message where channel = ? and direction = ? and created_date >= ?",
                CommunicationChannel.WHATSAPP.name(), CommunicationDirection.INBOUND.name(), startOfDay));
        data.setOutboundMessagesToday(count(
                "select count(*) from communication_message where channel = ? and direction = ? and created_date >= ?",
                CommunicationChannel.WHATSAPP.name(), CommunicationDirection.OUTBOUND.name(), startOfDay));
        data.setOptedOutClients(countOptedOutClients());
        data.setTicketsResolvedToday(count(
                "select count(*) from whatsapp_support_ticket where status in (?, ?) and resolved_at >= ?",
                WhatsAppTicketStatus.RESOLVED.name(), WhatsAppTicketStatus.CLOSED.name(), startOfDay));
        return data;
    }

    private long count(final String sql, final Object... params) {
        final Long result = jdbcTemplate.queryForObject(sql, Long.class, params);
        return result == null ? 0L : result;
    }

    private long countOptedOutClients() {
        final String sql = "select count(*) from (select phone_number, max(created_date) as latest from whatsapp_opt_out_record group by phone_number) latest_records "
                + "join whatsapp_opt_out_record r on r.phone_number = latest_records.phone_number and r.created_date = latest_records.latest "
                + "where r.event_type = 'OPT_OUT'";
        final Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result;
    }
}
