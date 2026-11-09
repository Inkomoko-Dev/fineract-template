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

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionCatalogue;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ActiveProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ConnectionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CreateProfileRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogEntryData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ParamData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProductData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.SaveDraftRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.VersionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.ProductProfileData;
import org.apache.fineract.portfolio.creditdecision.exception.CreditDecisionProfileNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the decision profiles: which data is sent to the decision engine for which loan products, who approved it, and
 * what the engine replied. It never scores or decides.
 */
@Service
@RequiredArgsConstructor
public class CreditDecisionService {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String RETIRED = "RETIRED";
    public static final Set<String> MODES = Set.of("OFF", "SHADOW", "LIVE");
    private static final Set<String> NO_REPLY_POLICIES = Set.of("RETRY", "ALERT");
    private static final String SELF_APPROVAL_CONFIG = "credit-decision-allow-self-approval";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<ProfileData> profiles() {
        return load(null);
    }

    @Transactional(readOnly = true)
    public ProfileData profile(final Long profileId) {
        final List<ProfileData> found = load(profileId);
        if (found.isEmpty()) {
            throw new CreditDecisionProfileNotFoundException(profileId);
        }
        return found.get(0);
    }

    /** Every loan product, and the profile (if any) that already uses it in a live, pending or draft version. */
    @Transactional(readOnly = true)
    public List<ProductData> products() {
        final Map<Long, Claim> claims = claims(null);
        return jdbcTemplate.query("SELECT id, name, currency_code FROM m_product_loan ORDER BY currency_code, name", (rs, i) -> {
            final long id = rs.getLong("id");
            final Claim claim = claims.get(id);
            return new ProductData(id, rs.getString("name"), rs.getString("currency_code"), claim == null ? null : claim.profileId,
                    claim == null ? null : claim.profileName);
        });
    }

