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
package org.apache.fineract.infrastructure.dataqueries.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.w3c.dom.NodeList;

@Testcontainers(disabledWithoutDocker = true)
class ScheduleHistorySupersededBackfillSqlTest {

    private static final String CHANGELOG = "src/main/resources/db/changelog/tenant/custom-changelog/CGLT-538_schedule_history_superseded_on_date.xml";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    private static JdbcTemplate jdbc;
    private static List<String> backfill;

    @BeforeAll
    static void createSchemaAndRunBackfill() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        backfill = loadBackfillSql();
        jdbc.execute("CREATE TABLE m_loan (id BIGINT PRIMARY KEY, rescheduledon_date DATE)");
        jdbc.execute("CREATE TABLE m_loan_reschedule_request (id BIGINT PRIMARY KEY, loan_id BIGINT, status_enum INT,"
                + " submitted_on_date DATE, approved_on_date DATE, approved_by_user_id BIGINT)");
        jdbc.execute("CREATE TABLE m_loan_repayment_schedule_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, loan_id BIGINT,"
                + " loan_reschedule_request_id BIGINT, version INT, duedate DATE, superseded_on_date DATE)");
        jdbc.execute("CREATE TABLE m_portfolio_command_source (id BIGINT AUTO_INCREMENT PRIMARY KEY, entity_name VARCHAR(50),"
                + " action_name VARCHAR(50), resource_id BIGINT, command_as_json TEXT, maker_id BIGINT, made_on_date DATETIME,"
                + " processing_result_enum INT)");

        request(1, 200, "2026-04-02");
        approval(1, "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"approvedOnDate\":\"3 April 2026\"}", 11, "2026-04-09 08:00:00");
        request(2, 200, "2026-05-04");
        approval(2, "{\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\",\"approvedOnDate\":\"2026-05-05\"}", 12, "2026-05-05 01:00:00");
        request(3, 200, "2026-06-01");
        request(4, 800, "2026-07-01");
        approval(4, "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"approvedOnDate\":\"02 July 2026\"}", 14, "2026-07-02 09:00:00");
        request(5, 100, "2026-08-01");
        request(6, 200, "2026-09-01");
        approval(6, "not json", 16, "2026-09-03 10:00:00");
        request(7, 200, "2026-10-01");
        jdbc.update("INSERT INTO m_portfolio_command_source (entity_name, action_name, resource_id, command_as_json, maker_id,"
                + " made_on_date, processing_result_enum) VALUES ('RESCHEDULELOAN', 'APPROVE', 7, '{}', 17, '2026-10-01 10:00:00', 0)");
        request(9, 9, 200, "2026-04-01");
        jdbc.update("INSERT INTO m_loan VALUES (9, '2026-04-20')");
        request(10, 10, 200, "2026-04-23");
        request(11, 10, 200, "2026-04-23");
        jdbc.update("INSERT INTO m_loan VALUES (10, '2026-04-24')");
        request(12, 12, 200, "2026-05-10");
        request(13, 12, 800, "2026-05-12");
        approval(13, "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"approvedOnDate\":\"12 May 2026\"}", 18, "2026-05-12 09:00:00");
        jdbc.update("INSERT INTO m_loan VALUES (12, '2026-05-30')");
        request(14, 14, 200, "2026-06-10");
        jdbc.update("INSERT INTO m_loan VALUES (14, '2026-06-01')");
        for (long id = 1; id <= 7; id++) {
            jdbc.update("INSERT INTO m_loan_repayment_schedule_history (loan_id, loan_reschedule_request_id, version, duedate)"
                    + " VALUES (?, ?, 1, '2026-01-31')", id, id);
        }
        jdbc.update("INSERT INTO m_loan_repayment_schedule_history (loan_id, loan_reschedule_request_id, version, duedate)"
                + " VALUES (8, NULL, 1, '2026-01-31')");

