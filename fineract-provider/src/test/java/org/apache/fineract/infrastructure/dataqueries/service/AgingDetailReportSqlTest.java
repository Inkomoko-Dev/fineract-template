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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Testcontainers(disabledWithoutDocker = true)
class AgingDetailReportSqlTest {

    private static final String CHANGELOG = "src/main/resources/db/changelog/tenant/custom-changelog/CGLT-538_aging_detail_par_category.xml";
    private static final String BACKFILL_CHANGELOG = "src/main/resources/db/changelog/tenant/custom-changelog/CGLT-538_schedule_history_superseded_on_date.xml";
    private static final String AS_AT = "2026-03-31";
    private static final int REPAYMENT = 2;
    private static final int WRITEOFF = 6;
    private static final int REFUND_FOR_ACTIVE_LOAN = 18;
    private static final int GOODWILL_CREDIT = 23;
    private static final int PARTIAL_WRITEOFF = 31;
    private static final int RESIDUAL_BALANCE_ADJUSTMENT = 32;
    private static final int WRITEOFF_REVERSAL = 33;
    private static final int FUTURE_INTEREST_CANCELLATION = 34;
    private static final int ORIGINAL_SCHEDULE_PRODUCT = 2;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    private static JdbcTemplate jdbc;
    private static String reportSql;
    private static long nextTransactionId = 1;