    /**
     * The live version of the profile that covers a product, for the service that builds the request. Null when none.
     */
    @Transactional(readOnly = true)
    public ActiveProfileData activeForProduct(final Long productId) {
        final List<ActiveProfileData> rows = jdbcTemplate.query(
                "SELECT p.id, p.name, p.mode, v.id AS version_id, v.version_no FROM m_credit_decision_profile p "
                        + "JOIN m_credit_decision_profile_version v ON v.profile_id = p.id AND v.status = 'ACTIVE' "
                        + "JOIN m_credit_decision_version_product vp ON vp.version_id = v.id WHERE vp.loan_product_id = ? AND p.mode <> 'OFF'",
                (rs, i) -> {
                    final ConnectionData conn = connection();
                    return new ActiveProfileData(rs.getLong("id"), rs.getString("name"), rs.getString("mode"), rs.getInt("version_no"),
                            conn.isEnabled(), conn.getProvider(), paramsOf(rs.getLong("version_id")));
                }, productId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * The profile that covers a product, whatever state its versions are in. Null when the product is in no profile.
     */
    @Transactional(readOnly = true)
    public ProductProfileData profileForProduct(final Long productId) {
        final List<ProductProfileData> rows = jdbcTemplate.query("SELECT p.id, p.name, p.mode, (SELECT MAX(a.version_no) FROM "
                + "m_credit_decision_profile_version a WHERE a.profile_id = p.id AND a.status = 'ACTIVE') AS active_no "
                + "FROM m_credit_decision_version_product vp JOIN m_credit_decision_profile_version v ON v.id = vp.version_id "
                + "JOIN m_credit_decision_profile p ON p.id = v.profile_id WHERE vp.loan_product_id = ? "
                + "AND v.status IN ('ACTIVE', 'PENDING', 'DRAFT') ORDER BY p.id LIMIT 1", (rs, i) -> {
                    final int activeNo = rs.getInt("active_no");
                    final boolean noActiveVersion = rs.wasNull();
                    return new ProductProfileData(rs.getLong("id"), rs.getString("name"), rs.getString("mode"),
                            noActiveVersion ? null : activeNo);
                }, productId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional(readOnly = true)
    public ConnectionData connection() {
        return jdbcTemplate.queryForObject(
                "SELECT provider, enabled, endpoint_url, timeout_seconds, retries, on_no_reply, updated_by, "
                        + "updated_on FROM m_credit_decision_connection WHERE id = 1",
                (rs, i) -> new ConnectionData(rs.getString("provider"), rs.getBoolean("enabled"), rs.getString("endpoint_url"),
                        rs.getInt("timeout_seconds"), rs.getInt("retries"), rs.getString("on_no_reply"), rs.getString("updated_by"),
                        iso(rs.getTimestamp("updated_on"))));
    }

    // ------------------------------------------------------------------ profile and version changes

    @Transactional
    public ProfileData createProfile(final CreateProfileRequest request) {
        final String name = request.getName() == null ? "" : request.getName().trim();
        final List<Long> productIds = distinct(request.getProductIds());
        final List<ApiParameterError> errors = new ArrayList<>();
        if (name.isEmpty()) {
            errors.add(ApiParameterError.parameterError("validation.msg.creditdecision.name.mandatory", "Give the profile a name", "name"));
        } else if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_credit_decision_profile WHERE LOWER(name) = ?", Integer.class,
                name.toLowerCase(Locale.ROOT)) > 0) {
            errors.add(ApiParameterError.parameterError("validation.msg.creditdecision.name.duplicate",
                    "A profile with this name already exists", "name", name));
        }
        if (productIds.isEmpty()) {
            errors.add(ApiParameterError.parameterError("validation.msg.creditdecision.products.mandatory", "Choose at least one product",
                    "productIds"));
        }
        CreditDecisionRules.throwIfAny(errors);
        assertProductsFree(productIds, null);

        final List<ParamData> params = request.getCopyFromProfileId() == null ? CreditDecisionCatalogue.starterParams()
                : latestParams(profile(request.getCopyFromProfileId()));
        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        final String user = username();
        final long profileId = insert("INSERT INTO m_credit_decision_profile (name, mode, created_by, created_on) VALUES (?, 'OFF', ?, ?)",
                name, user, Timestamp.valueOf(now));
        insertVersion(profileId, 1, DRAFT, "First draft", user, now, productIds, params);
        return profile(profileId);
    }

    @Transactional
    public ProfileData saveDraft(final Long profileId, final int versionNo, final SaveDraftRequest request) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, DRAFT, "Only a draft can be changed");
        final List<ParamData> params = CreditDecisionRules.complete(request.getParams());
        CreditDecisionRules.throwIfAny(CreditDecisionRules.validateParams(request.getParams()));
        final List<Long> productIds = request.getProductIds() == null ? productIdsOf(version.id) : distinct(request.getProductIds());
        assertProductsFree(productIds, profileId);
        replaceParams(version.id, params);
        replaceProducts(version.id, productIds);
        return profile(profileId);
    }

