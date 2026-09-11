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
package org.apache.fineract.portfolio.loanclassification.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanClassificationCandidateAssembler {

    static final int ACTIVE_STATUS = LoanStatus.ACTIVE.getValue();
    static final int WRITTEN_OFF_STATUS = LoanStatus.CLOSED_WRITTEN_OFF.getValue();
    static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final CandidateMapper mapper = new CandidateMapper();

    public Candidate load(final Long loanId) {
        final List<Candidate> rows = this.jdbcTemplate.query(baseSql() + " WHERE l.id = ?", this.mapper, loanId);
        if (rows.isEmpty()) {
            return new Candidate(loanId, false, null, null, null);
        }
        return rows.get(0);
    }

    public List<Candidate> loadPage(final long lastLoanId, final int pageSize) {
        return this.jdbcTemplate.query(baseSql() + " WHERE l.loan_status_id IN (?, ?) AND l.id > ? ORDER BY l.id ASC LIMIT ?", this.mapper,
                ACTIVE_STATUS, WRITTEN_OFF_STATUS, lastLoanId, pageSize);
    }

    private String baseSql() {
        return "SELECT l.id AS loanId, l.loan_status_id AS loanStatusId, mlaa.overdue_since_date_derived AS overdueSince, "
                + "mlaa.total_overdue_derived AS totalOverdue, COALESCE(("
                + "SELECT ra.country_id FROM m_client_address ca INNER JOIN m_address ra ON ra.id = ca.address_id "
                + "WHERE ca.client_id = l.client_id ORDER BY ca.is_active DESC, ca.id DESC LIMIT 1), ("
                + "SELECT dd.country_cv_id FROM m_loan_due_diligence_info dd WHERE dd.loan_id = l.id LIMIT 1)) AS countryCvId "
                + "FROM m_loan l LEFT JOIN m_loan_arrears_aging mlaa ON mlaa.loan_id = l.id";
    }

    public static final class Candidate {

        private final Long loanId;
        private final boolean writtenOff;
        private final Integer daysInArrears;
        private final Long countryCvId;
        private final BigDecimal totalOverdue;

        public Candidate(final Long loanId, final boolean writtenOff, final Integer daysInArrears, final Long countryCvId,
                final BigDecimal totalOverdue) {
            this.loanId = loanId;
            this.writtenOff = writtenOff;
            this.daysInArrears = daysInArrears;
            this.countryCvId = countryCvId;
            this.totalOverdue = totalOverdue;
        }

        public Long getLoanId() {
            return this.loanId;
        }

        public boolean isWrittenOff() {
            return this.writtenOff;
        }

        public Integer getDaysInArrears() {
            return this.daysInArrears;
        }

        public Long getCountryCvId() {
            return this.countryCvId;
        }

        public BigDecimal getTotalOverdue() {
            return this.totalOverdue;
        }
    }

    private static final class CandidateMapper implements RowMapper<Candidate> {

        @Override
        public Candidate mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Long loanId = rs.getLong("loanId");
            final int status = rs.getInt("loanStatusId");
            final boolean writtenOff = status == WRITTEN_OFF_STATUS;
            final LocalDate overdueSince = JdbcSupport.getLocalDate(rs, "overdueSince");
            final BigDecimal totalOverdue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalOverdue");
            final Long countryCvId = JdbcSupport.getLong(rs, "countryCvId");
            return new Candidate(loanId, writtenOff, resolveDaysInArrears(writtenOff, overdueSince, totalOverdue), countryCvId,
                    totalOverdue);
        }

        private Integer resolveDaysInArrears(final boolean writtenOff, final LocalDate overdueSince, final BigDecimal totalOverdue) {
            if (writtenOff) {
                return null;
            }
            if (overdueSince == null) {
                if (totalOverdue != null && totalOverdue.compareTo(BigDecimal.ZERO) > 0) {
                    return null;
                }
                return 0;
            }
            return Math.toIntExact(ChronoUnit.DAYS.between(overdueSince, DateUtils.getBusinessLocalDate()));
        }
    }
}