    @BeforeAll
    static void createSchemaAndFixtures() throws Exception {
        final DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(),
                MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        reportSql = loadReportSql();
        createSchema();

        jdbc.update("INSERT INTO m_office VALUES (1, 'Head Office', '.')");
        jdbc.update("INSERT INTO m_currency VALUES ('RWF', 'RF')");
        jdbc.update("INSERT INTO m_product_loan_recalculation_details VALUES (?, 1)", ORIGINAL_SCHEDULE_PRODUCT);

        loan(1, 300);
        schedule(1, "2026-01-31");
        payment(1, REPAYMENT, "2026-01-31", 1000, 100, 11);
        payment(1, REPAYMENT, "2026-04-05", 1000, 100, 12);

        loan(2, 300);
        schedule(2, "2026-01-31");
        payment(2, REPAYMENT, "2026-02-28", 1000, 100, 22);
        payment(2, REPAYMENT, "2026-03-15", 1000, 100, 24);

        loan(3, 300);
        schedule(3, "2026-01-31");
        payment(3, REPAYMENT, "2026-02-28", 1000, 100, 32);
        payment(3, REPAYMENT, "2026-03-15", 500, 100, 34);

        loan(4, 300);
        schedule(4, "2026-01-31");
        payment(4, REPAYMENT, "2026-01-31", 1000, 100, 41);
        payment(4, GOODWILL_CREDIT, "2026-02-28", 1000, 100, 42);
        payment(4, REFUND_FOR_ACTIVE_LOAN, "2026-03-10", 1000, 100, 41);

        loan(5, 300);
        schedule(5, "2026-01-31");
        payment(5, REPAYMENT, "2026-02-28", 1000, 100, 52);
        transaction(5, PARTIAL_WRITEOFF, "2026-03-01", 1000, 1000, 0, false, null, null);
        transaction(5, PARTIAL_WRITEOFF, "2026-04-10", 100, 0, 100, false, null, null);
        jdbc.update(
                "UPDATE m_loan_repayment_schedule SET principal_writtenoff_derived = 1000, interest_writtenoff_derived = 100 WHERE id = 51");

        loan(6, 300);
        schedule(6, "2026-01-31");
        payment(6, REPAYMENT, "2026-02-28", 1000, 0, 62);
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_cancelled_derived = 100 WHERE id = 62");
        transaction(6, FUTURE_INTEREST_CANCELLATION, "2026-03-01", 100, 0, 100, false, null, null);

        loan(7, 300);
        schedule(7, "2026-01-31");
        payment(7, REPAYMENT, "2026-02-28", 1000, 0, 72);
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_cancelled_derived = 100 WHERE id = 72");
        transaction(7, FUTURE_INTEREST_CANCELLATION, "2026-04-10", 100, 0, 100, false, null, null);

        loan(8, 300);
        schedule(8, "2026-01-31");
        final long undoneWriteOff = transaction(8, WRITEOFF, "2026-02-15", 4400, 4000, 400, true, null, null);
        transaction(8, WRITEOFF_REVERSAL, "2026-04-10", 4400, 4000, 400, false, undoneWriteOff, null);

        loan(9, 300);
        schedule(9, "2026-01-31");
        transaction(9, WRITEOFF, "2026-02-15", 4400, 4000, 400, true, null, "2026-04-10 10:00:00");

        loan(10, 601);
        schedule(10, "2026-01-31");
        transaction(10, WRITEOFF, "2026-02-15", 4400, 4000, 400, false, null, null);

        loan(11, 300, ORIGINAL_SCHEDULE_PRODUCT);
        schedule(11, "2026-01-31");
        payment(11, REPAYMENT, "2026-01-31", 1000, 100, 111);
        payment(11, REPAYMENT, "2026-02-28", 500, 100, 112);
        history(11, 1, null, "2026-01-31", 1200, 100);
        history(11, 1, null, "2026-02-28", 1200, 100);
        history(11, 1, null, "2026-03-31", 1200, 100);

        loan(12, 300);
        schedule(12, "2025-09-30");

        loan(13, 300);
        schedule(13, "2026-01-31");
        payment(13, REPAYMENT, "2026-01-31", 1000, 100, 131);
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_cancelled_derived = 100 WHERE id = 132");
        replacedCancellation(13, "2026-03-10", 100, "2026-04-10", 100, "2026-04-10 09:00:00", "2026-04-10 09:00:00");

        loan(14, 300);
        schedule(14, "2026-01-31");
        payment(14, REPAYMENT, "2026-01-31", 1000, 100, 141);
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_cancelled_derived = 100 WHERE id = 142");
        replacedCancellation(14, "2026-03-10", 50, "2026-04-10", 100, "2026-04-10 09:00:00", "2026-04-10 09:00:00");

        loan(15, 300);
        schedule(15, "2026-01-31");
        payment(15, REPAYMENT, "2026-01-31", 1000, 100, 151);
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_cancelled_derived = 100 WHERE id = 152");
        transaction(15, FUTURE_INTEREST_CANCELLATION, "2026-03-10", 100, 0, 100, true, null, null);

        loan(19, 300);
        schedule(19, "2026-01-31");
        payment(19, REPAYMENT, "2026-01-31", 1000, 100, 191);
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_cancelled_derived = 100 WHERE id = 192");
        replacedCancellation(19, "2026-03-10", 100, "2026-04-10", 100, "2026-03-20 09:00:00", "2026-04-10 09:00:00");

        loan(16, 300);
        schedule(16, "2026-01-31");
        payment(16, REPAYMENT, "2026-01-31", 1000, 100, 161);
        payment(16, REPAYMENT, "2026-02-28", 999.6, 100, 162);
        payment(16, REPAYMENT, "2026-03-31", 1000, 100, 163);
        payment(16, REPAYMENT, "2026-04-30", 1000, 100, 164);
        transaction(16, RESIDUAL_BALANCE_ADJUSTMENT, "2026-05-02", 0.4, 0.4, 0, false, null, null);
        jdbc.update("UPDATE m_loan_repayment_schedule SET principal_writtenoff_derived = 0.4 WHERE id = 162");
        payment(16, REFUND_FOR_ACTIVE_LOAN, "2026-05-20", 100, 0, 164);

        loan(17, 300);
        schedule(17, "2026-04-30");
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_amount = 150 WHERE loan_id = 17 AND installment > 1");
        jdbc.update("UPDATE m_loan SET interest_charged_derived = 550 WHERE id = 17");
        payment(17, REPAYMENT, "2026-01-31", 1000, 100, 171);
        history(17, 1, "2026-04-10", "2026-01-31", 1000, 100);
        history(17, 1, "2026-04-10", "2026-02-28", 1000, 100);
        history(17, 1, "2026-04-10", "2026-03-31", 1000, 100);
        history(17, 1, "2026-04-10", "2026-04-30", 1000, 100);

        loan(18, 300);
        schedule(18, "2026-01-31");
        payment(18, REPAYMENT, "2026-01-31", 1000, 100, 181);
        history(18, 1, null, "2026-01-31", 4000, 100);

        loan(20, 300);
        schedule(20, "2026-01-31");
        payment(20, REPAYMENT, "2026-01-31", 1000, 100, 201);
        history(20, 1, "2026-03-15", "2026-01-31", 1000, 100);
        history(20, 1, "2026-03-15", "2026-02-28", 1000, 100);
        history(20, 1, "2026-03-15", "2026-03-31", 1000, 100);
        history(20, 1, "2026-03-15", "2026-04-30", 1000, 100);
        history(20, 2, "2026-04-10", "2026-01-31", 1000, 100);
        history(20, 2, "2026-04-10", "2026-04-30", 1000, 100);
        history(20, 2, "2026-04-10", "2026-05-31", 1000, 100);
        history(20, 2, "2026-04-10", "2026-06-30", 1000, 100);

        loan(21, 300);
        schedule(21, "2026-01-31");
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_amount = 150 WHERE loan_id = 21 AND installment > 1");
        jdbc.update("UPDATE m_loan SET interest_charged_derived = 550, principal_disbursed_derived = 5000 WHERE id = 21");
        jdbc.update("INSERT INTO m_loan_disbursement_detail VALUES (1, 21, 4000, '2025-08-31'), (2, 21, 1000, '2026-04-10')");
        payment(21, REPAYMENT, "2026-01-31", 1000, 100, 211);
        history(21, 1, "2026-04-10", "2026-01-31", 1000, 100);
        history(21, 1, "2026-04-10", "2026-02-28", 1000, 100);
        history(21, 1, "2026-04-10", "2026-03-31", 1000, 100);
        history(21, 1, "2026-04-10", "2026-04-30", 1000, 100);

        loan(22, 300);
        schedule(22, "2026-01-31");
        payment(22, REPAYMENT, "2026-01-31", 1000, 100, 221);
        payment(22, REPAYMENT, "2026-02-28", 1000, 99.7, 222);
        payment(22, REPAYMENT, "2026-03-31", 1000, 100, 223);
        payment(22, REPAYMENT, "2026-04-30", 1000, 100, 224);
        transaction(22, RESIDUAL_BALANCE_ADJUSTMENT, "2026-05-02", 0.3, 0, 0.3, false, null, null);
        payment(22, REFUND_FOR_ACTIVE_LOAN, "2026-05-20", 100, 0, 224);

        loan(23, 300);
        schedule(23, "2026-04-30");
        jdbc.update("UPDATE m_loan_repayment_schedule SET interest_amount = 150 WHERE loan_id = 23 AND installment > 1");
        jdbc.update("UPDATE m_loan SET interest_charged_derived = 550, rescheduledon_date = '2026-04-12' WHERE id = 23");
        payment(23, REPAYMENT, "2026-01-31", 1000, 100, 231);
        jdbc.update(
                "INSERT INTO m_loan_reschedule_request (id, loan_id, status_enum, submitted_on_date) VALUES (23, 23, 200, '2026-03-20')");
        jdbc.update(
                "INSERT INTO m_portfolio_command_source (entity_name, action_name, resource_id, command_as_json, maker_id, made_on_date,"
                        + " processing_result_enum) VALUES ('RESCHEDULELOAN', 'APPROVE', 23, ?, 5, '2026-04-12 09:00:00', 1)",
                "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"approvedOnDate\":\"10 April 2026\"}");
        for (final String due : new String[] { "2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30" }) {
            jdbc.update("INSERT INTO m_loan_repayment_schedule_history (loan_id, loan_reschedule_request_id, version, duedate,"
                    + " principal_amount, interest_amount, fee_charges_amount, penalty_charges_amount) VALUES (23, 23, 1, ?, 1000, 100, 0, 0)",
                    due);
        }
        loadChangelogSql(BACKFILL_CHANGELOG, "sql").forEach(jdbc::execute);

        jdbc.update("UPDATE m_loan SET closedon_date = '2026-02-15', writtenoffon_date = '2026-02-15' WHERE id IN (8, 9, 10)");
        jdbc.update("UPDATE m_loan ml SET interest_cancelled_derived = (SELECT SUM(COALESCE(rs.interest_cancelled_derived, 0))"
                + " FROM m_loan_repayment_schedule rs WHERE rs.loan_id = ml.id)");
    }

