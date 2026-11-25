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
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "CGLT820_REPORT_DB_URL", matches = ".+")
class LoanPaymentDetailsReportStoryLoansDbTest {

    private static final Pattern PARAMETER = Pattern.compile("\\$\\{(\\w+)}");

    private static JdbcTemplate jdbc;
    private static String reportSql;

    @BeforeAll
    static void connect() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("CGLT820_REPORT_DB_URL"),
                System.getenv().getOrDefault("CGLT820_REPORT_DB_USER", "root"),
                System.getenv().getOrDefault("CGLT820_REPORT_DB_PASSWORD", "")));
        reportSql = LoanPaymentDetailsReportChangelogTest.effectiveReportSql();
    }

    @ParameterizedTest(name = "{0} txn {1} on {2} appears in the Loan Payment Details Report")
    @DisplayName("Story loans: every repayment the reporters flagged as missing is in the report")
    @CsvSource({ "LOAN/15866/2023, 2954481, 2025-09-05", "LOAN/16665/2024, 2136405, 2024-08-19", "000427057, 3371395, 2026-09-03",
            "000430449, 3387684, 2026-09-17" })
    void flaggedRepaymentIsReported(final String accountNo, final long transactionId, final String date) {
        final List<Map<String, Object>> rows = runReport(date, date);
        assertTrue(rows.stream().anyMatch(row -> transactionId == ((Number) row.get("Transaction ID")).longValue()),
                accountNo + " transaction " + transactionId + " is missing from the report run for " + date);
    }

    @ParameterizedTest(name = "group loan {0}: every repayment is reported")
    @DisplayName("Story (Ethiopia): group loans 402907-402912 are reported like individual loans")
    @ValueSource(longs = { 402907L, 402908L, 402909L, 402910L, 402911L, 402912L })
    void groupLoanRepaymentsAreReported(final long loanId) {
        final List<Map<String, Object>> expected = jdbc.queryForList(
                "SELECT id, transaction_date FROM m_loan_transaction WHERE loan_id = ? AND transaction_type_enum IN (2, 8)", loanId);
        assertTrue(!expected.isEmpty(), "Loan " + loanId + " has no repayments in this database");
        for (final Map<String, Object> transaction : expected) {
            final String date = transaction.get("transaction_date").toString();
            final long id = ((Number) transaction.get("id")).longValue();
            assertTrue(runReport(date, date).stream().anyMatch(row -> id == ((Number) row.get("Transaction ID")).longValue()),
                    "Group loan " + loanId + " repayment " + id + " on " + date + " is missing from the report");
        }
    }

    @Test
    @DisplayName("AC5: LOAN/15866/2023's top-up closure is classified as 'Top-up payment'")
    void topupClosureIsClassifiedAsTopUpPayment() {
        final Map<String, Object> row = runReport("2025-09-05", "2025-09-05").stream()
                .filter(candidate -> ((Number) candidate.get("Transaction ID")).longValue() == 2954481L).findFirst()
                .orElseGet(() -> fail("Transaction 2954481 is not in the report"));
        assertEquals(LoanPaymentDetailsReportChangelogTest.TOP_UP_PAYMENT, row.get("Payment Type"));
    }

    @Test
    @DisplayName("TC-01/AC3: in September 2026 every repayment and recovery is in the report, none is dropped")
    void everyRepaymentInAMonthIsReported() {
        final Integer ledger = jdbc.queryForObject("SELECT COUNT(*) FROM m_loan_transaction WHERE transaction_type_enum IN (2, 8) "
                + "AND transaction_date BETWEEN '2026-09-01' AND '2026-09-30'", Integer.class);
        final List<Map<String, Object>> report = runReport("2026-09-01", "2026-09-30");
        final long reportedRepayments = report.stream().map(row -> ((Number) row.get("Transaction ID")).longValue()).distinct().count();
        assertTrue(reportedRepayments >= ledger,
                "The ledger has " + ledger + " repayments in September 2026 but the report shows " + reportedRepayments);
    }

    private List<Map<String, Object>> runReport(final String startDate, final String endDate) {
        final Map<String, String> parameters = Map.of("startDate", startDate, "endDate", endDate, "officeId", "-1", "paymentTypeId", "-1",
                "currencyId", "-1");
        final Matcher matcher = PARAMETER.matcher(reportSql);
        final StringBuilder sql = new StringBuilder();
        while (matcher.find()) {
            final String value = parameters.get(matcher.group(1));
            if (value == null) {
                fail("The report uses an unexpected parameter ${" + matcher.group(1) + "}");
            }
            matcher.appendReplacement(sql, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sql);
        return jdbc.queryForList(sql.toString());
    }
}
