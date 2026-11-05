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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.fineract.organisation.office.domain.OfficeAccessScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class AgingDetailReportChangelogTest {

    private static final String CHANGELOG = "src/main/resources/db/changelog/tenant/custom-changelog/CGLT-538_aging_detail_par_category.xml";

    private static final List<String> EXISTING_COLUMNS = List.of("Office/Branch", "Currency", "Client Account No.", "Client Name",
            "Account Number", "Loan Amount", "Original Principal", "Original Interest", "Principal Paid", "Interest Paid",
            "Principal Overdue", "Interest Overdue", "Days in Arrears", "Weeks In Arrears Band", "Days in Arrears Band");

    private static final List<Band> STORY_BANDS = List.of(new Band(1, "Normal", 0, 30), new Band(2, "Watch", 31, 90),
            new Band(3, "Substandard", 91, 180), new Band(4, "Doubtful", 181, 365), new Band(5, "Loss", 366, null));

    private static final Pattern BAND_ROW = Pattern
            .compile("SELECT (\\d+)(?: AS id)?, '([A-Za-z]+)'(?: AS name)?, (\\d+)(?: AS min_dpd)?, (\\d+|NULL)(?: AS max_dpd)?");

    private static final Pattern OPTION_ROW = Pattern.compile("SELECT (\\d+)(?: AS id)?, '([A-Za-z]+)'(?: AS `?name`?)?");

    private static Document document;
    private static String reportSql;
    private static String parameterSql;

    @BeforeAll
    static void loadChangelog() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        document = factory.newDocumentBuilder().parse(resolveSource(CHANGELOG).toFile());
        final NodeList columns = document.getElementsByTagName("column");
        for (int index = 0; index < columns.getLength(); index++) {
            final Element column = (Element) columns.item(index);
            if ("report_sql".equals(column.getAttribute("name"))) {
                reportSql = column.getTextContent();
            } else if ("parameter_sql".equals(column.getAttribute("name"))) {
                parameterSql = column.getTextContent();
            }
        }
        assertNotNull(reportSql, "Changelog must set report_sql");
        assertNotNull(parameterSql, "Changelog must set the PAR Category parameter_sql");
    }

    @Test
    @DisplayName("AC2: the PAR bands are exactly the story's mapping")
    void parBandsMatchTheStory() {
        assertEquals(STORY_BANDS, reportBands());
    }

    @Test
    @DisplayName("AC5: every DPD from 0 upwards falls into exactly one PAR band")
    void parBandsAreMutuallyExclusiveAndExhaustive() {
        final List<Band> bands = reportBands();
        for (int dpd = 0; dpd <= 5000; dpd++) {
            final int value = dpd;
            final long matches = bands.stream().filter(band -> band.contains(value)).count();
            assertEquals(1, matches, "DPD " + dpd + " must map to exactly one PAR category");
        }
    }

    @Test
    @DisplayName("The PAR Category filter offers the same ids and names as the bands the column uses")
    void filterOptionsComeFromTheSameMapping() {
        final List<String> options = new ArrayList<>();
        final Matcher matcher = OPTION_ROW.matcher(parameterSql);
        while (matcher.find()) {
            options.add(matcher.group(1) + ":" + matcher.group(2));
        }
        final List<String> bands = reportBands().stream().map(band -> band.id + ":" + band.name).toList();
        assertEquals(bands, options);
    }

    @Test
    @DisplayName("AC3: the filter and the column use the same band join, and -1 (All) returns every band")
    void filterUsesTheBandJoin() {
        assertTrue(reportSql.contains("LEFT JOIN par_band par ON ag.dpd >= par.min_dpd AND (par.max_dpd IS NULL OR ag.dpd <= par.max_dpd)"));
        assertTrue(reportSql.contains("WHERE (${parCategory} = -1 OR par.id = ${parCategory})"));
        assertTrue(reportSql.contains("par.name AS \"PAR Category\""));
    }

    @Test
    @DisplayName("AC4: existing columns keep their names and order, PAR Category follows the Days in Arrears Band")
    void existingColumnsAreUnchanged() {
        final List<String> expected = new ArrayList<>(EXISTING_COLUMNS);
        expected.add("PAR Category");
        assertEquals(expected, outerSelectColumns());
    }

    @Test
    @DisplayName("AC1: every age is measured from the As at date, today only switches to the nightly aging table")
    void ageIsMeasuredFromTheAsAtDate() {
        assertEquals(1, occurrences(reportSql, "CURDATE()"), "CURDATE() may only pick the aging source");
        assertTrue(reportSql.contains("SELECT DATE('${asAtDate}') AS d, DATE('${asAtDate}') = CURDATE() AS is_today"));
        assertTrue(reportSql.contains("DATEDIFF(p.d, laa.overdue_since_date_derived)"));
        assertTrue(reportSql.contains("DATEDIFF(p.d, a.overdue_since)"));
        assertFalse(reportSql.contains("DATEDIFF(CURDATE()"));
    }

    @Test
    @DisplayName("AC1: a past As at date rebuilds arrears from transactions dated on or before it")
    void pastDateRebuildsArrearsFromTransactions() {
        assertTrue(reportSql.contains("AND t.is_reversed = 0"));
        assertTrue(reportSql.contains("AND t.transaction_date <= p.d"));
        assertTrue(reportSql.contains("AND rs.duedate < DATE_SUB(p.d, INTERVAL COALESCE(s.grace, 0) DAY)"));
        assertTrue(reportSql.contains("AND ml.disbursedon_date <= p.d"));
        assertTrue(reportSql.contains("AND (ml.closedon_date IS NULL OR ml.closedon_date > p.d)"));
        assertTrue(reportSql.contains("AND (ml.writtenoffon_date IS NULL OR ml.writtenoffon_date > p.d)"));
        assertTrue(reportSql.contains("WHERE type IN (2, 5, 8, 28)"));
        assertTrue(reportSql.contains("WHERE live.type = 9"));
    }

    @Test
    @DisplayName("Today and a past date read mutually exclusive aging sources")
    void agingSourcesAreMutuallyExclusive() {
        assertTrue(reportSql.contains("(p.is_today AND ml.loan_status_id = 300)"));
        assertTrue(reportSql.contains("OR (NOT p.is_today"));
        assertEquals(2, occurrences(reportSql, "WHERE NOT p.is_today"));
        assertEquals(1, occurrences(reportSql, "WHERE p.is_today"));
    }

    @Test
    @DisplayName("The multi-office access rewrite still restricts the report to the user's offices")
    void officeScopeRewriteStillApplies() {
        final String rewritten = OfficeScopeReportSqlRewriter.rewrite(reportSql, OfficeAccessScope.hierarchical(List.of(".1.2.")));
        assertFalse(rewritten.contains("${currentUserHierarchy}"));
        assertTrue(rewritten.contains("(ounder.hierarchy like '.1.2.%')"));
    }

    @Test
    @DisplayName("The changelog maps the As at and PAR Category parameters to the Aging Detail report")
    void parametersAreMappedToTheReport() {
        final String xml = textOf(document.getDocumentElement());
        assertTrue(xml.contains("report_name = 'Aging Detail'"));
        assertTrue(xml.contains("parameter_name = 'asAtDate'"));
        assertTrue(xml.contains("parameter_name = 'parCategorySelectAll'"));
        final NodeList changeSets = document.getElementsByTagName("changeSet");
        assertTrue(changeSets.getLength() >= 4);
        for (int index = 0; index < changeSets.getLength(); index++) {
            final Element changeSet = (Element) changeSets.item(index);
            assertEquals(1, changeSet.getElementsByTagName("preConditions").getLength(),
                    changeSet.getAttribute("id") + " must be guarded by a precondition");
        }
    }

    private static List<Band> reportBands() {
        final String table = reportSql.substring(reportSql.indexOf("par_band AS ("), reportSql.indexOf("),", reportSql.indexOf("par_band AS (")));
        final List<Band> bands = new ArrayList<>();
        final Matcher matcher = BAND_ROW.matcher(table);
        while (matcher.find()) {
            bands.add(new Band(Integer.parseInt(matcher.group(1)), matcher.group(2), Integer.parseInt(matcher.group(3)),
                    "NULL".equals(matcher.group(4)) ? null : Integer.valueOf(matcher.group(4))));
        }
        return bands;
    }

    private static List<String> outerSelectColumns() {
        final String outerSelect = reportSql.substring(reportSql.lastIndexOf("\nSELECT"), reportSql.indexOf("\nFROM aging ag"));
        final Matcher alias = Pattern.compile("(?i)\\bAS\\s+(?:\"([^\"]+)\"|(Currency)\\b)").matcher(outerSelect);
        final List<String> columns = new ArrayList<>();
        while (alias.find()) {
            columns.add(alias.group(1) != null ? alias.group(1) : alias.group(2));
        }
        return columns;
    }

    private static int occurrences(final String text, final String token) {
        int count = 0;
        for (int from = text.indexOf(token); from >= 0; from = text.indexOf(token, from + token.length())) {
            count++;
        }
        return count;
    }

    private static String textOf(final Element element) {
        final StringBuilder text = new StringBuilder(element.getTextContent());
        final NodeList columns = element.getElementsByTagName("column");
        for (int index = 0; index < columns.getLength(); index++) {
            text.append('\n').append(((Element) columns.item(index)).getAttribute("valueComputed"));
        }
        return text.toString();
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

    private record Band(int id, String name, int minDpd, Integer maxDpd) {

        boolean contains(final int dpd) {
            return dpd >= minDpd && (maxDpd == null || dpd <= maxDpd);
        }
    }
}
