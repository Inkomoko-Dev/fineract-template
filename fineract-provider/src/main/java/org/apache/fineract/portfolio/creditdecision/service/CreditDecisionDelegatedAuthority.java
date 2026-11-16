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
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.DelegatedAuthorityData.EntryData;
import org.apache.fineract.portfolio.creditdecision.data.DelegatedAuthorityData.RuleData;
import org.apache.fineract.portfolio.creditdecision.data.DelegatedAuthorityData.RuleRequest;
import org.apache.fineract.portfolio.creditdecision.exception.DelegatedAuthorityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The delegated authority rules: for an application the decision engine marked IC_REVIEW_REQUIRED, the country (currency),
 * the amount and the risk tier choose the IC level it enters, and the levels below that are skipped. An application no
 * rule fits starts at IC level one, as before. These rules choose where to start; the existing approval matrix still
 * decides where the application stops.
 */
@Service
@RequiredArgsConstructor
public class CreditDecisionDelegatedAuthority {

    private static final int MAX_DESCRIPTION = 200;
    private static final int MAX_RISK_TIER = 30;

    private final JdbcTemplate jdbc;
    private final PlatformSecurityContext context;

    // ------------------------------------------------------------------ matching

    /**
     * The rule that sends this application to its entry level, or null when none fits. When several fit, the one that
     * names a risk tier wins over one that does not, then the one that names the currency, then the narrower amount band,
     * then the older rule.
     */
    @Transactional(readOnly = true)
    public EntryData resolve(final String currency, final BigDecimal amount, final String riskTier) {
        if (amount == null) {
            return null;
        }
        final String tier = normaliseTier(riskTier);
        final String cur = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
        final List<RuleData> rules = jdbc.query(select() + " WHERE r.is_active = TRUE AND (r.currency IS NULL OR r.currency = ?) "
                + "AND (r.risk_tier IS NULL OR r.risk_tier = ?) AND r.min_amount <= ? AND (r.max_amount IS NULL OR r.max_amount >= ?)",
                (rs, i) -> map(rs), cur, tier == null ? "" : tier, amount, amount);
        return rules.stream().min(Comparator.<RuleData, Integer>comparing(r -> r.getRiskTier() == null ? 1 : 0)
                .thenComparing(r -> r.getCurrency() == null ? 1 : 0).thenComparing(CreditDecisionDelegatedAuthority::band)
                .thenComparing(RuleData::getId)).map(r -> EntryData.builder().ruleId(r.getId()).entryLevelNumber(r.getEntryLevelNumber())
                        .approverUserId(r.getApproverUserId())
                        .description(describe(r)).build()).orElse(null);
    }

    private static BigDecimal band(final RuleData rule) {
        return rule.getMaxAmount() == null ? new BigDecimal("1E30") : rule.getMaxAmount().subtract(rule.getMinAmount());
    }

    private static String describe(final RuleData rule) {
        return "rule " + rule.getId() + (rule.getDescription() == null || rule.getDescription().isBlank() ? "" : " (" + rule.getDescription() + ")");
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<RuleData> rules() {
        return jdbc.query(select() + " ORDER BY r.currency IS NULL, r.currency, r.risk_tier IS NULL, r.risk_tier, r.min_amount, r.id",
                (rs, i) -> map(rs));
    }

    @Transactional(readOnly = true)
    public RuleData rule(final Long id) {
        final List<RuleData> rows = jdbc.query(select() + " WHERE r.id = ?", (rs, i) -> map(rs), id);
        if (rows.isEmpty()) {
            throw new DelegatedAuthorityNotFoundException(id);
        }
        return rows.get(0);
    }

    // ------------------------------------------------------------------ changing

    @Transactional
    public RuleData create(final RuleRequest request) {
        final Validated v = validate(request);
        final Timestamp now = Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem());
        final String user = context.authenticatedUser().getUsername();
        jdbc.update("INSERT INTO m_ic_delegated_authority (currency, risk_tier, min_amount, max_amount, entry_level_number, "
                + "approver_user_id, is_active, description, created_by, created_on, updated_by, updated_on) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                v.currency, v.riskTier, v.minAmount, v.maxAmount, v.level, v.approverUserId, v.active, v.description, user, now, user, now);
        return rule(jdbc.queryForObject("SELECT MAX(id) FROM m_ic_delegated_authority", Long.class));
    }

    @Transactional
    public RuleData update(final Long id, final RuleRequest request) {
        rule(id);
        final Validated v = validate(request);
        jdbc.update("UPDATE m_ic_delegated_authority SET currency = ?, risk_tier = ?, min_amount = ?, max_amount = ?, "
                + "entry_level_number = ?, approver_user_id = ?, is_active = ?, description = ?, updated_by = ?, updated_on = ? WHERE id = ?",
                v.currency, v.riskTier, v.minAmount, v.maxAmount, v.level, v.approverUserId, v.active, v.description,
                context.authenticatedUser().getUsername(), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()), id);
        return rule(id);
    }

