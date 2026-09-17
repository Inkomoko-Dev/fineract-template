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
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceVoicemailData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceVoicemailReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final VoiceVoicemailMapper mapper = new VoiceVoicemailMapper();

    public List<VoiceVoicemailData> retrieveVoicemails() {
        return jdbcTemplate.query("select " + mapper.schema() + " order by vv.created_date desc", mapper);
    }

    private static final class VoiceVoicemailMapper implements RowMapper<VoiceVoicemailData> {

        private String schema() {
            return "vv.id as id, vv.caller_number as callerNumber, vv.client_id as clientId, vv.department_code as departmentCode, "
                    + "vv.recording_url as recordingUrl, vv.duration_seconds as durationSeconds, vv.status as status, "
                    + "vv.created_date as createdDate from voice_voicemail vv";
        }

        @Override
        public VoiceVoicemailData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final VoiceVoicemailData data = new VoiceVoicemailData();
            data.setId(rs.getLong("id"));
            data.setCallerNumber(rs.getString("callerNumber"));
            data.setClientId(JdbcSupport.getLong(rs, "clientId"));
            data.setDepartmentCode(rs.getString("departmentCode"));
            data.setRecordingUrl(rs.getString("recordingUrl"));
            data.setDurationSeconds(JdbcSupport.getInteger(rs, "durationSeconds"));
            data.setStatus(rs.getString("status"));
            data.setCreatedDate(JdbcSupport.getLocalDateTime(rs, "createdDate"));
            return data;
        }
    }
}
