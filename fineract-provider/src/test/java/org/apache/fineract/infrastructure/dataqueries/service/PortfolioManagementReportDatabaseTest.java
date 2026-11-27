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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PortfolioManagementReportDatabaseTest {

    private static final String URL = System.getenv("PMR_TEST_JDBC_URL");

    private static String reportSql;
    private static Connection connection;

    private record Row(String status, BigDecimal excessBalance, BigDecimal excessAmountPaid, BigDecimal amountPastDue,
            int installmentsInArrears, int daysInArrears, BigDecimal emi, BigDecimal advance) {}

    @BeforeAll
    static void connect() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "PMR_TEST_JDBC_URL is not set");
        reportSql = PortfolioManagementReportSqlTest.column("report_sql");
        connection = DriverManager.getConnection(URL, System.getenv("PMR_TEST_JDBC_USER"), System.getenv("PMR_TEST_JDBC_PASSWORD"));
        connection.setAutoCommit(false);
    }

    @AfterAll
    static void close() throws SQLException {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    private static Row run(long loanId, String asOn, String... fixture) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (String sql : fixture) {
                st.execute(sql);
            }
            String sql = reportSql;
            for (Map.Entry<String, String> p : Map.of("asOn", asOn, "officeId", "1", "currentUserHierarchy", ".", "currentUserId", "1",
                    "fundId", "-1", "loanOfficerId", "-1", "loanProductId", "-1", "loanPurposeId", "-1", "currencyId", "-1", "loanStatusId",
                    "-1").entrySet()) {
                sql = sql.replace("${" + p.getKey() + "}", p.getValue());
            }
            sql = sql.replace("${limit}", "100000000").replace("${offset}", "0");
            final String anchor = "AND l.disbursedon_date <= DATE('" + asOn + "')";
            assertTrue(sql.contains(anchor));
            sql = sql.replace(anchor, "AND l.id = " + loanId + " " + anchor);
            try (ResultSet rs = st.executeQuery(sql)) {
                assertTrue(rs.next(), "loan " + loanId + " not in the report at " + asOn);
                return new Row(rs.getString("Loan Status"), rs.getBigDecimal("Excess Balance"), rs.getBigDecimal("Excess Amount Paid"),
                        rs.getBigDecimal("Amount Past Due"), rs.getInt("Installment in Arrears"), rs.getInt("Days in Arrears"),
                        rs.getBigDecimal("EMI"), rs.getBigDecimal("Advance Payment Balance"));
            }
        } finally {
            connection.rollback();
            try (Statement st = connection.createStatement()) {
                st.execute("DROP TEMPORARY TABLE IF EXISTS pmr_fixture");
            }
        }
    }

    private static String[] copyTransaction(String where, String changes) {
        return new String[] { "CREATE TEMPORARY TABLE pmr_fixture AS SELECT * FROM m_loan_transaction WHERE " + where + " LIMIT 1",
                "UPDATE pmr_fixture SET id = (SELECT MAX(id) + 1 FROM m_loan_transaction), external_id = NULL, " + changes,
                "INSERT INTO m_loan_transaction SELECT * FROM pmr_fixture" };
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("A deposit redraw after the as-at date is added back and the loan stays Overpaid")
    public void depositRedrawAfterAsAtRestoresOverpaid() throws SQLException {
        final Row row = run(403094, "2026-03-31");
        assertEquals("Overpaid", row.status());
        assertAmount("8000", row.excessBalance());
    }

    @Test
    @DisplayName("A deposit redraw on or before the as-at date plus a later withdrawal redraw stays Closed with no excess")
    public void earlierDepositRedrawPlusLaterWithdrawalStaysClosed() throws SQLException {
        final Row row = run(409598, "2025-08-20", copyTransaction("loan_id = 409598 AND transaction_type_enum = 27 AND is_reversed = 0",
                "transaction_type_enum = 26, transaction_date = '2025-09-01', amount = 5"));
        assertEquals("Closed", row.status());
        assertAmount("0", row.excessBalance());
    }

    @Test
    @DisplayName("Post-write-off excess that was redrawn is not written-off excess")
    public void redrawnWrittenOffExcessIsNotReported() throws SQLException {
        final Row row = run(403164, "2026-08-12");
        assertEquals("Written Off", row.status());
        assertAmount("0", row.excessBalance());
        assertAmount("0", row.excessAmountPaid());
    }

    @Test
    @DisplayName("Post-write-off excess that was not redrawn is reported as Written Off (Overpaid)")
    public void unredrawnWrittenOffExcessIsReported() throws SQLException {
        final Row row = run(403164, "2026-08-12",
                "UPDATE m_loan_transaction SET is_reversed = 1 WHERE loan_id = 403164 AND transaction_type_enum IN (26,27)");
        assertEquals("Written Off (Overpaid)", row.status());
        assertAmount("1000", row.excessBalance());
        assertAmount("1000", row.excessAmountPaid());
    }

    @Test
    @DisplayName("A partial refund after write-off reduces Excess Balance but not the gross Excess Amount Paid")
    public void partialRefundAfterWriteOff() throws SQLException {
        final String[] refund = copyTransaction("loan_id = 403164 AND transaction_type_enum = 27",
                "transaction_type_enum = 16, transaction_date = '2025-05-01', amount = 400, is_reversed = 0");
        final Row row = run(403164, "2026-08-12",
                "UPDATE m_loan_transaction SET is_reversed = 1 WHERE loan_id = 403164 AND transaction_type_enum IN (26,27)", refund[0],
                refund[1], refund[2]);
        assertEquals("Written Off (Overpaid)", row.status());
        assertAmount("600", row.excessBalance());
        assertAmount("1000", row.excessAmountPaid());
    }

    @Test
    @DisplayName("Loans that CBS shows without excess have no Excess Balance in the report")
    public void loansWithoutExcessInCbs() throws SQLException {
        assertAmount("0", run(425483, "2026-08-12").excessBalance());
        final Row writtenOff = run(394657, "2026-08-12");
        assertEquals("Written Off", writtenOff.status());
        assertAmount("0", writtenOff.excessBalance());
    }

    @Test
    @DisplayName("With a five-day allowance an instalment is in arrears only from the sixth day after its due date")
    public void arrearsAfterFiveDayAllowance() throws SQLException {
        for (String asOn : new String[] { "2026-05-22", "2026-05-27" }) {
            final Row row = run(417857, asOn);
            assertAmount("0", row.amountPastDue());
            assertEquals(0, row.installmentsInArrears(), asOn);
            assertEquals(0, row.daysInArrears(), asOn);
        }
        final Row overdue = run(417857, "2026-05-28");
        assertTrue(overdue.amountPastDue().signum() > 0);
        assertEquals(1, overdue.installmentsInArrears());
        assertTrue(overdue.daysInArrears() > 0);
    }

    @Test
    @DisplayName("Without an allowance an instalment due today is not in arrears and is from the next day")
    public void arrearsWithoutAllowance() throws SQLException {
        final Row dueToday = run(419642, "2026-05-24");
        assertAmount("0", dueToday.amountPastDue());
        assertEquals(0, dueToday.installmentsInArrears());
        assertEquals(0, dueToday.daysInArrears());
        final Row overdue = run(419642, "2026-05-25");
        assertTrue(overdue.amountPastDue().signum() > 0);
        assertEquals(1, overdue.installmentsInArrears());
        assertTrue(overdue.daysInArrears() > 0);
    }

    @Test
    @DisplayName("A bullet loan after empty grace instalments reports its real instalment as EMI")
    public void emiOfBulletLoanAfterGraceInstalments() throws SQLException {
        assertAmount("150000", run(421094, "2026-08-12").emi());
    }

    @Test
    @DisplayName("A loan that is closed holds no advance payment balance")
    public void closedLoanHoldsNoAdvance() throws SQLException {
        final Row row = run(388537, "2026-08-12");
        assertEquals("Closed", row.status());
        assertAmount("0", row.advance());
    }
}