    @Test
    @DisplayName("AC1: a past date ages a loan from its oldest instalment still unpaid on that date")
    void ageIsMeasuredFromTheOldestInstalmentUnpaidOnTheAsAtDate() {
        final Map<String, Object> row = run(AS_AT, -1).get("L1");
        assertAging(row, 1000, 100, 31, "Watch");
        assertAmount(1000, row.get("Principal Paid"));
        assertAmount(100, row.get("Interest Paid"));
        assertAmount(4000, row.get("Original Principal"));
    }

    @Test
    @DisplayName("AC1: like the aging job, a loan is not aged when cash paid in advance covers what is overdue")
    void paidInAdvanceCoveringTheOverdueKeepsTheLoanOutOfArrears() {
        final Map<String, Map<String, Object>> rows = run(AS_AT, -1);
        assertFalse(rows.containsKey("L2"));
        assertAging(rows.get("L3"), 1000, 100, 59, "Watch");
    }

    @Test
    @DisplayName("AC1: a goodwill credit pays and an active-loan refund un-pays the instalments they are mapped to")
    void goodwillCreditAndActiveLoanRefundMoveTheSchedule() {
        final Map<String, Object> row = run(AS_AT, -1).get("L4");
        assertAging(row, 1000, 100, 59, "Watch");
        assertAmount(1000, row.get("Principal Paid"));
        assertAmount(100, row.get("Interest Paid"));
    }

