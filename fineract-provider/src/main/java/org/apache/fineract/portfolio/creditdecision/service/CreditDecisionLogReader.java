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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogCounts;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogEntryData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogFilter;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogPageData;
import org.apache.fineract.portfolio.creditdecision.exception.CreditDecisionLogNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the decision log a page at a time. The list leaves out the request and reply bodies, which can be large; they
 * are read for one entry when it is opened.
 */
@Service
@RequiredArgsConstructor
public class CreditDecisionLogReader {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> DECISIONS = Set.of("GO", "REVIEW", "NO-GO", "NONE");
    private static final Set<String> STATUSES = Set.of("DECIDED", "HELD", "AWAITING");
    private static final Map<String, String> SORTS = Map.of("date_desc", "created_on DESC, id DESC", "date_asc",
            "created_on ASC, id ASC", "score_desc", "score DESC, id DESC", "score_asc", "score IS NULL, score ASC, id ASC");

    private static final String LIST_COLUMNS = "id, application_ref, loan_id, customer_ref, profile_id, profile_name, version_no, mode, "
            + "provider, status, decision, score, reason_codes, reply_ms, next_step, created_on";

    private final JdbcTemplate jdbcTemplate;

    /** The conditions of a query and the values that go with them. */
    static final class Where {

        final StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        final List<Object> args = new ArrayList<>();

        Where and(final String condition, final Object... values) {
            sql.append(" AND ").append(condition);
            args.addAll(List.of(values));
            return this;
        }
    }

    @Transactional(readOnly = true)
    public LogPageData page(final LogFilter filter) {
        final Map<String, String> problems = new LinkedHashMap<>();
        final String decision = decision(filter.getDecision(), problems);
        final String status = choice(filter.getStatus(), STATUSES, "status", "DECIDED, HELD or AWAITING", problems);
        final String mode = choice(filter.getMode(), CreditDecisionService.MODES, "mode", "OFF, SHADOW or LIVE", problems);
        final LocalDate from = date(filter.getFrom(), "from", problems);
        final LocalDate to = date(filter.getTo(), "to", problems);
        if (from != null && to != null && from.isAfter(to)) {
            problems.put("from", "The first day cannot be after the last day");
        }
        final String sortKey = blank(filter.getSort()) ? "date_desc" : filter.getSort().trim().toLowerCase(Locale.ROOT);
        if (!SORTS.containsKey(sortKey)) {
            problems.put("sort", "Sort must be date_desc, date_asc, score_desc or score_asc");
        }
        CreditDecisionRules.throwIfAny(problems);

        final Where base = baseConditions(filter, mode, from, to);
        final LogCounts counts = counts(base);

        final Where narrowed = baseConditions(filter, mode, from, to);
        if (decision != null) {
            if ("NONE".equals(decision)) {
                narrowed.and("decision IS NULL");
            } else {
                narrowed.and("decision = ?", decision);
            }
        }
        if (status != null) {
            narrowed.and("status = ?", status);
        }
        final long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_credit_decision_log" + narrowed.sql, Long.class,
                narrowed.args.toArray());

        final int pageSize = Math.max(1, Math.min(filter.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : filter.getPageSize(), MAX_PAGE_SIZE));
        final int totalPages = (int) Math.max(1, (total + pageSize - 1) / pageSize);
        final int page = Math.max(1, Math.min(filter.getPage(), totalPages));

