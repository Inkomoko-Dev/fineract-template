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
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.data.VoiceCallLogData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceCallLogReadPlatformService {

    private static final int DEFAULT_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final VoiceRecordingAccessAuditService recordingAccessAuditService;
    private final VoiceCallLogMapper listMapper = new VoiceCallLogMapper(false);
    private final VoiceCallLogMapper detailMapper = new VoiceCallLogMapper(true);

    public List<VoiceCallLogData> retrieveCallLogs() {
        return retrieveCallLogs(0, DEFAULT_PAGE_SIZE);
    }

    public List<VoiceCallLogData> retrieveCallLogs(final int offset, final int limit) {
        final int safeLimit = limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, 500);
        final int safeOffset = Math.max(offset, 0);
        final ScopeFilter scope = buildScopeFilter();
        final String sql = "select " + listMapper.schema() + scope.whereClause + " order by vcl.created_date desc limit ? offset ?";
        final List<Object> args = new ArrayList<>(scope.args);
        args.add(safeLimit);
        args.add(safeOffset);
        return this.jdbcTemplate.query(sql, listMapper, args.toArray());
    }

    public VoiceCallLogData retrieveOne(final Long callId) {
        final ScopeFilter scope = buildScopeFilter();
        final String sql = "select " + listMapper.schema() + " where vcl.id = ?" + scope.andClause;
        final List<Object> args = new ArrayList<>();
        args.add(callId);
        args.addAll(scope.args);
        final List<VoiceCallLogData> results = this.jdbcTemplate.query(sql, listMapper, args.toArray());
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Permissioned recording URL fetch with access audit. List/detail endpoints never include the URL.
     */
    public VoiceCallLogData retrieveRecording(final Long callId) {
        final ScopeFilter scope = buildScopeFilter();
        final String sql = "select " + detailMapper.schema() + " where vcl.id = ?" + scope.andClause;
        final List<Object> args = new ArrayList<>();
        args.add(callId);
        args.addAll(scope.args);
        final List<VoiceCallLogData> results = this.jdbcTemplate.query(sql, detailMapper, args.toArray());
        if (results.isEmpty()) {
            return null;
        }
        final VoiceCallLogData data = results.get(0);
        final AppUser user = context.authenticatedUser();
        recordingAccessAuditService.recordAccess(user.getId(), user.getUsername(), callId, data.getRecordingUrl());
        return data;
    }

    private ScopeFilter buildScopeFilter() {
        final AppUser user = context.authenticatedUser();
        final Long staffId = user.getStaffId();
        if (staffId == null) {
            return ScopeFilter.unrestricted();
        }
        // Investment officers see calls for their assigned clients (or themselves as staff).
        return new ScopeFilter(" where (vcl.staff_id = ? or vcl.client_id in (select c.id from m_client c where c.staff_id = ?))",
                " and (vcl.staff_id = ? or vcl.client_id in (select c.id from m_client c where c.staff_id = ?))", List.of(staffId, staffId));
    }

    private record ScopeFilter(String whereClause, String andClause, List<Object> args) {
        static ScopeFilter unrestricted() {
            return new ScopeFilter("", "", List.of());
        }
    }

    private static final class VoiceCallLogMapper implements RowMapper<VoiceCallLogData> {

        private final boolean includeRecordingUrl;

        private VoiceCallLogMapper(final boolean includeRecordingUrl) {
            this.includeRecordingUrl = includeRecordingUrl;
        }

        public String schema() {
            return "vcl.id as id, vcl.external_session_id as externalSessionId, vcl.direction as direction, "
                    + "vcl.caller_number as callerNumber, vcl.destination_number as destinationNumber, "
                    + "vcl.client_id as clientId, vcl.staff_id as staffId, vcl.status as status, "
                    + "vcl.duration_seconds as durationSeconds, vcl.recording_url as recordingUrl, "
                    + "vcl.call_purpose as callPurpose, "
                    + "vcl.recording_consent_required as recordingConsentRequired, vcl.recording_consent_given as recordingConsentGiven, "
                    + "vcl.callback_request_id as callbackRequestId, vcl.created_date as createdDate from voice_call_log vcl";
        }

        @Override
        public VoiceCallLogData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            // Never expose DTMF (may contain OTP). Recording URL only on explicit recording fetch.
            return VoiceCallLogData.instance(rs.getLong("id"), rs.getString("externalSessionId"),
                    CommunicationDirection.valueOf(rs.getString("direction")), rs.getString("callerNumber"),
                    rs.getString("destinationNumber"), JdbcSupport.getLong(rs, "clientId"), JdbcSupport.getLong(rs, "staffId"),
                    rs.getString("status"), JdbcSupport.getInteger(rs, "durationSeconds"),
                    includeRecordingUrl ? rs.getString("recordingUrl") : null, null, rs.getString("callPurpose"),
                    rs.getBoolean("recordingConsentRequired"), nullableBoolean(rs, "recordingConsentGiven"),
                    JdbcSupport.getLong(rs, "callbackRequestId"), JdbcSupport.getLocalDateTime(rs, "createdDate"));
        }

        private static Boolean nullableBoolean(final ResultSet rs, final String columnName) throws SQLException {
            final boolean value = rs.getBoolean(columnName);
            return rs.wasNull() ? null : value;
        }
    }
}