    @Transactional
    public ProfileData newDraft(final Long profileId, final Integer fromVersion) {
        final ProfileData profile = profile(profileId);
        if (profile.getVersions().stream().anyMatch(v -> DRAFT.equals(v.getStatus()) || PENDING.equals(v.getStatus()))) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.change.in.progress",
                    "This profile already has a change in progress. Submit, withdraw or finish it first.");
        }
        final VersionData source = fromVersion != null
                ? profile.getVersions().stream().filter(v -> v.getVersionNo() == fromVersion).findFirst()
                        .orElseThrow(() -> new CreditDecisionProfileNotFoundException(profileId))
                : profile.getVersions().stream().filter(v -> ACTIVE.equals(v.getStatus())).findFirst().orElse(profile.getVersions().get(0));
        final Map<Long, Claim> taken = claims(profileId);
        final List<Long> productIds = source.getProductIds().stream().filter(id -> !taken.containsKey(id)).collect(Collectors.toList());
        final int next = profile.getVersions().stream().mapToInt(VersionData::getVersionNo).max().orElse(0) + 1;
        final String reason = fromVersion != null && !ACTIVE.equals(source.getStatus()) ? "Restored from v" + source.getVersionNo() : "";
        insertVersion(profileId, next, DRAFT, reason, username(), DateUtils.getLocalDateTimeOfSystem(), productIds, source.getParams());
        return profile(profileId);
    }

    @Transactional
    public ProfileData submit(final Long profileId, final int versionNo, final String reason) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, DRAFT, "Only a draft can be submitted for approval");
        final List<ParamData> params = paramsOf(version.id);
        final List<Long> productIds = productIdsOf(version.id);
        CreditDecisionRules.throwIfAny(CreditDecisionRules.submitProblems(params, productIds, reason));
        assertProductsFree(productIds, profileId);
        jdbcTemplate.update(
                "UPDATE m_credit_decision_profile_version SET status = ?, reason = ?, prepared_by = ?, prepared_on = ?, "
                        + "decided_by = NULL, decided_on = NULL WHERE id = ?",
                PENDING, reason.trim(), username(), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()), version.id);
        return profile(profileId);
    }

    @Transactional
    public ProfileData withdraw(final Long profileId, final int versionNo) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, PENDING, "Only a version awaiting approval can be withdrawn");
        jdbcTemplate.update("UPDATE m_credit_decision_profile_version SET status = ? WHERE id = ?", DRAFT, version.id);
        return profile(profileId);
    }

    @Transactional
    public ProfileData reject(final Long profileId, final int versionNo, final String reason) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, PENDING, "Only a version awaiting approval can be rejected");
        if (reason == null || reason.isBlank()) {
            CreditDecisionRules.throwIfAny(Map.of("reason", "Say why it is being returned"));
        }
        jdbcTemplate.update(
                "UPDATE m_credit_decision_profile_version SET status = ?, reason = ?, decided_by = ?, decided_on = ? WHERE id = ?", DRAFT,
                "Returned: " + reason.trim(), username(), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()), version.id);
        return profile(profileId);
    }

    @Transactional
    public ProfileData approve(final Long profileId, final int versionNo) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, PENDING, "Only a version awaiting approval can be approved");
        if (version.preparedBy.equalsIgnoreCase(username()) && !selfApprovalAllowed()) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.approver.must.differ",
                    "A second person must approve this version. It was submitted by " + version.preparedBy + ".");
        }
        assertProductsFree(productIdsOf(version.id), profileId);
        jdbcTemplate.update("UPDATE m_credit_decision_profile_version SET status = ? WHERE profile_id = ? AND status = ?", RETIRED,
                profileId, ACTIVE);
        jdbcTemplate.update("UPDATE m_credit_decision_profile_version SET status = ?, decided_by = ?, decided_on = ? WHERE id = ?", ACTIVE,
                username(), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()), version.id);
        return profile(profileId);
    }

    @Transactional
    public ProfileData setMode(final Long profileId, final String requested) {
        final String mode = requested == null ? "" : requested.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            CreditDecisionRules.throwIfAny(Map.of("mode", "Mode must be Off, Shadow or Live"));
        }
        final ProfileData profile = profile(profileId);
        if (!"OFF".equals(mode) && profile.getVersions().stream().noneMatch(v -> ACTIVE.equals(v.getStatus()))) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.mode.needs.active.version",
                    "Approve a version before switching this profile to " + mode.toLowerCase(Locale.ROOT) + ".");
        }
        if ("LIVE".equals(mode)) {
            context.authenticatedUser().validateHasPermissionTo("APPROVE_CREDITDECISION");
        }
        jdbcTemplate.update("UPDATE m_credit_decision_profile SET mode = ? WHERE id = ?", mode, profileId);
        return profile(profileId);
    }

    // ------------------------------------------------------------------ connection and log

    @Transactional
    public ConnectionData updateConnection(final ConnectionData request) {
        final ConnectionData current = connection();
        final String provider = request.getProvider() == null || request.getProvider().isBlank() ? current.getProvider()
                : request.getProvider().trim();
        final String policy = request.getOnNoReply() == null ? current.getOnNoReply()
                : request.getOnNoReply().trim().toUpperCase(Locale.ROOT);
        final Map<String, String> problems = new LinkedHashMap<>();
        if (!NO_REPLY_POLICIES.contains(policy)) {
            problems.put("onNoReply", "Choose retry, or alert the credit team");
        }
        if (request.getTimeoutSeconds() < 1 || request.getTimeoutSeconds() > 120) {
            problems.put("timeoutSeconds", "Wait between 1 and 120 seconds");
        }
        if (request.getRetries() < 0 || request.getRetries() > 10) {
            problems.put("retries", "Retries must be between 0 and 10");
        }
        final String url = request.getEndpointUrl() == null ? "" : request.getEndpointUrl().trim();
        if (!url.isEmpty() && !url.matches("^https?://\\S+$")) {
            problems.put("endpointUrl", "Enter a web address starting with http:// or https://");
        }
        if (request.isEnabled() && url.isEmpty()) {
            problems.put("endpointUrl", "Set the endpoint before switching sending on");
        }
        CreditDecisionRules.throwIfAny(problems);
        jdbcTemplate.update(
                "UPDATE m_credit_decision_connection SET provider = ?, enabled = ?, endpoint_url = ?, timeout_seconds = ?, "
                        + "retries = ?, on_no_reply = ?, updated_by = ?, updated_on = ? WHERE id = 1",
                provider, request.isEnabled(), url.isEmpty() ? null : url, request.getTimeoutSeconds(), request.getRetries(), policy,
                username(), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()));
        return connection();
    }

    @Transactional
    public Long recordLog(final LogEntryData entry) {
        final Map<String, String> problems = new LinkedHashMap<>();
        if (entry.getApplicationRef() == null || entry.getApplicationRef().isBlank()) {
            problems.put("applicationRef", "Say which application this decision is for");
        }
        final String status = entry.getStatus() == null ? "" : entry.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DECIDED", "HELD", "AWAITING").contains(status)) {
            problems.put("status", "Status must be DECIDED, HELD or AWAITING");
        }
        CreditDecisionRules.throwIfAny(problems);
        final ConnectionData conn = connection();
        return insert(
                "INSERT INTO m_credit_decision_log (application_ref, loan_id, customer_ref, profile_id, profile_name, version_no, mode, "
                        + "provider, status, decision, score, reason_codes, reply_ms, next_step, request_json, reply_json, created_on, "
                        + "risk_tier, max_exposure, model_version, request_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entry.getApplicationRef().trim(), entry.getLoanId(), entry.getCustomerRef(), entry.getProfileId(), entry.getProfileName(),
                entry.getVersionNo(), entry.getMode(),
                entry.getProvider() == null || entry.getProvider().isBlank() ? conn.getProvider() : entry.getProvider(), status,
                entry.getDecision(), entry.getScore(), entry.getReasonCodes(), entry.getReplyMs(), entry.getNextStep(),
                entry.getRequestJson(), entry.getReplyJson(), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()), entry.getRiskTier(),
                entry.getMaxExposure(), entry.getModelVersion(), entry.getRequestId());
    }

    // ------------------------------------------------------------------ helpers

    private static final class Claim {

        final Long profileId;
        final String profileName;

        Claim(final Long profileId, final String profileName) {
            this.profileId = profileId;
            this.profileName = profileName;
        }
    }

    private static final class VersionRow {

        final long id;
        final String status;
        final String preparedBy;

        VersionRow(final long id, final String status, final String preparedBy) {
            this.id = id;
            this.status = status;
            this.preparedBy = preparedBy;
        }
    }

    /**
     * Products used by another profile's live, pending or draft version. One product can belong to one profile only.
     */
    private Map<Long, Claim> claims(final Long exceptProfileId) {
        final Map<Long, Claim> out = new LinkedHashMap<>();
        final StringBuilder sql = new StringBuilder(
                "SELECT vp.loan_product_id, p.id AS profile_id, p.name FROM m_credit_decision_version_product vp "
                        + "JOIN m_credit_decision_profile_version v ON v.id = vp.version_id "
                        + "JOIN m_credit_decision_profile p ON p.id = v.profile_id WHERE v.status IN ('ACTIVE', 'PENDING', 'DRAFT')");
        final List<Object> args = new ArrayList<>();
        if (exceptProfileId != null) {
            sql.append(" AND p.id <> ?");
            args.add(exceptProfileId);
        }
        sql.append(" ORDER BY p.id");
        jdbcTemplate.query(sql.toString(), rs -> {
            out.putIfAbsent(rs.getLong("loan_product_id"), new Claim(rs.getLong("profile_id"), rs.getString("name")));
        }, args.toArray());
        return out;
    }

    private void assertProductsFree(final List<Long> productIds, final Long profileId) {
        if (productIds.isEmpty()) {
            return;
        }
        final Set<Long> known = new LinkedHashSet<>(jdbcTemplate.queryForList("SELECT id FROM m_product_loan", Long.class));
        final Map<Long, Claim> taken = claims(profileId);
        final List<ApiParameterError> errors = new ArrayList<>();
        for (final Long id : productIds) {
            if (!known.contains(id)) {
                errors.add(ApiParameterError.parameterError("validation.msg.creditdecision.product.unknown",
                        "Loan product " + id + " does not exist", "productIds", id));
            } else if (taken.containsKey(id)) {
                errors.add(ApiParameterError.parameterError("validation.msg.creditdecision.product.taken",
                        "Loan product " + id + " is already used by the profile '" + taken.get(id).profileName + "'", "productIds", id));
            }
        }
        CreditDecisionRules.throwIfAny(errors);
    }

    private List<ProfileData> load(final Long profileId) {
        final String where = profileId == null ? "" : " WHERE p.id = ?";
        final Object[] args = profileId == null ? new Object[0] : new Object[] { profileId };
        final Map<Long, List<VersionData>> versionsByProfile = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT v.*, p.id AS pid FROM m_credit_decision_profile_version v JOIN m_credit_decision_profile p ON p.id = v.profile_id"
                        + where + " ORDER BY v.version_no DESC",
                rs -> {
                    final long versionId = rs.getLong("id");
                    versionsByProfile.computeIfAbsent(rs.getLong("pid"), k -> new ArrayList<>())
                            .add(new VersionData(versionId, rs.getInt("version_no"), rs.getString("status"), rs.getString("reason"),
                                    rs.getString("prepared_by"), iso(rs.getTimestamp("prepared_on")), rs.getString("decided_by"),
                                    iso(rs.getTimestamp("decided_on")), productIdsOf(versionId), paramsOf(versionId)));
                }, args);
        return jdbcTemplate.query(
                "SELECT p.id, p.name, p.mode, p.created_by, p.created_on FROM m_credit_decision_profile p" + where + " ORDER BY p.name",
                (rs, i) -> new ProfileData(rs.getLong("id"), rs.getString("name"), rs.getString("mode"), rs.getString("created_by"),
                        iso(rs.getTimestamp("created_on")), versionsByProfile.getOrDefault(rs.getLong("id"), List.of())),
                args);
    }

    private List<ParamData> paramsOf(final long versionId) {
        final Map<String, ParamData> stored = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT param_key, enabled, field_name, missing_policy FROM m_credit_decision_version_param WHERE version_id = ?", rs -> {
                    stored.put(rs.getString("param_key"), new ParamData(rs.getString("param_key"), rs.getBoolean("enabled"),
                            rs.getString("field_name"), rs.getString("missing_policy")));
                }, versionId);
        // every catalogue item is always present, in catalogue order, so a newly added item shows up switched off
        return CreditDecisionRules.complete(new ArrayList<>(stored.values()));
    }

    private List<Long> productIdsOf(final long versionId) {
        return jdbcTemplate.queryForList(
                "SELECT loan_product_id FROM m_credit_decision_version_product WHERE version_id = ? ORDER BY loan_product_id", Long.class,
                versionId);
    }

    private VersionRow version(final Long profileId, final int versionNo) {
        final List<VersionRow> rows = jdbcTemplate.query(
                "SELECT id, status, prepared_by FROM m_credit_decision_profile_version WHERE profile_id = ? AND version_no = ?",
                (rs, i) -> new VersionRow(rs.getLong("id"), rs.getString("status"), rs.getString("prepared_by")), profileId, versionNo);
        if (rows.isEmpty()) {
            throw new CreditDecisionProfileNotFoundException(profileId);
        }
        return rows.get(0);
    }

    private static void requireStatus(final VersionRow version, final String expected, final String message) {
        if (!expected.equals(version.status)) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.version.status.invalid",
                    message + ". This version is " + version.status.toLowerCase(Locale.ROOT) + ".");
        }
    }

    private static List<ParamData> latestParams(final ProfileData profile) {
        final VersionData source = profile.getVersions().stream().filter(v -> ACTIVE.equals(v.getStatus())).findFirst()
                .orElse(profile.getVersions().get(0));
        return source.getParams();
    }

    private void insertVersion(final long profileId, final int versionNo, final String status, final String reason, final String user,
            final LocalDateTime now, final List<Long> productIds, final List<ParamData> params) {
        final long versionId = insert("INSERT INTO m_credit_decision_profile_version (profile_id, version_no, status, reason, prepared_by, "
                + "prepared_on) VALUES (?, ?, ?, ?, ?, ?)", profileId, versionNo, status, reason, user, Timestamp.valueOf(now));
        replaceParams(versionId, CreditDecisionRules.complete(params));
        replaceProducts(versionId, productIds);
    }

    private void replaceParams(final long versionId, final List<ParamData> params) {
        jdbcTemplate.update("DELETE FROM m_credit_decision_version_param WHERE version_id = ?", versionId);
        for (final ParamData p : params) {
            jdbcTemplate.update("INSERT INTO m_credit_decision_version_param (version_id, param_key, enabled, field_name, missing_policy) "
                    + "VALUES (?, ?, ?, ?, ?)", versionId, p.getKey(), p.isEnabled(), p.getFieldName(), p.getMissingPolicy());
        }
    }

    private void replaceProducts(final long versionId, final List<Long> productIds) {
        jdbcTemplate.update("DELETE FROM m_credit_decision_version_product WHERE version_id = ?", versionId);
        for (final Long productId : productIds) {
            jdbcTemplate.update("INSERT INTO m_credit_decision_version_product (version_id, loan_product_id) VALUES (?, ?)", versionId,
                    productId);
        }
    }

    private long insert(final String sql, final Object... args) {
        final KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            final PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private boolean selfApprovalAllowed() {
        final List<Boolean> rows = jdbcTemplate.query("SELECT enabled FROM c_configuration WHERE name = ?",
                (rs, i) -> rs.getBoolean("enabled"), SELF_APPROVAL_CONFIG);
        return !rows.isEmpty() && rows.get(0);
    }

    private String username() {
        return context.authenticatedUser().getUsername();
    }

    private static List<Long> distinct(final List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(i -> i != null).distinct().collect(Collectors.toList());
    }

    private static String iso(final Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().toString();
    }
}
