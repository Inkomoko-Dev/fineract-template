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
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operational metrics for the credit decision control framework (CGLT-849 FR-05). */
@Service
@RequiredArgsConstructor
public class CreditDecisionMetrics {

    /** Protects the TAT percentile queries from unbounded history scans. */
    static final int MAX_RANGE_DAYS = 90;

    private final JdbcTemplate jdbc;

    @Getter
    @Builder
    public static class MetricsData {
        private final String from;
        private final String to;
        private final String currency;
        private final Long productId;
        private final long throughput;
        private final long decided;
        private final long autoApprove;
        private final long icReview;
        private final long ineligible;
        private final long held;
        private final long exceptions;
        private final BigDecimal autoApproveRate;
        private final BigDecimal reviewRate;
        private final BigDecimal ineligibleRate;
        private final BigDecimal exceptionRate;
        private final Integer tatP50Ms;
        private final Integer tatP95Ms;
        private final List<DailyPoint> daily;
    }

    @Getter
    @Builder
    public static class DailyPoint {
        private final String day;
        private final long throughput;
        private final long autoApprove;
        private final long icReview;
        private final long exceptions;
    }

    @Transactional(readOnly = true)
    public MetricsData metrics(final String fromRaw, final String toRaw, final String currency, final Long productId) {
        final Map<String, String> problems = new LinkedHashMap<>();
        final LocalDate from = parseDate(fromRaw, "from", problems);
        final LocalDate to = parseDate(toRaw, "to", problems);
        if (from != null && to != null && from.isAfter(to)) {
            problems.put("from", "The first day cannot be after the last day");
        }
        CreditDecisionRules.throwIfAny(problems);

        final LocalDate end = to == null ? LocalDate.now() : to;
        final LocalDate start = from == null ? end.minusDays(29) : from;
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            problems.put("to", "The range can be at most " + MAX_RANGE_DAYS + " days");
            CreditDecisionRules.throwIfAny(problems);
        }

        final StringBuilder where = new StringBuilder(" WHERE log.created_on >= ? AND log.created_on < ?");
        final List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(start.atStartOfDay()));
        args.add(Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
        if (currency != null && !currency.isBlank()) {
            where.append(" AND loan.currency_code = ?");
            args.add(currency.trim().toUpperCase());
        }
        if (productId != null) {
            where.append(" AND loan.product_id = ?");
            args.add(productId);
        }

        final String join = " FROM m_credit_decision_log log LEFT JOIN m_loan loan ON loan.id = log.loan_id";
        final Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) AS throughput, "
                + "SUM(log.status = 'DECIDED') AS decided, "
                + "SUM(log.decision = 'AUTO_APPROVE') AS auto_approve, "
                + "SUM(log.decision = 'IC_REVIEW_REQUIRED') AS ic_review, "
                + "SUM(log.decision = 'INELIGIBLE') AS ineligible, "
                + "SUM(log.status = 'HELD') AS held, "
                + "SUM(log.status = 'AWAITING' OR log.status = 'HELD') AS exceptions "
                + join + where, args.toArray());

        final long throughput = number(row.get("throughput"));
        final long decided = number(row.get("decided"));
        final long autoApprove = number(row.get("auto_approve"));
        final long icReview = number(row.get("ic_review"));
        final long ineligible = number(row.get("ineligible"));
        final long held = number(row.get("held"));
        final long exceptions = number(row.get("exceptions"));

        final Integer tatP50 = tatPercentile(join, where.toString(), args, 0.50);
        final Integer tatP95 = tatPercentile(join, where.toString(), args, 0.95);

        final List<DailyPoint> daily = jdbc.query(
                "SELECT DATE(log.created_on) AS day, COUNT(*) AS throughput, "
                        + "SUM(log.decision = 'AUTO_APPROVE') AS auto_approve, "
                        + "SUM(log.decision = 'IC_REVIEW_REQUIRED') AS ic_review, "
                        + "SUM(log.status = 'AWAITING' OR log.status = 'HELD') AS exceptions " + join + where
                        + " GROUP BY DATE(log.created_on) ORDER BY day",
                (rs, i) -> DailyPoint.builder().day(rs.getDate("day").toLocalDate().toString()).throughput(rs.getLong("throughput"))
                        .autoApprove(rs.getLong("auto_approve")).icReview(rs.getLong("ic_review")).exceptions(rs.getLong("exceptions"))
                        .build(),
                args.toArray());

        return MetricsData.builder().from(start.toString()).to(end.toString()).currency(currency).productId(productId)
                .throughput(throughput).decided(decided).autoApprove(autoApprove).icReview(icReview).ineligible(ineligible).held(held)
                .exceptions(exceptions).autoApproveRate(rate(autoApprove, decided)).reviewRate(rate(icReview, decided))
                .ineligibleRate(rate(ineligible, decided)).exceptionRate(rate(exceptions, throughput)).tatP50Ms(tatP50)
                .tatP95Ms(tatP95).daily(daily).build();
    }

    /**
     * Reads a single percentile value via COUNT + ORDER BY LIMIT 1 OFFSET n — avoids loading all reply_ms into memory.
     */
    private Integer tatPercentile(final String join, final String where, final List<Object> args, final double p) {
        final Long count = jdbc.queryForObject("SELECT COUNT(*) " + join + where + " AND log.reply_ms IS NOT NULL", Long.class,
                args.toArray());
        if (count == null || count == 0) {
            return null;
        }
        final long offset = Math.min(count - 1, Math.max(0, (long) Math.ceil(p * count) - 1));
        final List<Integer> one = jdbc.query(
                "SELECT log.reply_ms " + join + where + " AND log.reply_ms IS NOT NULL ORDER BY log.reply_ms ASC LIMIT 1 OFFSET " + offset,
                (rs, i) -> rs.getInt(1), args.toArray());
        return one.isEmpty() ? null : one.get(0);
    }

    private static LocalDate parseDate(final String raw, final String field, final Map<String, String> problems) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (final DateTimeParseException e) {
            problems.put(field, "Use yyyy-MM-dd");
            return null;
        }
    }

    private static long number(final Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static BigDecimal rate(final long part, final long whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP);
    }
}
