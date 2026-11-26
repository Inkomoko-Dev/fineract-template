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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emergency kill switch (CGLT-849 FR-03). GLOBAL / COUNTRY (currency) / PRODUCT. When killed, auto-approval is suppressed
 * and the application enters manual IC Level 1. Cache is cleared immediately on change.
 */
@Service
@RequiredArgsConstructor
public class CreditDecisionKillSwitch {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_COUNTRY = "COUNTRY";
    public static final String SCOPE_PRODUCT = "PRODUCT";
    private static final Set<String> SCOPES = Set.of(SCOPE_GLOBAL, SCOPE_COUNTRY, SCOPE_PRODUCT);
    private static final String GLOBAL_KEY = "*";

    private final JdbcTemplate jdbc;
    private final PlatformSecurityContext context;
    private final CreditDecisionService profiles;
    /** Snapshot for hot-path evaluate(); null means reload from DB. */
    private final AtomicReference<List<SwitchData>> cache = new AtomicReference<>();

    @Getter
    @Builder
    public static class SwitchData {
        private final Long id;
        private final String scope;
        private final String scopeKey;
        private final boolean enabled;
        private final String reason;
        private final String updatedBy;
        private final String updatedOn;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SwitchRequest {
        private String scope;
        private String scopeKey;
        private Boolean enabled;
        private String reason;
    }

    @Getter
    @Builder
    public static class KillHit {
        private final boolean killed;
        private final String scope;
        private final String scopeKey;
        private final String reason;
    }

    @Transactional(readOnly = true)
    public List<SwitchData> list() {
        return loadFromDb();
    }

    @Transactional
    public SwitchData upsert(final SwitchRequest request) {
        final Map<String, String> problems = new LinkedHashMap<>();
        final String scope = request.getScope() == null ? "" : request.getScope().trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            problems.put("scope", "Scope must be GLOBAL, COUNTRY or PRODUCT");
        }
        String key = request.getScopeKey() == null ? "" : request.getScopeKey().trim();
        if (SCOPE_GLOBAL.equals(scope)) {
            key = GLOBAL_KEY;
        } else if (SCOPE_COUNTRY.equals(scope)) {
            key = key.toUpperCase(Locale.ROOT);
            if (!key.matches("[A-Z]{3}")) {
                problems.put("scopeKey", "Country scope needs a 3-letter currency code such as KES");
            }
        } else if (SCOPE_PRODUCT.equals(scope)) {
            if (!key.matches("\\d+")) {
                problems.put("scopeKey", "Product scope needs the loan product id");
            }
        }
        if (request.getEnabled() == null) {
            problems.put("enabled", "Say whether the kill switch is on or off");
        }
        final String reason = request.getReason() == null ? null : request.getReason().trim();
        if (reason != null && reason.length() > 500) {
            problems.put("reason", "The reason can be at most 500 characters");
        }
        CreditDecisionRules.throwIfAny(problems);

        final Timestamp now = Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem());
        final String user = context.authenticatedUser().getUsername();
        jdbc.update("INSERT INTO m_credit_decision_kill_switch (scope, scope_key, enabled, reason, updated_by, updated_on) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), reason = VALUES(reason), "
                + "updated_by = VALUES(updated_by), updated_on = VALUES(updated_on)", scope, key, request.getEnabled(),
                reason == null || reason.isEmpty() ? null : reason, user, now);
        invalidateCaches();
        final String scopeKey = key;
        return loadFromDb().stream().filter(s -> scope.equals(s.getScope()) && scopeKey.equals(s.getScopeKey())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Kill switch row missing after upsert"));
    }

    /** First matching enabled switch by precedence GLOBAL > COUNTRY > PRODUCT. Uses in-process cache. */
    @Transactional(readOnly = true)
    public KillHit evaluate(final String currency, final Long productId) {
        final List<SwitchData> enabled = snapshot().stream().filter(SwitchData::isEnabled).toList();
        for (final SwitchData s : enabled) {
            if (SCOPE_GLOBAL.equals(s.getScope())) {
                return hit(s);
            }
        }
        final String ccy = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!ccy.isEmpty()) {
            for (final SwitchData s : enabled) {
                if (SCOPE_COUNTRY.equals(s.getScope()) && ccy.equals(s.getScopeKey())) {
                    return hit(s);
                }
            }
        }
        if (productId != null) {
            final String pid = String.valueOf(productId);
            for (final SwitchData s : enabled) {
                if (SCOPE_PRODUCT.equals(s.getScope()) && pid.equals(s.getScopeKey())) {
                    return hit(s);
                }
            }
        }
        return KillHit.builder().killed(false).build();
    }

    void invalidateCaches() {
        cache.set(null);
        profiles.invalidateActiveProfileCache();
    }

    private List<SwitchData> snapshot() {
        List<SwitchData> snap = cache.get();
        if (snap == null) {
            snap = loadFromDb();
            cache.set(snap);
        }
        return snap;
    }

    private List<SwitchData> loadFromDb() {
        return jdbc.query("SELECT id, scope, scope_key, enabled, reason, updated_by, updated_on FROM m_credit_decision_kill_switch "
                + "ORDER BY FIELD(scope, 'GLOBAL', 'COUNTRY', 'PRODUCT'), scope_key", (rs, i) -> SwitchData.builder().id(rs.getLong("id"))
                        .scope(rs.getString("scope")).scopeKey(rs.getString("scope_key")).enabled(rs.getBoolean("enabled"))
                        .reason(rs.getString("reason")).updatedBy(rs.getString("updated_by"))
                        .updatedOn(rs.getTimestamp("updated_on") == null ? null : rs.getTimestamp("updated_on").toLocalDateTime().toString())
                        .build());
    }

    private static KillHit hit(final SwitchData s) {
        return KillHit.builder().killed(true).scope(s.getScope()).scopeKey(s.getScopeKey()).reason(s.getReason()).build();
    }
}
