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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.FileSystemResourceAccessor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CreateProfileRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.VersionData;
import org.apache.fineract.portfolio.creditdecision.handler.ActivateCreditDecisionCommandHandler;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Activation of a policy version through the Maker-Checker workflow: submitting files a task, nothing is live until the
 * checker approves it, and a task turned down in the Checker Inbox returns the version to draft. Runs the real
 * migrations and SQL against a throw-away MySQL schema; the Checker Inbox itself is a stand-in. Switched off unless
 * CREDIT_DECISION_TEST_JDBC_URL is set. See {@link CreditDecisionServiceDbTest} for how.
 */
@EnabledIfEnvironmentVariable(named = "CREDIT_DECISION_TEST_JDBC_URL", matches = ".+")
class CreditDecisionPolicyWorkflowDbTest {

    private static final int AWAITING = 2;
    private static final int REJECTED = 3;

    private Connection connection;
    private JdbcTemplate jdbc;
    private CreditDecisionService service;
    private CreditDecisionMakerChecker inbox;
    private CreditDecisionPolicyWorkflow workflow;
    private PlatformSecurityContext context;
    private long product;

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
        jdbc.execute("CREATE TABLE m_appuser (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100))");
        jdbc.execute("CREATE TABLE m_portfolio_command_source (id BIGINT AUTO_INCREMENT PRIMARY KEY, action_name VARCHAR(100), "
                + "entity_name VARCHAR(100), maker_id BIGINT, checker_id BIGINT NULL, processing_result_enum INT)");
        jdbc.execute("CREATE TABLE c_configuration (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), value BIGINT NULL, "
                + "date_value DATE NULL, string_value VARCHAR(100) NULL, enabled TINYINT(1), is_trap_door TINYINT(1), "
                + "description VARCHAR(500))");
        jdbc.update("INSERT INTO m_role (name) VALUES ('Super user'), ('Credit risk')");
        jdbc.update("INSERT INTO m_product_loan (name, currency_code) VALUES ('Business loan', 'KES'), ('Uganda loan', 'UGX')");
        product = jdbc.queryForObject("SELECT id FROM m_product_loan WHERE currency_code = 'KES'", Long.class);

        final Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        final FileSystemResourceAccessor files = new FileSystemResourceAccessor(new File("src/main/resources/db/changelog/tenant/parts"));
        new Liquibase("CGLT-846_credit_decision_profiles.xml", files, database).update("");
        new Liquibase("CGLT-846_loan_credit_decision.xml", files, database).update("");
        // Liquibase leaves the shared connection with auto-commit off; commit so later JDBC setup is not rolled back
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
        connection.setAutoCommit(true);

