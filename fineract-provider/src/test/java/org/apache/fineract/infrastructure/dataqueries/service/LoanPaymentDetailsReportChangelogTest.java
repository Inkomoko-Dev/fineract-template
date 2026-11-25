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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class LoanPaymentDetailsReportChangelogTest {

    static final String CUSTOM_CHANGELOG = "src/main/resources/db/changelog/tenant/custom-changelog";
    static final String REPORT_NAME = "Loan Payment Details Report";
    static final String TOP_UP_PAYMENT = "Top-up payment";

    private static final Pattern PAYMENT_DETAIL_JOIN = Pattern.compile("(LEFT\\s+(?:OUTER\\s+)?)?JOIN\\s+m_payment_detail\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLIENT_JOIN = Pattern.compile("(LEFT\\s+(?:OUTER\\s+)?)?JOIN\\s+m_client\\s+mc\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSACTION_TYPES = Pattern
            .compile("transaction_type_enum\\s+IN\\s*\\(([^)]*)\\)|transaction_type_enum\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static String effectiveSql;
    private static String effectiveFile;
    private static List<String> paymentTypeSeeds;

    @BeforeAll
    static void loadEffectiveReport() throws Exception {
        final List<Path> files;
        try (Stream<Path> listing = Files.list(changelogDirectory())) {
            files = listing.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        }
        paymentTypeSeeds = new ArrayList<>();
        for (final Path file : files) {
            final Document document = parse(file);
            final NodeList updates = document.getElementsByTagName("update");
            for (int index = 0; index < updates.getLength(); index++) {
                final Element update = (Element) updates.item(index);
                if (!"stretchy_report".equals(update.getAttribute("tableName")) || !update.getTextContent().contains(REPORT_NAME)) {
                    continue;
                }
                final NodeList columns = update.getElementsByTagName("column");
                for (int column = 0; column < columns.getLength(); column++) {
                    final Element element = (Element) columns.item(column);
                    if ("report_sql".equals(element.getAttribute("name"))) {
                        effectiveSql = element.getTextContent();
                        effectiveFile = file.getFileName().toString();
                    }
                }
            }
            final NodeList sqls = document.getElementsByTagName("sql");
            for (int index = 0; index < sqls.getLength(); index++) {
                final String sql = sqls.item(index).getTextContent();
                if (sql.contains(REPORT_NAME) && sql.contains("report_sql")) {
                    effectiveSql = sql;
                    effectiveFile = file.getFileName().toString();
                }
                if (sql.toLowerCase(Locale.ROOT).contains("m_payment_type")) {
                    paymentTypeSeeds.add(sql);
                }
            }
            final NodeList inserts = document.getElementsByTagName("insert");
            for (int index = 0; index < inserts.getLength(); index++) {
                final Element insert = (Element) inserts.item(index);
                if ("m_payment_type".equals(insert.getAttribute("tableName"))) {
                    paymentTypeSeeds.add(insert.getTextContent() + " " + valueAttributes(insert));
                }
            }
        }
        assertNotNull(effectiveSql, "No changeset sets the report_sql of " + REPORT_NAME);
    }

    @Test
    @DisplayName("AC5/TC-02: repayments without a payment detail row are not dropped by an INNER JOIN on m_payment_detail")
    void paymentDetailIsLeftJoined() {
        final Matcher join = PAYMENT_DETAIL_JOIN.matcher(effectiveSql);
        assertTrue(join.find(), effectiveFile + " no longer joins m_payment_detail at all");
        do {
            assertNotNull(join.group(1), effectiveFile + " INNER JOINs m_payment_detail, so a repayment posted without a payment type "
                    + "(e.g. txn 2954481 on LOAN/15866/2023) never reaches the report");
        } while (join.find());
    }

    @Test
    @DisplayName("Story (Ethiopia 402907-402912): group loans, which have no client_id, are not dropped")
    void groupLoansAreIncluded() {
        final Matcher join = CLIENT_JOIN.matcher(effectiveSql);
        if (join.find()) {
            assertNotNull(join.group(1), effectiveFile
                    + " INNER JOINs m_client on ml.client_id, so every group loan (client_id NULL) is dropped from the report");
        }
        assertTrue(effectiveSql.toLowerCase(Locale.ROOT).contains("m_group"),
                effectiveFile + " never reads m_group, so a group loan has no name in the report");
    }

    @Test
    @DisplayName("QA hand-edit: paymentTypeId 0 means all payment types, so the 'All' option does not filter everything out")
    void paymentTypeZeroMeansAll() {
        final String normalised = effectiveSql.replaceAll("\\s+", " ");
        assertTrue(
                normalised.contains("${paymentTypeId} IN (-1, 0)") || normalised.contains("${paymentTypeId} IN (0, -1)")
                        || normalised.contains("${paymentTypeId} <= 0"),
                effectiveFile + " treats only -1 as 'all'; QA's live report was hand-edited to also accept 0 and the repo never got it");
    }

    @Test
    @DisplayName("Sylvester's residual LOAN/17816/2024: principal paid via PAY_OFF (type 28) is reported")
    void payOffTransactionsAreReported() {
        assertTrue(reportedTransactionTypes().contains("28"),
                effectiveFile + " reports only types " + reportedTransactionTypes() + ", so a pay-off collection never shows");
    }

    @Test
    @DisplayName("Guard: repayments (2) and recovery repayments (8) are still reported")
    void repaymentAndRecoveryAreStillReported() {
        final List<String> types = reportedTransactionTypes();
        assertTrue(types.contains("2") && types.contains("8"), effectiveFile + " reports types " + types);
    }

    @Test
    @DisplayName("Guard: reversed rows stay visible with their flag and the CGLT-825 MFI Code stays")
    void reversalFlagAndMfiCodeAreKept() {
        assertTrue(effectiveSql.contains("'Is Reversed'"), effectiveFile + " lost the Is Reversed column");
        assertTrue(effectiveSql.contains("mfi_code"), effectiveFile + " lost the CGLT-825 MFI Code column");
        assertFalse(effectiveSql.toLowerCase(Locale.ROOT).contains("is_reversed = 0"),
                effectiveFile + " now hides reversed payments, which the report has always listed");
    }

    @Test
    @DisplayName("Sylvester 17/Nov: a 'Top-up payment' payment type is seeded so top-up closures can carry it")
    void topUpPaymentTypeIsSeeded() {
        assertTrue(paymentTypeSeeds.stream().anyMatch(seed -> seed.contains("'" + TOP_UP_PAYMENT + "'") || seed.contains(TOP_UP_PAYMENT)),
                "No changeset inserts the '" + TOP_UP_PAYMENT + "' payment type (only 'Top-Up Correction' exists on QA and prod)");
    }

    private static List<String> reportedTransactionTypes() {
        final List<String> types = new ArrayList<>();
        final Matcher matcher = TRANSACTION_TYPES.matcher(effectiveSql);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                for (final String type : matcher.group(1).split(",")) {
                    types.add(type.trim());
                }
            } else {
                types.add(matcher.group(2));
            }
        }
        return types;
    }

    private static String valueAttributes(final Element insert) {
        final StringBuilder values = new StringBuilder();
        final NodeList columns = insert.getElementsByTagName("column");
        for (int index = 0; index < columns.getLength(); index++) {
            values.append(((Element) columns.item(index)).getAttribute("value")).append(' ');
        }
        return values.toString();
    }

    private static Document parse(final Path file) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    static Path changelogDirectory() {
        final Path fromModule = Path.of(CUSTOM_CHANGELOG);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        final Path fromRepoRoot = Path.of("fineract-provider", CUSTOM_CHANGELOG);
        assertTrue(Files.isDirectory(fromRepoRoot), "Could not find " + CUSTOM_CHANGELOG);
        return fromRepoRoot;
    }

    static String effectiveReportSql() throws Exception {
        if (effectiveSql == null) {
            loadEffectiveReport();
        }
        return effectiveSql;
    }
}