    @Transactional
    public void delete(final Long id) {
        rule(id);
        jdbc.update("DELETE FROM m_ic_delegated_authority WHERE id = ?", id);
    }

    private Validated validate(final RuleRequest request) {
        final Map<String, String> problems = new LinkedHashMap<>();
        final Validated v = new Validated();
        final String currency = request.getCurrency() == null ? "" : request.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!currency.isEmpty() && !currency.matches("[A-Z]{3}")) {
            problems.put("currency", "Give a 3 letter currency code such as KES, or leave it empty for every country");
        }
        v.currency = currency.isEmpty() ? null : currency;
        final String tier = normaliseTier(request.getRiskTier());
        if (tier != null && tier.length() > MAX_RISK_TIER) {
            problems.put("riskTier", "The risk tier can be at most " + MAX_RISK_TIER + " characters");
        }
        v.riskTier = tier;
        v.minAmount = request.getMinAmount() == null ? BigDecimal.ZERO : request.getMinAmount();
        v.maxAmount = request.getMaxAmount();
        if (v.minAmount.signum() < 0) {
            problems.put("minAmount", "The smallest amount cannot be negative");
        }
        if (v.maxAmount != null && v.maxAmount.compareTo(v.minAmount) < 0) {
            problems.put("maxAmount", "The largest amount cannot be below the smallest");
        }
        v.level = request.getEntryLevelNumber();
        if (v.level == null) {
            problems.put("entryLevelNumber", "Say which IC level the application enters");
        } else if (jdbc.queryForObject("SELECT COUNT(*) FROM m_ic_review_level_config WHERE level_number = ? AND is_active = TRUE", Integer.class,
                v.level) == 0) {
            problems.put("entryLevelNumber", "IC level " + v.level + " is not configured or not active");
        }
        v.approverUserId = request.getApproverUserId();
        if (v.approverUserId != null && jdbc.queryForObject("SELECT COUNT(*) FROM m_appuser WHERE id = ? AND is_deleted = FALSE", Integer.class,
                v.approverUserId) == 0) {
            problems.put("approverUserId", "That user does not exist");
        }
        v.active = request.getActive() == null || request.getActive();
        final String description = request.getDescription() == null ? null : request.getDescription().trim();
        if (description != null && description.length() > MAX_DESCRIPTION) {
            problems.put("description", "The description can be at most " + MAX_DESCRIPTION + " characters");
        }
        v.description = description == null || description.isEmpty() ? null : description;
        CreditDecisionRules.throwIfAny(problems);
        return v;
    }

    private static String normaliseTier(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String select() {
        return "SELECT r.id, r.currency, r.risk_tier, r.min_amount, r.max_amount, r.entry_level_number, l.level_name, r.approver_user_id, "
                + "u.username, r.is_active, r.description, r.updated_by, r.updated_on FROM m_ic_delegated_authority r "
                + "LEFT JOIN m_ic_review_level_config l ON l.level_number = r.entry_level_number "
                + "LEFT JOIN m_appuser u ON u.id = r.approver_user_id";
    }

    private static RuleData map(final java.sql.ResultSet rs) throws java.sql.SQLException {
        final Timestamp updated = rs.getTimestamp("updated_on");
        return RuleData.builder().id(rs.getLong("id")).currency(rs.getString("currency")).riskTier(rs.getString("risk_tier"))
                .minAmount(rs.getBigDecimal("min_amount")).maxAmount(rs.getBigDecimal("max_amount"))
                .entryLevelNumber(rs.getInt("entry_level_number")).entryLevelName(rs.getString("level_name"))
                .approverUserId((Long) rs.getObject("approver_user_id")).approverUsername(rs.getString("username"))
                .active(rs.getBoolean("is_active")).description(rs.getString("description")).updatedBy(rs.getString("updated_by"))
                .updatedOn(updated == null ? null : updated.toLocalDateTime().toString()).build();
    }

    private static final class Validated {

        String currency;
        String riskTier;
        BigDecimal minAmount;
        BigDecimal maxAmount;
        Integer level;
        Long approverUserId;
        boolean active;
        String description;
    }
}
