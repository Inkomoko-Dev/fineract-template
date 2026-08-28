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
package org.apache.fineract.infrastructure.africastalking.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.data.VoiceCallLogData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceCallLogReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final VoiceCallLogMapper mapper = new VoiceCallLogMapper();

    public List<VoiceCallLogData> retrieveCallLogs() {
        final String sql = "select " + mapper.schema() + " order by vcl.created_date desc";
        return this.jdbcTemplate.query(sql, this.mapper);
    }

    public VoiceCallLogData retrieveOne(final Long callId) {
        final String sql = "select " + mapper.schema() + " where vcl.id = ?";
        final List<VoiceCallLogData> results = this.jdbcTemplate.query(sql, this.mapper, callId);
        return results.isEmpty() ? null : results.get(0);
    }

    private static final class VoiceCallLogMapper implements RowMapper<VoiceCallLogData> {

        public String schema() {
            return "vcl.id as id, vcl.external_session_id as externalSessionId, vcl.direction as direction, "
                    + "vcl.caller_number as callerNumber, vcl.destination_number as destinationNumber, "
                    + "vcl.client_id as clientId, vcl.staff_id as staffId, vcl.status as status, "
                    + "vcl.duration_seconds as durationSeconds, vcl.recording_url as recordingUrl, "
                    + "vcl.dtmf_digits as dtmfDigits, vcl.created_date as createdDate from voice_call_log vcl";
        }

        @Override
        public VoiceCallLogData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return VoiceCallLogData.instance(rs.getLong("id"), rs.getString("externalSessionId"),
                    CommunicationDirection.valueOf(rs.getString("direction")), rs.getString("callerNumber"),
                    rs.getString("destinationNumber"), JdbcSupport.getLong(rs, "clientId"), JdbcSupport.getLong(rs, "staffId"),
                    rs.getString("status"), JdbcSupport.getInteger(rs, "durationSeconds"), rs.getString("recordingUrl"),
                    rs.getString("dtmfDigits"), JdbcSupport.getLocalDateTime(rs, "createdDate"));
        }
    }
}