    @Test
    @DisplayName("AC1: only partial write-offs dated on or before the As at date reduce what is overdue")
    void partialWriteOffsCountFromTheirOwnDate() {
        assertAging(run(AS_AT, -1).get("L5"), 0, 100, 59, "Watch");
    }

    @Test
    @DisplayName("AC1: cancelled interest stops being overdue from the date it was cancelled")
    void cancelledInterestCountsFromTheCancellationDate() {
        final Map<String, Map<String, Object>> rows = run(AS_AT, -1);
        assertAging(rows.get("L6"), 1000, 100, 59, "Watch");
        assertAging(rows.get("L7"), 1000, 200, 59, "Watch");
    }

    @Test
    @DisplayName("AC1: a loan is left out while it was written off and comes back once the write-off was undone")
    void undoneWriteOffIsExcludedOnlyWhileItWasInForce() {
        assertAging(run("2026-02-10", -1).get("L8"), 1000, 100, 10, "Normal");
        assertFalse(run(AS_AT, -1).containsKey("L8"));
        assertAging(run("2026-04-15", -1).get("L8"), 3000, 300, 74, "Watch");
    }

    @Test
    @DisplayName("AC1: an undo recorded before write-off reversals existed is dated by the reversed write-off")
    void legacyUndoIsDatedByTheReversedWriteOff() {
        assertFalse(run(AS_AT, -1).containsKey("L9"));
        assertTrue(run("2026-04-15", -1).containsKey("L9"));
    }

    @Test
    @DisplayName("AC1: a written-off loan is aged before its write-off and left out after it")
    void writtenOffLoanIsLeftOutFromItsWriteOffDate() {
        assertAging(run("2026-02-10", -1).get("L10"), 1000, 100, 10, "Normal");
        assertFalse(run(AS_AT, -1).containsKey("L10"));
    }

    @Test
    @DisplayName("AC1: products aged on the original schedule fill the original instalments with what was paid by the date")
    void originalScheduleProductsAreAgedOnTheOriginalSchedule() {
        assertAging(run(AS_AT, -1).get("L11"), 900, 0, 31, "Watch");
    }

    @Test
    @DisplayName("AC1: a cancellation counts while it was in force, even after it was re-dated onto a later settlement")
    void cancelledInterestFollowsTheCancellationInForceOnTheAsAtDate() {
        assertAging(run("2026-03-05", -1).get("L13"), 1000, 100, 5, "Normal");
        assertAging(run(AS_AT, -1).get("L13"), 1000, 0, 31, "Watch");
        assertAging(run("2026-04-15", -1).get("L13"), 2000, 100, 46, "Watch");
    }

