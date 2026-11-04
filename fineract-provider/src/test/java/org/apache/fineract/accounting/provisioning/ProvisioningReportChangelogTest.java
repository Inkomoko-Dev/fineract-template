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
package org.apache.fineract.accounting.provisioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ProvisioningReportChangelogTest {

    private static final String CHANGELOG = "src/main/resources/db/changelog/tenant/custom-changelog/CGLT-818_provisioning_report_overdue_columns.xml";

    private static final List<String> EXPECTED_COLUMNS = List.of("Office Name", "Product Name", "Category", "Client Name", "Loan ID",
            "Account No.", "Currency Code", "Days In Arrears", "Last Expected Repayment Date", "Provisioning Percentage",
            "Total Amount Overdue", "Provisioning Amount", "Transaction ID", "Liability Account", "Expense Account");

    private static String reportSql;
    private static String reportCountSql;

    @BeforeAll
    static void loadChangelog() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        final Document document = factory.newDocumentBuilder().parse(resolveSource(CHANGELOG).toFile());
        final NodeList columns = document.getElementsByTagName("column");
        for (int index = 0; index < columns.getLength(); index++) {
            final Element column = (Element) columns.item(index);
            if ("report_sql".equals(column.getAttribute("name"))) {
                reportSql = column.getTextContent();
            } else if ("report_count_sql".equals(column.getAttribute("name"))) {
                reportCountSql = column.getTextContent();
            }
        }
        assertNotNull(reportSql, "Changelog must set report_sql");
        assertNotNull(reportCountSql, "Changelog must set report_count_sql");
    }

    @Test
    void reportReturnsEveryColumnInTheAgreedOrder() {
        final String outerSelect = reportSql.substring(reportSql.lastIndexOf("\nSELECT"), reportSql.indexOf("\nFROM m_loanproduct_provisioning_entry m"));
        final Matcher alias = Pattern.compile("AS `([^`]+)`").matcher(outerSelect);
        final List<String> columns = new ArrayList<>();
        while (alias.find()) {
            columns.add(alias.group(1));
        }
        assertEquals(EXPECTED_COLUMNS, columns);
    }

    @Test
    void lastExpectedRepaymentDateIsTheLatestInstalmentDueOnOrBeforeTheCutOff() {
        assertTrue(reportSql.contains("MAX(rs.duedate) AS last_expected_repayment_date"));
        assertTrue(reportSql.contains("WHERE rs.duedate <= DATE('${endDate}')"));
    }

    @Test
    void totalAmountOverdueCountsOnlyInstalmentsPastDueAtTheCutOff() {
        assertTrue(reportSql.contains("SUM(IF(rs.duedate < DATE('${endDate}'),"));
        assertTrue(reportSql.contains("t.transaction_date <= DATE('${endDate}')"));
        assertTrue(reportSql.contains("ROUND(COALESCE(od.total_amount_overdue, 0), 2) AS `Total Amount Overdue`"));
    }

    @Test
    void totalAmountOverdueExcludesPaidWaivedAndWrittenOffAmounts() {
        assertTrue(reportSql.contains("JOIN m_loan_transaction_repayment_schedule_mapping mp ON mp.loan_transaction_id = live.id"));
        assertTrue(reportSql.contains("WHERE live.type = 9"));
        assertTrue(reportSql.contains("WHERE type = 6"));
        assertTrue(reportSql.contains("t.is_reversed = 0"));
    }

    @Test
    void countQueryUsesTheSameProvisioningRunAndOfficeFilter() {
        assertTrue(reportCountSql.contains("SELECT MAX(id)\n        FROM m_provisioning_history"));
        assertTrue(reportCountSql.contains("${officeId} IS NULL"));
        assertTrue(reportSql.contains("${officeId} IS NULL"));
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
