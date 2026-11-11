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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.FileSystemResourceAccessor;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ActiveProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.AuditData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.EffectiveRuleData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.PolicyValueData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ConnectionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CreateProfileRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogEntryData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogFilter;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogPageData;
import org.apache.fineract.portfolio.creditdecision.exception.CreditDecisionLogNotFoundException;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ParamData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProductData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.SaveDraftRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.VersionData;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Runs the real migration and the real SQL against a throw-away MySQL schema. Switched off unless
 * CREDIT_DECISION_TEST_JDBC_URL is set, for example
 * {@code jdbc:mysql://127.0.0.1:3307/cd_test?allowPublicKeyRetrieval=true&useSSL=false}. The schema is dropped and rebuilt
 * for every test, so never point it at a real database. CREDIT_DECISION_TEST_JDBC_USER and _PASSWORD default to root and
 * an empty password.
 */
@EnabledIfEnvironmentVariable(named = "CREDIT_DECISION_TEST_JDBC_URL", matches = ".+")
class CreditDecisionServiceDbTest {

    private Connection connection;
    private JdbcTemplate jdbc;
    private CreditDecisionService service;
    private CreditDecisionLogReader logs;
    private PlatformSecurityContext context;
    private AppUser current;

    @BeforeEach
    void freshSchema() throws Exception {
        final String url = System.getenv("CREDIT_DECISION_TEST_JDBC_URL");
        final String user = System.getenv().getOrDefault("CREDIT_DECISION_TEST_JDBC_USER", "root");
        final String password = System.getenv().getOrDefault("CREDIT_DECISION_TEST_JDBC_PASSWORD", "");
        connection = DriverManager.getConnection(url, user, password);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));

        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (final String table : jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class)) {
            jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");

        // just enough of the Fineract schema for the migration and the queries to run
        jdbc.execute("CREATE TABLE m_product_loan (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), currency_code VARCHAR(3))");
        jdbc.execute("CREATE TABLE m_permission (id BIGINT AUTO_INCREMENT PRIMARY KEY, `grouping` VARCHAR(45), code VARCHAR(100) UNIQUE, "
                + "entity_name VARCHAR(100), action_name VARCHAR(100), can_maker_checker TINYINT(1))");
        jdbc.execute("CREATE TABLE m_loan (id BIGINT AUTO_INCREMENT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE job (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), display_name VARCHAR(100), "
                + "cron_expression VARCHAR(100), create_time DATETIME, task_priority SMALLINT, group_name VARCHAR(100), job_key VARCHAR(500), "
                + "is_active TINYINT(1), currently_running TINYINT(1), updates_allowed TINYINT(1), scheduler_group SMALLINT, "
                + "is_misfired TINYINT(1), node_id INT, is_mismatched_job TINYINT(1))");
        jdbc.execute("CREATE TABLE m_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100))");
        jdbc.execute("CREATE TABLE m_role_permission (role_id BIGINT, permission_id BIGINT)");
        jdbc.execute("CREATE TABLE c_configuration (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), value BIGINT NULL, "
                + "date_value DATE NULL, string_value VARCHAR(100) NULL, enabled TINYINT(1), is_trap_door TINYINT(1), "
                + "description VARCHAR(500))");
        jdbc.update("INSERT INTO m_role (name) VALUES ('Super user')");
        jdbc.update("INSERT INTO m_product_loan (name, currency_code) VALUES ('Business loan - individual', 'KES'), "
                + "('Business loan - group', 'KES'), ('Digital micro-loan', 'RWF')");

        final Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        new Liquibase("CGLT-846_credit_decision_profiles.xml",
                new FileSystemResourceAccessor(new File("src/main/resources/db/changelog/tenant/parts")), database).update("");
        // the decision log also keeps the risk tier, maximum exposure and model version, which the loan migration adds
        new Liquibase("CGLT-846_loan_credit_decision.xml",
                new FileSystemResourceAccessor(new File("src/main/resources/db/changelog/tenant/parts")), database).update("");

        context = mock(PlatformSecurityContext.class);
        service = new CreditDecisionService(jdbc, context);
        logs = new CreditDecisionLogReader(jdbc);
        actAs("credit.analyst");
    }

    @AfterEach
    void closeConnection() throws Exception {
        // an open connection would keep metadata locks and block the next test's DROP TABLE
        if (connection != null) {
            connection.close();
        }
    }

    private AppUser actAs(final String username) {
        current = mock(AppUser.class);
        when(current.getUsername()).thenReturn(username);
        when(context.authenticatedUser()).thenReturn(current);
        return current;
    }

    private long productId(final String name, final String currency) {
        return jdbc.queryForObject("SELECT id FROM m_product_loan WHERE name = ? AND currency_code = ?", Long.class, name, currency);
    }

    private CreateProfileRequest newProfile(final String name, final Long... productIds) {
        final CreateProfileRequest request = new CreateProfileRequest();
        request.setName(name);
        request.setProductIds(List.of(productIds));
        return request;
    }

    private static VersionData version(final ProfileData profile, final int no) {
        return profile.getVersions().stream().filter(v -> v.getVersionNo() == no).findFirst().orElseThrow();
    }

    private ProfileData liveProfile(final String name, final Long... products) {
        final ProfileData created = service.createProfile(newProfile(name, products));
        service.submit(created.getId(), 1, "Initial rollout");
        actAs("credit.head");
        final ProfileData approved = service.approve(created.getId(), 1);
        actAs("credit.analyst");
        return approved;
    }

    @Test
    void migrationCreatesTablesPermissionsAndTheConnectionRow() {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM m_credit_decision_connection", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM m_permission WHERE entity_name LIKE 'CREDITDECISION%' AND entity_name <> 'CREDITDECISIONLOAN'", Integer.class));
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM m_role_permission", Integer.class),
                "the 5 profile permissions and the 3 per-loan ones, since the loan migration is loaded too");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM c_configuration WHERE name = 'credit-decision-allow-self-approval'",
                Integer.class));
        assertFalse(service.connection().isEnabled());
    }

    @Test
    void aNewProfileStartsAsAnOffDraftWithTheStarterSet() {
        final ProfileData profile = service.createProfile(newProfile("Business loans", productId("Business loan - individual", "KES")));

        assertEquals("OFF", profile.getMode());
        assertEquals(1, profile.getVersions().size());
        final VersionData v1 = version(profile, 1);
        assertEquals("DRAFT", v1.getStatus());
        assertEquals("credit.analyst", v1.getPreparedBy());
        assertEquals(List.of("amount", "term", "currency", "bscore", "blist"),
                v1.getParams().stream().filter(ParamData::isEnabled).map(ParamData::getKey).toList());
        assertEquals(1, v1.getProductIds().size());
    }

    @Test
    void aProductBelongsToOneProfileOnly() {
        final long kes = productId("Business loan - individual", "KES");
        service.createProfile(newProfile("Business loans", kes));

        assertThrows(PlatformApiDataValidationException.class, () -> service.createProfile(newProfile("Another", kes)));
        assertThrows(PlatformApiDataValidationException.class, () -> service.createProfile(newProfile("business LOANS", 99999L)));

        final ProductData claimed = service.products().stream().filter(p -> p.getId() == kes).findFirst().orElseThrow();
        assertEquals("Business loans", claimed.getClaimedByProfileName());
        final ProductData free = service.products().stream().filter(p -> p.getCurrencyCode().equals("RWF")).findFirst().orElseThrow();
        assertNull(free.getClaimedByProfileId());
    }

    @Test
    void aDraftCanBeEditedThenSubmittedThenApprovedByASecondPerson() {
        final long a = productId("Business loan - individual", "KES"), b = productId("Business loan - group", "KES");
        final ProfileData created = service.createProfile(newProfile("Business loans", a));

        final SaveDraftRequest edit = new SaveDraftRequest();
        edit.setParams(List.of(new ParamData("amount", true, "loanAmount", "null"), new ParamData("wo", true, "writtenOff24m", "null")));
        edit.setProductIds(List.of(a, b));
        final VersionData saved = version(service.saveDraft(created.getId(), 1, edit), 1);
        assertEquals(List.of("amount", "wo"), saved.getParams().stream().filter(ParamData::isEnabled).map(ParamData::getKey).toList());
        assertEquals("loanAmount", saved.getParams().stream().filter(p -> p.getKey().equals("amount")).findFirst().orElseThrow().getFieldName());
        assertEquals(2, saved.getProductIds().size());

        assertThrows(PlatformApiDataValidationException.class, () -> service.submit(created.getId(), 1, " "));
        service.submit(created.getId(), 1, "Initial rollout");
        assertEquals("PENDING", version(service.profile(created.getId()), 1).getStatus());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.saveDraft(created.getId(), 1, edit));

        // the person who submitted cannot approve it
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.approve(created.getId(), 1));

        actAs("credit.head");
        final ProfileData approved = service.approve(created.getId(), 1);
        assertEquals("ACTIVE", version(approved, 1).getStatus());
        assertEquals("credit.head", version(approved, 1).getDecidedBy());
    }

    @Test
    void selfApprovalCanBeSwitchedOnForATestTenant() {
        final ProfileData created = service.createProfile(newProfile("Business loans", productId("Business loan - individual", "KES")));
        service.submit(created.getId(), 1, "Because");
        jdbc.update("UPDATE c_configuration SET enabled = 1 WHERE name = 'credit-decision-allow-self-approval'");

        assertEquals("ACTIVE", version(service.approve(created.getId(), 1), 1).getStatus());
    }

    @Test
    void rejectingReturnsTheVersionToDraftWithTheReason() {
        final ProfileData created = service.createProfile(newProfile("Business loans", productId("Business loan - individual", "KES")));
        service.submit(created.getId(), 1, "Because");
        actAs("credit.head");

        assertThrows(PlatformApiDataValidationException.class, () -> service.reject(created.getId(), 1, ""));
        final VersionData returned = version(service.reject(created.getId(), 1, "Add bureau score"), 1);

        assertEquals("DRAFT", returned.getStatus());
        assertEquals("Returned: Add bureau score", returned.getReason());
    }

    @Test
    void approvingANewVersionRetiresTheOldOneAndOnlyOneChangeCanBeOpen() {
        final ProfileData live = liveProfile("Business loans", productId("Business loan - individual", "KES"));

        final ProfileData withDraft = service.newDraft(live.getId(), null);
        assertEquals(2, withDraft.getVersions().get(0).getVersionNo());
        assertEquals("DRAFT", withDraft.getVersions().get(0).getStatus());
        assertEquals(version(live, 1).getProductIds(), withDraft.getVersions().get(0).getProductIds());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.newDraft(live.getId(), null));

        service.submit(live.getId(), 2, "Add write-off history");
        actAs("credit.head");
        final ProfileData after = service.approve(live.getId(), 2);

        assertEquals("ACTIVE", version(after, 2).getStatus());
        assertEquals("RETIRED", version(after, 1).getStatus());

        actAs("credit.analyst");
        final ProfileData restored = service.newDraft(live.getId(), 1);
        assertEquals("Restored from v1", version(restored, 3).getReason());
    }

    @Test
    void theIntegrationServiceGetsTheLiveVersionOnlyWhileTheProfileIsNotOff() {
        final long a = productId("Business loan - individual", "KES");
        final ProfileData live = liveProfile("Business loans", a);

        assertNull(service.activeForProduct(a), "a profile is Off until someone switches it on");
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.setMode(createdOffProfile().getId(), "SHADOW"));

        goLive(live.getId());
        final ActiveProfileData active = service.activeForProduct(a);

        assertNotNull(active);
        assertEquals("LIVE", active.getMode());
        assertEquals(1, active.getVersionNo());
        assertFalse(active.isSendingEnabled());
        assertTrue(active.getParams().stream().anyMatch(p -> p.getKey().equals("amount") && p.isEnabled()));
        assertNull(service.activeForProduct(productId("Digital micro-loan", "RWF")));

        service.setMode(live.getId(), "OFF");
        assertNull(service.activeForProduct(a));
    }

    /** Live is asked for by one authorised person and approved by another. */
    private ProfileData goLive(final long profileId) {
        final String me = current.getUsername();
        service.setMode(profileId, "live");
        actAs("credit.head");
        final ProfileData live = service.approveMode(profileId);
        actAs(me);
        return live;
    }

    private ProfileData createdOffProfile() {
        return service.createProfile(newProfile("Digital micro-loans", productId("Digital micro-loan", "RWF")));
    }

    @Test
    void connectionSettingsAreValidatedAndSaved() {
        final ConnectionData bad = new ConnectionData("Engine", true, "", 8, 3, "RETRY", null, null);
        assertThrows(PlatformApiDataValidationException.class, () -> service.updateConnection(bad));
        assertThrows(PlatformApiDataValidationException.class,
                () -> service.updateConnection(new ConnectionData("Engine", false, "ftp://x", 8, 3, "RETRY", null, null)));
        assertThrows(PlatformApiDataValidationException.class,
                () -> service.updateConnection(new ConnectionData("Engine", false, "", 0, 3, "SHRUG", null, null)));

        final ConnectionData saved = service.updateConnection(
                new ConnectionData(" Engine A ", true, "https://engine.example/decisions", 12, 2, "alert", null, null));

        assertEquals("Engine A", saved.getProvider());
        assertTrue(saved.isEnabled());
        assertEquals("ALERT", saved.getOnNoReply());
        assertEquals("credit.analyst", saved.getUpdatedBy());
    }

    @Test
    void theLogKeepsTheRequestIdentifierRiskTierAndModelVersionAndFiltersByThem() {
        for (final String[] row : new String[][] { { "10301", "req_aaa", "LOW", "1.0" }, { "10302", "req_bbb", "HIGH", "1.0" },
                { "10303", "req_ccc", "LOW", "1.1" } }) {
            final LogEntryData entry = new LogEntryData();
            entry.setApplicationRef(row[0]);
            entry.setRequestId(row[1]);
            entry.setRiskTier(row[2]);
            entry.setModelVersion(row[3]);
            entry.setStatus("DECIDED");
            entry.setDecision("AUTO_APPROVE");
            entry.setMaxExposure(new BigDecimal("500000"));
            service.recordLog(entry);
        }

        final LogEntryData stored = logs.page(filter(null, "10301")).getContent().get(0);
        assertEquals("req_aaa", stored.getRequestId());
        assertEquals("LOW", stored.getRiskTier());
        assertEquals("1.0", stored.getModelVersion());
        assertEquals(0, new BigDecimal("500000").compareTo(stored.getMaxExposure()));

        final LogFilter byTier = new LogFilter();
        byTier.setRiskTier("LOW");
        assertEquals(2, logs.page(byTier).getTotal());
        final LogFilter byModel = new LogFilter();
        byModel.setModelVersion("1.1");
        assertEquals(1, logs.page(byModel).getTotal());
        byModel.setRiskTier("HIGH");
        assertEquals(0, logs.page(byModel).getTotal(), "the filters narrow each other");
        assertEquals(1, logs.page(filter(null, "req_bbb")).getTotal(), "the request identifier can be searched for");

        final LogPageData page = logs.page(new LogFilter());
        assertEquals(java.util.List.of("HIGH", "LOW"), page.getRiskTiers());
        assertEquals(java.util.List.of("1.0", "1.1"), page.getModelVersions());
    }

    @Test
    void theLogRecordsRequestsAndRepliesAndCanBeFiltered() {
        service.updateConnection(new ConnectionData("Engine A", false, "", 8, 3, "RETRY", null, null));
        final LogEntryData go = new LogEntryData();
        go.setApplicationRef("10231");
        go.setCustomerRef("000884635");
        go.setProfileName("Business loans");
        go.setVersionNo(7);
        go.setMode("LIVE");
        go.setStatus("decided");
        go.setDecision("AUTO_APPROVE");
        go.setScore(new BigDecimal("82"));
        go.setRequestJson("{\"applicationId\":10231}");
        go.setReplyJson("{\"recommendation\":\"AUTO_APPROVE\"}");
        service.recordLog(go);
        final LogEntryData held = new LogEntryData();
        held.setApplicationRef("10232");
        held.setStatus("HELD");
        service.recordLog(held);

        assertThrows(PlatformApiDataValidationException.class, () -> service.recordLog(new LogEntryData()));
        assertEquals(2, logs.page(new LogFilter()).getTotal());
        assertEquals(1, logs.page(filter("AUTO_APPROVE", null)).getTotal());
        assertEquals(1, logs.page(filter("NONE", null)).getTotal());
        assertEquals(1, logs.page(filter(null, "0232")).getTotal());
        final LogEntryData row = logs.page(filter("AUTO_APPROVE", null)).getContent().get(0);
        assertNull(row.getRequestJson(), "the list leaves the request and reply out, they are read when an entry is opened");
        final LogEntryData full = logs.entry(row.getId());
        assertEquals("{\"applicationId\":10231}", full.getRequestJson());
        assertEquals("{\"recommendation\":\"AUTO_APPROVE\"}", full.getReplyJson());
        assertThrows(CreditDecisionLogNotFoundException.class, () -> logs.entry(999_999L));
        assertEquals("Engine A", row.getProvider(), "the provider defaults to the one configured when the entry was recorded");
        assertEquals(0, new BigDecimal("82").compareTo(row.getScore()));
        assertEquals("DECIDED", row.getStatus());
    }

    private static LogFilter filter(final String decision, final String search) {
        final LogFilter f = new LogFilter();
        f.setDecision(decision);
        f.setSearch(search);
        return f;
    }

    private void log(final String ref, final String profile, final Long profileId, final String mode, final String status,
            final String decision, final String score, final String reasons, final String when) {
        jdbc.update("INSERT INTO m_credit_decision_log (application_ref, customer_ref, profile_id, profile_name, mode, status, decision, "
                + "score, reason_codes, created_on) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ref, "C" + ref, profileId, profile, mode,
                status, decision, score == null ? null : new BigDecimal(score), reasons, java.sql.Timestamp.valueOf(when));
    }

    @Test
    void theLogIsPagedAndFilteredOnTheServer() {
        // 60 rows over three days: every third is held, the rest cycle through the outcomes
        int go = 0;
        int review = 0;
        int noGo = 0;
        int held = 0;
        int arrears = 0;
        for (int i = 1; i <= 60; i++) {
            final boolean isHeld = i % 3 == 0;
            final String decision = isHeld ? null : i % 2 == 0 ? "AUTO_APPROVE" : i % 4 == 1 ? "IC_REVIEW_REQUIRED" : "INELIGIBLE";
            final boolean late = !isHeld && i % 5 == 0;
            if (isHeld) {
                held++;
            } else if ("AUTO_APPROVE".equals(decision)) {
                go++;
            } else if ("IC_REVIEW_REQUIRED".equals(decision)) {
                review++;
            } else {
                noGo++;
            }
            if (late) {
                arrears++;
            }
            final String day = i <= 20 ? "2026-11-07" : i <= 40 ? "2026-11-08" : "2026-11-09";
            log(String.format("A%03d", i), i % 2 == 0 ? "Business" : "Agri", i % 2 == 0 ? 1L : 2L, i % 2 == 0 ? "LIVE" : "SHADOW",
                    isHeld ? "HELD" : "DECIDED", decision, isHeld ? null : String.valueOf(300 + i),
                    isHeld ? null : late ? "BUREAU_LISTED,CURRENT_ARREARS" : "ALL_CHECKS_PASSED",
                    day + String.format(" %02d:%02d:00", 8 + i % 10, i % 60));
        }

        final LogFilter all = new LogFilter();
        all.setPageSize(25);
        LogPageData p1 = logs.page(all);
        assertEquals(60, p1.getTotal());
        assertEquals(25, p1.getContent().size());
        assertEquals(3, p1.getTotalPages());
        assertEquals(1, p1.getPage());
        assertEquals(60, p1.getCounts().getTotal());
        assertEquals(held, p1.getCounts().getHeld());
        assertEquals(go, p1.getCounts().getAutoApprove());
        assertEquals(review, p1.getCounts().getIcReview());
        assertEquals(noGo, p1.getCounts().getIneligible());

        all.setPage(3);
        final LogPageData last = logs.page(all);
        assertEquals(10, last.getContent().size(), "the last page holds what is left");
        all.setPage(99);
        assertEquals(3, logs.page(all).getPage(), "a page past the end is brought back to the last one");
        all.setPage(1);
        final java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int page = 1; page <= 3; page++) {
            all.setPage(page);
            logs.page(all).getContent().forEach(r -> assertTrue(seen.add(r.getId()), "no row appears on two pages"));
        }
        assertEquals(60, seen.size());

        all.setPage(1);
        all.setPageSize(1000);
        assertEquals(CreditDecisionLogReader.MAX_PAGE_SIZE, logs.page(all).getPageSize());
        all.setPageSize(0);
        assertEquals(CreditDecisionLogReader.DEFAULT_PAGE_SIZE, logs.page(all).getPageSize());

        // newest first by default, oldest first on request
        final LogFilter newest = new LogFilter();
        final LogFilter oldest = new LogFilter();
        oldest.setSort("date_asc");
        assertTrue(logs.page(newest).getContent().get(0).getCreatedOn().compareTo(logs.page(oldest).getContent().get(0).getCreatedOn()) > 0);

        // outcome filters, with counts that ignore the outcome so every tab can show its own
        final LogFilter goOnly = new LogFilter();
        goOnly.setDecision("go");
        final LogPageData goPage = logs.page(goOnly);
        assertEquals(goPage.getCounts().getAutoApprove(), goPage.getTotal());
        assertEquals(60, goPage.getCounts().getTotal());
        assertEquals(go, goPage.getTotal());
        final LogFilter heldOnly = new LogFilter();
        heldOnly.setStatus("held");
        assertEquals(held, logs.page(heldOnly).getTotal());
        final LogFilter none = new LogFilter();
        none.setDecision("NONE");
        assertEquals(held, logs.page(none).getTotal());

        // profile, mode, reason, search, dates
        final LogFilter profile = new LogFilter();
        profile.setProfileId(1L);
        assertEquals(30, logs.page(profile).getTotal());
        final LogFilter shadow = new LogFilter();
        shadow.setMode("shadow");
        assertEquals(30, logs.page(shadow).getTotal());
        final LogFilter reason = new LogFilter();
        reason.setReason("bureau_listed");
        assertEquals(arrears, logs.page(reason).getTotal());
        reason.setReason("CURRENT_ARR");
        assertEquals(arrears, logs.page(reason).getTotal());
        final LogFilter ref = new LogFilter();
        ref.setSearch("A05");
        assertEquals(10, logs.page(ref).getTotal());
        final LogFilter wildcard = new LogFilter();
        wildcard.setSearch("%");
        assertEquals(0, logs.page(wildcard).getTotal(), "a typed percent sign is not a wildcard");

        final LogFilter oneDay = new LogFilter();
        oneDay.setFrom("2026-11-08");
        oneDay.setTo("2026-11-08");
        assertEquals(20, logs.page(oneDay).getTotal());
        final LogFilter fromOnly = new LogFilter();
        fromOnly.setFrom("2026-11-08");
        assertEquals(40, logs.page(fromOnly).getTotal());
        final LogFilter toOnly = new LogFilter();
        toOnly.setTo("2026-11-07");
        assertEquals(20, logs.page(toOnly).getTotal());

        // filters combine, and the counts follow every filter but the outcome
        final LogFilter combined = new LogFilter();
        combined.setProfileId(1L);
        combined.setFrom("2026-11-08");
        combined.setDecision("IC_REVIEW_REQUIRED");
        final LogPageData c = logs.page(combined);
        assertEquals(c.getCounts().getIcReview(), c.getTotal());
        assertTrue(c.getCounts().getTotal() <= 20);
        assertEquals(c.getCounts().getTotal(), c.getCounts().getAutoApprove() + c.getCounts().getIcReview() + c.getCounts().getIneligible()
                + c.getCounts().getHeld(), "outcomes add up to the total");
    }

    @Test
    void aBadLogFilterIsRefusedAndNotRunAsSql() {
        for (final String[] bad : new String[][] { { "decision", "maybe" }, { "status", "x" }, { "mode", "x" }, { "from", "08/11/2026" },
                { "to", "tomorrow" }, { "sort", "id; DROP TABLE m_loan" } }) {
            final LogFilter f = new LogFilter();
            switch (bad[0]) {
                case "decision":
                    f.setDecision(bad[1]);
                break;
                case "status":
                    f.setStatus(bad[1]);
                break;
                case "mode":
                    f.setMode(bad[1]);
                break;
                case "from":
                    f.setFrom(bad[1]);
                break;
                case "to":
                    f.setTo(bad[1]);
                break;
                default:
                    f.setSort(bad[1]);
            }
            assertThrows(PlatformApiDataValidationException.class, () -> logs.page(f), bad[0]);
        }
        final LogFilter backwards = new LogFilter();
        backwards.setFrom("2026-11-09");
        backwards.setTo("2026-11-01");
        assertThrows(PlatformApiDataValidationException.class, () -> logs.page(backwards));
    }

    // ---- the eligibility policy, second-person approval, claims, audit, Live approval and the cache ----

    private static PolicyValueData rule(final String scope, final String scopeKey, final String key, final String value) {
        return new PolicyValueData(scope, scopeKey, key, new BigDecimal(value));
    }

    private static List<PolicyValueData> starterWith(final PolicyValueData... changes) {
        final List<PolicyValueData> rules = new ArrayList<>(org.apache.fineract.portfolio.creditdecision.data.CreditDecisionPolicy.starter());
        for (final PolicyValueData change : changes) {
            rules.removeIf(r -> r.getScope().equals(change.getScope()) && r.getScopeKey().equals(change.getScopeKey())
                    && r.getKey().equals(change.getKey()));
            rules.add(change);
        }
        return rules;
    }

    private ProfileData saveRules(final long profileId, final int versionNo, final List<PolicyValueData> rules) {
        final SaveDraftRequest request = new SaveDraftRequest();
        request.setRules(rules);
        return service.saveDraft(profileId, versionNo, request);
    }

    private static BigDecimal value(final ActiveProfileData active, final String key) {
        return active.getPolicy().stream().filter(e -> e.getKey().equals(key)).map(EffectiveRuleData::getValue).findFirst().orElse(null);
    }

    private static List<PolicyValueData> rulesOf(final ProfileData profile, final int versionNo) {
        return version(profile, versionNo).getRules();
    }

    private static BigDecimal ruleValue(final List<PolicyValueData> rules, final String scope, final String scopeKey, final String key) {
        return rules.stream().filter(r -> r.getScope().equals(scope) && r.getScopeKey().equals(scopeKey) && r.getKey().equals(key))
                .map(PolicyValueData::getValue).findFirst().orElse(null);
    }

    @Test
    void aNewProfileStartsWithTheProcessFlowPolicyAndACopyKeepsTheLivePolicy() {
        final ProfileData created = service.createProfile(newProfile("Business loans", productId("Business loan - individual", "KES")));
        final List<PolicyValueData> rules = rulesOf(created, 1);

        assertEquals(0, new BigDecimal("30").compareTo(ruleValue(rules, "GLOBAL", "*", "maxDaysPastDue")));
        assertEquals(0, new BigDecimal("0").compareTo(ruleValue(rules, "GLOBAL", "*", "arrearsTolerance")));
        assertEquals(0, new BigDecimal("18").compareTo(ruleValue(rules, "GLOBAL", "*", "minAge")));
        assertEquals(0, new BigDecimal("6").compareTo(ruleValue(rules, "GLOBAL", "*", "minMonthsInBusiness")));
        assertEquals(0, new BigDecimal("750").compareTo(ruleValue(rules, "GLOBAL", "*", "lowRiskMinScore")));
        assertEquals(0, new BigDecimal("650").compareTo(ruleValue(rules, "GLOBAL", "*", "mediumRiskMinScore")));
        assertEquals(0, new BigDecimal("40").compareTo(ruleValue(rules, "GLOBAL", "*", "weightInternalHistory")));
        assertEquals(0, new BigDecimal("10").compareTo(ruleValue(rules, "GLOBAL", "*", "weightDemographics")));

        final ProfileData live = liveProfile("Digital", productId("Digital micro-loan", "RWF"));
        service.newDraft(live.getId(), null);
        saveRules(live.getId(), 2, starterWith(rule("GLOBAL", "*", "maxDaysPastDue", "45")));
        service.submit(live.getId(), 2, "Relax DPD");
        actAs("credit.head");
        service.approve(live.getId(), 2);
        actAs("credit.analyst");

        final CreateProfileRequest copy = newProfile("Copy", productId("Business loan - group", "KES"));
        copy.setCopyFromProfileId(live.getId());
        assertEquals(0, new BigDecimal("45").compareTo(ruleValue(rulesOf(service.createProfile(copy), 1), "GLOBAL", "*", "maxDaysPastDue")));
    }

    @Test
    void theScorecardWeightsMustAddUpToOneHundredPercentBeforeSubmit() {
        final ProfileData created = service.createProfile(newProfile("Business loans", productId("Business loan - individual", "KES")));

        // a draft may be saved while the weights are being worked out, but it cannot be submitted
        saveRules(created.getId(), 1, starterWith(rule("GLOBAL", "*", "weightInternalHistory", "50")));
        final PlatformApiDataValidationException refused = assertThrows(PlatformApiDataValidationException.class,
                () -> service.submit(created.getId(), 1, "Weights"));
        assertTrue(refused.getErrors().stream().anyMatch(e -> e.getParameterName().startsWith("weights.GLOBAL")), refused.getErrors().toString());
        assertEquals("DRAFT", version(service.profile(created.getId()), 1).getStatus());

        // a country that sets weights sets all four and they add up too
        saveRules(created.getId(), 1, starterWith(rule("COUNTRY", "KES", "weightInternalHistory", "100")));
        assertThrows(PlatformApiDataValidationException.class, () -> service.submit(created.getId(), 1, "Partial country weights"));
        saveRules(created.getId(), 1, starterWith(rule("COUNTRY", "KES", "weightInternalHistory", "25"),
                rule("COUNTRY", "KES", "weightBureauHistory", "25"), rule("COUNTRY", "KES", "weightBusinessFinancials", "25"),
                rule("COUNTRY", "KES", "weightDemographics", "25")));
        assertEquals("PENDING", version(service.submit(created.getId(), 1, "Weights"), 1).getStatus());
    }

    @Test
    void amountsCannotBeNegativeAndSettingsMustBeKnown() {
        final ProfileData created = service.createProfile(newProfile("Business loans", productId("Business loan - individual", "KES")));

        assertThrows(PlatformApiDataValidationException.class,
                () -> saveRules(created.getId(), 1, starterWith(rule("COUNTRY", "KES", "maxLoanAmount", "-1"))));
        assertThrows(PlatformApiDataValidationException.class,
                () -> saveRules(created.getId(), 1, starterWith(rule("GLOBAL", "*", "arrearsTolerance", "-5"))));
        assertThrows(PlatformApiDataValidationException.class,
                () -> saveRules(created.getId(), 1, starterWith(rule("GLOBAL", "*", "maxDaysPastDue", "30.5"))));
        assertThrows(PlatformApiDataValidationException.class,
                () -> saveRules(created.getId(), 1, starterWith(rule("COUNTRY", "UGX", "maxLoanAmount", "100"))));
        assertThrows(PlatformApiDataValidationException.class,
                () -> saveRules(created.getId(), 1, starterWith(rule("GLOBAL", "*", "somethingElse", "1"))));
        assertThrows(PlatformApiDataValidationException.class,
                () -> saveRules(created.getId(), 1, starterWith(rule("GLOBAL", "*", "weightDemographics", "101"))));
        assertEquals(0, new BigDecimal("30").compareTo(ruleValue(rulesOf(service.profile(created.getId()), 1), "GLOBAL", "*", "maxDaysPastDue")),
                "a refused save changes nothing");
    }

    @Test
    void theSmallestLoanCannotBeAboveTheLargestAndTheMediumCutoffStaysBelowTheLowOne() {
        final ProfileData created = service.createProfile(newProfile("Business loans", productId("Business loan - individual", "KES")));

        saveRules(created.getId(), 1, starterWith(rule("GLOBAL", "*", "minLoanAmount", "500"), rule("COUNTRY", "KES", "maxLoanAmount", "400")));
        assertThrows(PlatformApiDataValidationException.class, () -> service.submit(created.getId(), 1, "Amounts"),
                "the KES cap is below the global smallest loan once they are combined");

        saveRules(created.getId(), 1, starterWith(rule("GLOBAL", "*", "mediumRiskMinScore", "800")));
        assertThrows(PlatformApiDataValidationException.class, () -> service.submit(created.getId(), 1, "Tiers"));

        saveRules(created.getId(), 1, starterWith(rule("PRODUCT", "99999", "maxLoanAmount", "10")));
        assertThrows(PlatformApiDataValidationException.class, () -> service.submit(created.getId(), 1, "Product"),
                "a product setting must refer to a product the version covers");
    }

    @Test
    void countryCapsAndProductOverridesApplyMostSpecificFirst() {
        final long kes = productId("Business loan - individual", "KES"), kesGroup = productId("Business loan - group", "KES"),
                rwf = productId("Digital micro-loan", "RWF");
        final ProfileData created = service.createProfile(newProfile("Loans", kes, kesGroup, rwf));
        saveRules(created.getId(), 1,
                starterWith(rule("GLOBAL", "*", "maxLoanAmount", "1000000"), rule("COUNTRY", "KES", "maxLoanAmount", "500000"),
                        rule("COUNTRY", "RWF", "maxLoanAmount", "2000000"), rule("PRODUCT", String.valueOf(kesGroup), "maxLoanAmount", "150000"),
                        rule("COUNTRY", "KES", "minLoanAmount", "10000")));
        service.submit(created.getId(), 1, "Caps");
        actAs("credit.head");
        service.approve(created.getId(), 1);
        service.setMode(created.getId(), "SHADOW");

        assertEquals(0, new BigDecimal("500000").compareTo(value(service.activeForProduct(kes), "maxLoanAmount")));
        assertEquals(0, new BigDecimal("150000").compareTo(value(service.activeForProduct(kesGroup), "maxLoanAmount")));
        assertEquals(0, new BigDecimal("2000000").compareTo(value(service.activeForProduct(rwf), "maxLoanAmount")));
        assertEquals(0, new BigDecimal("10000").compareTo(value(service.activeForProduct(kesGroup), "minLoanAmount")));
        assertNull(value(service.activeForProduct(rwf), "minLoanAmount"), "nothing is set for that country");
        assertEquals(0, new BigDecimal("30").compareTo(value(service.activeForProduct(rwf), "maxDaysPastDue")), "the global value still applies");
    }

    @Test
    void changingADpdThresholdOrAnAmountStaysDraftUntilASecondPersonApprovesAndAnActiveVersionNeverChanges() {
        final long kes = productId("Business loan - individual", "KES");
        final ProfileData live = liveProfile("Business loans", kes);
        goLive(live.getId());
        assertEquals(0, new BigDecimal("30").compareTo(value(service.activeForProduct(kes), "maxDaysPastDue")));

        // the editor changes the threshold and an amount cap: nothing changes for the engine yet
        service.newDraft(live.getId(), null);
        saveRules(live.getId(), 2, starterWith(rule("GLOBAL", "*", "maxDaysPastDue", "60"), rule("COUNTRY", "KES", "maxLoanAmount", "900000")));
        assertEquals("DRAFT", version(service.profile(live.getId()), 2).getStatus());
        assertEquals(0, new BigDecimal("30").compareTo(value(service.activeForProduct(kes), "maxDaysPastDue")));
        assertNull(value(service.activeForProduct(kes), "maxLoanAmount"));

        // a second person submits, so the editor is not the only one who has touched it
        actAs("credit.officer");
        service.submit(live.getId(), 2, "Relax the DPD limit");
        assertEquals("PENDING", version(service.profile(live.getId()), 2).getStatus());
        assertEquals(0, new BigDecimal("30").compareTo(value(service.activeForProduct(kes), "maxDaysPastDue")), "pending is not active");

        // neither the editor nor the submitter may approve it
        actAs("credit.analyst");
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.approve(live.getId(), 2), "the editor");
        actAs("credit.officer");
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.approve(live.getId(), 2), "the submitter");
        assertEquals(0, new BigDecimal("30").compareTo(value(service.activeForProduct(kes), "maxDaysPastDue")));
        assertEquals("PENDING", version(service.profile(live.getId()), 2).getStatus());

        // a third person can, and only then does the engine see the new values
        actAs("credit.head");
        service.approve(live.getId(), 2);
        final ActiveProfileData active = service.activeForProduct(kes);
        assertEquals(2, active.getVersionNo());
        assertEquals(0, new BigDecimal("60").compareTo(value(active, "maxDaysPastDue")));
        assertEquals(0, new BigDecimal("900000").compareTo(value(active, "maxLoanAmount")));

        // an active version cannot be edited, withdrawn or submitted again
        final SaveDraftRequest sneaky = new SaveDraftRequest();
        sneaky.setRules(starterWith(rule("GLOBAL", "*", "maxDaysPastDue", "999")));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.saveDraft(live.getId(), 2, sneaky));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.saveDraft(live.getId(), 1, sneaky), "nor a retired one");
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.withdraw(live.getId(), 2));
        assertEquals(0, new BigDecimal("60").compareTo(value(service.activeForProduct(kes), "maxDaysPastDue")));
    }

    @Test
    void anEditorCannotApproveEvenWhenSomeoneElseSubmitted() {
        final ProfileData created = service.createProfile(newProfile("Business loans", productId("Business loan - individual", "KES")));
        saveRules(created.getId(), 1, starterWith(rule("GLOBAL", "*", "maxDaysPastDue", "90")));
        actAs("credit.officer");
        service.submit(created.getId(), 1, "Because");

        actAs("credit.analyst");
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.approve(created.getId(), 1));
        jdbc.update("UPDATE c_configuration SET enabled = 1 WHERE name = 'credit-decision-allow-self-approval'");
        assertEquals("ACTIVE", version(service.approve(created.getId(), 1), 1).getStatus(), "only the test-tenant switch lifts the rule");
    }

    @Test
    void aProductCanBelongToOneProfileAndTheKeyHoldsEvenWhenTheCodeIsBypassed() {
        final long kes = productId("Business loan - individual", "KES");
        final ProfileData first = service.createProfile(newProfile("Business loans", kes));
        final ProfileData second = service.createProfile(newProfile("Digital", productId("Digital micro-loan", "RWF")));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM m_credit_decision_product_claim WHERE loan_product_id = ?", Integer.class, kes));
        final SaveDraftRequest takeIt = new SaveDraftRequest();
        takeIt.setProductIds(List.of(kes));
        assertThrows(PlatformApiDataValidationException.class, () -> service.saveDraft(second.getId(), 1, takeIt));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO m_credit_decision_product_claim (loan_product_id, profile_id) VALUES (?, ?)", kes, second.getId()),
                "the database itself refuses a second profile");

        // dropping the product from a draft frees it, unless the live version still has it
        final SaveDraftRequest drop = new SaveDraftRequest();
        drop.setProductIds(List.of(productId("Business loan - group", "KES")));
        service.saveDraft(first.getId(), 1, drop);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM m_credit_decision_product_claim WHERE loan_product_id = ?", Integer.class, kes));
        service.saveDraft(second.getId(), 1, takeIt);
        assertEquals(second.getId(), jdbc.queryForObject("SELECT profile_id FROM m_credit_decision_product_claim WHERE loan_product_id = ?",
                Long.class, kes));
    }

    @Test
    void theLiveLookupReadsTheOneProfileThatClaimsTheProduct() {
        final long kes = productId("Business loan - individual", "KES");
        final ProfileData live = liveProfile("Business loans", kes);
        goLive(live.getId());

        assertEquals(live.getId(), service.activeForProduct(kes).getProfileId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM m_credit_decision_product_claim WHERE loan_product_id = ?", Integer.class, kes));
        // a product held by a profile with no live version yet is not served
        final ProfileData draftOnly = service.createProfile(newProfile("Digital", productId("Digital micro-loan", "RWF")));
        assertNotNull(draftOnly);
        assertNull(service.activeForProduct(productId("Digital micro-loan", "RWF")));
    }

    @Test
    void everyActivationRecordsWhoWhenAndEachFieldBeforeAndAfterIncludingTheMode() {
        final long kes = productId("Business loan - individual", "KES");
        final ProfileData live = liveProfile("Business loans", kes);
        goLive(live.getId());
        service.newDraft(live.getId(), null);
        saveRules(live.getId(), 2, starterWith(rule("GLOBAL", "*", "maxDaysPastDue", "45")));
        service.submit(live.getId(), 2, "Relax the DPD limit");
        actAs("credit.head");
        service.approve(live.getId(), 2);
        service.setMode(live.getId(), "SHADOW");

        final List<AuditData> history = service.history(live.getId());
        final AuditData change = history.stream().filter(h -> "ACTIVATED".equals(h.getAction()) && h.getVersionNo() == 2
                && "rule.GLOBAL.*.maxDaysPastDue".equals(h.getField())).findFirst().orElseThrow();
        assertEquals("30", change.getOldValue());
        assertEquals("45", change.getNewValue());
        assertEquals("credit.head", change.getActedBy());
        assertEquals(2, change.getVersionNo());
        assertNotNull(change.getActedOn());
        assertTrue(history.stream().noneMatch(h -> "ACTIVATED".equals(h.getAction()) && h.getVersionNo() == 2 && "rule.GLOBAL.*.minAge".equals(h.getField())),
                "fields that did not change are not listed");

        final AuditData toLive = history.stream().filter(h -> "MODE_APPROVED".equals(h.getAction())).findFirst().orElseThrow();
        assertEquals("OFF", toLive.getOldValue());
        assertEquals("LIVE", toLive.getNewValue());
        assertEquals("credit.head", toLive.getActedBy());
        assertTrue(history.stream().anyMatch(h -> "MODE_REQUESTED".equals(h.getAction()) && "credit.analyst".equals(h.getActedBy())));
        final AuditData toShadow = history.stream().filter(h -> "MODE_CHANGED".equals(h.getAction())).findFirst().orElseThrow();
        assertEquals("LIVE", toShadow.getOldValue());
        assertEquals("SHADOW", toShadow.getNewValue());
        assertTrue(history.stream().anyMatch(h -> "SUBMITTED".equals(h.getAction()) && h.getVersionNo() == 2));
        assertTrue(history.stream().anyMatch(h -> "DRAFT_SAVED".equals(h.getAction()) && "rule.GLOBAL.*.maxDaysPastDue".equals(h.getField())));
        assertEquals("credit.head", service.profile(live.getId()).getModeChangedBy());
    }

    @Test
    void switchingToLiveNeedsASecondAuthorisedPerson() {
        final ProfileData live = liveProfile("Business loans", productId("Business loan - individual", "KES"));

        ProfileData asked = service.setMode(live.getId(), "LIVE");
        assertEquals("OFF", asked.getMode(), "asking does not switch anything");
        assertEquals("LIVE", asked.getPendingMode());
        assertEquals("credit.analyst", asked.getPendingBy());
        assertNull(service.activeForProduct(version(asked, 1).getProductIds().get(0)));

        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.approveMode(live.getId()), "the person who asked cannot approve");
        assertEquals("OFF", service.profile(live.getId()).getMode());

        actAs("credit.head");
        final ProfileData approved = service.approveMode(live.getId());
        assertEquals("LIVE", approved.getMode());
        assertNull(approved.getPendingMode());
        assertEquals("credit.head", approved.getModeChangedBy());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.approveMode(live.getId()), "nothing is waiting any more");
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.setMode(live.getId(), "LIVE"), "it is live already");

        // taking it down is immediate, and a request can be cancelled
        assertEquals("OFF", service.setMode(live.getId(), "OFF").getMode());
        service.setMode(live.getId(), "LIVE");
        assertEquals("LIVE", service.profile(live.getId()).getPendingMode());
        assertNull(service.cancelMode(live.getId()).getPendingMode());
        assertEquals("OFF", service.profile(live.getId()).getMode());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.cancelMode(live.getId()));
    }

    @Test
    void theLiveProfileIsKeptPerProductAndDroppedWhenAnApprovalAModeOrTheConnectionChanges() {
        final long kes = productId("Business loan - individual", "KES");
        final ProfileData live = liveProfile("Business loans", kes);
        goLive(live.getId());

        final ActiveProfileData first = service.activeForProduct(kes);
        assertFalse(first.isSendingEnabled());
        assertSame(first, service.activeForProduct(kes), "the second read is served from the cache");

        // a change made behind the service's back is not seen until the cache is dropped
        jdbc.update("UPDATE m_credit_decision_profile SET mode = 'SHADOW' WHERE id = ?", live.getId());
        assertEquals("LIVE", service.activeForProduct(kes).getMode());

        service.updateConnection(new ConnectionData("Engine", true, "https://engine.example/decisions", 8, 3, "RETRY", null, null));
        final ActiveProfileData afterConnection = service.activeForProduct(kes);
        assertEquals("SHADOW", afterConnection.getMode(), "a connection change dropped the cache");
        assertTrue(afterConnection.isSendingEnabled(), "and the connection snapshot is fresh");

        service.setMode(live.getId(), "OFF");
        assertNull(service.activeForProduct(kes), "a mode change drops it too");

        service.setMode(live.getId(), "SHADOW");
        final int before = service.activeForProduct(kes).getVersionNo();
        service.newDraft(live.getId(), null);
        saveRules(live.getId(), 2, starterWith(rule("GLOBAL", "*", "maxDaysPastDue", "50")));
        service.submit(live.getId(), 2, "More");
        actAs("credit.head");
        assertEquals(before, service.activeForProduct(kes).getVersionNo());
        service.approve(live.getId(), 2);
        assertEquals(2, service.activeForProduct(kes).getVersionNo(), "an approval drops it");
        assertEquals(0, new BigDecimal("50").compareTo(value(service.activeForProduct(kes), "maxDaysPastDue")));
    }

    @Test
    void connectionChangesAreRecordedWithTheirOldAndNewValues() {
        service.updateConnection(new ConnectionData("Engine", true, "https://engine.example/decisions", 8, 3, "RETRY", null, null));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM m_credit_decision_audit WHERE action = 'CONNECTION_CHANGED' AND field_name = 'endpointUrl' "
                        + "AND new_value = 'https://engine.example/decisions' AND acted_by = 'credit.analyst' AND profile_id IS NULL",
                Integer.class));
    }
}