    @Test
    @DisplayName("AC1: the cancellation in force on the As at date is subtracted, not the current schedule total")
    void onlyTheAmountCancelledByTheAsAtDateIsSubtracted() {
        assertAging(run(AS_AT, -1).get("L14"), 1000, 50, 31, "Watch");
    }

    @Test
    @DisplayName("AC1: a cancellation reversed by an undo, not replaced when it was reversed, does not reduce what is overdue")
    void reversedCancellationWithoutReplacementIsIgnored() {
        assertAging(run(AS_AT, -1).get("L15"), 1000, 100, 31, "Watch");
        assertAging(run(AS_AT, -1).get("L19"), 1000, 100, 31, "Watch");
        assertAging(run("2026-04-15", -1).get("L19"), 2000, 100, 46, "Watch");
    }

    @Test
    @DisplayName("AC1: a loan closed by a residual adjustment and reopened is aged again, with the residual settled from its date")
    void residualAdjustmentSettlesTheResidualAndDoesNotHideAReopenedLoan() {
        assertFalse(run("2026-05-10", -1).containsKey("L16"));
        assertAging(run("2026-05-25", -1).get("L16"), 100, 0, 25, "Normal");
    }

    @Test
    @DisplayName("AC1: a loan rescheduled after the As at date is aged on the schedule it had on that date")
    void loanRescheduledAfterTheAsAtDateIsAgedOnTheScheduleInForceThen() {
        final Map<String, Object> row = run(AS_AT, -1).get("L17");
        assertAging(row, 1000, 100, 31, "Watch");
        assertAmount(400, row.get("Original Interest"));
        assertAmount(1000, row.get("Principal Paid"));
        assertFalse(run("2026-05-05", -1).containsKey("L17"));
    }

    @Test
    @DisplayName("AC1: a reschedule submitted before the As at date but approved after it is dated from its approval")
    void rescheduleSubmittedBeforeButApprovedAfterTheAsAtDateIsAgedOnTheArchivedSchedule() {
        final Map<String, Object> row = run(AS_AT, -1).get("L23");
        assertAging(row, 1000, 100, 31, "Watch");
        assertAmount(400, row.get("Original Interest"));
        assertFalse(run("2026-04-11", -1).containsKey("L23"));
    }

    @Test
    @DisplayName("AC1: while an approved reschedule was later undone, the rescheduled schedule applies only between approval and undo")
    void undoneRescheduleAppliesOnlyBetweenApprovalAndUndo() {
        assertAging(run("2026-03-10", -1).get("L20"), 1000, 100, 10, "Normal");
        assertFalse(run(AS_AT, -1).containsKey("L20"));
        assertAging(run("2026-04-15", -1).get("L20"), 2000, 200, 46, "Watch");
    }

    @Test
    @DisplayName("AC1: a tranche disbursed after the As at date is left out of Original Principal and Original Interest")
    void laterTrancheIsLeftOutOfTheOriginalAmounts() {
        final Map<String, Object> row = run(AS_AT, -1).get("L21");
        assertAging(row, 1000, 100, 31, "Watch");
        assertAmount(4000, row.get("Original Principal"));
        assertAmount(400, row.get("Original Interest"));
    }

    @Test
    @DisplayName("AC1: a residual adjustment settles interest it waived as well as principal it wrote off")
    void residualAdjustmentSettlesWaivedInterest() {
        assertFalse(run("2026-05-10", -1).containsKey("L22"));
        assertAging(run("2026-05-25", -1).get("L22"), 100, 0, 25, "Normal");
    }

    @Test
    @DisplayName("AC1: an archive kept for interest recalculation, never superseded, does not replace the schedule")
    void archiveThatWasNeverSupersededIsIgnored() {
        final Map<String, Object> row = run(AS_AT, -1).get("L18");
        assertAging(row, 1000, 100, 31, "Watch");
        assertAmount(400, row.get("Original Interest"));
    }

