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
package org.apache.fineract.infrastructure.creditbureau.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.creditbureau.data.CrbRequestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CrbDuplicateRequestGuard {

    private static final Logger LOG = LoggerFactory.getLogger(CrbDuplicateRequestGuard.class);

    public static final String WINDOW_SECONDS_PROPERTY = "fineract.integrations.crb.duplicateRequestWindowSeconds";
    private static final long DEFAULT_WINDOW_SECONDS = 60L;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate newTransaction;
    private final Environment env;

    public CrbDuplicateRequestGuard(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, Environment env) {
        this.jdbcTemplate = jdbcTemplate;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.env = env;
    }

    public Optional<Long> acquire(CrbRequestType type, Long loanId) {
        ensureGuardRow(type, loanId);
        Map<String, Object> guard = jdbcTemplate.queryForMap(
                "SELECT last_report_id, last_completed_on_utc FROM m_crb_request_guard WHERE request_type = ? AND loan_id = ? FOR UPDATE",
                type.name(), loanId);
        Long lastReportId = guard.get("last_report_id") == null ? null : ((Number) guard.get("last_report_id")).longValue();
        LocalDateTime lastCompletedOn = toLocalDateTime(guard.get("last_completed_on_utc"));
        long windowSeconds = windowSeconds();
        if (lastReportId != null && lastCompletedOn != null && !lastCompletedOn.plusSeconds(windowSeconds).isBefore(now())) {
            LOG.info("CRB {} request for loan {} not sent: report {} completed at {} UTC is within the {}s duplicate window", type, loanId,
                    lastReportId, lastCompletedOn, windowSeconds);
            return Optional.of(lastReportId);
        }
        LOG.info("CRB {} request for loan {} sent to the bureau", type, loanId);
        return Optional.empty();
    }

    public void complete(CrbRequestType type, Long loanId, Long reportId) {
        jdbcTemplate.update("UPDATE m_crb_request_guard SET last_report_id = ?, last_completed_on_utc = ? WHERE request_type = ? AND loan_id = ?",
                reportId, now(), type.name(), loanId);
        LOG.info("CRB {} request for loan {} completed with report {}", type, loanId, reportId);
    }

    private void ensureGuardRow(CrbRequestType type, Long loanId) {
        try {
            newTransaction.executeWithoutResult(status -> {
                Integer existing = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM m_crb_request_guard WHERE request_type = ? AND loan_id = ?", Integer.class, type.name(), loanId);
                if (existing == null || existing == 0) {
                    jdbcTemplate.update("INSERT INTO m_crb_request_guard (request_type, loan_id) VALUES (?, ?)", type.name(), loanId);
                }
            });
        } catch (DataIntegrityViolationException e) {
            LOG.debug("CRB {} guard row for loan {} was created by a concurrent request", type, loanId);
        }
    }

    private long windowSeconds() {
        return env.getProperty(WINDOW_SECONDS_PROPERTY, Long.class, DEFAULT_WINDOW_SECONDS);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        return null;
    }
}
