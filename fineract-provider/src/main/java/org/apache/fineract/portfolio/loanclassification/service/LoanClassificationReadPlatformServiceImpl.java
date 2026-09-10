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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationAuditData;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationCodes;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationCountryConfigData;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationData;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationSummaryRowData;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationThresholdData;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationCountryConfig;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationCountryConfigRepository;
import org.apache.fineract.portfolio.loanclassification.exception.LoanClassificationCountryConfigNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanClassificationReadPlatformServiceImpl implements LoanClassificationReadPlatformService {

    private final LoanClassificationCountryConfigRepository countryConfigRepository;
    private final CodeValueReadPlatformService codeValueReadPlatformService;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final JdbcTemplate jdbcTemplate;
    private final LoanClassificationDataMapper loanMapper = new LoanClassificationDataMapper();
    private final AuditMapper auditMapper = new AuditMapper();
    private final SummaryMapper summaryMapper = new SummaryMapper();

    @Override
    public Collection<LoanClassificationCountryConfigData> retrieveAllCountryConfigs() {
        return this.countryConfigRepository.findAll().stream().map(this::toData).collect(Collectors.toList());
    }

    @Override
    public LoanClassificationCountryConfigData retrieveCountryConfig(final Long configId) {
        final LoanClassificationCountryConfig config = this.countryConfigRepository.findById(configId)
                .orElseThrow(() -> new LoanClassificationCountryConfigNotFoundException(configId));
        return toData(config);
    }

    @Override
    public LoanClassificationCountryConfigData retrieveTemplate() {
        final List<CodeValueData> available = this.jdbcTemplate.query(
                "SELECT cv.id AS id, cv.code_value AS value, cv.external_code AS external_code, "
                        + "cv.code_description AS description, cv.order_position AS position, "
                        + "cv.is_active AS isActive, cv.is_mandatory AS mandatory "
                        + "FROM m_code_value cv INNER JOIN m_code c ON c.id = cv.code_id AND c.code_name = 'COUNTRY' "
                        + "WHERE cv.is_active = 1 "
                        + "AND (EXISTS (SELECT 1 FROM m_address a WHERE a.country_id = cv.id) "
                        + " OR EXISTS (SELECT 1 FROM m_loan_due_diligence_info dd WHERE dd.country_cv_id = cv.id)) "
                        + "AND NOT EXISTS (SELECT 1 FROM m_loan_classification_country_config cfg WHERE cfg.country_cv_id = cv.id) "
                        + "ORDER BY cv.code_value",
                (rs, rowNum) -> CodeValueData.instance(rs.getLong("id"), rs.getString("value"), rs.getString("external_code"),
                        JdbcSupport.getInteger(rs, "position"), rs.getString("description"), rs.getBoolean("isActive"),
                        rs.getBoolean("mandatory")));
        return LoanClassificationCountryConfigData.template(available, defaultThresholds());
    }

    @Override
    public LoanClassificationData retrieveLoanClassification(final Long loanId) {
        this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
        final List<LoanClassificationData> rows = this.jdbcTemplate.query(
                "SELECT lc.loan_id, lc.classification_code, code.label AS classification_label, lc.status, lc.source, "
                        + "lc.days_in_arrears, lc.country_cv_id, cv.code_value AS country_name, lc.error_message, "
                        + "lc.excluded_from_downstream, lc.override_active, lc.override_reason, lc.classified_on_utc "
                        + "FROM m_loan_classification lc "
                        + "LEFT JOIN m_loan_classification_code code ON code.code = lc.classification_code "
                        + "LEFT JOIN m_code_value cv ON cv.id = lc.country_cv_id WHERE lc.loan_id = ?",
                this.loanMapper, loanId);
        if (rows.isEmpty()) {
            return new LoanClassificationData(loanId, null, null, null, null, null, null, null, null, false, false, null, null);
        }
        return rows.get(0);
    }

    @Override
    public Collection<LoanClassificationAuditData> retrieveLoanAudit(final Long loanId) {
        this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
        return this.jdbcTemplate.query(
                "SELECT a.id, a.loan_id, a.old_classification, a.new_classification, a.old_status, a.new_status, a.source, "
                        + "a.days_in_arrears, cv.code_value AS country_name, a.reason, a.error_message, a.created_by, a.created_on_utc "
                        + "FROM m_loan_classification_audit a LEFT JOIN m_code_value cv ON cv.id = a.country_cv_id "
                        + "WHERE a.loan_id = ? ORDER BY a.created_on_utc ASC, a.id ASC",
                this.auditMapper, loanId);
    }

    @Override
    public Collection<LoanClassificationSummaryRowData> retrieveSummary(final Long countryId, final Long officeId, final Long loanProductId,
            final LocalDate fromDate, final LocalDate toDate) {
        final LocalDate start = fromDate == null ? DateUtils.getBusinessLocalDate().minusYears(10) : fromDate;
        final LocalDate end = toDate == null ? DateUtils.getBusinessLocalDate() : toDate;
        final StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(cv.code_value, 'Unassigned') AS country_name, o.name AS office_name, lp.name AS loan_product_name, ");
        sql.append("lc.classification_code, COALESCE(code.label, 'Invalid/Missing') AS classification_label, COUNT(*) AS loan_count, ");
        sql.append("SUM(CASE WHEN lc.excluded_from_downstream = 1 THEN 1 ELSE 0 END) AS excluded_count, ");
        sql.append("SUM(CASE WHEN lc.override_active = 1 THEN 1 ELSE 0 END) AS override_count ");
        sql.append("FROM m_loan_classification lc INNER JOIN m_loan l ON l.id = lc.loan_id ");
        sql.append("INNER JOIN m_office o ON o.id = l.office_id INNER JOIN m_product_loan lp ON lp.id = l.product_id ");
        sql.append("LEFT JOIN m_code_value cv ON cv.id = lc.country_cv_id ");
        sql.append("LEFT JOIN m_loan_classification_code code ON code.code = lc.classification_code ");
        sql.append("WHERE l.loan_status_id IN (300, 601) AND DATE(lc.classified_on_utc) BETWEEN ? AND ? ");
        final List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(start));
        params.add(java.sql.Date.valueOf(end));
        if (countryId != null && countryId > 0) {
            sql.append("AND lc.country_cv_id = ? ");
            params.add(countryId);
        }
        if (officeId != null && officeId > 0) {
            sql.append("AND (l.office_id = ? OR o.hierarchy LIKE CONCAT((SELECT hierarchy FROM m_office WHERE id = ?), '%')) ");
            params.add(officeId);
            params.add(officeId);
        }
        if (loanProductId != null && loanProductId > 0) {
            sql.append("AND l.product_id = ? ");
            params.add(loanProductId);
        }
        sql.append("GROUP BY country_name, office_name, loan_product_name, lc.classification_code, classification_label ");
        sql.append("ORDER BY country_name, office_name, loan_product_name, lc.classification_code");
        return this.jdbcTemplate.query(sql.toString(), this.summaryMapper, params.toArray());
    }

    private LoanClassificationCountryConfigData toData(final LoanClassificationCountryConfig config) {
        final CodeValueData country = this.codeValueReadPlatformService.retrieveCodeValue(config.getCountryCvId());
        final List<LoanClassificationThresholdData> thresholds = config.getThresholds().stream()
                .map(t -> new LoanClassificationThresholdData(t.getClassificationCode(),
                        LoanClassificationCodes.labelOf(t.getClassificationCode()), t.getMinDaysInArrears(), t.getMaxDaysInArrears()))
                .collect(Collectors.toList());
        return LoanClassificationCountryConfigData.instance(config.getId(), config.getCountryCvId(), country.getName(), thresholds);
    }

    private List<LoanClassificationThresholdData> defaultThresholds() {
        return Arrays.asList(new LoanClassificationThresholdData(1, LoanClassificationCodes.NORMAL.getLabel(), 0, 30),
                new LoanClassificationThresholdData(2, LoanClassificationCodes.WATCH.getLabel(), 31, 90),
                new LoanClassificationThresholdData(3, LoanClassificationCodes.SUBSTANDARD.getLabel(), 91, 180),
                new LoanClassificationThresholdData(4, LoanClassificationCodes.DOUBTFUL.getLabel(), 181, 360),
                new LoanClassificationThresholdData(5, LoanClassificationCodes.LOSS.getLabel(), 361, null));
    }

    private static OffsetDateTime toOffset(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static final class LoanClassificationDataMapper implements RowMapper<LoanClassificationData> {

        @Override
        public LoanClassificationData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return new LoanClassificationData(rs.getLong("loan_id"), JdbcSupport.getInteger(rs, "classification_code"),
                    rs.getString("classification_label"), rs.getString("status"), rs.getString("source"),
                    JdbcSupport.getInteger(rs, "days_in_arrears"), JdbcSupport.getLong(rs, "country_cv_id"), rs.getString("country_name"),
                    rs.getString("error_message"), rs.getBoolean("excluded_from_downstream"), rs.getBoolean("override_active"),
                    rs.getString("override_reason"), toOffset(rs.getTimestamp("classified_on_utc")));
        }
    }

    private static final class AuditMapper implements RowMapper<LoanClassificationAuditData> {

        @Override
        public LoanClassificationAuditData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return new LoanClassificationAuditData(rs.getLong("id"), rs.getLong("loan_id"), JdbcSupport.getInteger(rs, "old_classification"),
                    JdbcSupport.getInteger(rs, "new_classification"), rs.getString("old_status"), rs.getString("new_status"),
                    rs.getString("source"), JdbcSupport.getInteger(rs, "days_in_arrears"), rs.getString("country_name"),
                    rs.getString("reason"), rs.getString("error_message"), JdbcSupport.getLong(rs, "created_by"),
                    toOffset(rs.getTimestamp("created_on_utc")));
        }
    }

    private static final class SummaryMapper implements RowMapper<LoanClassificationSummaryRowData> {

        @Override
        public LoanClassificationSummaryRowData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return new LoanClassificationSummaryRowData(rs.getString("country_name"), rs.getString("office_name"),
                    rs.getString("loan_product_name"), JdbcSupport.getInteger(rs, "classification_code"),
                    rs.getString("classification_label"), rs.getLong("loan_count"), rs.getLong("excluded_count"),
                    rs.getLong("override_count"));
        }
    }
}
