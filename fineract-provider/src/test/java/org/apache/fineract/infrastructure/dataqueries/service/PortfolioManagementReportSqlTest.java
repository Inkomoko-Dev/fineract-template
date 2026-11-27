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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PortfolioManagementReportSqlTest {

    static final String CHANGELOG = "db/changelog/tenant/custom-changelog/CGLT-739_portfolio_management_report.xml";

    private static final String AS_AT = "t.transaction_date < DATE('${asOn}') + INTERVAL 1 DAY";
    private static final String AFTER_AS_AT = "t.transaction_date >= DATE('${asOn}') + INTERVAL 1 DAY";
    private static final String CAPPED_EXCESS = "LEAST(GREATEST(IFNULL(b.total_overpaid_derived,0) - IFNULL(pc.ovp_adj,0) + pc.rd_adj, 0), "
            + "GREATEST(IFNULL(op.ovp_pre,0), 0))";

    private static String reportSql;
    private static String countSql;

    @BeforeAll
    static void load() throws IOException {
        reportSql = column("report_sql");
        countSql = column("report_count_sql");
    }

    static String column(String name) throws IOException {
        final Matcher m = Pattern.compile("<column name=\"" + name + "\"><!\\[CDATA\\[(.*?)\\]\\]></column>", Pattern.DOTALL)
                .matcher(changelog());
        assertTrue(m.find(), name + " not found in " + CHANGELOG);
        return m.group(1);
    }

    private static String changelog() throws IOException {
        final Path source = Path.of("src/main/resources", CHANGELOG);
        if (Files.exists(source)) {
            return Files.readString(source, StandardCharsets.UTF_8);
        }
        try (InputStream in = PortfolioManagementReportSqlTest.class.getClassLoader().getResourceAsStream(CHANGELOG)) {
            assertNotNull(in, CHANGELOG + " not found");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String cte(String sql, String name) {
        final Matcher m = Pattern.compile("(?m)^" + name + " AS \\(").matcher(sql);
        assertTrue(m.find(), "CTE " + name + " not found");
        int depth = 1;
        int i = m.end();
        while (depth > 0) {
            final char c = sql.charAt(i++);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }
        return sql.substring(m.end(), i - 1);
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    @DisplayName("Excess held at the as-at date nets refunds and both redraw types booked on or before that date")
    public void historicalExcessCapNetsRefundsAndRedraws() {
        for (String body : new String[] { cte(reportSql, "ovp_pre"), cte(reportSql, "ovp_pre_c"), cte(countSql, "ovp_pre_c") }) {
            assertTrue(body.contains("t.transaction_type_enum IN (16,18,20,26,27) THEN IFNULL(t.amount,0)"), body);
            assertTrue(body.contains("t.is_reversed = 0"), body);
            assertTrue(body.contains(AS_AT), body);
        }
    }

    @Test
    @DisplayName("Redraws after the as-at date are added back separately and never double counted with refunds")
    public void postAsAtRedrawsAreAddedBack() {
        for (String body : new String[] { cte(reportSql, "tx_asat"), cte(reportSql, "txa"), cte(countSql, "tx_asat") }) {
            assertTrue(body.contains("t.transaction_type_enum IN (26,27) THEN IFNULL(t.amount,0) ELSE 0 END) AS rd_adj"), body);
            assertTrue(body.contains("t.transaction_type_enum IN (16,18,20) THEN IFNULL(t.amount,0) ELSE 0 END) AS ovp_adj"), body);
            assertTrue(body.contains(AFTER_AS_AT), body);
        }
    }

    @Test
    @DisplayName("Status and Excess Balance cap the added-back redraws at the excess held on the as-at date")
    public void excessIsCappedWherePostAsAtRedrawsExist() {
        assertEquals(4, occurrences(reportSql, CAPPED_EXCESS));
        assertEquals(1, occurrences(countSql, CAPPED_EXCESS));
    }

    @Test
    @DisplayName("Written-off excess nets refunds and redraws booked since the write-off, up to the as-at date")
    public void writtenOffExcessNetsRefundsAndRedraws() {
        final String txb = cte(reportSql, "txb");
        assertTrue(txb.contains("t.transaction_type_enum IN (16,18,20,26,27) AND t.transaction_date >= l.writtenoffon_date"), txb);
        assertTrue(txb.contains(AS_AT), txb);
        assertTrue(reportSql.contains("GREATEST(IFNULL(pc.wo_excess,0), 0)) AS wo_ovp_bal"));
    }

    @Test
    @DisplayName("Excess Amount Paid is the gross amount ever overpaid for Overpaid and Written Off (Overpaid) loans")
    public void excessAmountPaidIsGross() {
        assertTrue(reportSql.contains("CASE WHEN a.st_id = 700 THEN GREATEST(IFNULL(tx.excess_paid, 0), a.ovp_bal)\n"
                + "         WHEN a.st_id = 601 AND a.wo_ovp_bal > 0.005 THEN GREATEST(IFNULL(tx.excess_paid, 0), a.wo_ovp_bal)\n"
                + "         ELSE 0 END AS 'Excess Amount Paid'"));
        assertTrue(reportSql.contains("WHEN a.st_id = 601 AND a.wo_ovp_bal > 0.005 THEN a.wo_ovp_bal\n"
                + "         ELSE 0 END AS 'Excess Balance'"));
    }

    @Test
    @DisplayName("Arrears start only after the loan or product allowance, an unset allowance is zero days, and due today is not overdue")
    public void arrearsStartStrictlyAfterTheAllowance() {
        assertTrue(reportSql.contains("COALESCE(l.grace_on_arrears_ageing, (SELECT pg.grace_on_arrears_ageing FROM m_product_loan pg "
                + "WHERE pg.id = l.product_id), 0) AS arrears_grace"));
        assertEquals(7, occurrences(reportSql, "duedate < DATE('${asOn}') - INTERVAL grace DAY AND unpaid"));
        assertFalse(reportSql.contains("duedate <= DATE('${asOn}')"));
        assertFalse(reportSql.contains("INTERVAL 5 DAY"));
    }

    @Test
    @DisplayName("EMI is the most common instalment among instalments that have something due")
    public void emiIgnoresEmptyInstalments() {
        assertTrue(cte(reportSql, "emi").contains("FROM schx WHERE emi_val > 0 GROUP BY loan_id, emi_val"));
    }

    @Test
    @DisplayName("Advance Payment Balance is held only by active loans")
    public void advanceOnlyOnActiveLoans() {
        assertTrue(reportSql.contains("CASE WHEN a.st_id = 300 THEN IFNULL(sagg.advance_bal, 0) ELSE 0 END AS 'Advance Payment Balance'"));
    }

    @Test
    @DisplayName("Status labels follow the product wording")
    public void statusLabels() {
        assertTrue(reportSql.contains("'Written Off (Overpaid)'"));
        assertFalse(reportSql.contains("Active/In Arrears"));
    }

    @Test
    @DisplayName("The count query classifies loans exactly as the detail query does")
    public void countQueryMatchesDetailQuery() {
        for (String name : new String[] { "scope_loan", "tx_asat", "ovp_pre_c", "cand" }) {
            assertEquals(cte(reportSql, name), cte(countSql, name), name);
        }
    }
}
