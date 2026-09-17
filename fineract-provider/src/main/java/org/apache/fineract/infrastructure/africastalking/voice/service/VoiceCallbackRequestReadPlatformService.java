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
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceCallbackRequestData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceCallbackRequestReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final VoiceCallbackRequestMapper mapper = new VoiceCallbackRequestMapper();

    public List<VoiceCallbackRequestData> retrieveCallbacks() {
        return jdbcTemplate.query("select " + mapper.schema() + " order by vcr.created_date desc", mapper);
    }

    public VoiceCallbackRequestData retrieveOne(final Long callbackId) {
        final List<VoiceCallbackRequestData> results = jdbcTemplate.query("select " + mapper.schema() + " where vcr.id = ?", mapper,
                callbackId);
        return results.isEmpty() ? null : results.get(0);
    }

    private static final class VoiceCallbackRequestMapper implements RowMapper<VoiceCallbackRequestData> {

        private String schema() {
            return "vcr.id as id, vcr.caller_number as callerNumber, vcr.client_id as clientId, vcr.department_code as departmentCode, "
                    + "vcr.language_code as languageCode, vcr.status as status, vcr.support_ticket_id as supportTicketId, "
                    + "vcr.outbound_call_log_id as outboundCallLogId, vcr.scheduled_at as scheduledAt, vcr.created_date as createdDate "
                    + "from voice_callback_request vcr";
        }

        @Override
        public VoiceCallbackRequestData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final VoiceCallbackRequestData data = new VoiceCallbackRequestData();
            data.setId(rs.getLong("id"));
            data.setCallerNumber(rs.getString("callerNumber"));
            data.setClientId(JdbcSupport.getLong(rs, "clientId"));
            data.setDepartmentCode(rs.getString("departmentCode"));
            data.setLanguageCode(rs.getString("languageCode"));
            data.setStatus(rs.getString("status"));
            data.setSupportTicketId(JdbcSupport.getLong(rs, "supportTicketId"));
            data.setOutboundCallLogId(JdbcSupport.getLong(rs, "outboundCallLogId"));
            data.setScheduledAt(JdbcSupport.getLocalDateTime(rs, "scheduledAt"));
            data.setCreatedDate(JdbcSupport.getLocalDateTime(rs, "createdDate"));
            return data;
        }
    }
}