    @Test
    @DisplayName("AC3: the PAR Category filter returns only loans in that band")
    void filterReturnsOnlyTheSelectedBand() {
        final Map<String, Map<String, Object>> all = run(AS_AT, -1);
        assertAging(all.get("L12"), 4000, 400, 182, "Doubtful");
        final Map<String, Map<String, Object>> watch = run(AS_AT, 2);
        assertFalse(watch.isEmpty());
        assertFalse(watch.containsKey("L12"));
        watch.values().forEach(row -> assertEquals("Watch", row.get("PAR Category")));
        final Map<String, Map<String, Object>> doubtful = run(AS_AT, 4);
        assertEquals(List.of("L12"), List.copyOf(doubtful.keySet()));
    }

    @Test
    @DisplayName("AC4: today reads the nightly aging table, as the report always has")
    void todayReadsTheNightlyAgingTable() {
        final LocalDate today = jdbc.queryForObject("SELECT CURDATE()", LocalDate.class);
        jdbc.update("INSERT INTO m_loan_arrears_aging VALUES (1, 777, 77, ?)", today.minusDays(40));
        try {
            final Map<String, Map<String, Object>> rows = run(today.toString(), -1);
            assertEquals(List.of("L1"), List.copyOf(rows.keySet()));
            assertAging(rows.get("L1"), 777, 77, 40, "Watch");
        } finally {
            jdbc.update("DELETE FROM m_loan_arrears_aging");
        }
    }

    private static Map<String, Map<String, Object>> run(final String asAt, final int parCategory) {
        final String sql = reportSql.replace("${asAtDate}", asAt).replace("${officeId}", "1").replace("${currentUserHierarchy}", ".")
                .replace("${parCategory}", Integer.toString(parCategory));
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (final Map<String, Object> row : jdbc.queryForList(sql)) {
            rows.put((String) row.get("Account Number"), row);
        }
        return rows;
    }

    private static void assertAging(final Map<String, Object> row, final double principal, final double interest, final long dpd,
            final String category) {
        assertNotNull(row, "loan missing from the report");
        assertAmount(principal, row.get("Principal Overdue"));
        assertAmount(interest, row.get("Interest Overdue"));
        assertEquals(dpd, ((Number) row.get("Days in Arrears")).longValue());
        assertEquals(category, row.get("PAR Category"));
    }

