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
package org.apache.fineract.portfolio.creditdecision.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the values the catalogue talks about for one loan application, from the loan, the client, their loan history,
 * the credit bureau report and the due diligence. A key that cannot be worked out is left out of the result, which the
 * profile then treats as missing data. Channel, KYC status and bankruptcy are not held anywhere in the core system, so
 * they are always missing until a source is added.
 */
@Component
@RequiredArgsConstructor
public class CreditDecisionLoanFacts {

    private static final String YES = "Yes";
    private static final String NO = "No";

    // loan_status_id values: 300 active, 600 closed (obligations met), 601 written off, 602 closed (rescheduled), 700 overpaid
    private static final String HISTORY_STATUSES = "300, 600, 601, 700";
    private static final String CLOSED_STATUSES = "600, 601, 602, 700";

    private final JdbcTemplate jdbc;

    public Map<String, Object> resolve(final long loanId, final Collection<String> keys) {
        return resolve(loanId, keys, LocalDate.now(ZoneOffset.UTC));
    }

    public Map<String, Object> resolve(final long loanId, final Collection<String> keys, final LocalDate today) {
        final Map<String, Object> out = new LinkedHashMap<>();
        final Session s = new Session(loanId, today);
        for (final String key : keys) {
            final Object value = s.value(key);
            if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    /** One resolution pass. Remembers what it already looked up so several keys share one query. */
    private final class Session {

        private final long loanId;
        private final LocalDate today;
        private Map<String, Object> loan;
        private Delinquency delinquency;
        private Map<String, Object> bureauSummary;
        private Long headerId;
        private boolean headerLoaded;
        private Map<String, Object> diligence;
        private boolean diligenceLoaded;

        Session(final long loanId, final LocalDate today) {
            this.loanId = loanId;
            this.today = today;
        }

        Object value(final String key) {
            switch (key) {
                case "amount":
                    return plain(loan().get("principal_amount_proposed"));
                case "term":
                    return termMonths();
                case "currency":
                    return loan().get("currency_code");
                case "purpose":
                    return codeValue(loan().get("loanpurpose_cv_id"));
                case "age":
                    return age();
                case "tenure":
                    return tenureMonths();
                case "sector":
                    // the client classification is what the business sector is kept in
                    return codeValue(client().get("client_classification_cv_id"));
                case "cycles":
                    return clientId() == null ? null : count("loan_status_id IN (600, 700)");
                case "dpd":
                    return delinquency().hasHistory ? (Object) delinquency().maxDays : null;
                case "late":
                    return delinquency().hasHistory ? (Object) delinquency().late : null;
                case "arrears":
                    return arrears();
                case "activeInt":
                    return clientId() == null ? null : count("loan_status_id = 300");
                case "prev":
                    return previousLoanStatus();
                case "wo":
                    return clientId() == null ? null : writtenOffRecently();
                case "bscore":
                    return bureauScore();
                case "blist":
                    return bureauNumber("npa_accounts_all_sectors", true);
                case "bactive":
                    return bureauNumber("open_accounts_all_sectors", false);
                case "bdate":
                    return bureauReportDate();
                case "benq":
                    return bureauEnquiryReference();
                case "bfraud":
                    return bureauNumber("fc_all_sectors", true);
                case "resid":
                    return countryOfResidence();
                case "bizMonths":
                    return monthsInBusiness();
                case "ddDone":
                    return diligence() == null ? null : (isTrue(diligence().get("is_due_diligence_signed")) ? YES : NO);
                case "ddRes":
                    return dueDiligenceResult();
                default:
                    // channel, kyc and bankruptcy: not held anywhere yet
                    return null;
            }
        }

        // ---------------------------------------------------------------- loan and client

        private Map<String, Object> loan() {
            if (loan == null) {
                final List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT client_id, currency_code, principal_amount_proposed, term_frequency, term_period_frequency_enum, "
                                + "loanpurpose_cv_id FROM m_loan WHERE id = ?",
                        loanId);
                loan = rows.isEmpty() ? Map.of() : rows.get(0);
            }
            return loan;
        }

        private Long clientId() {
            final Object id = loan().get("client_id");
            return id == null ? null : ((Number) id).longValue();
        }

        private Map<String, Object> client() {
            final Long id = clientId();
            if (id == null) {
                return Map.of();
            }
            final List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT date_of_birth, submittedon_date, activation_date, client_classification_cv_id FROM m_client WHERE id = ?", id);
            return rows.isEmpty() ? Map.of() : rows.get(0);
        }

        private Object termMonths() {
            return months(loan().get("term_frequency"), loan().get("term_period_frequency_enum"));
        }

        private Object age() {
            final LocalDate born = date(client().get("date_of_birth"));
            return born == null || born.isAfter(today) ? null : (Object) Period.between(born, today).getYears();
        }

        private Object tenureMonths() {
            final Map<String, Object> client = client();
            LocalDate since = date(client.get("submittedon_date"));
            if (since == null) {
                since = date(client.get("activation_date"));
            }
            return since == null || since.isAfter(today) ? null : (Object) ChronoUnit.MONTHS.between(since, today);
        }

        /** The country on the client's address that is in use, the newest one if there are several. */
        private Object countryOfResidence() {
            final Long id = clientId();
            if (id == null) {
                return null;
            }
            final List<String> rows = jdbc.queryForList("SELECT cv.code_value FROM m_client_address ca "
                    + "JOIN m_address a ON a.id = ca.address_id JOIN m_code_value cv ON cv.id = a.country_id "
                    + "WHERE ca.client_id = ? AND ca.is_active = 1 ORDER BY ca.id DESC LIMIT 1", String.class, id);
            return rows.isEmpty() ? null : rows.get(0);
        }

        /**
         * Whole months since the business was started: the earliest start date on the client's business details, or for
         * a business entity the "in business since" date.
         */
        private Object monthsInBusiness() {
            final Long id = clientId();
            if (id == null) {
                return null;
            }
            final List<Date> rows = jdbc.queryForList("SELECT MIN(started) FROM (SELECT business_creation_date AS started FROM "
                    + "m_business_detail WHERE client_id = ? UNION ALL SELECT in_business_since FROM m_client_non_person WHERE client_id = ?) b",
                    Date.class, id, id);
            final LocalDate since = rows.isEmpty() || rows.get(0) == null ? null : rows.get(0).toLocalDate();
            return since == null || since.isAfter(today) ? null : (Object) ChronoUnit.MONTHS.between(since, today);
        }

        private String codeValue(final Object codeValueId) {
            if (codeValueId == null) {
                return null;
            }
            final List<String> rows = jdbc.queryForList("SELECT code_value FROM m_code_value WHERE id = ?", String.class,
                    ((Number) codeValueId).longValue());
            return rows.isEmpty() ? null : rows.get(0);
        }

        // ---------------------------------------------------------------- loan history (other loans of the client)

        private long count(final String statusClause) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM m_loan WHERE client_id = ? AND id <> ? AND " + statusClause, Long.class,
                    clientId(), loanId);
        }

