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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
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
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ConnectionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CreateProfileRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogEntryData;
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

        context = mock(PlatformSecurityContext.class);
        service = new CreditDecisionService(jdbc, context);
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
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM m_permission WHERE entity_name LIKE 'CREDITDECISION%'", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM m_role_permission", Integer.class));
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

        service.setMode(live.getId(), "live");
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
    void theLogRecordsRequestsAndRepliesAndCanBeFiltered() {
        service.updateConnection(new ConnectionData("Engine A", false, "", 8, 3, "RETRY", null, null));
        final LogEntryData go = new LogEntryData();
        go.setApplicationRef("10231");
        go.setCustomerRef("000884635");
        go.setProfileName("Business loans");
        go.setVersionNo(7);
        go.setMode("LIVE");
        go.setStatus("decided");
        go.setDecision("GO");
        go.setScore(new BigDecimal("82"));
        go.setRequestJson("{\"applicationId\":10231}");
        go.setReplyJson("{\"decision\":\"GO\"}");
        service.recordLog(go);
        final LogEntryData held = new LogEntryData();
        held.setApplicationRef("10232");
        held.setStatus("HELD");
        service.recordLog(held);

        assertThrows(PlatformApiDataValidationException.class, () -> service.recordLog(new LogEntryData()));
        assertEquals(2, service.log(null, null, 50).size());
        assertEquals(1, service.log("GO", null, 50).size());
        assertEquals(1, service.log("NONE", null, 50).size());
        assertEquals(1, service.log(null, "0232", 50).size());
        final LogEntryData row = service.log("GO", null, 50).get(0);
        assertEquals("Engine A", row.getProvider(), "the provider defaults to the one configured when the entry was recorded");
        assertEquals(0, new BigDecimal("82").compareTo(row.getScore()));
        assertEquals("DECIDED", row.getStatus());
    }
}
