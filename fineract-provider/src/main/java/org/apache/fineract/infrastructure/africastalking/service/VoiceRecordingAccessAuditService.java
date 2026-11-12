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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceRecordingAccessAuditService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void recordAccess(final Long userId, final String username, final Long callLogId, final String recordingUrl) {
        log.info("Voice recording accessed by userId={} username={} callLogId={}", userId, username, callLogId);
        jdbcTemplate.update(
                "insert into voice_recording_access_log (user_id, username, call_log_id, recording_url_present, accessed_at) values (?, ?, ?, ?, ?)",
                userId, username, callLogId, recordingUrl != null && !recordingUrl.isBlank(), DateUtils.getLocalDateTimeOfTenant());
    }
}