        private Object arrears() {
            if (clientId() == null) {
                return null;
            }
            return plain(jdbc.queryForObject("SELECT COALESCE(SUM(a.total_overdue_derived), 0) FROM m_loan_arrears_aging a "
                    + "JOIN m_loan l ON l.id = a.loan_id WHERE l.client_id = ? AND l.id <> ? AND l.loan_status_id = 300", BigDecimal.class,
                    clientId(), loanId));
        }

        private Object previousLoanStatus() {
            if (clientId() == null) {
                return null;
            }
            final List<Integer> rows = jdbc.queryForList("SELECT loan_status_id FROM m_loan WHERE client_id = ? AND id <> ? "
                    + "AND loan_status_id IN (" + CLOSED_STATUSES + ") ORDER BY id DESC LIMIT 1", Integer.class, clientId(), loanId);
            if (rows.isEmpty()) {
                return "NONE";
            }
            return rows.get(0) == 601 ? "WRITTEN_OFF" : "CLOSED";
        }

        private Object writtenOffRecently() {
            final long found = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM m_loan WHERE client_id = ? AND id <> ? AND loan_status_id = 601 AND writtenoffon_date >= ?",
                    Long.class, clientId(), loanId, Date.valueOf(today.minusMonths(24)));
            return found > 0 ? YES : NO;
        }

        /**
         * Looks at every instalment that fell due in the last 12 months on the client's other loans. An instalment paid
         * late is late by the days between its due date and the day it was settled; an unpaid one by the days since.
         */
        private Delinquency delinquency() {
            if (delinquency != null) {
                return delinquency;
            }
            delinquency = new Delinquency();
            if (clientId() == null) {
                return delinquency;
            }
            delinquency.hasHistory = count("loan_status_id IN (" + HISTORY_STATUSES + ")") > 0;
            if (!delinquency.hasHistory) {
                return delinquency;
            }
            final List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT rs.duedate, rs.obligations_met_on_date, rs.completed_derived FROM m_loan_repayment_schedule rs "
                            + "JOIN m_loan l ON l.id = rs.loan_id WHERE l.client_id = ? AND l.id <> ? AND l.loan_status_id IN ("
                            + HISTORY_STATUSES + ") AND rs.duedate > ? AND rs.duedate <= ?",
                    clientId(), loanId, Date.valueOf(today.minusMonths(12)), Date.valueOf(today));
            for (final Map<String, Object> row : rows) {
                final LocalDate due = date(row.get("duedate"));
                final LocalDate settled = date(row.get("obligations_met_on_date"));
                final long days = ChronoUnit.DAYS.between(due, isTrue(row.get("completed_derived")) && settled != null ? settled : today);
                if (days > 0) {
                    delinquency.late++;
                    delinquency.maxDays = (int) Math.max(delinquency.maxDays, days);
                }
            }
            return delinquency;
        }

        // ---------------------------------------------------------------- credit bureau

        /** The report pulled for this loan, or else the client's latest one. */
        private Long header() {
            if (!headerLoaded) {
                headerLoaded = true;
                List<Long> rows = jdbc.queryForList("SELECT id FROM m_transunion_crb_header WHERE loan_id = ? ORDER BY id DESC LIMIT 1",
                        Long.class, loanId);
                if (rows.isEmpty() && clientId() != null) {
                    rows = jdbc.queryForList("SELECT id FROM m_transunion_crb_header WHERE client_id = ? ORDER BY id DESC LIMIT 1",
                            Long.class, clientId());
                }
                headerId = rows.isEmpty() ? null : rows.get(0);
            }
            return headerId;
        }

        private Object bureauScore() {
            if (header() == null) {
                return null;
            }
            final List<String> rows = jdbc.queryForList(
                    "SELECT positive_score FROM m_transunion_crb_score_output WHERE header_id = ? ORDER BY id DESC LIMIT 1", String.class,
                    header());
            if (rows.isEmpty() || rows.get(0) == null) {
                return null;
            }
            try {
                return new BigDecimal(rows.get(0).trim());
            } catch (final NumberFormatException e) {
                return null;
            }
        }

        private Object bureauNumber(final String column, final boolean asYesNo) {
            if (header() == null) {
                return null;
            }
            if (bureauSummary == null) {
                final List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT npa_accounts_all_sectors, open_accounts_all_sectors, fc_all_sectors FROM m_transunion_crb_summary "
                                + "WHERE header_id = ? "
                                + "ORDER BY id DESC LIMIT 1",
                        header());
                bureauSummary = rows.isEmpty() ? Map.of() : rows.get(0);
            }
            final Object raw = bureauSummary.get(column);
            if (raw == null) {
                return null;
            }
            final long n = ((Number) raw).longValue();
            return asYesNo ? (n > 0 ? YES : NO) : (Object) n;
        }

        private Object bureauReportDate() {
            if (header() == null) {
                return null;
            }
            final List<String> rows = jdbc.queryForList("SELECT report_date FROM m_transunion_crb_header WHERE id = ?", String.class,
                    header());
            return rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank() ? null : rows.get(0);
        }

        /** The reference the bureau gave the enquiry, so the engine and the bureau can be matched up. */
        private Object bureauEnquiryReference() {
            if (header() == null) {
                return null;
            }
            final List<String> rows = jdbc.queryForList("SELECT request_no FROM m_transunion_crb_header WHERE id = ?", String.class,
                    header());
            return rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank() ? null : rows.get(0).trim();
        }

        // ---------------------------------------------------------------- due diligence

        private Map<String, Object> diligence() {
            if (!diligenceLoaded) {
                diligenceLoaded = true;
                final List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT is_due_diligence_signed, is_reject_due_diligence, due_diligence_recommended_amount, "
                                + "due_diligence_term_frequency, due_diligence_term_period_frequency_type FROM m_loan_decision WHERE loan_id = ?",
                        loanId);
                diligence = rows.isEmpty() ? null : rows.get(0);
            }
            return diligence;
        }

        private Object dueDiligenceResult() {
            final Map<String, Object> d = diligence();
            if (d == null || !isTrue(d.get("is_due_diligence_signed"))) {
                return null;
            }
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("result", isTrue(d.get("is_reject_due_diligence")) ? "REJECTED" : "PASSED");
            out.put("recommendedAmount", plain(d.get("due_diligence_recommended_amount")));
            out.put("recommendedTermMonths",
                    months(d.get("due_diligence_term_frequency"), d.get("due_diligence_term_period_frequency_type")));
            return out;
        }
    }

    private static final class Delinquency {

        private boolean hasHistory;
        private int maxDays;
        private int late;
    }

    /** Whole months for a term given in days (0), weeks (1), months (2) or years (3). */
    static Long months(final Object frequency, final Object type) {
        if (frequency == null || type == null) {
            return null;
        }
        final int n = ((Number) frequency).intValue();
        switch (((Number) type).intValue()) {
            case 0:
                return Math.round(n / 30.0);
            case 1:
                return Math.round(n * 7 / 30.0);
            case 2:
                return (long) n;
            case 3:
                return (long) n * 12;
            default:
                return null;
        }
    }

    /** 40000.000000 as 40000, and never as 4E+4, so the request reads the way a person would write it. */
    static Object plain(final Object value) {
        if (!(value instanceof BigDecimal)) {
            return value;
        }
        final BigDecimal stripped = ((BigDecimal) value).stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static boolean isTrue(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value instanceof Number && ((Number) value).intValue() != 0;
    }

    private static LocalDate date(final Object value) {
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) value).getTime()).toLocalDate();
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        return null;
    }
}
