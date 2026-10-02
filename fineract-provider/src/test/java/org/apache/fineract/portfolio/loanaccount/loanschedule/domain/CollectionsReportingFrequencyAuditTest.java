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
package org.apache.fineract.portfolio.loanaccount.loanschedule.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Audit that collections / Payment Due Today (T+X) SQL filters on installment due dates and calendar days,
 * not on a monthly repayment assumption. Covers Payment Due / T+X report SQL and arrears aging.
 */
class CollectionsReportingFrequencyAuditTest {

    private static final String LOAN_PAYMENTS_DUE_CHANGELOG = "src/main/resources/db/changelog/tenant/custom-changelog/CGLT-539-8-fix-written-off-loans-in-loan-payments-due.xml";
    private static final String PAYMENT_DUE_TODAY_TPLUSX_CHANGELOG = "src/main/resources/db/changelog/tenant/custom-changelog/CGLT-431_add_language_parameter.xml";

    @Test
    void loanPaymentsDueReportUsesInstallmentDueDatesAndDayGrace() throws IOException {
        final String sql = readReportSql(LOAN_PAYMENTS_DUE_CHANGELOG);

        assertTrue(sql.contains("ls.duedate"), "Report must use installment due dates from m_loan_repayment_schedule");
        assertTrue(sql.contains("INTERVAL ${gracePeriod} DAY"), "Grace / T+X window must be expressed in days, not months");
        assertTrue(sql.contains("DATEDIFF('${asAtDate}', ls.duedate)"), "Days past due must be derived from installment due date");
        assertNoMonthlyPeriodicityAssumptions(sql);
    }

    @Test
    void paymentDueTodayTPlusXUsesInstallmentDueDateDayOffset() throws IOException {
        final String sql = Files.readString(resolveSource(PAYMENT_DUE_TODAY_TPLUSX_CHANGELOG), StandardCharsets.UTF_8);

        assertTrue(sql.contains("ls.duedate"), "T+X report must key off installment due dates");
        assertTrue(sql.contains("DATEDIFF(ls.duedate, CURDATE()) = ${fromX}"),
                "T+X window must be a calendar-day offset from due date, not a month step");
        assertNoMonthlyPeriodicityAssumptions(sql);
    }

    @Test
    void arrearsAgingUsesEarliestUnpaidInstallmentDueDate() throws IOException {
        final Path sourcePath = resolveSource(
                "src/main/java/org/apache/fineract/portfolio/loanaccount/service/LoanArrearsAgingServiceImpl.java");
        final String source = Files.readString(sourcePath, StandardCharsets.UTF_8);

        assertTrue(source.contains("MIN(mr.duedate) as overdue_since_date_derived"));
        assertTrue(source.contains("mr.duedate"));
        assertNoMonthlyPeriodicityAssumptions(source);
        assertFalse(source.contains("repay_every = 1"));
    }

    private static void assertNoMonthlyPeriodicityAssumptions(final String sql) {
        assertFalse(sql.toLowerCase().contains("interval 1 month"), "Must not assume monthly periodicity");
        assertFalse(sql.contains("repay_every"), "Must not filter or derive dues from repay_every");
        assertFalse(sql.contains("number_of_repayments"), "Must not assume installments equal term-in-months");
    }

    private static String readReportSql(final String relativeToProvider) throws IOException {
        final String xml = Files.readString(resolveSource(relativeToProvider), StandardCharsets.UTF_8);
        final String marker = "report_sql\" value=\"";
        final int start = xml.indexOf(marker);
        assertTrue(start >= 0, "Expected report_sql column in " + relativeToProvider);
        final int valueStart = start + marker.length();
        final int valueEnd = xml.indexOf("\"", valueStart);
        return xml.substring(valueStart, valueEnd).replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")
                .replace("&quot;", "\"");
    }

    private static Path resolveSource(final String relativeToProvider) {
        final Path fromModule = Path.of(relativeToProvider);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        final Path fromRepoRoot = Path.of("fineract-provider", relativeToProvider);
        assertTrue(Files.exists(fromRepoRoot), "Could not find " + relativeToProvider);
        return fromRepoRoot;
    }
}