        context = mock(PlatformSecurityContext.class);
        service = new CreditDecisionService(jdbc, context);
        inbox = mock(CreditDecisionMakerChecker.class);
        workflow = new CreditDecisionPolicyWorkflow(service, inbox);
        actAs("credit.analyst");
    }

    @AfterEach
    void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private void actAs(final String username) {
        final AppUser current = mock(AppUser.class);
        when(current.getUsername()).thenReturn(username);
        when(context.authenticatedUser()).thenReturn(current);
    }

    private long draft(final String name) {
        final CreateProfileRequest request = new CreateProfileRequest();
        request.setName(name);
        request.setProductIds(List.of(product));
        return service.createProfile(request).getId();
    }

    private static VersionData v1(final ProfileData profile) {
        return profile.getVersions().stream().filter(v -> v.getVersionNo() == 1).findFirst().orElseThrow();
    }

    private void migrationAppliedToTheTask(final long taskId, final int result, final String checker) {
        jdbc.update("INSERT INTO m_appuser (username) VALUES (?)", checker);
        final long checkerId = jdbc.queryForObject("SELECT id FROM m_appuser WHERE username = ?", Long.class, checker);
        jdbc.update("INSERT INTO m_portfolio_command_source (id, action_name, entity_name, maker_id, checker_id, processing_result_enum) "
                + "VALUES (?, 'ACTIVATE', 'CREDITDECISION', 1, ?, ?) ON DUPLICATE KEY UPDATE checker_id = VALUES(checker_id), "
                + "processing_result_enum = VALUES(processing_result_enum)", taskId, checkerId, result);
    }

    // ---------------------------------------------------------------- migration

    @Test
    void theMigrationMakesActivationMakerCheckerAndLetsEarlierApproversBeCheckers() throws Exception {
        final Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        final FileSystemResourceAccessor files = new FileSystemResourceAccessor(new File("src/main/resources/db/changelog/tenant/parts"));
        final long creditRisk = jdbc.queryForObject("SELECT id FROM m_role WHERE name = 'Credit risk'", Long.class);
        final long approve = jdbc.queryForObject("SELECT id FROM m_permission WHERE code = 'APPROVE_CREDITDECISION'", Long.class);
        // Commit before Liquibase: it rolls back the shared connection when it starts, which would drop uncommitted grants.
        connection.setAutoCommit(false);
        jdbc.update("INSERT INTO m_role_permission (role_id, permission_id) VALUES (?, ?)", creditRisk, approve);
        connection.commit();

        new Liquibase("CGLT-846_policy_maker_checker.xml", files, database).update("");
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
        connection.setAutoCommit(true);

        assertEquals(1, jdbc.queryForObject("SELECT can_maker_checker FROM m_permission WHERE code = 'ACTIVATE_CREDITDECISION'",
                Integer.class));
        assertEquals("ACTIVATE_CHECKER", jdbc.queryForObject(
                "SELECT action_name FROM m_permission WHERE code = 'ACTIVATE_CREDITDECISION_CHECKER'", String.class));
        assertEquals(List.of("ACTIVATE_CREDITDECISION", "ACTIVATE_CREDITDECISION_CHECKER"), jdbc.queryForList(
                "SELECT p.code FROM m_role_permission rp JOIN m_role r ON r.id = rp.role_id "
                        + "JOIN m_permission p ON p.id = rp.permission_id WHERE r.name = 'Super user' AND p.code LIKE 'ACTIVATE_CREDITDECISION%' "
                        + "ORDER BY p.code",
                String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM m_role_permission WHERE role_id = ? AND permission_id = ?", Integer.class, creditRisk, approve));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM m_role_permission rp JOIN m_permission p ON p.id = rp.permission_id "
                        + "WHERE rp.role_id = ? AND p.code = 'ACTIVATE_CREDITDECISION_CHECKER'",
                Integer.class, creditRisk));
    }

    // ---------------------------------------------------------------- submit

    @Test
    void submittingFilesATaskForACheckerAndLocksTheVersion() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        when(inbox.file(eq(profileId), eq(1), eq("Business loans"), eq("Initial rollout"))).thenReturn(41L);

        final ProfileData profile = workflow.submit(profileId, 1, "Initial rollout");

        assertEquals("PENDING", v1(profile).getStatus());
        assertEquals(41L, v1(profile).getMakerCheckerId());
        verify(inbox).file(profileId, 1, "Business loans", "Initial rollout");
        assertTrue(service.profile(profileId).getVersions().stream().noneMatch(v -> "ACTIVE".equals(v.getStatus())),
                "nothing is live before a checker approves");
    }

    @Test
    void aVersionThatCannotBeSubmittedFilesNoTask() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");

        assertThrows(RuntimeException.class, () -> workflow.submit(profileId, 1, " "));

        verify(inbox, never()).file(anyLong(), anyInt(), anyString(), anyString());
        assertEquals("DRAFT", v1(service.profile(profileId)).getStatus());
    }

    // ---------------------------------------------------------------- the checker

    @Test
    void approvingGoesThroughTheTaskAndNeverActivatesByItself() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        when(inbox.file(anyLong(), anyInt(), anyString(), anyString())).thenReturn(41L);
        workflow.submit(profileId, 1, "Initial rollout");

        workflow.approve(profileId, 1);

        verify(inbox).approve(41L);
        // the stand-in inbox did nothing, so the version is still waiting: only the task's handler activates it
        assertEquals("PENDING", v1(service.profile(profileId)).getStatus());
    }

    @Test
    void aVersionSubmittedBeforeTheInboxExistedMustBeSubmittedAgain() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        service.submit(profileId, 1, "Old style");

        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> workflow.approve(profileId, 1));
        assertEquals("error.msg.creditdecision.task.missing", e.getGlobalisationMessageCode());
    }

    @Test
    void rejectingReturnsTheVersionAndTurnsTheTaskDown() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        when(inbox.file(anyLong(), anyInt(), anyString(), anyString())).thenReturn(41L);
        workflow.submit(profileId, 1, "Initial rollout");
        actAs("credit.head");

        final ProfileData profile = workflow.reject(profileId, 1, "Cutoffs too loose");

        assertEquals("DRAFT", v1(profile).getStatus());
        assertNull(v1(profile).getMakerCheckerId());
        assertTrue(v1(profile).getReason().contains("Cutoffs too loose"));
        verify(inbox).reject(41L, "Cutoffs too loose");
    }

    @Test
    void withdrawingTakesTheTaskBack() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        when(inbox.file(anyLong(), anyInt(), anyString(), anyString())).thenReturn(41L);
        workflow.submit(profileId, 1, "Initial rollout");

        final ProfileData profile = workflow.withdraw(profileId, 1);

        assertEquals("DRAFT", v1(profile).getStatus());
        assertNull(v1(profile).getMakerCheckerId());
        verify(inbox).cancel(41L);
    }

    @Test
    void aTaskTurnedDownInTheCheckerInboxReturnsTheVersionToDraft() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        when(inbox.file(anyLong(), anyInt(), anyString(), anyString())).thenReturn(41L);
        workflow.submit(profileId, 1, "Initial rollout");
        migrationAppliedToTheTask(41L, REJECTED, "credit.head");

        final VersionData version = v1(workflow.profile(profileId));

        assertEquals("DRAFT", version.getStatus());
        assertNull(version.getMakerCheckerId());
        assertEquals("Returned: rejected by credit.head in the Checker Inbox", version.getReason());
        assertEquals("credit.head", jdbc.queryForObject(
                "SELECT acted_by FROM m_credit_decision_audit WHERE profile_id = ? AND action = 'RETURNED'", String.class, profileId));
        // it can be changed and sent again
        when(inbox.file(anyLong(), anyInt(), anyString(), anyString())).thenReturn(42L);
        assertEquals(42L, v1(workflow.submit(profileId, 1, "Looser cutoffs fixed")).getMakerCheckerId());
    }

    @Test
    void aTaskDeletedFromTheCheckerInboxReturnsTheVersionToDraftToo() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        when(inbox.file(anyLong(), anyInt(), anyString(), anyString())).thenReturn(41L);
        workflow.submit(profileId, 1, "Initial rollout");

        final List<ProfileData> all = workflow.profiles();

        assertEquals("DRAFT", v1(all.get(0)).getStatus());
        assertTrue(v1(all.get(0)).getReason().contains("deleted"));
    }

    @Test
    void aTaskStillWaitingLeavesTheVersionAlone() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        when(inbox.file(anyLong(), anyInt(), anyString(), anyString())).thenReturn(41L);
        workflow.submit(profileId, 1, "Initial rollout");
        migrationAppliedToTheTask(41L, AWAITING, "credit.head");

        assertEquals("PENDING", v1(workflow.profile(profileId)).getStatus());
    }

    // ---------------------------------------------------------------- the handler

    private JsonCommand taskCommand(final long profileId, final Integer versionNo) {
        final FromJsonHelper helper = new FromJsonHelper();
        final String json = versionNo == null ? "{}" : "{\"versionNo\":" + versionNo + "}";
        return JsonCommand.from(json, helper.parse(json), helper, "CREDITDECISION", profileId, null, null, null, null, null, null, null,
                null, null, null);
    }

    @Test
    void theCheckersApprovalActivatesTheVersionAndAMakerCannotActivateTheirOwn() throws Exception {
        runMigration();
        final long profileId = draft("Business loans");
        when(inbox.file(anyLong(), anyInt(), anyString(), anyString())).thenReturn(41L);
        workflow.submit(profileId, 1, "Initial rollout");
        final ActivateCreditDecisionCommandHandler handler = new ActivateCreditDecisionCommandHandler(service);

        assertThrows(GeneralPlatformDomainRuleException.class, () -> handler.processCommand(taskCommand(profileId, 1)));
        assertEquals("PENDING", v1(service.profile(profileId)).getStatus());

        actAs("credit.head");
        final CommandProcessingResult result = handler.processCommand(taskCommand(profileId, 1));

        assertEquals(profileId, result.resourceId());
        assertEquals("ACTIVE", v1(service.profile(profileId)).getStatus());
        assertEquals("credit.head", v1(service.profile(profileId)).getDecidedBy());
    }

    @Test
    void aTaskThatDoesNotNameTheVersionIsRefused() {
        final ActivateCreditDecisionCommandHandler handler = new ActivateCreditDecisionCommandHandler(service);

        assertThrows(GeneralPlatformDomainRuleException.class, () -> handler.processCommand(taskCommand(1L, null)));
        assertNotNull(handler);
    }

    private void runMigration() throws Exception {
        final Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        new Liquibase("CGLT-846_policy_maker_checker.xml",
                new FileSystemResourceAccessor(new File("src/main/resources/db/changelog/tenant/parts")), database).update("");
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
        connection.setAutoCommit(true);
    }
}
