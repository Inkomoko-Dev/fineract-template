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
            final Object next = script.isEmpty() ? new Response(200, "{\"recommendation\":\"AUTO_APPROVE\"}") : script.poll();
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
    private List<Object> published;
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
                + "report_date VARCHAR(255), request_no VARCHAR(255), created_on_utc DATETIME NULL)");
        jdbc.execute("CREATE TABLE m_transunion_crb_score_output (id BIGINT AUTO_INCREMENT PRIMARY KEY, header_id BIGINT, "
                + "positive_score VARCHAR(255))");
        jdbc.execute("CREATE TABLE m_transunion_crb_summary (id BIGINT AUTO_INCREMENT PRIMARY KEY, header_id BIGINT, "
                + "npa_accounts_all_sectors INT, open_accounts_all_sectors INT, fc_all_sectors INT)");
        jdbc.execute("CREATE TABLE m_address (id BIGINT AUTO_INCREMENT PRIMARY KEY, country_id BIGINT NULL)");
        jdbc.execute("CREATE TABLE m_client_address (id BIGINT AUTO_INCREMENT PRIMARY KEY, client_id BIGINT, address_id BIGINT, "
                + "is_active TINYINT(1))");
        jdbc.execute("CREATE TABLE m_business_detail (id BIGINT AUTO_INCREMENT PRIMARY KEY, client_id BIGINT, business_creation_date DATE)");
        jdbc.execute("CREATE TABLE m_client_non_person (id BIGINT AUTO_INCREMENT PRIMARY KEY, client_id BIGINT, in_business_since DATE NULL)");
        jdbc.execute("CREATE TABLE job (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), display_name VARCHAR(100), "
                + "cron_expression VARCHAR(100), create_time DATETIME, task_priority SMALLINT, group_name VARCHAR(100), job_key VARCHAR(500), "
                + "is_active TINYINT(1), currently_running TINYINT(1), updates_allowed TINYINT(1), scheduler_group SMALLINT, "
                + "is_misfired TINYINT(1), node_id INT, is_mismatched_job TINYINT(1))");
        jdbc.execute("CREATE TABLE m_appuser (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), is_deleted TINYINT(1) DEFAULT 0, "
                + "enabled TINYINT(1) DEFAULT 1)");
        jdbc.execute("CREATE TABLE m_appuser_role (appuser_id BIGINT, role_id BIGINT)");
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
        published = new ArrayList<>();
        service = new CreditDecisionLoanService(jdbc, context, profiles, facts, engine, published::add,
                CreditDecisionCircuitBreaker.disabled(), CreditDecisionBackoff.none());
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
        if ("OFF".equals(mode)) {
            return created;
        }
        final ProfileData asked = profiles.setMode(created.getId(), mode);
        if (asked.getPendingMode() == null) {
            return asked;
        }
        // going live is asked for by one person and approved by another
        actAs("credit.head");
        final ProfileData live = profiles.approveMode(created.getId());
        actAs("credit.analyst");
        return live;
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

    /** A recent report date, so a request sent today never meets a report that has aged past the limit. */
    private static final String RECENT_REPORT = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(3).toString();

    private void bureau(final long loanId, final String score, final int npa) {
        bureau(loanId, score, npa, RECENT_REPORT);
    }

    private void bureau(final long loanId, final String score, final int npa, final String reportDate) {
        final Long clientId = jdbc.queryForObject("SELECT client_id FROM m_loan WHERE id = ?", Long.class, loanId);
        jdbc.update("INSERT INTO m_transunion_crb_header (client_id, loan_id, report_date) VALUES (?, ?, ?)", clientId, loanId,
                reportDate);
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

    /** Another application like {@link #readyApplication()}, using the profile and engine that one already set up. */
    private long readyApplication(final String accountNo) {
        final long loanId = applicant(accountNo);
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
        engine.script.add(reply("{\"recommendation\":\"AUTO_APPROVE\",\"score\":720}"));
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
        engine.script.add(reply("{\"recommendation\":\"AUTO_APPROVE\",\"score\":720.5,\"reasonCodes\":[\"R1\",\"R2\"]}"));

        final LoanDecisionData sent = service.send(loanId);

        assertEquals(CreditDecisionLoanData.DECIDED, sent.getRequestStatus());
        assertEquals("AUTO_APPROVE", sent.getDecision());
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
        assertTrue(sent.getReplyJson().contains("\"AUTO_APPROVE\""), sent.getReplyJson());

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
        assertEquals("AUTO_APPROVE", log.get("decision"));
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
    void aBureauReportOlderThanThirtyDaysIsNotUsedAndTheOfficerIsTold() {
        profile("LIVE", "amount", "bscore");
        connectEngine();
        final long loanId = applicant("A-2");
        bureau(loanId, "688", 0, LocalDate.now(java.time.ZoneOffset.UTC).minusDays(41).toString());
        service.setRequired(loanId, true);
        service.verify(loanId, null);

        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(loanId));
        assertTrue(e.getDefaultUserMessage().contains("Bureau score"), e.getDefaultUserMessage());
        assertTrue(e.getDefaultUserMessage().contains("41 days old"), e.getDefaultUserMessage());
        assertTrue(e.getDefaultUserMessage().contains("Pull a fresh report"), e.getDefaultUserMessage());
        assertFalse(e.getDefaultUserMessage().contains(".."), e.getDefaultUserMessage());
        assertTrue(engine.bodies.isEmpty());
        assertEquals(CreditDecisionLoanData.NOT_SENT, service.status(loanId).getRequestStatus());
    }

    @Test
    void aBureauReportOnTheThirtiethDayIsStillUsed() {
        final long loanId = readyApplication();
        jdbc.update("UPDATE m_transunion_crb_header SET report_date = ? WHERE loan_id = ?", LocalDate.now(java.time.ZoneOffset.UTC).minusDays(30).toString(), loanId);
        assertEquals(new BigDecimal("688"), facts.resolve(loanId, List.of("bscore")).get("bscore"));
        assertNull(facts.staleBureauNote(loanId));

        jdbc.update("UPDATE m_transunion_crb_header SET report_date = ? WHERE loan_id = ?", LocalDate.now(java.time.ZoneOffset.UTC).minusDays(31).toString(), loanId);
        assertFalse(facts.resolve(loanId, List.of("bscore")).containsKey("bscore"));
        assertTrue(facts.staleBureauNote(loanId).contains("31 days old"));
    }

    @Test
    void aFresherReportPulledOnAnotherOfApplicationOfTheClientIsUsedWhenTheLoansOwnIsStale() {
        final long old = applicant("A-2");
        bureau(old, "500", 0, LocalDate.now(java.time.ZoneOffset.UTC).minusDays(60).toString());
        final Long clientId = jdbc.queryForObject("SELECT client_id FROM m_loan WHERE id = ?", Long.class, old);
        jdbc.update("INSERT INTO m_transunion_crb_header (client_id, loan_id, report_date) VALUES (?, ?, ?)", clientId, old + 1000,
                LocalDate.now(java.time.ZoneOffset.UTC).minusDays(2).toString());
        final long fresh = jdbc.queryForObject("SELECT MAX(id) FROM m_transunion_crb_header", Long.class);
        jdbc.update("INSERT INTO m_transunion_crb_score_output (header_id, positive_score) VALUES (?, '710')", fresh);

        assertEquals(new BigDecimal("710"), facts.resolve(old, List.of("bscore")).get("bscore"));
        assertNull(facts.staleBureauNote(old));
    }

    @Test
    void aBureauReportWithNoUsableDateIsTreatedAsStaleUnlessItWasStoredRecently() {
        final long loanId = applicant("A-2");
        bureau(loanId, "688", 0, "not a date");
        assertFalse(facts.resolve(loanId, List.of("bscore")).containsKey("bscore"));
        assertTrue(facts.staleBureauNote(loanId).contains("no date"));

        jdbc.update("UPDATE m_transunion_crb_header SET created_on_utc = ? WHERE loan_id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1)), loanId);
        assertEquals(new BigDecimal("688"), facts.resolve(loanId, List.of("bscore")).get("bscore"));
    }

    @Test
    void aReportDateWithATimeAndOffsetIsRead() {
        final long loanId = applicant("A-2");
        final String stamp = java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(2)).minusDays(45).toString();
        bureau(loanId, "688", 0, stamp);
        assertTrue(facts.staleBureauNote(loanId).contains("45 days old"), facts.staleBureauNote(loanId));
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
        engine.script.add(reply("{\"recommendation\":\"AUTO_APPROVE\",\"score\":610}"));
        final LoanDecisionData retried = service.send(loanId);
        assertEquals(CreditDecisionLoanData.DECIDED, retried.getRequestStatus());
        assertEquals("AUTO_APPROVE", retried.getDecision());
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
        engine.script.add(reply("{\"recommendation\":\"IC_REVIEW_REQUIRED\",\"score\":402}"));
        final LoanDecisionData decided = service.send(loanId);
        assertEquals(2, engine.bodies.size());
        assertEquals("IC_REVIEW_REQUIRED", decided.getDecision());

        engine.bodies.clear();
        engine.script.add(new CreditDecisionEngineTransport.Response(401, "bad key"));
        final LoanDecisionData refused = service.send(loanId);
        assertEquals(1, engine.bodies.size());
        assertEquals(CreditDecisionLoanData.FAILED, refused.getRequestStatus());
        assertTrue(refused.getErrorMessage().contains("401"), refused.getErrorMessage());
    }

    @Test
    void afterRepeatedFailuresTheEngineIsNotCalledUntilThePauseIsOverAndThenOneTrialDecidesWhetherToResume() {
        final long[] clock = { 1_000_000L };
        final CreditDecisionCircuitBreaker breaker = new CreditDecisionCircuitBreaker(2, 60_000, () -> clock[0]);
        final CreditDecisionLoanService guarded = new CreditDecisionLoanService(jdbc, context, profiles, facts, engine, published::add,
                breaker, CreditDecisionBackoff.none());
        final long first = readyApplication();
        final long second = readyApplication("A-2");
        final long third = readyApplication("A-3");

        engine.script.add(new IOException("down"));
        guarded.send(first);
        engine.script.add(new CreditDecisionEngineTransport.Response(503, "busy"));
        guarded.send(second);
        assertEquals(CreditDecisionCircuitBreaker.State.OPEN, breaker.state());
        assertEquals(2, engine.bodies.size());

        // while it is open the engine is not called, and the application is kept as failed with a clear reason
        final LoanDecisionData skipped = guarded.send(third);
        assertEquals(2, engine.bodies.size(), "the engine was not called");
        assertEquals(CreditDecisionLoanData.FAILED, skipped.getRequestStatus());
        assertTrue(skipped.getErrorMessage().contains("temporarily unavailable"), skipped.getErrorMessage());
        assertTrue(skipped.getErrorMessage().contains("sent again automatically"), skipped.getErrorMessage());
        assertNotNull(skipped.getNextRetryOn(), "it is picked up again by the scheduled job");

        // after the pause one trial call goes through, and an answer closes the breaker
        clock[0] += 61_000;
        engine.script.add(reply("{\"recommendation\":\"AUTO_APPROVE\"}"));
        assertEquals(CreditDecisionLoanData.DECIDED, guarded.send(third).getRequestStatus());
        assertEquals(CreditDecisionCircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(3, engine.bodies.size());
    }

    @Test
    void aRefusalFromTheEngineDoesNotCountAsAFailureOfTheEngine() {
        final CreditDecisionCircuitBreaker breaker = new CreditDecisionCircuitBreaker(2, 60_000, () -> 0L);
        final CreditDecisionLoanService guarded = new CreditDecisionLoanService(jdbc, context, profiles, facts, engine, published::add,
                breaker, CreditDecisionBackoff.none());
        final long loanId = readyApplication();
        for (int i = 0; i < 4; i++) {
            engine.script.add(new CreditDecisionEngineTransport.Response(400, "bad request"));
            guarded.send(loanId);
        }
        assertEquals(CreditDecisionCircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(4, engine.bodies.size());
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

    @Test
    void aLiveProfileLetsAnAutoApprovedApplicationStraightOnToTheContract() {
        final long loanId = readyApplication();
        engine.script.add(reply("{\"recommendation\":\"AUTO_APPROVE\",\"score\":720}"));
        final LoanDecisionData decided = service.send(loanId);

        assertFalse(decided.isBlocksContract());
        assertTrue(decided.isBlocksIcReview(), "an approved application skips IC review");
        assertTrue(service.bypassesIcReview(loanId));
        service.assertContractAllowed(loanId, "moved to Prepare and sign contract");
        service.assertOutcomeReceived(loanId);
        final GeneralPlatformDomainRuleException ic = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.assertIcReviewAllowed(loanId));
        assertEquals("error.msg.creditdecision.loan.ic.review.not.allowed", ic.getGlobalisationMessageCode());
    }

    @Test
    void anApplicationThatNeedsIcReviewGoesThroughItAndReachesTheContractAfterwards() {
        final long loanId = readyApplication();
        engine.script.add(reply("{\"recommendation\":\"IC_REVIEW_REQUIRED\",\"score\":610,\"reasonCodes\":[\"BUREAU_SCORE_MARGINAL\"]}"));
        final LoanDecisionData decided = service.send(loanId);

        // Due Diligence still happens, IC review is open and the contract is not
        assertTrue(decided.isBlocksContract());
        assertFalse(decided.isBlocksIcReview());
        assertFalse(service.bypassesIcReview(loanId));
        service.assertOutcomeReceived(loanId, "moved to Due Diligence");
        service.assertNotIneligible(loanId, "moved to Due Diligence");
        service.assertIcReviewAllowed(loanId);
        final GeneralPlatformDomainRuleException early = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.assertContractAllowed(loanId, "moved to Prepare and sign contract"));
        assertEquals("error.msg.creditdecision.loan.ic.review.required", early.getGlobalisationMessageCode());

        // once the IC levels have been passed the contract is open
        stage(loanId, 1500);
        service.assertContractAllowed(loanId, "moved to Prepare and sign contract");
        assertFalse(service.status(loanId).isBlocksContract());
    }

    @Test
    void anIneligibleApplicationCanOnlyBeRejectedAndAShadowProfileNeverHoldsAnything() {
        final long loanId = readyApplication();
        engine.script.add(reply("{\"recommendation\":\"INELIGIBLE\",\"score\":280}"));
        service.send(loanId);
        for (final String action : List.of("approved", "moved to Due Diligence", "put through IC review")) {
            final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class,
                    () -> service.assertNotIneligible(loanId, action));
            assertEquals("error.msg.creditdecision.loan.ineligible", e.getGlobalisationMessageCode());
            assertTrue(e.getDefaultUserMessage().contains("can only be rejected"));
        }
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.assertOutcomeReceived(loanId));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.assertIcReviewAllowed(loanId));

        jdbc.update("UPDATE m_credit_decision_profile SET mode = 'SHADOW'");
        service.assertNotIneligible(loanId, "approved");
        service.assertContractAllowed(loanId, "moved to Prepare and sign contract");
        service.assertIcReviewAllowed(loanId);
        assertFalse(service.bypassesIcReview(loanId));
        final LoanDecisionData shadow = service.status(loanId);
        assertFalse(shadow.isBlocksContract());
        assertFalse(shadow.isBlocksIcReview());
    }

    @Test
    void theRiskTierMaximumExposureAndModelVersionAreKeptWithTheOutcomeAndInTheLog() {
        final long loanId = readyApplication();
        engine.script.add(reply("{\"scoringStatus\":\"COMPLETED\",\"recommendation\":\"AUTO_APPROVE\",\"score\":731,"
                + "\"riskTier\":\"LOW\",\"maxAllowedExposure\":150000.50,\"modelVersion\":\"scorecard-v3\"}"));
        final LoanDecisionData decided = service.send(loanId);

        assertEquals("LOW", decided.getRiskTier());
        assertEquals(0, new BigDecimal("150000.50").compareTo(decided.getMaxExposure()));
        assertEquals("scorecard-v3", decided.getModelVersion());
        final Map<String, Object> log = jdbc.queryForMap("SELECT * FROM m_credit_decision_log ORDER BY id DESC LIMIT 1");
        assertEquals("LOW", log.get("risk_tier"));
        assertEquals("scorecard-v3", log.get("model_version"));

        // re-opening the verification clears them with the outcome
        service.unverify(loanId);
        final LoanDecisionData cleared = service.status(loanId);
        assertNull(cleared.getRiskTier());
        assertNull(cleared.getMaxExposure());
        assertNull(cleared.getModelVersion());
    }

    @Test
    void theDecisionIsPublishedSoAnIneligibleApplicationCanBeRejected() {
        final long loanId = readyApplication();
        engine.script.add(reply("{\"recommendation\":\"INELIGIBLE\",\"reasonCodes\":[\"ACTIVE_DEFAULT\"]}"));
        service.send(loanId);

        assertEquals(1, published.size());
        final CreditDecisionOutcomeRecorded event = (CreditDecisionOutcomeRecorded) published.get(0);
        assertEquals(loanId, event.getLoanId());
        assertEquals("INELIGIBLE", event.getDecision());
        assertEquals("ACTIVE_DEFAULT", event.getReasonCodes());
        assertTrue(event.isLive());

        service.recordRejection(loanId, true, "The application was rejected automatically");
        service.recordRejection(loanId, false, "Could not reject");
        final List<CreditDecisionLoanData.EventData> history = service.history(loanId);
        assertEquals("FAILED", history.get(0).getEventType());
        assertEquals("REJECTED", history.get(1).getEventType());
        assertEquals("INELIGIBLE", history.get(1).getDecision());
    }

    @Test
    void aFailedSendPublishesNothing() {
        final long loanId = readyApplication();
        engine.script.add(new java.io.IOException("connection refused"));
        service.send(loanId);
        assertTrue(published.isEmpty());
    }

    // ---------------------------------------------------------------- automatic start

    @Test
    void anApplicationInAProfileIsFlaggedVerifiedAndSentOnceItsReviewIsAccepted() {
        profile("LIVE", "amount", "term", "currency");
        connectEngine();
        extendedLifecycle(true);
        final long loanId = applicant("A-1");
        stage(loanId, 1000);
        engine.script.add(reply("{\"recommendation\":\"IC_REVIEW_REQUIRED\",\"score\":640}"));

        service.autoStart(loanId);

        final LoanDecisionData status = service.status(loanId);
        assertTrue(status.isRequired());
        assertEquals(CreditDecisionLoanData.VERIFIED, status.getVerificationStatus());
        assertEquals(CreditDecisionLoanData.DECIDED, status.getRequestStatus());
        assertEquals("IC_REVIEW_REQUIRED", status.getDecision());
        assertEquals(List.of("DECIDED", "VERIFIED", "FLAGGED"), service.history(loanId).stream()
                .map(CreditDecisionLoanData.EventData::getEventType).collect(java.util.stream.Collectors.toList()));
        assertTrue(service.history(loanId).get(2).getDetail().startsWith("Automatically"));
        assertEquals(1, engine.bodies.size());

        // nothing is sent again once there is an outcome
        service.autoStart(loanId);
        assertEquals(1, engine.bodies.size());
    }

    @Test
    void automaticStartLeavesAloneWhatItShould() {
        profile("LIVE", "amount");
        connectEngine();
        extendedLifecycle(true);

        // an officer switched the decision off for this application
        final long switchedOff = applicant("A-1");
        service.setRequired(switchedOff, true);
        service.setRequired(switchedOff, false);
        stage(switchedOff, 1000);
        service.autoStart(switchedOff);
        assertFalse(service.isRequired(switchedOff));

        // not yet reviewed, and already past the review
        final long notReviewed = applicant("A-2");
        service.autoStart(notReviewed);
        assertFalse(service.isRequired(notReviewed));
        final long pastReview = applicant("A-3");
        stage(pastReview, 1200);
        service.autoStart(pastReview);
        assertFalse(service.isRequired(pastReview));

        // a product that is in no profile, and a profile that is switched off
        final long otherProductLoan = loan("B-1", client("C-B1"), otherProduct, 100, "9000");
        stage(otherProductLoan, 1000);
        service.autoStart(otherProductLoan);
        assertFalse(service.isRequired(otherProductLoan));
        final long inOffProfile = applicant("A-4");
        stage(inOffProfile, 1000);
        jdbc.update("UPDATE m_credit_decision_profile SET mode = 'OFF'");
        service.autoStart(inOffProfile);
        assertFalse(service.isRequired(inOffProfile));
        assertTrue(engine.bodies.isEmpty());
    }

    @Test
    void whenTheEngineCannotBeReachedAutomaticStartLeavesTheApplicationReadyToSendByHand() {
        profile("LIVE", "amount");
        connectEngine();
        extendedLifecycle(true);
        final long loanId = applicant("A-1");
        stage(loanId, 1000);
        engine.script.add(new java.io.IOException("connection refused"));

        service.autoStart(loanId);

        final LoanDecisionData status = service.status(loanId);
        assertTrue(status.isRequired());
        assertEquals(CreditDecisionLoanData.FAILED, status.getRequestStatus());
        assertTrue(status.getBlockers().isEmpty(), "the officer can send it again");
        assertTrue(status.isBlocksApproval());

        // and a disconnected engine is not an error either
        jdbc.update("UPDATE m_credit_decision_connection SET enabled = 0 WHERE id = 1");
        final long second = applicant("A-2");
        stage(second, 1000);
        service.autoStart(second);
        assertEquals(CreditDecisionLoanData.NOT_SENT, service.status(second).getRequestStatus());
        assertTrue(service.status(second).isRequired());
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
    void icReviewWaitsForTheOutcomeAndOnlyAnIcReviewRequiredApplicationGoesThroughIt() {
        profile("LIVE", "amount");
        connectEngine();
        final long loanId = applicant("A-1");
        service.assertIcReviewAllowed(loanId);

        // flagged but no outcome yet
        service.setRequired(loanId, true);
        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.assertIcReviewAllowed(loanId));
        assertEquals("error.msg.creditdecision.loan.outcome.required", e.getGlobalisationMessageCode());

        service.verify(loanId, null);
        engine.script.add(reply("{\"recommendation\":\"IC_REVIEW_REQUIRED\"}"));
        service.send(loanId);
        service.assertIcReviewAllowed(loanId);

        service.unverify(loanId);
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
    void theRequirementCannotBeRemovedOnceTheEngineHasGivenItsOutcome() {
        final long loanId = readyApplication();
        assertTrue(service.status(loanId).isCanChangeFlag());
        service.send(loanId);

        assertFalse(service.status(loanId).isCanChangeFlag());
        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.setRequired(loanId, false));
        assertEquals("error.msg.creditdecision.loan.already.decided", e.getGlobalisationMessageCode());
        assertTrue(service.isRequired(loanId));

        // re-opening the verification clears the outcome, and then the requirement can be taken off
        service.unverify(loanId);
        assertTrue(service.status(loanId).isCanChangeFlag());
        service.setRequired(loanId, false);
        assertFalse(service.isRequired(loanId));
    }

    @Test
    void anIcReviewOutcomeCanBeAskedAgainInTheReviewStageButAFinalOneAndAnyOutcomeAfterItCannot() {
        profile("LIVE", "amount");
        connectEngine();
        extendedLifecycle(true);
        final long loanId = applicant("A-1");
        service.setRequired(loanId, true);
        stage(loanId, 1000);
        service.verify(loanId, null);
        engine.script.add(reply("{\"recommendation\":\"IC_REVIEW_REQUIRED\",\"score\":640}"));
        assertEquals("IC_REVIEW_REQUIRED", service.send(loanId).getDecision());

        // the details were corrected, so the request can go again from the review
        final LoanDecisionData waiting = service.status(loanId);
        assertTrue(waiting.isSendStageOpen());
        assertTrue(waiting.getBlockers().isEmpty(), waiting.getBlockers().toString());
        engine.script.add(reply("{\"recommendation\":\"AUTO_APPROVE\",\"score\":720}"));
        assertEquals("AUTO_APPROVE", service.send(loanId).getDecision());

        // AUTO_APPROVE is final
        final LoanDecisionData done = service.status(loanId);
        assertEquals(List.of(CreditDecisionLoanData.BLOCKER_OUTCOME_FINAL), done.getBlockers());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(loanId));

        // nothing is sent once Due Diligence has been accepted
        stage(loanId, 1200);
        assertFalse(service.status(loanId).isSendStageOpen());
        assertTrue(service.status(loanId).isBlocksIcReview());
        assertFalse(service.status(loanId).isBlocksContract(), "AUTO_APPROVE goes from Due Diligence to the contract");
    }

    @Test
    void whatWasDoneToTheDecisionIsKeptWithWhoAndWhen() {
        profile("LIVE", "amount");
        connectEngine();
        final long loanId = applicant("A-1");
        actAs("loan.officer");
        service.setRequired(loanId, true);
        actAs("credit.analyst");
        service.verify(loanId, "Checked with the customer");
        engine.script.add(reply("{\"recommendation\":\"IC_REVIEW_REQUIRED\",\"score\":640,\"reasonCodes\":[\"R1\",\"R2\"]}"));
        service.send(loanId);
        service.unverify(loanId);
        service.setRequired(loanId, false);

        final List<CreditDecisionLoanData.EventData> history = service.history(loanId);
        assertEquals(List.of("UNFLAGGED", "REOPENED", "DECIDED", "VERIFIED", "FLAGGED"),
                history.stream().map(CreditDecisionLoanData.EventData::getEventType).collect(java.util.stream.Collectors.toList()));
        assertEquals("loan.officer", history.get(4).getActor());
        assertEquals("credit.analyst", history.get(3).getActor());
        assertEquals("Checked with the customer", history.get(3).getDetail());

        final CreditDecisionLoanData.EventData decided = history.get(2);
        assertEquals("IC_REVIEW_REQUIRED", decided.getDecision());
        assertEquals(0, new BigDecimal("640").compareTo(decided.getScore()));
        assertEquals("R1, R2", decided.getDetail());
        // the request and the reply of that send are in the decision log
        assertNotNull(decided.getLogId());
        assertEquals("IC_REVIEW_REQUIRED", jdbc.queryForObject("SELECT decision FROM m_credit_decision_log WHERE id = ?", String.class, decided.getLogId()));
        assertTrue(history.get(1).getDetail().contains("IC_REVIEW_REQUIRED"));
        assertNotNull(decided.getOccurredOn());
    }

    @Test
    void aFailedAndAHeldRequestAreKeptInTheHistoryToo() {
        final long loanId = readyApplication();
        engine.script.add(new java.io.IOException("connection refused"));
        service.send(loanId);
        assertEquals("FAILED", service.history(loanId).get(0).getEventType());
        assertTrue(service.history(loanId).get(0).getDetail().contains("connection refused"));
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
        jdbc.update("UPDATE m_transunion_crb_header SET request_no = ' TU-2026-778 '");
        jdbc.update("UPDATE m_transunion_crb_summary SET fc_all_sectors = 2");
        jdbc.update("INSERT INTO m_code_value (code_value) VALUES ('Rwanda')");
        final long rwanda = jdbc.queryForObject("SELECT id FROM m_code_value WHERE code_value = 'Rwanda'", Long.class);
        jdbc.update("INSERT INTO m_address (country_id) VALUES (NULL), (?)", rwanda);
        jdbc.update("INSERT INTO m_client_address (client_id, address_id, is_active) VALUES (?, 1, 1), (?, 2, 1)", clientId, clientId);
        jdbc.update("INSERT INTO m_business_detail (client_id, business_creation_date) VALUES (?, '2025-11-09'), (?, '2024-05-20')", clientId,
                clientId);
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
        assertEquals(RECENT_REPORT, values.get("bdate"));
        assertEquals("TU-2026-778", values.get("benq"));
        assertEquals("Yes", values.get("bfraud"));
        assertEquals("Rwanda", values.get("resid"), "the newest active address that names a country");
        assertEquals(29L, values.get("bizMonths"), "whole months from the earliest business start, 2024-05-20");
        assertEquals("Yes", values.get("ddDone"));
        @SuppressWarnings("unchecked")
        final Map<String, Object> diligence = (Map<String, Object>) values.get("ddRes");
        assertEquals("PASSED", diligence.get("result"));
        assertEquals(0, new BigDecimal("35000").compareTo((BigDecimal) diligence.get("recommendedAmount")));
        assertEquals(10L, diligence.get("recommendedTermMonths"));

        // nothing in the core system holds these, so they are always missing
        assertFalse(values.containsKey("channel"));
        assertFalse(values.containsKey("kyc"));
        assertFalse(values.containsKey("bbank"));
    }

    @Test
    void aFirstTimeCustomerHasNoHistoryRatherThanAPerfectOne() {
        final long current = applicant("A-9");
        final Map<String, Object> values = facts.resolve(current, List.of("cycles", "activeInt", "arrears", "prev", "wo", "dpd", "late", "bscore",
                "blist", "ddDone", "ddRes", "purpose", "sector", "benq", "bfraud", "resid", "bizMonths"), TODAY);

        assertEquals(0L, values.get("cycles"));
        assertEquals(0L, values.get("activeInt"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) values.get("arrears")));
        assertEquals("NONE", values.get("prev"));
        assertEquals("No", values.get("wo"));
        for (final String missing : List.of("dpd", "late", "bscore", "blist", "ddDone", "ddRes", "purpose", "sector", "benq", "bfraud", "resid",
                "bizMonths")) {
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

    @Test
    void aCleanBureauReportSaysNoFraudAndAnEntityIsTimedFromItsInBusinessSinceDate() {
        final long loanId = applicant("A-5");
        final long clientId = jdbc.queryForObject("SELECT client_id FROM m_loan WHERE id = ?", Long.class, loanId);
        bureau(loanId, "700", 0);
        jdbc.update("UPDATE m_transunion_crb_summary SET fc_all_sectors = 0");
        jdbc.update("INSERT INTO m_client_non_person (client_id, in_business_since) VALUES (?, '2026-05-09')", clientId);
        jdbc.update("INSERT INTO m_address (country_id) VALUES (NULL)");
        jdbc.update("INSERT INTO m_client_address (client_id, address_id, is_active) VALUES (?, 1, 1)", clientId);

        final Map<String, Object> values = facts.resolve(loanId, List.of("bfraud", "benq", "bizMonths", "resid"), TODAY);

        assertEquals("No", values.get("bfraud"));
        assertEquals(6L, values.get("bizMonths"));
        assertFalse(values.containsKey("benq"), "a report without a reference sends none");
        assertFalse(values.containsKey("resid"), "an address that names no country says nothing about residence");
    }

    // ---------------------------------------------------------------- one send at a time, and the request identifier

    private long claimedLoan() {
        final long loanId = readyApplication();
        jdbc.update("UPDATE m_loan_credit_decision SET send_started_on = ? WHERE loan_id = ?",
                java.sql.Timestamp.valueOf(org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfSystem()), loanId);
        return loanId;
    }

    @Test
    void aSecondSendIsRefusedWhileOneIsUnderWay() {
        final long loanId = claimedLoan();

        assertTrue(service.status(loanId).getBlockers().contains(CreditDecisionLoanData.BLOCKER_SEND_IN_PROGRESS));
        final GeneralPlatformDomainRuleException e = assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(loanId));
        assertEquals("error.msg.creditdecision.loan.send.in.progress", e.getGlobalisationMessageCode());
        assertTrue(engine.bodies.isEmpty());
        assertNotNull(jdbc.queryForObject("SELECT send_started_on FROM m_loan_credit_decision WHERE loan_id = ?", java.sql.Timestamp.class,
                loanId), "the send that is under way keeps its claim");
    }

    @Test
    void theClaimIsAtomicSoTwoSendsThatBothPassedTheChecksCannotBothRun() {
        final long loanId = readyApplication();
        // both have passed the checks, one of them claims first: the other finds the claim and is told so
        final int first = jdbc.update("UPDATE m_loan_credit_decision SET send_started_on = NOW() WHERE loan_id = ? "
                + "AND (send_started_on IS NULL OR send_started_on < NOW() - INTERVAL 5 MINUTE)", loanId);
        final int second = jdbc.update("UPDATE m_loan_credit_decision SET send_started_on = NOW() WHERE loan_id = ? "
                + "AND (send_started_on IS NULL OR send_started_on < NOW() - INTERVAL 5 MINUTE)", loanId);
        assertEquals(1, first);
        assertEquals(0, second);
    }

    @Test
    void aClaimLeftByASendThatDiedStopsHoldingAnythingBack() {
        final long loanId = readyApplication();
        jdbc.update("UPDATE m_loan_credit_decision SET send_started_on = ? WHERE loan_id = ?",
                java.sql.Timestamp.valueOf(org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfSystem().minusHours(1)), loanId);

        assertFalse(service.status(loanId).getBlockers().contains(CreditDecisionLoanData.BLOCKER_SEND_IN_PROGRESS));
        assertEquals(CreditDecisionLoanData.DECIDED, service.send(loanId).getRequestStatus());
    }

    @Test
    void theClaimIsReleasedWhateverBecomesOfTheSend() {
        final long loanId = readyApplication();
        engine.script.add(new IOException("connection refused"));
        service.send(loanId);
        assertNull(jdbc.queryForObject("SELECT send_started_on FROM m_loan_credit_decision WHERE loan_id = ?", java.sql.Timestamp.class,
                loanId));
        service.send(loanId);
        assertNull(jdbc.queryForObject("SELECT send_started_on FROM m_loan_credit_decision WHERE loan_id = ?", java.sql.Timestamp.class,
                loanId));

        // a send that is held back for missing details releases it too
        final long held = applicant("A-2"); // no bureau report, which the profile needs
        service.setRequired(held, true);
        service.verify(held, null);
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(held));
        assertNull(jdbc.queryForObject("SELECT send_started_on FROM m_loan_credit_decision WHERE loan_id = ?", java.sql.Timestamp.class, held));
    }

    @Test
    void aSendThatIsRunningIsNotJoinedByAnother() throws Exception {
        final long loanId = readyApplication();
        final java.util.concurrent.CountDownLatch inEngine = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final CreditDecisionLoanService slow = new CreditDecisionLoanService(jdbc, context, profiles, facts,
                (url, body, timeout, token) -> {
                    inEngine.countDown();
                    try {
                        release.await(20, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return reply("{\"recommendation\":\"AUTO_APPROVE\"}");
                }, published::add, CreditDecisionCircuitBreaker.disabled(), CreditDecisionBackoff.none());

        final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            final java.util.concurrent.Future<LoanDecisionData> running = pool.submit(() -> slow.send(loanId));
            assertTrue(inEngine.await(20, java.util.concurrent.TimeUnit.SECONDS));

            assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(loanId));
            assertTrue(engine.bodies.isEmpty(), "the second send never reached the engine");

            release.countDown();
            assertEquals(CreditDecisionLoanData.DECIDED, running.get(20, java.util.concurrent.TimeUnit.SECONDS).getRequestStatus());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void theRequestIdentifierIsKeptAndSharedWithTheLog() {
        final long loanId = readyApplication();
        final LoanDecisionData sent = service.send(loanId);

        assertNotNull(sent.getRequestId());
        assertTrue(sent.getRequestId().startsWith("req_"));
        assertTrue(sent.getRequestJson().contains("\"requestId\":\"" + sent.getRequestId() + "\""));
        assertEquals(sent.getRequestId(), jdbc.queryForObject("SELECT request_id FROM m_credit_decision_log", String.class));
    }

    @Test
    void aFailedSendIsAskedAgainWithTheSameIdentifierWhileNothingInItHasChanged() {
        final long loanId = readyApplication();
        engine.script.add(new IOException("connection refused"));
        final String first = service.send(loanId).getRequestId();
        service.send(loanId); // answered, the engine's default reply is usable

        assertEquals(2, engine.bodies.size());
        assertTrue(engine.bodies.get(1).contains(first), "the retry carries the identifier of the request that failed");
        assertEquals(first, service.status(loanId).getRequestId());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM m_credit_decision_log WHERE request_id = ?", Integer.class, first));
    }

    @Test
    void aRequestWhoseContentChangedGetsANewIdentifier() {
        final long loanId = readyApplication();
        engine.script.add(new IOException("connection refused"));
        final String first = service.send(loanId).getRequestId();
        jdbc.update("UPDATE m_loan SET principal_amount_proposed = 55000 WHERE id = ?", loanId);

        final String second = service.send(loanId).getRequestId();

        assertFalse(first.equals(second), "different details are a different request");
    }

    @Test
    void askingAgainAfterAnOutcomeIsANewRequest() {
        final long loanId = readyApplication();
        engine.script.add(reply("{\"recommendation\":\"IC_REVIEW_REQUIRED\"}"));
        final String first = service.send(loanId).getRequestId();
        final String second = service.send(loanId).getRequestId();

        assertFalse(first.equals(second));
    }

    // ---------------------------------------------------------------- failed scoring: retries, queue and alert

    private List<CreditDecisionScoringFailed> alerts() {
        final List<CreditDecisionScoringFailed> out = new ArrayList<>();
        for (final Object event : published) {
            if (event instanceof CreditDecisionScoringFailed) {
                out.add((CreditDecisionScoringFailed) event);
            }
        }
        return out;
    }

    private void fail(final long loanId) {
        engine.script.add(new IOException("connection refused"));
        service.send(loanId);
    }

    @Test
    void theFirstFailureIsRetriedLaterAndNobodyIsAlertedYet() {
        final long loanId = readyApplication();
        final java.time.LocalDateTime before = org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfSystem();

        fail(loanId);

        final LoanDecisionData status = service.status(loanId);
        assertEquals(1, status.getFailureCount());
        final java.time.LocalDateTime next = java.time.LocalDateTime.parse(status.getNextRetryOn());
        assertTrue(next.isAfter(before.plusMinutes(4)) && next.isBefore(before.plusMinutes(6)), "the first retry is in 5 minutes: " + next);
        assertNull(status.getAlertedOn());
        assertTrue(alerts().isEmpty());
    }

    @Test
    void theRetriesBackOffAndTheCreditTeamIsAlertedWhenTheyRunOut() {
        final long loanId = readyApplication();
        final int[] expectedMinutes = { 5, 15, 60, 240 };
        for (int failure = 1; failure <= 4; failure++) {
            fail(loanId);
            final java.sql.Timestamp attempted = jdbc.queryForObject("SELECT last_attempt_on FROM m_loan_credit_decision WHERE loan_id = ?",
                    java.sql.Timestamp.class, loanId);
            final java.sql.Timestamp next = jdbc.queryForObject("SELECT next_retry_on FROM m_loan_credit_decision WHERE loan_id = ?",
                    java.sql.Timestamp.class, loanId);
            assertEquals(expectedMinutes[failure - 1], java.time.Duration.between(attempted.toLocalDateTime(), next.toLocalDateTime()).toMinutes());
            assertTrue(alerts().isEmpty(), "no alert while there are retries left");
        }

        fail(loanId);

        final LoanDecisionData status = service.status(loanId);
        assertEquals(5, status.getFailureCount());
        assertNull(status.getNextRetryOn(), "nothing more is tried by itself");
        assertNotNull(status.getAlertedOn());
        assertEquals(1, alerts().size());
        assertEquals(loanId, (long) alerts().get(0).getLoanId());
        assertEquals("A-1", alerts().get(0).getApplicationRef());
        assertEquals(5, alerts().get(0).getFailures());
        assertTrue(alerts().get(0).isRetriesExhausted());
        assertTrue(alerts().get(0).getMessage().contains("could not be reached"));

        // a person can still send it, and the failures stay counted until it succeeds
        fail(loanId);
        assertEquals(6, service.status(loanId).getFailureCount());
        assertEquals(CreditDecisionLoanData.DECIDED, service.send(loanId).getRequestStatus());
        final LoanDecisionData decided = service.status(loanId);
        assertEquals(0, decided.getFailureCount());
        assertNull(decided.getNextRetryOn());
        assertNull(decided.getAlertedOn());
    }

    @Test
    void whenTheConnectionSaysAlertTheTeamIsToldAtOnceAndNothingIsRetried() {
        final long loanId = readyApplication();
        jdbc.update("UPDATE m_credit_decision_connection SET on_no_reply = 'ALERT' WHERE id = 1");

        fail(loanId);

        assertNull(service.status(loanId).getNextRetryOn());
        assertEquals(1, alerts().size());
        assertFalse(alerts().get(0).isRetriesExhausted());
        assertEquals(0, service.retryDue());
    }

    @Test
    void nextRetryFollowsTheBackOffAndEndsWithIt() {
        final java.sql.Timestamp from = java.sql.Timestamp.valueOf("2026-11-09 10:00:00");
        assertNull(CreditDecisionLoanService.nextRetry(from, 0));
        assertEquals(java.sql.Timestamp.valueOf("2026-11-09 10:05:00"), CreditDecisionLoanService.nextRetry(from, 1));
        assertEquals(java.sql.Timestamp.valueOf("2026-11-09 14:00:00"), CreditDecisionLoanService.nextRetry(from, 4));
        assertNull(CreditDecisionLoanService.nextRetry(from, 5));
    }

    @Test
    void theJobSendsOnlyWhatIsDueAndTheReplyClearsTheFailure() {
        final long loanId = readyApplication();
        fail(loanId);
        assertEquals(0, service.retryDue(), "the retry is not due yet");
        assertEquals(1, engine.bodies.size());

        jdbc.update("UPDATE m_loan_credit_decision SET next_retry_on = ? WHERE loan_id = ?",
                java.sql.Timestamp.valueOf(org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfSystem().minusMinutes(1)), loanId);
        assertEquals(1, service.retryDue());

        assertEquals(2, engine.bodies.size());
        final LoanDecisionData status = service.status(loanId);
        assertEquals(CreditDecisionLoanData.DECIDED, status.getRequestStatus());
        assertEquals(0, status.getFailureCount());
        assertEquals(0, service.retryDue(), "an application with an outcome is not picked up again");
    }

    @Test
    void theJobStopsAskingForAnApplicationThatCanNoLongerBeScored() {
        final long loanId = readyApplication();
        fail(loanId);
        jdbc.update("UPDATE m_loan_credit_decision SET next_retry_on = ? WHERE loan_id = ?",
                java.sql.Timestamp.valueOf(org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfSystem().minusMinutes(1)), loanId);
        jdbc.update("UPDATE m_loan SET loan_status_id = 200 WHERE id = ?", loanId); // approved meanwhile

        assertEquals(0, service.retryDue());

        assertNull(service.status(loanId).getNextRetryOn());
        assertTrue(service.history(loanId).get(0).getDetail().startsWith("Automatic retry stopped"));
        assertEquals(1, engine.bodies.size());
    }

    @Test
    void theJobLeavesAnApplicationAloneWhileASendIsUnderWay() {
        final long loanId = readyApplication();
        fail(loanId);
        jdbc.update("UPDATE m_loan_credit_decision SET next_retry_on = ?, send_started_on = ? WHERE loan_id = ?",
                java.sql.Timestamp.valueOf(org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfSystem().minusMinutes(1)),
                java.sql.Timestamp.valueOf(org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfSystem()), loanId);

        assertEquals(0, service.retryDue());

        assertNotNull(service.status(loanId).getNextRetryOn(), "it is tried again at the next turn");
    }

    @Test
    void theExceptionQueueListsWhatNeedsSomebodyOldestFirst() {
        profile("LIVE", "amount", "bscore");
        connectEngine();

        // failed
        final long failed = applicant("A-1");
        bureau(failed, "688", 0);
        service.setRequired(failed, true);
        service.verify(failed, null);
        fail(failed);
        // held, for the bureau score it has not got
        final long held = applicant("A-2");
        service.setRequired(held, true);
        service.verify(held, null);
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.send(held));
        // a send that began and never finished
        final long stuck = applicant("A-3");
        bureau(stuck, "688", 0);
        service.setRequired(stuck, true);
        service.verify(stuck, null);
        jdbc.update("UPDATE m_loan_credit_decision SET send_started_on = ? WHERE loan_id = ?",
                java.sql.Timestamp.valueOf(org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfSystem().minusHours(2)), stuck);
        // fine: decided, and not flagged
        final long decided = applicant("A-4");
        bureau(decided, "688", 0);
        service.setRequired(decided, true);
        service.verify(decided, null);
        service.send(decided);
        applicant("A-5");

        final List<CreditDecisionLoanData.ExceptionData> queue = service.exceptions();

        assertEquals(3, queue.size());
        final Map<Long, CreditDecisionLoanData.ExceptionData> byLoan = new java.util.HashMap<>();
        queue.forEach(x -> byLoan.put(x.getLoanId(), x));
        assertEquals(CreditDecisionLoanData.EXCEPTION_FAILED, byLoan.get(failed).getKind());
        assertEquals(1, byLoan.get(failed).getFailureCount());
        assertNotNull(byLoan.get(failed).getNextRetryOn());
        assertNotNull(byLoan.get(failed).getRequestId());
        assertTrue(byLoan.get(failed).getMessage().contains("could not be reached"));
        assertEquals("A-1", byLoan.get(failed).getApplicationRef());
        assertEquals("C-A-1", byLoan.get(failed).getCustomerRef());
        assertEquals(CreditDecisionLoanData.EXCEPTION_HELD, byLoan.get(held).getKind());
        assertTrue(byLoan.get(held).getMessage().contains("Bureau score"), byLoan.get(held).getMessage());
        assertEquals(CreditDecisionLoanData.EXCEPTION_STUCK, byLoan.get(stuck).getKind());
        assertEquals(stuck, queue.get(0).getLoanId(), "the send that began two hours ago is the oldest");

        // one that is approved meanwhile leaves the queue, and so does one whose details were completed and sent
        jdbc.update("UPDATE m_loan SET loan_status_id = 200 WHERE id = ?", failed);
        bureau(held, "640", 0);
        service.send(held);
        jdbc.update("UPDATE m_loan_credit_decision SET send_started_on = NULL WHERE loan_id = ?", stuck);
        assertEquals(0, service.exceptions().size());
    }

    @Test
    void reopeningTheVerificationClearsWhatWasKeptAboutTheFailures() {
        final long loanId = readyApplication();
        fail(loanId);
        service.unverify(loanId);

        final LoanDecisionData status = service.status(loanId);
        assertEquals(0, status.getFailureCount());
        assertNull(status.getNextRetryOn());
        assertNull(status.getRequestId());
        assertTrue(service.exceptions().isEmpty());
    }

    @Test
    void theRetryJobIsScheduledEveryFiveMinutes() {
        assertEquals("0 */5 * * * ?", jdbc.queryForObject("SELECT cron_expression FROM job WHERE name = 'Retry Failed Credit Decisions'",
                String.class));
        assertEquals(1, jdbc.queryForObject("SELECT is_active FROM job WHERE name = 'Retry Failed Credit Decisions'", Integer.class));
    }

    // ---------------------------------------------------------------- the alert

    @Test
    void theAlertGoesToEveryEnabledUserWhoCanSendARequest() {
        jdbc.update("INSERT INTO m_permission (code) VALUES ('ALL_FUNCTIONS'), ('READ_LOAN')");
        final long all = jdbc.queryForObject("SELECT id FROM m_permission WHERE code = 'ALL_FUNCTIONS'", Long.class);
        final long send = jdbc.queryForObject("SELECT id FROM m_permission WHERE code = 'SEND_CREDITDECISIONLOAN'", Long.class);
        final long read = jdbc.queryForObject("SELECT id FROM m_permission WHERE code = 'READ_LOAN'", Long.class);
        jdbc.update("INSERT INTO m_role (name) VALUES ('Admin'), ('Credit'), ('Reader')");
        final long admin = jdbc.queryForObject("SELECT id FROM m_role WHERE name = 'Admin'", Long.class);
        final long credit = jdbc.queryForObject("SELECT id FROM m_role WHERE name = 'Credit'", Long.class);
        final long reader = jdbc.queryForObject("SELECT id FROM m_role WHERE name = 'Reader'", Long.class);
        jdbc.update("INSERT INTO m_role_permission (role_id, permission_id) VALUES (?, ?), (?, ?), (?, ?)", admin, all, credit, send, reader, read);
        jdbc.update("INSERT INTO m_appuser (username, is_deleted, enabled) VALUES ('admin', 0, 1), ('analyst', 0, 1), ('reader', 0, 1), "
                + "('gone', 1, 1), ('off', 0, 0)");
        final Map<String, Long> users = new java.util.HashMap<>();
        for (final String name : List.of("admin", "analyst", "reader", "gone", "off")) {
            users.put(name, jdbc.queryForObject("SELECT id FROM m_appuser WHERE username = ?", Long.class, name));
        }
        jdbc.update("INSERT INTO m_appuser_role (appuser_id, role_id) VALUES (?, ?), (?, ?), (?, ?), (?, ?), (?, ?)", users.get("admin"), admin,
                users.get("analyst"), credit, users.get("reader"), reader, users.get("gone"), credit, users.get("off"), credit);
        final org.apache.fineract.notification.service.NotificationWritePlatformService notifications = mock(
                org.apache.fineract.notification.service.NotificationWritePlatformService.class);
        final CreditDecisionAlertListener listener = new CreditDecisionAlertListener(jdbc, notifications);

        listener.onScoringFailed(new CreditDecisionScoringFailed(77L, "A-77", "The decision engine could not be reached", 5, true));

        org.mockito.Mockito.verify(notifications).notify(org.mockito.ArgumentMatchers.eq(List.of(users.get("admin"), users.get("analyst"))),
                org.mockito.ArgumentMatchers.eq("CREDIT_DECISION"), org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq("SCORING_FAILED"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.contains("A-77 after 5 attempts and will not be retried automatically"),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void anAlertThatCannotBeDeliveredNeverBreaksTheSend() {
        final org.apache.fineract.notification.service.NotificationWritePlatformService notifications = mock(
                org.apache.fineract.notification.service.NotificationWritePlatformService.class);
        final CreditDecisionAlertListener listener = new CreditDecisionAlertListener(jdbc, notifications);

        // nobody holds the permission: only logged
        listener.onScoringFailed(new CreditDecisionScoringFailed(1L, "A-1", "x", 1, false));
        org.mockito.Mockito.verifyNoInteractions(notifications);

        // the notification service fails: swallowed
        jdbc.update("INSERT INTO m_permission (code) VALUES ('ALL_FUNCTIONS')");
        jdbc.update("INSERT INTO m_role (name) VALUES ('Admin2')");
        jdbc.update("INSERT INTO m_role_permission (role_id, permission_id) SELECT r.id, p.id FROM m_role r, m_permission p "
                + "WHERE r.name = 'Admin2' AND p.code = 'ALL_FUNCTIONS'");
        jdbc.update("INSERT INTO m_appuser (username) VALUES ('boss')");
        jdbc.update("INSERT INTO m_appuser_role (appuser_id, role_id) SELECT u.id, r.id FROM m_appuser u, m_role r "
                + "WHERE u.username = 'boss' AND r.name = 'Admin2'");
        when(notifications.notify(org.mockito.ArgumentMatchers.<java.util.Collection<Long>>any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenThrow(new IllegalStateException("down"));
        listener.onScoringFailed(new CreditDecisionScoringFailed(1L, "A-1", "x", 1, false));
    }
}