    private static void assertAmount(final double expected, final Object actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(new BigDecimal(actual.toString())),
                "expected " + expected + " got " + actual);
    }

    private static void loan(final long id, final int status) {
        loan(id, status, 1);
    }

    private static void loan(final long id, final int status, final long productId) {
        jdbc.update("INSERT INTO m_client VALUES (?, 1, ?, ?)", id, "C" + id, "Client " + id);
        jdbc.update(
                "INSERT INTO m_loan (id, client_id, product_id, account_no, loan_status_id, currency_code, disbursedon_date, principal_amount,"
                        + " principal_disbursed_derived, interest_charged_derived, principal_repaid_derived, interest_repaid_derived,"
                        + " grace_on_arrears_ageing) VALUES (?, ?, ?, ?, ?, 'RWF', '2025-08-31', 4000, 4000, 400, 0, 0, 0)",
                id, id, productId, "L" + id, status);
    }

    private static void schedule(final long loanId, final String firstDueDate) {
        LocalDate from = LocalDate.parse(firstDueDate).minusMonths(1);
        for (int installment = 1; installment <= 4; installment++) {
            final LocalDate due = LocalDate.parse(firstDueDate).plusMonths(installment - 1).withDayOfMonth(1).plusMonths(1).minusDays(1);
            jdbc.update(
                    "INSERT INTO m_loan_repayment_schedule (id, loan_id, installment, fromdate, duedate, principal_amount,"
                            + " interest_amount, fee_charges_amount, penalty_charges_amount) VALUES (?, ?, ?, ?, ?, 1000, 100, 0, 0)",
                    loanId * 10 + installment, loanId, installment, from, due);
            from = due;
        }
    }

    private static void replacedCancellation(final long loanId, final String firstDate, final double firstAmount, final String secondDate,
            final double secondAmount, final String reversedAt, final String secondCreatedAt) {
        final long first = transaction(loanId, FUTURE_INTEREST_CANCELLATION, firstDate, firstAmount, 0, firstAmount, true, null,
                reversedAt);
        final long second = transaction(loanId, FUTURE_INTEREST_CANCELLATION, secondDate, secondAmount, 0, secondAmount, false, null, null);
        jdbc.update("UPDATE m_loan_transaction SET created_on_utc = ? WHERE id = ?", secondCreatedAt, second);
        assertTrue(first < second);
    }

    private static void history(final long loanId, final int version, final String supersededOn, final String dueDate,
            final double principal, final double interest) {
        jdbc.update(
                "INSERT INTO m_loan_repayment_schedule_history (loan_id, version, superseded_on_date, duedate, principal_amount,"
                        + " interest_amount, fee_charges_amount, penalty_charges_amount) VALUES (?, ?, ?, ?, ?, ?, 0, 0)",
                loanId, version, supersededOn, dueDate, principal, interest);
    }

    private static void payment(final long loanId, final int type, final String date, final double principal, final double interest,
            final long scheduleId) {
        final long id = transaction(loanId, type, date, principal + interest, principal, interest, false, null, null);
        jdbc.update("INSERT INTO m_loan_transaction_repayment_schedule_mapping (loan_transaction_id, loan_repayment_schedule_id,"
                + " principal_portion_derived, interest_portion_derived) VALUES (?, ?, ?, ?)", id, scheduleId, principal, interest);
    }

    private static long transaction(final long loanId, final int type, final String date, final double amount, final double principal,
            final double interest, final boolean reversed, final Long originalTransactionId, final String lastModifiedOnUtc) {
        final long id = nextTransactionId++;
        jdbc.update("INSERT INTO m_loan_transaction (id, loan_id, transaction_type_enum, transaction_date, amount,"
                + " principal_portion_derived, interest_portion_derived, is_reversed, original_transaction_id, last_modified_on_utc)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, loanId, type, date, amount, principal, interest, reversed,
                originalTransactionId, lastModifiedOnUtc);
        return id;
    }

    private static void createSchema() {
        jdbc.execute("CREATE TABLE m_office (id BIGINT PRIMARY KEY, name VARCHAR(50), hierarchy VARCHAR(100))");
        jdbc.execute("CREATE TABLE m_currency (code VARCHAR(3) PRIMARY KEY, display_symbol VARCHAR(10))");
        jdbc.execute("CREATE TABLE m_client (id BIGINT PRIMARY KEY, office_id BIGINT, account_no VARCHAR(20), display_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE m_product_loan_recalculation_details (product_id BIGINT, arrears_based_on_original_schedule TINYINT)");
        jdbc.execute("CREATE TABLE m_loan (id BIGINT PRIMARY KEY, client_id BIGINT, product_id BIGINT, account_no VARCHAR(20),"
                + " loan_status_id INT, currency_code VARCHAR(3), disbursedon_date DATE, closedon_date DATE, writtenoffon_date DATE,"
                + " principal_amount DECIMAL(19,6), principal_disbursed_derived DECIMAL(19,6), interest_charged_derived DECIMAL(19,6),"
                + " principal_repaid_derived DECIMAL(19,6), interest_repaid_derived DECIMAL(19,6), interest_cancelled_derived DECIMAL(19,6),"
                + " grace_on_arrears_ageing INT, rescheduledon_date DATE)");
        jdbc.execute("CREATE TABLE m_loan_reschedule_request (id BIGINT PRIMARY KEY, loan_id BIGINT, status_enum INT,"
                + " submitted_on_date DATE, approved_on_date DATE, approved_by_user_id BIGINT)");
        jdbc.execute("CREATE TABLE m_portfolio_command_source (id BIGINT AUTO_INCREMENT PRIMARY KEY, entity_name VARCHAR(50),"
                + " action_name VARCHAR(50), resource_id BIGINT, command_as_json TEXT, maker_id BIGINT, made_on_date DATETIME,"
                + " processing_result_enum INT)");
        jdbc.execute("CREATE TABLE m_loan_transaction (id BIGINT PRIMARY KEY, loan_id BIGINT, transaction_type_enum INT,"
                + " transaction_date DATE, amount DECIMAL(19,6), principal_portion_derived DECIMAL(19,6),"
                + " interest_portion_derived DECIMAL(19,6), fee_charges_portion_derived DECIMAL(19,6),"
                + " penalty_charges_portion_derived DECIMAL(19,6), is_reversed TINYINT, original_transaction_id BIGINT,"
                + " created_on_utc DATETIME, last_modified_on_utc DATETIME, KEY (loan_id))");
        jdbc.execute("CREATE TABLE m_loan_repayment_schedule (id BIGINT PRIMARY KEY, loan_id BIGINT, installment INT, fromdate DATE,"
                + " duedate DATE, principal_amount DECIMAL(19,6), interest_amount DECIMAL(19,6), fee_charges_amount DECIMAL(19,6),"
                + " penalty_charges_amount DECIMAL(19,6), principal_writtenoff_derived DECIMAL(19,6),"
                + " interest_writtenoff_derived DECIMAL(19,6), fee_charges_writtenoff_derived DECIMAL(19,6),"
                + " penalty_charges_writtenoff_derived DECIMAL(19,6), interest_cancelled_derived DECIMAL(19,6), KEY (loan_id))");
        jdbc.execute("CREATE TABLE m_loan_transaction_repayment_schedule_mapping (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " loan_transaction_id BIGINT, loan_repayment_schedule_id BIGINT, principal_portion_derived DECIMAL(19,6),"
                + " interest_portion_derived DECIMAL(19,6), fee_charges_portion_derived DECIMAL(19,6),"
                + " penalty_charges_portion_derived DECIMAL(19,6), KEY (loan_transaction_id))");
        jdbc.execute("CREATE TABLE m_loan_charge (id BIGINT PRIMARY KEY, loan_id BIGINT, is_penalty TINYINT, is_active TINYINT,"
                + " due_for_collection_as_of_date DATE)");
        jdbc.execute("CREATE TABLE m_loan_charge_paid_by (id BIGINT PRIMARY KEY, loan_transaction_id BIGINT, loan_charge_id BIGINT,"
                + " amount DECIMAL(19,6))");
        jdbc.execute("CREATE TABLE m_loan_disbursement_detail (id BIGINT PRIMARY KEY, loan_id BIGINT, principal DECIMAL(19,6),"
                + " disbursedon_date DATE)");
        jdbc.execute("CREATE TABLE m_loan_repayment_schedule_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, loan_id BIGINT,"
                + " loan_reschedule_request_id BIGINT, version INT, superseded_on_date DATE, duedate DATE, principal_amount DECIMAL(19,6),"
                + " interest_amount DECIMAL(19,6), fee_charges_amount DECIMAL(19,6), penalty_charges_amount DECIMAL(19,6),"
                + " KEY (loan_id))");
        jdbc.execute("CREATE TABLE m_loan_arrears_aging (loan_id BIGINT PRIMARY KEY, principal_overdue_derived DECIMAL(19,6),"
                + " interest_overdue_derived DECIMAL(19,6), overdue_since_date_derived DATE)");
    }

    private static String loadReportSql() throws Exception {
        final Path changelog = changelogPath(CHANGELOG);
        final NodeList columns = parse(changelog).getElementsByTagName("column");
        for (int index = 0; index < columns.getLength(); index++) {
            final Element column = (Element) columns.item(index);
            if ("report_sql".equals(column.getAttribute("name"))) {
                return column.getTextContent();
            }
        }
        throw new IllegalStateException("report_sql not found in " + changelog);
    }

    private static List<String> loadChangelogSql(final String file, final String tag) throws Exception {
        final NodeList statements = parse(changelogPath(file)).getElementsByTagName(tag);
        final List<String> sql = new ArrayList<>();
        for (int index = 0; index < statements.getLength(); index++) {
            sql.add(statements.item(index).getTextContent());
        }
        return sql;
    }

    private static Path changelogPath(final String file) {
        final Path changelog = Path.of(file);
        return Files.exists(changelog) ? changelog : Path.of("fineract-provider", file);
    }

    private static Document parse(final Path changelog) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(changelog.toFile());
    }
}