        final List<Object> args = new ArrayList<>(narrowed.args);
        args.add(pageSize);
        args.add((long) (page - 1) * pageSize);
        final List<LogEntryData> rows = jdbcTemplate.query("SELECT " + LIST_COLUMNS + " FROM m_credit_decision_log" + narrowed.sql
                + " ORDER BY " + SORTS.get(sortKey) + " LIMIT ? OFFSET ?", (rs, i) -> entry(rs, false), args.toArray());
        return new LogPageData(rows, total, page, pageSize, totalPages, counts);
    }

    /** One entry in full, with the request and the reply. */
    @Transactional(readOnly = true)
    public LogEntryData entry(final Long id) {
        final List<LogEntryData> rows = jdbcTemplate.query("SELECT * FROM m_credit_decision_log WHERE id = ?", (rs, i) -> entry(rs, true),
                id);
        if (rows.isEmpty()) {
            throw new CreditDecisionLogNotFoundException(id);
        }
        return rows.get(0);
    }

    private Where baseConditions(final LogFilter filter, final String mode, final LocalDate from, final LocalDate to) {
        final Where where = new Where();
        if (filter.getProfileId() != null) {
            where.and("profile_id = ?", filter.getProfileId());
        }
        if (mode != null) {
            where.and("mode = ?", mode);
        }
        if (!blank(filter.getSearch())) {
            final String like = "%" + escapeLike(filter.getSearch().trim()) + "%";
            where.and("(application_ref LIKE ? OR customer_ref LIKE ?)", like, like);
        }
        if (!blank(filter.getReason())) {
            where.and("reason_codes LIKE ?", "%" + escapeLike(filter.getReason().trim()) + "%");
        }
        if (from != null) {
            where.and("created_on >= ?", Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            where.and("created_on < ?", Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        return where;
    }

    private LogCounts counts(final Where base) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) AS total, SUM(decision = 'GO') AS go, SUM(decision = 'REVIEW') AS review, "
                + "SUM(decision = 'NO-GO') AS no_go, SUM(status = 'HELD') AS held, SUM(status = 'AWAITING') AS awaiting "
                + "FROM m_credit_decision_log" + base.sql,
                (rs, i) -> new LogCounts(rs.getLong("total"), rs.getLong("go"), rs.getLong("review"), rs.getLong("no_go"),
                        rs.getLong("held"), rs.getLong("awaiting")),
                base.args.toArray());
    }

    private static LogEntryData entry(final java.sql.ResultSet rs, final boolean withBodies) throws java.sql.SQLException {
        return new LogEntryData(rs.getLong("id"), rs.getString("application_ref"), (Long) rs.getObject("loan_id"),
                rs.getString("customer_ref"), (Long) rs.getObject("profile_id"), rs.getString("profile_name"),
                (Integer) rs.getObject("version_no"), rs.getString("mode"), rs.getString("provider"), rs.getString("status"),
                rs.getString("decision"), rs.getBigDecimal("score"), rs.getString("reason_codes"), (Integer) rs.getObject("reply_ms"),
                rs.getString("next_step"), withBodies ? rs.getString("request_json") : null,
                withBodies ? rs.getString("reply_json") : null, rs.getTimestamp("created_on").toLocalDateTime().toString());
    }

    private static String decision(final String raw, final Map<String, String> problems) {
        if (blank(raw)) {
            return null;
        }
        final String value = raw.trim().toUpperCase(Locale.ROOT).replace('_', '-');
        final String decision = "NOGO".equals(value) ? "NO-GO" : value;
        if (!DECISIONS.contains(decision)) {
            problems.put("decision", "Decision must be GO, REVIEW, NO-GO or NONE");
            return null;
        }
        return decision;
    }

    private static String choice(final String raw, final Set<String> allowed, final String param, final String expected,
            final Map<String, String> problems) {
        if (blank(raw)) {
            return null;
        }
        final String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            problems.put(param, param.substring(0, 1).toUpperCase(Locale.ROOT) + param.substring(1) + " must be " + expected);
            return null;
        }
        return value;
    }

    private static LocalDate date(final String raw, final String param, final Map<String, String> problems) {
        if (blank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (final DateTimeParseException e) {
            problems.put(param, "Use the date format yyyy-MM-dd");
            return null;
        }
    }

    /** So a typed % or _ is searched for as itself and not as a wildcard. */
    static String escapeLike(final String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean blank(final String text) {
        return text == null || text.isBlank();
    }
}
