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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.FileSystemResourceAccessor;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.DelegatedAuthorityData.EntryData;
import org.apache.fineract.portfolio.creditdecision.data.DelegatedAuthorityData.RuleData;
import org.apache.fineract.portfolio.creditdecision.data.DelegatedAuthorityData.RuleRequest;
import org.apache.fineract.portfolio.creditdecision.exception.DelegatedAuthorityNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The automatic approval and the delegated authority rules (CGLT-848), run against a throw-away MySQL schema with the
 * real migration and the real SQL. The loan approval itself is a stand-in that counts how often it is asked. Switched
 * off unless CREDIT_DECISION_TEST_JDBC_URL is set. See {@link CreditDecisionServiceDbTest} for how.
 */
@EnabledIfEnvironmentVariable(named = "CREDIT_DECISION_TEST_JDBC_URL", matches = ".+")
class CreditDecisionDelegatedAuthorityDbTest {

    private String url;
    private String user;
    private String password;
    private Connection connection;
    private JdbcTemplate jdbc;
        private CreditDecisionDelegatedAuthority authority;
    private PlatformSecurityContext context;

    /** The business date is read from the thread, as it is for a request. */
    private static void businessDate() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 11, 16))));
    }

    @BeforeEach
    void freshSchema() throws Exception {
        businessDate();
        url = System.getenv("CREDIT_DECISION_TEST_JDBC_URL");
        user = System.getenv().getOrDefault("CREDIT_DECISION_TEST_JDBC_USER", "root");
        password = System.getenv().getOrDefault("CREDIT_DECISION_TEST_JDBC_PASSWORD", "");
        connection = DriverManager.getConnection(url, user, password);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));

        // the switch is per connection, so the clean-up has to run on one connection
        final JdbcTemplate single = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        single.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (final String table : single.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class)) {
            single.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
        single.execute("SET FOREIGN_KEY_CHECKS = 1");

        jdbc.execute("CREATE TABLE m_product_loan (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), currency_code VARCHAR(3))");
        jdbc.execute("CREATE TABLE m_permission (id BIGINT AUTO_INCREMENT PRIMARY KEY, `grouping` VARCHAR(45), code VARCHAR(100) UNIQUE, "
                + "entity_name VARCHAR(100), action_name VARCHAR(100), can_maker_checker TINYINT(1))");
        jdbc.execute("CREATE TABLE m_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100))");
        jdbc.execute("CREATE TABLE m_role_permission (role_id BIGINT, permission_id BIGINT)");
        jdbc.execute("CREATE TABLE m_client (id BIGINT AUTO_INCREMENT PRIMARY KEY, account_no VARCHAR(20), status_enum INT)");
        jdbc.execute("CREATE TABLE m_loan (id BIGINT AUTO_INCREMENT PRIMARY KEY, account_no VARCHAR(20), client_id BIGINT, "
                + "product_id BIGINT, loan_status_id INT, loan_decision_state INT NULL, currency_code VARCHAR(3), "
                + "principal_amount_proposed DECIMAL(19,6), term_frequency INT)");
        jdbc.execute("CREATE TABLE m_loan_arrears_aging (loan_id BIGINT PRIMARY KEY, total_overdue_derived DECIMAL(19,6))");
        jdbc.execute("CREATE TABLE m_loan_credit_decision (id BIGINT AUTO_INCREMENT PRIMARY KEY, loan_id BIGINT, request_id VARCHAR(40))");
        jdbc.execute(
                "CREATE TABLE m_appuser (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), is_deleted TINYINT(1) DEFAULT 0)");
        jdbc.execute(
                "CREATE TABLE m_ic_review_level_config (id BIGINT AUTO_INCREMENT PRIMARY KEY, level_number INT, level_name VARCHAR(100), "
                        + "is_active TINYINT(1) DEFAULT 1)");
        jdbc.update("INSERT INTO m_ic_review_level_config (level_number, level_name, is_active) VALUES (1, 'IC Review Level One', 1), "
                + "(2, 'IC Review Level Two', 1), (3, 'IC Review Level Three', 1), (4, 'IC Review Level Four', 0)");
        jdbc.update("INSERT INTO m_role (name) VALUES ('Super user')");
        jdbc.update("INSERT INTO m_product_loan (name, currency_code) VALUES ('Business loan', 'KES')");

        final Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        final FileSystemResourceAccessor files = new FileSystemResourceAccessor(new File("src/main/resources/db/changelog/tenant/parts"));
        new Liquibase("CGLT-848_delegated_authority.xml", files, database).update("");

        context = mock(PlatformSecurityContext.class);
        final AppUser admin = mock(AppUser.class);
        when(admin.getUsername()).thenReturn("branch.admin");
        when(context.authenticatedUser()).thenReturn(admin);
        authority = new CreditDecisionDelegatedAuthority(jdbc, context);
    }

    @AfterEach
    void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    // ---------------------------------------------------------------- migration

    @Test
    void migrationAddsTheTableAndTheThreePermissionsForTheSuperUser() {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name = 'm_ic_delegated_authority'", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM m_permission", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM m_role_permission", Integer.class));
    }

    // ---------------------------------------------------------------- delegated authority

    private RuleRequest rule(final String currency, final String tier, final String min, final String max, final Integer level) {
        final RuleRequest request = new RuleRequest();
        request.setCurrency(currency);
        request.setRiskTier(tier);
        request.setMinAmount(min == null ? null : new BigDecimal(min));
        request.setMaxAmount(max == null ? null : new BigDecimal(max));
        request.setEntryLevelNumber(level);
        return request;
    }

    @Test
    void anApplicationNoRuleFitsStartsWhereItAlwaysDid() {
        assertNull(authority.resolve("KES", new BigDecimal("40000"), "LOW"));

        authority.create(rule("RWF", null, "0", "100000", 2));

        assertNull(authority.resolve("KES", new BigDecimal("40000"), "LOW"));
        assertNull(authority.resolve("RWF", new BigDecimal("500000"), "LOW"));
    }

    @Test
    void theRuleForTheCountryAmountAndRiskTierChoosesTheEntryLevel() {
        authority.create(rule("KES", null, "0", "50000", 2));
        authority.create(rule("KES", null, "50000.01", null, 3));

        assertEquals(2, authority.resolve("KES", new BigDecimal("40000"), null).getEntryLevelNumber());
        assertEquals(3, authority.resolve("kes", new BigDecimal("90000"), null).getEntryLevelNumber());
    }

    @Test
    void aRuleThatNamesTheRiskTierBeatsOneThatDoesNotAndTheCountryBeatsAnyCountry() {
        authority.create(rule(null, null, "0", null, 3));
        authority.create(rule("KES", null, "0", null, 2));
        final RuleData withTier = authority.create(rule("KES", "medium", "0", null, 1));

        final EntryData medium = authority.resolve("KES", new BigDecimal("1000"), "MEDIUM");
        assertEquals(1, medium.getEntryLevelNumber());
        assertEquals(withTier.getId(), medium.getRuleId());
        assertEquals(2, authority.resolve("KES", new BigDecimal("1000"), "HIGH").getEntryLevelNumber());
        assertEquals(3, authority.resolve("UGX", new BigDecimal("1000"), "HIGH").getEntryLevelNumber());
    }

    @Test
    void aSwitchedOffRuleIsIgnoredAndTheApproverIsPassedOn() {
        jdbc.update("INSERT INTO m_appuser (username) VALUES ('branch.manager')");
        final long manager = jdbc.queryForObject("SELECT id FROM m_appuser", Long.class);
        final RuleRequest request = rule("KES", null, "0", null, 3);
        request.setApproverUserId(manager);
        final RuleData created = authority.create(request);

        assertEquals(manager, authority.resolve("KES", new BigDecimal("10"), null).getApproverUserId());

        request.setActive(false);
        authority.update(created.getId(), request);
        assertNull(authority.resolve("KES", new BigDecimal("10"), null));
    }

    @Test
    void rulesAreChangedAndRemovedAndAnUnknownOneIsReported() {
        final RuleData created = authority.create(rule("KES", null, "0", null, 2));
        assertEquals("IC Review Level Two", created.getEntryLevelName());
        assertEquals("branch.admin", created.getUpdatedBy());

        final RuleData changed = authority.update(created.getId(), rule("KES", null, "0", "70000", 3));
        assertEquals(3, changed.getEntryLevelNumber());
        assertEquals(0, new BigDecimal("70000").compareTo(changed.getMaxAmount()));
        assertEquals(1, authority.rules().size());

        authority.delete(created.getId());
        assertTrue(authority.rules().isEmpty());
        assertThrows(DelegatedAuthorityNotFoundException.class, () -> authority.rule(created.getId()));
    }

    @Test
    void aRuleThatMakesNoSenseIsRefused() {
        assertThrows(PlatformApiDataValidationException.class, () -> authority.create(rule("KENYA", null, "0", null, 2)));
        assertThrows(PlatformApiDataValidationException.class, () -> authority.create(rule("KES", null, "100", "50", 2)));
        assertThrows(PlatformApiDataValidationException.class, () -> authority.create(rule("KES", null, "-1", null, 2)));
        assertThrows(PlatformApiDataValidationException.class, () -> authority.create(rule("KES", null, "0", null, 9)));
        assertThrows(PlatformApiDataValidationException.class, () -> authority.create(rule("KES", null, "0", null, 4)));
        assertThrows(PlatformApiDataValidationException.class, () -> authority.create(rule("KES", null, "0", null, null)));
        assertThrows(PlatformApiDataValidationException.class, () -> authority.create(withApprover(999L)));
    }

    private RuleRequest withApprover(final Long userId) {
        final RuleRequest request = rule("KES", null, "0", null, 2);
        request.setApproverUserId(userId);
        return request;
    }
}
