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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionCatalogue;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ActiveProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.AuditData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ConnectionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CreateProfileRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogEntryData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ParamData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.PolicyValueData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProductData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.SaveDraftRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.VersionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.ProductProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionPolicy;
import org.apache.fineract.portfolio.creditdecision.exception.CreditDecisionProfileNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    /** Another node may have changed the profile, so a cached answer is trusted for this long at most. */
    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final int AUDIT_VALUE_MAX = 500;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    /** The live profile per product, with its parameters, policy and the connection, until something that feeds it changes. */
    private final ConcurrentMap<Long, CachedActive> activeCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheGeneration = new AtomicLong();

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
     * The answer, with the parameters, the policy that applies to this product and the connection snapshot, is kept per
     * product and dropped when a version is approved, a mode changes or the connection changes.
     */
    @Transactional(readOnly = true)
    public ActiveProfileData activeForProduct(final Long productId) {
        if (productId == null) {
            return null;
        }
        final long now = System.nanoTime();
        final CachedActive hit = activeCache.get(productId);
        if (hit != null && now - hit.loadedAt < CACHE_TTL_NANOS) {
            return hit.data;
        }
        final long generation = cacheGeneration.get();
        final ActiveProfileData fresh = loadActive(productId);
        // a change that landed while this was being read would make it stale, so it is not kept
        if (generation == cacheGeneration.get()) {
            activeCache.put(productId, new CachedActive(fresh, now));
        }
        return fresh;
    }

    private ActiveProfileData loadActive(final Long productId) {
        // the claim holds one profile per product, so there is never more than one row
        final List<ActiveProfileData> rows = jdbcTemplate.query(
                "SELECT p.id, p.name, p.mode, v.id AS version_id, v.version_no, pr.currency_code FROM m_credit_decision_product_claim c "
                        + "JOIN m_credit_decision_profile p ON p.id = c.profile_id "
                        + "JOIN m_credit_decision_profile_version v ON v.profile_id = p.id AND v.status = 'ACTIVE' "
                        + "JOIN m_credit_decision_version_product vp ON vp.version_id = v.id AND vp.loan_product_id = c.loan_product_id "
                        + "JOIN m_product_loan pr ON pr.id = c.loan_product_id WHERE c.loan_product_id = ? AND p.mode <> 'OFF'",
                (rs, i) -> {
                    final ConnectionData conn = connection();
                    final long versionId = rs.getLong("version_id");
                    return new ActiveProfileData(rs.getLong("id"), rs.getString("name"), rs.getString("mode"), rs.getInt("version_no"),
                            conn.isEnabled(), conn.getProvider(), paramsOf(versionId),
                            CreditDecisionPolicy.effective(rulesOf(versionId), productId, rs.getString("currency_code")));
                }, productId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Forgets what is kept about live profiles: now, and again once the change that asked for it has been committed. */
    private void dropCachedActive() {
        evictActive();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    evictActive();
                }
            });
        }
    }

    private void evictActive() {
        cacheGeneration.incrementAndGet();
        activeCache.clear();
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

    /** What was done to a profile and its versions, by whom, when, and each field before and after. Newest first. */
    @Transactional(readOnly = true)
    public List<AuditData> history(final Long profileId) {
        profile(profileId);
        return jdbcTemplate.query(
                "SELECT id, version_no, action, field_name, old_value, new_value, acted_by, acted_on FROM m_credit_decision_audit "
                        + "WHERE profile_id = ? ORDER BY acted_on DESC, id ASC",
                (rs, i) -> {
                    final int versionNo = rs.getInt("version_no");
                    final Integer version = rs.wasNull() ? null : versionNo;
                    return new AuditData(rs.getLong("id"), version, rs.getString("action"), rs.getString("field_name"),
                            rs.getString("old_value"), rs.getString("new_value"), rs.getString("acted_by"),
                            iso(rs.getTimestamp("acted_on")));
                }, profileId);
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

        final List<ParamData> params;
        final List<PolicyValueData> rules;
        if (request.getCopyFromProfileId() == null) {
            params = CreditDecisionCatalogue.starterParams();
            rules = CreditDecisionPolicy.normalise(CreditDecisionPolicy.starter());
        } else {
            final VersionData source = latestVersion(profile(request.getCopyFromProfileId()));
            params = source.getParams();
            rules = source.getRules();
        }
        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        final String user = username();
        final long profileId = insert("INSERT INTO m_credit_decision_profile (name, mode, created_by, created_on) VALUES (?, 'OFF', ?, ?)",
                name, user, Timestamp.valueOf(now));
        insertVersion(profileId, 1, DRAFT, "First draft", user, now, productIds, params, rules);
        syncClaims(profileId);
        audit(profileId, 1, "PROFILE_CREATED", "name", null, name, now);
        return profile(profileId);
    }

    @Transactional
    public ProfileData saveDraft(final Long profileId, final int versionNo, final SaveDraftRequest request) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, DRAFT, "Only a draft can be changed");
        // leaving the parameters out keeps them as they are, so the policy can be saved on its own
        final List<ParamData> params = request.getParams() == null ? paramsOf(version.id) : CreditDecisionRules.complete(request.getParams());
        CreditDecisionRules.throwIfAny(CreditDecisionRules.validateParams(request.getParams()));
        final List<PolicyValueData> rules = request.getRules() == null ? null : CreditDecisionPolicy.normalise(request.getRules());
        if (rules != null) {
            CreditDecisionRules.throwIfAny(CreditDecisionPolicy.structuralProblems(rules));
        }
        final List<Long> productIds = request.getProductIds() == null ? productIdsOf(version.id) : distinct(request.getProductIds());
        assertProductsFree(productIds, profileId);
        final Map<String, String> before = snapshot(version.id);
        replaceParams(version.id, params);
        replaceProducts(version.id, productIds);
        if (rules != null) {
            replaceRules(version.id, rules);
        }
        syncClaims(profileId);
        auditChanges(profileId, versionNo, "DRAFT_SAVED", before, snapshot(version.id), DateUtils.getLocalDateTimeOfSystem());
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
        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        insertVersion(profileId, next, DRAFT, reason, username(), now, productIds, source.getParams(), source.getRules());
        syncClaims(profileId);
        audit(profileId, next, "DRAFT_CREATED", "copiedFrom", null, "v" + source.getVersionNo(), now);
        return profile(profileId);
    }

    @Transactional
    public ProfileData submit(final Long profileId, final int versionNo, final String reason) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, DRAFT, "Only a draft can be submitted for approval");
        final List<ParamData> params = paramsOf(version.id);
        final List<Long> productIds = productIdsOf(version.id);
        final Map<String, String> problems = new LinkedHashMap<>(CreditDecisionRules.submitProblems(params, productIds, reason));
        problems.putAll(CreditDecisionPolicy.submitProblems(rulesOf(version.id), productIds, currenciesOf(productIds)));
        CreditDecisionRules.throwIfAny(problems);
        assertProductsFree(productIds, profileId);
        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        jdbcTemplate.update(
                "UPDATE m_credit_decision_profile_version SET status = ?, reason = ?, prepared_by = ?, prepared_on = ?, "
                        + "submitted_by = ?, submitted_on = ?, decided_by = NULL, decided_on = NULL WHERE id = ?",
                PENDING, reason.trim(), username(), Timestamp.valueOf(now), username(), Timestamp.valueOf(now), version.id);
        audit(profileId, versionNo, "SUBMITTED", "status", DRAFT, PENDING, now);
        return profile(profileId);
    }

    @Transactional
    public ProfileData withdraw(final Long profileId, final int versionNo) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, PENDING, "Only a version awaiting approval can be withdrawn");
        jdbcTemplate.update("UPDATE m_credit_decision_profile_version SET status = ? WHERE id = ?", DRAFT, version.id);
        audit(profileId, versionNo, "WITHDRAWN", "status", PENDING, DRAFT, DateUtils.getLocalDateTimeOfSystem());
        return profile(profileId);
    }

    @Transactional
    public ProfileData reject(final Long profileId, final int versionNo, final String reason) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, PENDING, "Only a version awaiting approval can be rejected");
        if (reason == null || reason.isBlank()) {
            CreditDecisionRules.throwIfAny(Map.of("reason", "Say why it is being returned"));
        }
        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        jdbcTemplate.update(
                "UPDATE m_credit_decision_profile_version SET status = ?, reason = ?, decided_by = ?, decided_on = ? WHERE id = ?", DRAFT,
                "Returned: " + reason.trim(), username(), Timestamp.valueOf(now), version.id);
        audit(profileId, versionNo, "RETURNED", "reason", null, reason.trim(), now);
        return profile(profileId);
    }

    /**
     * Makes a version the live one. A second person must do it: not whoever created, edited or submitted the version
     * (unless self approval is switched on). Records every field that changes against the version it replaces.
     */
    @Transactional
    public ProfileData approve(final Long profileId, final int versionNo) {
        final VersionRow version = version(profileId, versionNo);
        requireStatus(version, PENDING, "Only a version awaiting approval can be approved");
        if (!selfApprovalAllowed()) {
            final String user = username();
            final List<String> editors = jdbcTemplate.queryForList(
                    "SELECT DISTINCT acted_by FROM m_credit_decision_audit WHERE profile_id = ? AND version_no = ? "
                            + "AND action IN ('PROFILE_CREATED', 'DRAFT_CREATED', 'DRAFT_SAVED', 'SUBMITTED')",
                    String.class, profileId, versionNo);
            if (version.preparedBy.equalsIgnoreCase(user) || editors.stream().anyMatch(user::equalsIgnoreCase)) {
                throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.approver.must.differ",
                        "A second person must approve this version. " + user + " prepared, edited or submitted it.");
            }
        }
        assertProductsFree(productIdsOf(version.id), profileId);
        final Map<String, String> after = snapshot(version.id);
        final List<Long> previous = jdbcTemplate.queryForList("SELECT id FROM m_credit_decision_profile_version WHERE profile_id = ? AND status = ?",
                Long.class, profileId, ACTIVE);
        final Map<String, String> before = previous.isEmpty() ? new LinkedHashMap<>() : snapshot(previous.get(0));
        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        jdbcTemplate.update("UPDATE m_credit_decision_profile_version SET status = ? WHERE profile_id = ? AND status = ?", RETIRED,
                profileId, ACTIVE);
        jdbcTemplate.update("UPDATE m_credit_decision_profile_version SET status = ?, decided_by = ?, decided_on = ? WHERE id = ?", ACTIVE,
                username(), Timestamp.valueOf(now), version.id);
        syncClaims(profileId);
        audit(profileId, versionNo, "ACTIVATED", "status", previous.isEmpty() ? null : "active version replaced", ACTIVE, now);
        auditChanges(profileId, versionNo, "ACTIVATED", before, after, now);
        dropCachedActive();
        return profile(profileId);
    }

    /**
     * Off and Shadow take effect at once. Live is only asked for here and takes effect when a second authorised person
     * approves it with {@link #approveMode}.
     */
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
        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        if ("LIVE".equals(mode)) {
            context.authenticatedUser().validateHasPermissionTo("APPROVE_CREDITDECISION");
            if ("LIVE".equals(profile.getMode())) {
                throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.mode.unchanged", "This profile is already live.");
            }
            jdbcTemplate.update("UPDATE m_credit_decision_profile SET pending_mode = ?, pending_by = ?, pending_on = ? WHERE id = ?", mode,
                    username(), Timestamp.valueOf(now), profileId);
            audit(profileId, null, "MODE_REQUESTED", "mode", profile.getMode(), mode, now);
            return profile(profileId);
        }
        jdbcTemplate.update(
                "UPDATE m_credit_decision_profile SET mode = ?, mode_changed_by = ?, mode_changed_on = ?, pending_mode = NULL, "
                        + "pending_by = NULL, pending_on = NULL WHERE id = ?",
                mode, username(), Timestamp.valueOf(now), profileId);
        if (!mode.equals(profile.getMode())) {
            audit(profileId, null, "MODE_CHANGED", "mode", profile.getMode(), mode, now);
        }
        dropCachedActive();
        return profile(profileId);
    }

    /** A second authorised person approves the switch to Live that someone else asked for. */
    @Transactional
    public ProfileData approveMode(final Long profileId) {
        final ProfileData profile = profile(profileId);
        if (profile.getPendingMode() == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.mode.nothing.pending",
                    "No one has asked to switch this profile to live.");
        }
        if (profile.getPendingBy().equalsIgnoreCase(username()) && !selfApprovalAllowed()) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.approver.must.differ",
                    "A second person must approve switching this profile to live. It was asked for by " + profile.getPendingBy() + ".");
        }
        if (profile.getVersions().stream().noneMatch(v -> ACTIVE.equals(v.getStatus()))) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.mode.needs.active.version",
                    "This profile has no approved version to go live with.");
        }
        final LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();
        jdbcTemplate.update(
                "UPDATE m_credit_decision_profile SET mode = ?, mode_changed_by = ?, mode_changed_on = ?, pending_mode = NULL, "
                        + "pending_by = NULL, pending_on = NULL WHERE id = ?",
                profile.getPendingMode(), username(), Timestamp.valueOf(now), profileId);
        audit(profileId, null, "MODE_APPROVED", "mode", profile.getMode(), profile.getPendingMode(), now);
        dropCachedActive();
        return profile(profileId);
    }

    /** Drops a request to switch to Live: by anyone authorised, including whoever asked. */
    @Transactional
    public ProfileData cancelMode(final Long profileId) {
        final ProfileData profile = profile(profileId);
        if (profile.getPendingMode() == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.mode.nothing.pending",
                    "No one has asked to switch this profile to live.");
        }
        jdbcTemplate.update("UPDATE m_credit_decision_profile SET pending_mode = NULL, pending_by = NULL, pending_on = NULL WHERE id = ?",
                profileId);
        audit(profileId, null, "MODE_CANCELLED", "mode", profile.getPendingMode(), profile.getMode(), DateUtils.getLocalDateTimeOfSystem());
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
        final LocalDateTime changedOn = DateUtils.getLocalDateTimeOfSystem();
        final String endpoint = url.isEmpty() ? null : url;
        auditConnection("provider", current.getProvider(), provider, changedOn);
        auditConnection("enabled", String.valueOf(current.isEnabled()), String.valueOf(request.isEnabled()), changedOn);
        auditConnection("endpointUrl", current.getEndpointUrl(), endpoint, changedOn);
        auditConnection("timeoutSeconds", String.valueOf(current.getTimeoutSeconds()), String.valueOf(request.getTimeoutSeconds()), changedOn);
        auditConnection("retries", String.valueOf(current.getRetries()), String.valueOf(request.getRetries()), changedOn);
        auditConnection("onNoReply", current.getOnNoReply(), policy, changedOn);
        jdbcTemplate.update(
                "UPDATE m_credit_decision_connection SET provider = ?, enabled = ?, endpoint_url = ?, timeout_seconds = ?, "
                        + "retries = ?, on_no_reply = ?, updated_by = ?, updated_on = ? WHERE id = 1",
                provider, request.isEnabled(), url.isEmpty() ? null : url, request.getTimeoutSeconds(), request.getRetries(), policy,
                username(), Timestamp.valueOf(changedOn));
        dropCachedActive();
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

    private static final class CachedActive {

        final ActiveProfileData data;
        final long loadedAt;

        CachedActive(final ActiveProfileData data, final long loadedAt) {
            this.data = data;
            this.loadedAt = loadedAt;
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
     * Products that belong to a profile, read from the claim table: one profile per product, enforced by its key.
     */
    private Map<Long, Claim> claims(final Long exceptProfileId) {
        final Map<Long, Claim> out = new LinkedHashMap<>();
        final StringBuilder sql = new StringBuilder("SELECT c.loan_product_id, p.id AS profile_id, p.name FROM m_credit_decision_product_claim c "
                + "JOIN m_credit_decision_profile p ON p.id = c.profile_id");
        final List<Object> args = new ArrayList<>();
        if (exceptProfileId != null) {
            sql.append(" WHERE p.id <> ?");
            args.add(exceptProfileId);
        }
        sql.append(" ORDER BY c.loan_product_id");
        jdbcTemplate.query(sql.toString(), rs -> {
            out.put(rs.getLong("loan_product_id"), new Claim(rs.getLong("profile_id"), rs.getString("name")));
        }, args.toArray());
        return out;
    }

    /**
     * Makes the claim table say which products the profile holds now: those of its live, pending and draft versions.
     * The key on the product stops a second profile taking one, even when two people save at the same moment.
     */
    private void syncClaims(final long profileId) {
        final List<Long> wanted = jdbcTemplate.queryForList(
                "SELECT DISTINCT vp.loan_product_id FROM m_credit_decision_version_product vp "
                        + "JOIN m_credit_decision_profile_version v ON v.id = vp.version_id "
                        + "WHERE v.profile_id = ? AND v.status IN ('ACTIVE', 'PENDING', 'DRAFT') ORDER BY vp.loan_product_id",
                Long.class, profileId);
        jdbcTemplate.update("DELETE FROM m_credit_decision_product_claim WHERE profile_id = ?", profileId);
        for (final Long productId : wanted) {
            try {
                jdbcTemplate.update("INSERT INTO m_credit_decision_product_claim (loan_product_id, profile_id) VALUES (?, ?)", productId,
                        profileId);
            } catch (final DataIntegrityViolationException e) {
                throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.product.taken",
                        "Loan product " + productId + " is already used by another profile.");
            }
        }
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
                                    iso(rs.getTimestamp("decided_on")), productIdsOf(versionId), paramsOf(versionId), rulesOf(versionId),
                                    rs.getString("submitted_by"), iso(rs.getTimestamp("submitted_on"))));
                }, args);
        return jdbcTemplate.query(
                "SELECT p.id, p.name, p.mode, p.created_by, p.created_on, p.mode_changed_by, p.mode_changed_on, p.pending_mode, "
                        + "p.pending_by, p.pending_on FROM m_credit_decision_profile p" + where + " ORDER BY p.name",
                (rs, i) -> new ProfileData(rs.getLong("id"), rs.getString("name"), rs.getString("mode"), rs.getString("created_by"),
                        iso(rs.getTimestamp("created_on")), versionsByProfile.getOrDefault(rs.getLong("id"), List.of()),
                        rs.getString("mode_changed_by"), iso(rs.getTimestamp("mode_changed_on")), rs.getString("pending_mode"),
                        rs.getString("pending_by"), iso(rs.getTimestamp("pending_on"))),
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

    private List<PolicyValueData> rulesOf(final long versionId) {
        final List<PolicyValueData> rules = new ArrayList<>();
        jdbcTemplate.query("SELECT scope, scope_key, rule_key, rule_value FROM m_credit_decision_version_rule WHERE version_id = ?", rs -> {
            rules.add(new PolicyValueData(rs.getString("scope"), rs.getString("scope_key"), rs.getString("rule_key"),
                    rs.getBigDecimal("rule_value")));
        }, versionId);
        return CreditDecisionPolicy.normalise(rules);
    }

    private Map<Long, String> currenciesOf(final List<Long> productIds) {
        final Map<Long, String> out = new LinkedHashMap<>();
        for (final Long id : productIds) {
            jdbcTemplate.query("SELECT currency_code FROM m_product_loan WHERE id = ?", rs -> {
                out.put(id, rs.getString("currency_code"));
            }, id);
        }
        return out;
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

    private static VersionData latestVersion(final ProfileData profile) {
        return profile.getVersions().stream().filter(v -> ACTIVE.equals(v.getStatus())).findFirst().orElse(profile.getVersions().get(0));
    }

    private void insertVersion(final long profileId, final int versionNo, final String status, final String reason, final String user,
            final LocalDateTime now, final List<Long> productIds, final List<ParamData> params, final List<PolicyValueData> rules) {
        final long versionId = insert("INSERT INTO m_credit_decision_profile_version (profile_id, version_no, status, reason, prepared_by, "
                + "prepared_on) VALUES (?, ?, ?, ?, ?, ?)", profileId, versionNo, status, reason, user, Timestamp.valueOf(now));
        replaceParams(versionId, CreditDecisionRules.complete(params));
        replaceProducts(versionId, productIds);
        replaceRules(versionId, rules);
    }

    private void replaceRules(final long versionId, final List<PolicyValueData> rules) {
        jdbcTemplate.update("DELETE FROM m_credit_decision_version_rule WHERE version_id = ?", versionId);
        for (final PolicyValueData r : rules) {
            jdbcTemplate.update("INSERT INTO m_credit_decision_version_rule (version_id, scope, scope_key, rule_key, rule_value) "
                    + "VALUES (?, ?, ?, ?, ?)", versionId, r.getScope(), r.getScopeKey(), r.getKey(), r.getValue());
        }
    }

    /** Everything a version holds, one entry per field, so two versions can be compared field by field. */
    private Map<String, String> snapshot(final long versionId) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final ParamData p : paramsOf(versionId)) {
            out.put("param." + p.getKey() + ".enabled", p.isEnabled() ? "on" : "off");
            out.put("param." + p.getKey() + ".fieldName", p.getFieldName());
            out.put("param." + p.getKey() + ".missingPolicy", p.getMissingPolicy());
        }
        for (final PolicyValueData r : rulesOf(versionId)) {
            out.put("rule." + r.getScope() + "." + r.getScopeKey() + "." + r.getKey(), r.getValue().toPlainString());
        }
        out.put("products", productIdsOf(versionId).stream().map(String::valueOf).collect(Collectors.joining(", ")));
        return out;
    }

    private void auditChanges(final long profileId, final Integer versionNo, final String action, final Map<String, String> before,
            final Map<String, String> after, final LocalDateTime now) {
        final Set<String> fields = new LinkedHashSet<>(before.keySet());
        fields.addAll(after.keySet());
        for (final String field : fields) {
            final String was = before.get(field), is = after.get(field);
            if (was == null ? is != null : !was.equals(is)) {
                audit(profileId, versionNo, action, field, was, is, now);
            }
        }
    }

    private void audit(final Long profileId, final Integer versionNo, final String action, final String field, final String oldValue,
            final String newValue, final LocalDateTime now) {
        jdbcTemplate.update("INSERT INTO m_credit_decision_audit (profile_id, version_no, action, field_name, old_value, new_value, "
                + "acted_by, acted_on) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", profileId, versionNo, action, field, abbreviate(oldValue),
                abbreviate(newValue), username(), Timestamp.valueOf(now));
    }

    /** The connection is shared by every profile, so its changes belong to none. */
    private void auditConnection(final String field, final String oldValue, final String newValue, final LocalDateTime now) {
        if (oldValue == null ? newValue != null : !oldValue.equals(newValue)) {
            audit(null, null, "CONNECTION_CHANGED", field, oldValue, newValue, now);
        }
    }

    private static String abbreviate(final String value) {
        return value == null || value.length() <= AUDIT_VALUE_MAX ? value : value.substring(0, AUDIT_VALUE_MAX - 3) + "...";
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
