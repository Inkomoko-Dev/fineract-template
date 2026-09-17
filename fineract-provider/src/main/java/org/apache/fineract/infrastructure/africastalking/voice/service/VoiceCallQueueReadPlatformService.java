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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceCallQueueEntryData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceCallQueueReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final VoiceCallQueueMapper mapper = new VoiceCallQueueMapper();

    public List<VoiceCallQueueEntryData> retrieveQueueEntries() {
        return jdbcTemplate.query("select " + mapper.schema() + " where vcq.status in ('WAITING', 'CONNECTING') order by vcq.created_date asc",
                mapper);
    }

    private static final class VoiceCallQueueMapper implements RowMapper<VoiceCallQueueEntryData> {

        private String schema() {
            return "vcq.id as id, vcq.caller_number as callerNumber, vcq.client_id as clientId, vcq.department_code as departmentCode, "
                    + "vcq.queue_position as queuePosition, vcq.status as status, vcq.support_ticket_id as supportTicketId, "
                    + "vcq.created_date as createdDate from voice_call_queue_entry vcq";
        }

        @Override
        public VoiceCallQueueEntryData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final VoiceCallQueueEntryData data = new VoiceCallQueueEntryData();
            data.setId(rs.getLong("id"));
            data.setCallerNumber(rs.getString("callerNumber"));
            data.setClientId(JdbcSupport.getLong(rs, "clientId"));
            data.setDepartmentCode(rs.getString("departmentCode"));
            data.setQueuePosition(rs.getInt("queuePosition"));
            data.setStatus(rs.getString("status"));
            data.setSupportTicketId(JdbcSupport.getLong(rs, "supportTicketId"));
            data.setCreatedDate(JdbcSupport.getLocalDateTime(rs, "createdDate"));
            return data;
        }
    }
}
