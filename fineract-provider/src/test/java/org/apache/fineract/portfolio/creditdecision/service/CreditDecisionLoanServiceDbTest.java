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
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.FileSystemResourceAccessor;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionCatalogue;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CreateProfileRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.SaveDraftRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.LoanDecisionData;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Runs the real migrations and the real SQL for the per-loan credit decision against a throw-away MySQL schema, with a
 * scripted decision engine in place of the network. Switched off unless CREDIT_DECISION_TEST_JDBC_URL is set. See
 * {@link CreditDecisionServiceDbTest} for how.
 */
@EnabledIfEnvironmentVariable(named = "CREDIT_DECISION_TEST_JDBC_URL", matches = ".+")
class CreditDecisionLoanServiceDbTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    /** Stands in for the decision engine: answers from a queue and remembers what it was asked. */
    private static final class ScriptedEngine implements CreditDecisionEngineTransport {

        final Deque<Object> script = new ArrayDeque<>();
        final List<String> bodies = new ArrayList<>();

        @Override
        public Response post(final String url, final String jsonBody, final int timeoutSeconds, final String bearerToken) throws IOException {
            bodies.add(jsonBody);
            final Object next = script.isEmpty() ? new Response(200, "{\"decision\":\"GO\"}") : script.poll();
            if (next instanceof IOException) {
                throw (IOException) next;
            }
            return (Response) next;
        }
    }

    private Connection connection;
    private JdbcTemplate jdbc;
    private CreditDecisionService profiles;
    private CreditDecisionLoanService service;
    private CreditDecisionLoanFacts facts;
    private ScriptedEngine engine;
    private PlatformSecurityContext context;

    private long product;
    private long otherProduct;

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

        // just enough of the Fineract schema for the migrations and the queries to run
        jdbc.execute("CREATE TABLE m_product_loan (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), currency_code VARCHAR(3))");
        jdbc.execute("CREATE TABLE m_permission (id BIGINT AUTO_INCREMENT PRIMARY KEY, `grouping` VARCHAR(45), code VARCHAR(100) UNIQUE, "
                + "entity_name VARCHAR(100), action_name VARCHAR(100), can_maker_checker TINYINT(1))");
        jdbc.execute("CREATE TABLE m_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100))");
        jdbc.execute("CREATE TABLE m_role_permission (role_id BIGINT, permission_id BIGINT)");
        jdbc.execute("CREATE TABLE c_configuration (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), value BIGINT NULL, "
                + "date_value DATE NULL, string_value VARCHAR(100) NULL, enabled TINYINT(1), is_trap_door TINYINT(1), "
                + "description VARCHAR(500))");
        jdbc.execute("CREATE TABLE m_code_value (id BIGINT AUTO_INCREMENT PRIMARY KEY, code_value VARCHAR(100))");
        jdbc.execute("CREATE TABLE m_client (id BIGINT AUTO_INCREMENT PRIMARY KEY, account_no VARCHAR(20), date_of_birth DATE, "
                + "submittedon_date DATE, activation_date DATE, client_classification_cv_id BIGINT)");
        jdbc.execute("CREATE TABLE m_loan (id BIGINT AUTO_INCREMENT PRIMARY KEY, account_no VARCHAR(20), client_id BIGINT, "
                + "product_id BIGINT, loan_status_id INT, loan_decision_state INT NULL, currency_code VARCHAR(3), principal_amount_proposed DECIMAL(19,6), "
                + "term_frequency INT, term_period_frequency_enum INT, loanpurpose_cv_id BIGINT, writtenoffon_date DATE)");
        jdbc.execute("CREATE TABLE m_loan_repayment_schedule (id BIGINT AUTO_INCREMENT PRIMARY KEY, loan_id BIGINT, duedate DATE, "
                + "obligations_met_on_date DATE, completed_derived TINYINT(1))");
        jdbc.execute("CREATE TABLE m_loan_arrears_aging (loan_id BIGINT PRIMARY KEY, total_overdue_derived DECIMAL(19,6))");
        jdbc.execute("CREATE TABLE m_transunion_crb_header (id BIGINT AUTO_INCREMENT PRIMARY KEY, client_id BIGINT, loan_id BIGINT, "
                + "report_date VARCHAR(255))");
        jdbc.execute("CREATE TABLE m_transunion_crb_score_output (id BIGINT AUTO_INCREMENT PRIMARY KEY, header_id BIGINT, "
                + "positive_score VARCHAR(255))");
        jdbc.execute("CREATE TABLE m_transunion_crb_summary (id BIGINT AUTO_INCREMENT PRIMARY KEY, header_id BIGINT, "
                + "npa_accounts_all_sectors INT, open_accounts_all_sectors INT)");
        jdbc.execute("CREATE TABLE m_loan_decision (id BIGINT AUTO_INCREMENT PRIMARY KEY, loan_id BIGINT, "
                + "is_due_diligence_signed TINYINT(1), is_reject_due_diligence TINYINT(1), due_diligence_recommended_amount DECIMAL(19,6), "
                + "due_diligence_term_frequency INT, due_diligence_term_period_frequency_type INT)");
        jdbc.update("INSERT INTO m_role (name) VALUES ('Super user')");
        jdbc.update("INSERT INTO m_product_loan (name, currency_code) VALUES ('Business loan - individual', 'KES'), "
                + "('Digital micro-loan', 'RWF')");
        product = jdbc.queryForObject("SELECT id FROM m_product_loan WHERE currency_code = 'KES'", Long.class);
        otherProduct = jdbc.queryForObject("SELECT id FROM m_product_loan WHERE currency_code = 'RWF'", Long.class);

        final Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        final FileSystemResourceAccessor files = new FileSystemResourceAccessor(new File("src/main/resources/db/changelog/tenant/parts"));
        new Liquibase("CGLT-846_credit_decision_profiles.xml", files, database).update("");
        new Liquibase("CGLT-846_loan_credit_decision.xml", files, database).update("");

        context = mock(PlatformSecurityContext.class);
        actAs("credit.analyst");
        profiles = new CreditDecisionService(jdbc, context);
        facts = new CreditDecisionLoanFacts(jdbc);
        engine = new ScriptedEngine();
        service = new CreditDecisionLoanService(jdbc, context, profiles, facts, engine);
    }

    @AfterEach
    void closeConnection() throws Exception {
        // an open connection would keep metadata locks and block the next test's DROP TABLE
        if (connection != null) {
            connection.close();
        }
    }

    private void actAs(final String username) {
        final AppUser current = mock(AppUser.class);
        when(current.getUsername()).thenReturn(username);
        when(context.authenticatedUser()).thenReturn(current);
    }

    // ---------------------------------------------------------------- fixtures

    /** A profile for the KES product with the given parameters switched on, approved by a second person. */
    private ProfileData profile(final String mode, final String... keys) {
        final CreateProfileRequest create = new CreateProfileRequest();
        create.setName("Business loans");
        create.setProductIds(List.of(product));
        final ProfileData created = profiles.createProfile(create);
        final Map<String, Boolean> on = new java.util.LinkedHashMap<>();
        for (final String key : keys) {
            on.put(key, Boolean.TRUE);
        }
        final SaveDraftRequest edit = new SaveDraftRequest();
        edit.setProductIds(List.of(product));
        edit.setParams(CreditDecisionCatalogue.params(on));
        profiles.saveDraft(created.getId(), 1, edit);
        profiles.submit(created.getId(), 1, "Initial rollout");
        actAs("credit.head");
        profiles.approve(created.getId(), 1);
        actAs("credit.analyst");
        return "OFF".equals(mode) ? created : profiles.setMode(created.getId(), mode);
    }

    private void connectEngine() {
        jdbc.update("UPDATE m_credit_decision_connection SET provider = 'Test engine', enabled = 1, "
                + "endpoint_url = 'http://engine.test/decide', retries = 0, timeout_seconds = 2 WHERE id = 1");
    }

    private long client(final String accountNo) {
        jdbc.update("INSERT INTO m_client (account_no, date_of_birth, submittedon_date) VALUES (?, '1990-05-01', '2024-11-01')", accountNo);
        return jdbc.queryForObject("SELECT id FROM m_client WHERE account_no = ?", Long.class, accountNo);
    }

    private long loan(final String accountNo, final Long clientId, final long productId, final int status, final String amount) {
        jdbc.update("INSERT INTO m_loan (account_no, client_id, product_id, loan_status_id, currency_code, principal_amount_proposed, "
                + "term_frequency, term_period_frequency_enum) VALUES (?, ?, ?, ?, 'KES', ?, 12, 2)", accountNo, clientId, productId, status,
                new BigDecimal(amount));
        return jdbc.queryForObject("SELECT id FROM m_loan WHERE account_no = ?", Long.class, accountNo);
    }

    private long applicant(final String accountNo) {
        return loan(accountNo, client("C-" + accountNo), product, 100, "40000");
    }

    private void bureau(final long loanId, final String score, final int npa) {
        final Long clientId = jdbc.queryForObject("SELECT client_id FROM m_loan WHERE id = ?", Long.class, loanId);
        jdbc.update("INSERT INTO m_transunion_crb_header (client_id, loan_id, report_date) VALUES (?, ?, '2026-10-21')", clientId, loanId);
        final long header = jdbc.queryForObject("SELECT MAX(id) FROM m_transunion_crb_header", Long.class);
        jdbc.update("INSERT INTO m_transunion_crb_score_output (header_id, positive_score) VALUES (?, ?)", header, score);
        jdbc.update("INSERT INTO m_transunion_crb_summary (header_id, npa_accounts_all_sectors, open_accounts_all_sectors) VALUES (?, ?, 2)",
                header, npa);
    }

    private CreditDecisionEngineTransport.Response reply(final String json) {
        return new CreditDecisionEngineTransport.Response(200, json);
    }

    /** A live profile, a connected engine, and an application that is flagged, verified and has its bureau report. */
    private long readyApplication() {
        profile("LIVE", "amount", "term", "currency", "bscore", "blist");
        connectEngine();
        final long loanId = applicant("A-1");
        bureau(loanId, "688", 0);
        service.setRequired(loanId, true);
        service.verify(loanId, "Checked amount and term with the customer");
        return loanId;
    }

    // ---------------------------------------------------------------- flag

    @Test
    void migrationAddsTheTableAndTheThreePermissionsForTheSuperUser() {
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM m_permission WHERE entity_name = 'CREDITDECISIONLOAN'", Integer.class));
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM m_role_permission", Integer.class));
    }

    @Test
    void aLoanDoesNotNeedADecisionUnlessFlagged() {
        profile("LIVE", "amount");
        final long loanId = applicant("A-1");

        final LoanDecisionData status = service.status(loanId);
        assertTrue(status.isEligible());
        assertFalse(status.isRequired());
        assertFalse(status.isBlocksApproval());
        assertEquals(List.of(CreditDecisionLoanData.BLOCKER_NOT_REQUIRED), status.getBlockers());
        // nothing is held back for a loan that is not flagged
        service.assertOutcomeReceived(loanId);
    }

    @Test
    void onlyProductsInAProfileCanBeFlagged() {
        profile("LIVE", "amount");
        final long loanId = loan("B-1", client("C-B1"), otherProduct, 100, "9000");

        assertFalse(service.status(loanId).isEligible());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.setRequired(loanId, true));
    }

    @Test
    void flaggingStartsPendingVerificationAndCanBeUndone() {
        profile("LIVE", "amount");
        final long loanId = applicant("A-1");

        final LoanDecisionData flagged = service.setRequired(loanId, true);
        assertTrue(flagged.isRequired());
        assertEquals(CreditDecisionLoanData.PENDING_VERIFICATION, flagged.getVerificationStatus());
        assertEquals(CreditDecisionLoanData.NOT_SENT, flagged.getRequestStatus());
        assertTrue(flagged.isBlocksApproval());

        final LoanDecisionData cleared = service.setRequired(loanId, false);
        assertFalse(cleared.isRequired());
        assertFalse(cleared.isBlocksApproval());
        service.assertOutcomeReceived(loanId);
    }

    @Test
    void theFlagAndTheVerificationOnlyChangeWhileTheApplicationWaitsForApproval() {
        profile("LIVE", "amount");
        final long loanId = applicant("A-1");
        service.setRequired(loanId, true);
        jdbc.update("UPDATE m_loan SET loan_status_id = 200 WHERE id = ?", loanId);

        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.setRequired(loanId, false));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.verify(loanId, null));
        assertFalse(service.status(loanId).isCanChangeFlag());
    }

    @Test
    void anUnknownLoanIsNotFound() {
        assertThrows(LoanNotFoundException.class, () -> service.status(987654L));
    }

    // ---------------------------------------------------------------- verify

    @Test
    void verifyingNeedsAFlaggedLoanAndRecordsWhoAndWhen() {
        profile("LIVE", "amount");
        final long loanId = applicant("A-1");
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.verify(loanId, "x"));

        service.setRequired(loanId, true);
        actAs("loan.officer");
        final LoanDecisionData verified = service.verify(loanId, "  Phone number and amount corrected  ");
        assertEquals(CreditDecisionLoanData.VERIFIED, verified.getVerificationStatus());
        assertEquals("loan.officer", verified.getVerifiedBy());
        assertNotNull(verified.getVerifiedOn());
        assertEquals("Phone number and amount corrected", verified.getVerificationNote());
    }

    @Test
    void unverifyingPutsItBackToPendingAndClearsAnOutcomeGivenForTheOldDetails() {
        final long loanId = readyApplication();
        engine.script.add(reply("{\"decision\":\"GO\",\"score\":720}"));
        assertEquals(CreditDecisionLoanData.DECIDED, service.send(loanId).getRequestStatus());

        final LoanDecisionData reopened = service.unverify(loanId);
        assertEquals(CreditDecisionLoanData.PENDING_VERIFICATION, reopened.getVerificationStatus());
        assertNull(reopened.getVerifiedBy());
        assertEquals(CreditDecisionLoanData.NOT_SENT, reopened.getRequestStatus());
        assertNull(reopened.getDecision());
        assertTrue(reopened.isBlocksApproval());
    }

    // ---------------------------------------------------------------- send

    @Test
    void theRequestCannotBeSentUntilEverythingIsInPlace() {
        final long loanId = applicant("A-1");
        // no profile at all, nothing flagged
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(loanId));

        profile("OFF", "amount");
        service.setRequired(loanId, true);
        assertEquals(List.of(CreditDecisionLoanData.BLOCKER_PENDING_VERIFICATION, CreditDecisionLoanData.BLOCKER_PROFILE_OFF,
                CreditDecisionLoanData.BLOCKER_ENGINE_NOT_CONNECTED), service.status(loanId).getBlockers());
        assertTrue(engine.bodies.isEmpty());
    }

    @Test
    void sendBuildsTheRequestFromTheProfileAndKeepsTheOutcome() {
        final long loanId = readyApplication();
        actAs("loan.officer");
        engine.script.add(reply("{\"decision\":\"GO\",\"score\":720.5,\"reasonCodes\":[\"R1\",\"R2\"]}"));

        final LoanDecisionData sent = service.send(loanId);

        assertEquals(CreditDecisionLoanData.DECIDED, sent.getRequestStatus());
        assertEquals("GO", sent.getDecision());
        assertEquals(0, new BigDecimal("720.5").compareTo(sent.getScore()));
        assertEquals("R1, R2", sent.getReasonCodes());
        assertEquals("loan.officer", sent.getRequestedBy());
        assertNull(sent.getErrorMessage());
        assertFalse(sent.isBlocksApproval());
        assertEquals("Test engine", sent.getProvider());
        // what was exchanged stays with the application so it can be looked at later
        assertEquals("Business loans", sent.getRequestedProfileName());
        assertEquals(1, sent.getRequestedVersionNo());
        assertEquals(engine.bodies.get(0), sent.getRequestJson());
        assertTrue(sent.getReplyJson().contains("\"GO\""), sent.getReplyJson());

        assertEquals(1, engine.bodies.size());
        final String body = engine.bodies.get(0);
        assertTrue(body.contains("\"applicationId\":\"A-1\""), body);
        assertTrue(body.contains("\"customerId\":\"C-A-1\""), body);
        assertTrue(body.contains("\"profile\":\"Business loans v1\""), body);
        assertTrue(body.contains("\"requestedAmount\":40000"), body);
        assertTrue(body.contains("\"termMonths\":12"), body);
        assertTrue(body.contains("\"currency\":\"KES\""), body);
        assertTrue(body.contains("\"bureauScore\":688"), body);
        assertTrue(body.contains("\"bureauListed\":\"No\""), body);
        // a parameter that is switched off is never sent
        assertFalse(body.contains("applicantAge"), body);

        // the exchange is written to the decision log
        final Map<String, Object> log = jdbc.queryForMap("SELECT * FROM m_credit_decision_log");
        assertEquals("A-1", log.get("application_ref"));
        assertEquals("DECIDED", log.get("status"));
        assertEquals("GO", log.get("decision"));
        assertEquals("Business loans", log.get("profile_name"));
        assertEquals(1, ((Number) log.get("version_no")).intValue());
    }

    @Test
    void theRequestIsHeldBackWhenAParameterThatMustHaveAValueHasNone() {
        profile("LIVE", "amount", "bscore");
        connectEngine();
        final long loanId = applicant("A-2"); // no bureau report on file
        service.setRequired(loanId, true);
        service.verify(loanId, null);

        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(loanId));
        assertTrue(e.getDefaultUserMessage().contains("Bureau score"), e.getDefaultUserMessage());
        assertTrue(engine.bodies.isEmpty());
        assertEquals(CreditDecisionLoanData.NOT_SENT, service.status(loanId).getRequestStatus());
        assertEquals("HELD", jdbc.queryForObject("SELECT status FROM m_credit_decision_log", String.class));
    }

    @Test
    void aSendThatGetsNoUsableReplyIsKeptAsFailedAndStillBlocksApproval() {
        final long loanId = readyApplication();
        engine.script.add(reply("{\"decision\":\"MAYBE\"}"));

        final LoanDecisionData failed = service.send(loanId);

        assertEquals(CreditDecisionLoanData.FAILED, failed.getRequestStatus());
        assertNull(failed.getDecision());
        assertTrue(failed.getErrorMessage().contains("MAYBE"), failed.getErrorMessage());
        assertTrue(failed.isBlocksApproval());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.assertOutcomeReceived(loanId));
        assertEquals(1, engine.bodies.size(), "an answer that cannot be used is not asked for again");
        assertEquals("AWAITING", jdbc.queryForObject("SELECT status FROM m_credit_decision_log", String.class));

        // the person can try again, and a good answer replaces the failure
        engine.script.add(reply("{\"decision\":\"REVIEW\",\"score\":610}"));
        final LoanDecisionData retried = service.send(loanId);
        assertEquals(CreditDecisionLoanData.DECIDED, retried.getRequestStatus());
        assertEquals("REVIEW", retried.getDecision());
        assertNull(retried.getErrorMessage());
        service.assertOutcomeReceived(loanId);
    }

    @Test
    void anEngineThatCannotBeReachedIsAskedAgainAsOftenAsTheConnectionSays() {
        final long loanId = readyApplication();
        jdbc.update("UPDATE m_credit_decision_connection SET retries = 1 WHERE id = 1");
        engine.script.add(new IOException("Connection refused"));
        engine.script.add(new IOException("Connection refused"));

        final LoanDecisionData failed = service.send(loanId);

        assertEquals(2, engine.bodies.size());
        assertEquals(CreditDecisionLoanData.FAILED, failed.getRequestStatus());
        assertTrue(failed.getErrorMessage().contains("could not be reached"), failed.getErrorMessage());
    }

    @Test
    void aTemporaryEngineErrorIsRetriedButARefusalIsNot() {
        final long loanId = readyApplication();
        jdbc.update("UPDATE m_credit_decision_connection SET retries = 2 WHERE id = 1");
        engine.script.add(new CreditDecisionEngineTransport.Response(503, "busy"));
        engine.script.add(reply("{\"decision\":\"NO-GO\",\"score\":402}"));
        final LoanDecisionData decided = service.send(loanId);
        assertEquals(2, engine.bodies.size());
        assertEquals("NO-GO", decided.getDecision());

        engine.bodies.clear();
        engine.script.add(new CreditDecisionEngineTransport.Response(401, "bad key"));
        final LoanDecisionData refused = service.send(loanId);
        assertEquals(1, engine.bodies.size());
        assertEquals(CreditDecisionLoanData.FAILED, refused.getRequestStatus());
        assertTrue(refused.getErrorMessage().contains("401"), refused.getErrorMessage());
    }

    // ---------------------------------------------------------------- approval

    @Test
    void approvalWaitsForTheOutcomeOnlyWhenTheProfileIsLive() {
        final long loanId = readyApplication();
        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.assertOutcomeReceived(loanId));
        assertEquals("error.msg.creditdecision.loan.outcome.required", e.getGlobalisationMessageCode());

        service.send(loanId);
        service.assertOutcomeReceived(loanId);

        // in Shadow the engine is consulted, but approval does not wait for it
        final long second = applicant("A-3");
        service.setRequired(second, true);
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.assertOutcomeReceived(second));
        jdbc.update("UPDATE m_credit_decision_profile SET mode = 'SHADOW'");
        service.assertOutcomeReceived(second);
        assertFalse(service.status(second).isBlocksApproval());
    }

    // ---------------------------------------------------------------- loan stages

    private void extendedLifecycle(final boolean on) {
        jdbc.update("DELETE FROM c_configuration WHERE name = 'Add-More-Stages-To-A-Loan-Life-Cycle'");
        jdbc.update("INSERT INTO c_configuration (name, enabled, is_trap_door) VALUES ('Add-More-Stages-To-A-Loan-Life-Cycle', ?, 0)", on);
    }

    private void stage(final long loanId, final Integer state) {
        jdbc.update("UPDATE m_loan SET loan_decision_state = ? WHERE id = ?", state, loanId);
    }

    @Test
    void withTheExtendedLifecycleTheDecisionIsWorkedAfterTheReviewAndBeforeDueDiligence() {
        profile("LIVE", "amount");
        connectEngine();
        extendedLifecycle(true);
        final long loanId = applicant("A-1");

        // nothing has been reviewed yet: the flag can be set, but there is nothing to verify or send
        service.setRequired(loanId, true);
        assertTrue(service.status(loanId).isCanChangeFlag());
        assertFalse(service.status(loanId).isReviewStageOpen());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.verify(loanId, null));

        // the review is accepted: verify and send
        stage(loanId, 1000);
        assertTrue(service.status(loanId).isReviewStageOpen());
        service.verify(loanId, null);
        service.send(loanId);

        // Due Diligence is accepted: the decision is read-only from here
        stage(loanId, 1200);
        final LoanDecisionData later = service.status(loanId);
        assertFalse(later.isReviewStageOpen());
        assertFalse(later.isCanChangeFlag());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.setRequired(loanId, false));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.unverify(loanId));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(loanId));
        assertEquals(CreditDecisionLoanData.DECIDED, later.getRequestStatus());
    }

    @Test
    void withoutTheExtendedLifecycleAnyPendingApplicationCanBeWorked() {
        profile("LIVE", "amount");
        final long loanId = applicant("A-1");
        service.setRequired(loanId, true);
        assertTrue(service.status(loanId).isReviewStageOpen());
        service.verify(loanId, null);
    }

    @Test
    void anApplicationThatNeedsADecisionIsNeverPutThroughIcReview() {
        profile("LIVE", "amount");
        final long loanId = applicant("A-1");
        service.assertIcReviewAllowed(loanId);

        assertFalse(service.isRequired(loanId));
        service.setRequired(loanId, true);
        assertTrue(service.isRequired(loanId));
        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.assertIcReviewAllowed(loanId));
        assertEquals("error.msg.creditdecision.loan.ic.review.not.allowed", e.getGlobalisationMessageCode());

        service.setRequired(loanId, false);
        service.assertIcReviewAllowed(loanId);
    }

    @Test
    void theReviewCannotBeReturnedOnceTheEngineHasGivenItsOutcome() {
        final long loanId = readyApplication();
        service.assertReviewCanBeReturned(loanId);

        service.send(loanId);
        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.assertReviewCanBeReturned(loanId));
        assertEquals("error.msg.creditdecision.loan.review.cannot.be.returned", e.getGlobalisationMessageCode());

        // re-opening the verification clears the outcome and the review can be returned again
        service.unverify(loanId);
        service.assertReviewCanBeReturned(loanId);

        final long other = applicant("A-2");
        service.assertReviewCanBeReturned(other);
    }

    @Test
    void theOutcomeHoldNamesTheStepThatIsRefused() {
        final long loanId = readyApplication();
        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.assertOutcomeReceived(loanId, "moved to Due Diligence"));
        assertTrue(e.getDefaultUserMessage().contains("moved to Due Diligence"));
    }

    // ---------------------------------------------------------------- what is sent for a client

    @Test
    void theCatalogueValuesAreWorkedOutFromTheLoanTheClientTheirHistoryTheBureauAndTheDueDiligence() {
        jdbc.update("INSERT INTO m_code_value (code_value) VALUES ('Retail'), ('Stock purchase')");
        final long retail = jdbc.queryForObject("SELECT id FROM m_code_value WHERE code_value = 'Retail'", Long.class);
        final long stock = jdbc.queryForObject("SELECT id FROM m_code_value WHERE code_value = 'Stock purchase'", Long.class);
        final long clientId = client("C-100");
        jdbc.update("UPDATE m_client SET client_classification_cv_id = ? WHERE id = ?", retail, clientId);

        // closed on time and late, one still active and overdue, one written off within 24 months
        final long closed = loan("L-10", clientId, product, 600, "20000");
        jdbc.update("INSERT INTO m_loan_repayment_schedule (loan_id, duedate, obligations_met_on_date, completed_derived) VALUES "
                + "(?, '2026-03-01', '2026-03-11', 1), (?, '2026-04-01', '2026-04-01', 1), (?, '2025-09-01', '2025-12-01', 1)", closed, closed,
                closed);
        final long active = loan("L-11", clientId, product, 300, "30000");
        jdbc.update("INSERT INTO m_loan_repayment_schedule (loan_id, duedate, obligations_met_on_date, completed_derived) VALUES "
                + "(?, '2026-10-01', NULL, 0), (?, '2026-12-01', NULL, 0)", active, active);
        jdbc.update("INSERT INTO m_loan_arrears_aging (loan_id, total_overdue_derived) VALUES (?, 1500.50)", active);
        final long writtenOff = loan("L-12", clientId, product, 601, "10000");
        jdbc.update("UPDATE m_loan SET writtenoffon_date = '2025-02-10' WHERE id = ?", writtenOff);

        final long current = loan("L-20", clientId, product, 100, "40000");
        jdbc.update("UPDATE m_loan SET loanpurpose_cv_id = ? WHERE id = ?", stock, current);
        bureau(current, "688", 0);
        jdbc.update("INSERT INTO m_loan_decision (loan_id, is_due_diligence_signed, is_reject_due_diligence, "
                + "due_diligence_recommended_amount, due_diligence_term_frequency, due_diligence_term_period_frequency_type) "
                + "VALUES (?, 1, 0, 35000, 10, 2)", current);

        final List<String> every = new ArrayList<>(CreditDecisionCatalogue.items().keySet());
        final Map<String, Object> values = facts.resolve(current, every, TODAY);

        assertEquals(0, new BigDecimal("40000").compareTo((BigDecimal) values.get("amount")));
        assertEquals(12L, values.get("term"));
        assertEquals("KES", values.get("currency"));
        assertEquals("Stock purchase", values.get("purpose"));
        assertEquals(36, values.get("age"));
        assertEquals(24L, values.get("tenure"));
        assertEquals("Retail", values.get("sector"));
        assertEquals(1L, values.get("cycles"));
        assertEquals(39, values.get("dpd")); // 2026-10-01 still unpaid on 2026-11-09
        assertEquals(2, values.get("late")); // 10 days on L-10 and the unpaid one on L-11
        assertEquals(0, new BigDecimal("1500.5").compareTo((BigDecimal) values.get("arrears")));
        assertEquals(1L, values.get("activeInt"));
        assertEquals("WRITTEN_OFF", values.get("prev"));
        assertEquals("Yes", values.get("wo"));
        assertEquals(0, new BigDecimal("688").compareTo((BigDecimal) values.get("bscore")));
        assertEquals("No", values.get("blist"));
        assertEquals(2L, values.get("bactive"));
        assertEquals("2026-10-21", values.get("bdate"));
        assertEquals("Yes", values.get("ddDone"));
        @SuppressWarnings("unchecked")
        final Map<String, Object> diligence = (Map<String, Object>) values.get("ddRes");
        assertEquals("PASSED", diligence.get("result"));
        assertEquals(0, new BigDecimal("35000").compareTo((BigDecimal) diligence.get("recommendedAmount")));
        assertEquals(10L, diligence.get("recommendedTermMonths"));

        // nothing in the core system holds these, so they are always missing
        assertFalse(values.containsKey("channel"));
        assertFalse(values.containsKey("kyc"));
    }

    @Test
    void aFirstTimeCustomerHasNoHistoryRatherThanAPerfectOne() {
        final long current = applicant("A-9");
        final Map<String, Object> values = facts.resolve(current, List.of("cycles", "activeInt", "arrears", "prev", "wo", "dpd", "late", "bscore",
                "blist", "ddDone", "ddRes", "purpose", "sector"), TODAY);

        assertEquals(0L, values.get("cycles"));
        assertEquals(0L, values.get("activeInt"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) values.get("arrears")));
        assertEquals("NONE", values.get("prev"));
        assertEquals("No", values.get("wo"));
        for (final String missing : List.of("dpd", "late", "bscore", "blist", "ddDone", "ddRes", "purpose", "sector")) {
            assertFalse(values.containsKey(missing), missing + " should be missing");
        }
    }

    @Test
    void termsGivenInDaysWeeksOrYearsAreSentAsMonths() {
        final long current = applicant("A-8");
        final Map<String, Integer[]> cases = Map.of("days", new Integer[] { 90, 0, 3 }, "weeks", new Integer[] { 52, 1, 12 }, "years",
                new Integer[] { 2, 3, 24 }, "months", new Integer[] { 18, 2, 18 });
        for (final Map.Entry<String, Integer[]> c : cases.entrySet()) {
            jdbc.update("UPDATE m_loan SET term_frequency = ?, term_period_frequency_enum = ? WHERE id = ?", c.getValue()[0], c.getValue()[1],
                    current);
            assertEquals((long) c.getValue()[2], facts.resolve(current, List.of("term"), TODAY).get("term"), c.getKey());
        }
    }
}