        backfill.forEach(jdbc::execute);
    }

    @Test
    void approvalDateIsTheDateEnteredOnTheApprovalNotWhenItWasKeyed() {
        assertApproved(1, "2026-04-03", 11L);
        assertApproved(2, "2026-05-05", 12L);
    }

    @Test
    void undoneReschedulesKeepTheDateTheyWereApproved() {
        assertApproved(4, "2026-07-02", 14L);
    }

    @Test
    void approvalWithoutAReadableDateFallsBackToWhenItWasKeyed() {
        assertApproved(6, "2026-09-03", 16L);
    }

    @Test
    void approvalWithoutASuccessfulCommandFallsBackToTheSubmissionDate() {
        assertApproved(3, "2026-06-01", null);
        assertApproved(7, "2026-10-01", null);
    }

    @Test
    void latestApprovalWithoutACommandIsDatedByTheLoanRescheduleDate() {
        assertApproved(9, "2026-04-20", null);
        assertApproved(11, "2026-04-24", null);
    }

    @Test
    void earlierOrStaleApprovalsWithoutACommandKeepTheSubmissionDate() {
        assertApproved(10, "2026-04-23", null);
        assertApproved(12, "2026-05-10", null);
        assertApproved(14, "2026-06-10", null);
    }

    @Test
    void requestsThatWereNeverApprovedStayUndated() {
        assertNull(approvedOn(5));
        assertNull(supersededOn(5));
    }

    @Test
    void archivedSchedulesAreSupersededOnTheirApprovalDate() {
        assertEquals(LocalDate.parse("2026-04-03"), supersededOn(1));
        assertEquals(LocalDate.parse("2026-07-02"), supersededOn(4));
        assertEquals(LocalDate.parse("2026-06-01"), supersededOn(3));
        assertNull(
                jdbc.queryForObject("SELECT superseded_on_date FROM m_loan_repayment_schedule_history WHERE loan_id = 8", LocalDate.class));
    }

    @Test
    void runningTheBackfillAgainChangesNothing() {
        final String before = snapshot();
        backfill.forEach(jdbc::execute);
        assertEquals(before, snapshot());
    }

    private static void assertApproved(final long id, final String date, final Long userId) {
        assertEquals(LocalDate.parse(date), approvedOn(id));
        assertEquals(userId, jdbc.queryForObject("SELECT approved_by_user_id FROM m_loan_reschedule_request WHERE id = ?", Long.class, id));
    }

    private static LocalDate approvedOn(final long id) {
        return jdbc.queryForObject("SELECT approved_on_date FROM m_loan_reschedule_request WHERE id = ?", LocalDate.class, id);
    }

    private static LocalDate supersededOn(final long requestId) {
        return jdbc.queryForObject("SELECT superseded_on_date FROM m_loan_repayment_schedule_history WHERE loan_reschedule_request_id = ?",
                LocalDate.class, requestId);
    }

    private static String snapshot() {
        return jdbc.queryForList("SELECT id, approved_on_date, approved_by_user_id FROM m_loan_reschedule_request ORDER BY id").toString()
                + jdbc.queryForList("SELECT id, superseded_on_date FROM m_loan_repayment_schedule_history ORDER BY id").toString();
    }

    private static void request(final long id, final int status, final String submittedOn) {
        request(id, id, status, submittedOn);
    }

    private static void request(final long id, final long loanId, final int status, final String submittedOn) {
        jdbc.update("INSERT INTO m_loan_reschedule_request (id, loan_id, status_enum, submitted_on_date) VALUES (?, ?, ?, ?)", id, loanId,
                status, submittedOn);
    }

    private static void approval(final long requestId, final String json, final long makerId, final String madeOn) {
        jdbc.update(
                "INSERT INTO m_portfolio_command_source (entity_name, action_name, resource_id, command_as_json, maker_id,"
                        + " made_on_date, processing_result_enum) VALUES ('RESCHEDULELOAN', 'APPROVE', ?, ?, ?, ?, 1)",
                requestId, json, makerId, madeOn);
    }

    private static List<String> loadBackfillSql() throws Exception {
        Path changelog = Path.of(CHANGELOG);
        if (!Files.exists(changelog)) {
            changelog = Path.of("fineract-provider", CHANGELOG);
        }
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        final NodeList statements = factory.newDocumentBuilder().parse(changelog.toFile()).getElementsByTagName("sql");
        final List<String> sql = new ArrayList<>();
        for (int index = 0; index < statements.getLength(); index++) {
            sql.add(statements.item(index).getTextContent());
        }
        return sql;
    }
}
