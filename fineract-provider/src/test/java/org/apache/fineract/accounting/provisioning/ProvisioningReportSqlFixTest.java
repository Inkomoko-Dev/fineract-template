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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import liquibase.change.CheckSum;
import liquibase.changelog.ChangeLogParameters;
import liquibase.database.core.MySQLDatabase;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.precondition.core.PreconditionContainer;
import liquibase.resource.FileSystemResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ProvisioningReportSqlFixTest {

    private static final String CHANGELOG = "src/main/resources/db/changelog/tenant/parts/CGLT_537_fix_provisioning_report_sql.xml";

    private static final Map<String, String> REPORT_SQL_BY_CHANGESET = new HashMap<>();

    @BeforeAll
    static void loadChangelog() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        final Document document = factory.newDocumentBuilder().parse(resolveSource(CHANGELOG).toFile());
        final NodeList changeSets = document.getElementsByTagName("changeSet");
        for (int i = 0; i < changeSets.getLength(); i++) {
            final Element changeSet = (Element) changeSets.item(i);
            final String id = changeSet.getAttribute("id");
            final NodeList columns = changeSet.getElementsByTagName("column");
            for (int c = 0; c < columns.getLength(); c++) {
                final Element column = (Element) columns.item(c);
                if ("report_sql".equals(column.getAttribute("name"))) {
                    REPORT_SQL_BY_CHANGESET.put(id, column.getTextContent());
                }
            }
            final NodeList sqlNodes = changeSet.getElementsByTagName("sql");
            for (int s = 0; s < sqlNodes.getLength(); s++) {
                REPORT_SQL_BY_CHANGESET.put(id, sqlNodes.item(s).getTextContent());
            }
        }
    }

    @Test
    void byRunUsesDistinctAliasesForCriteriaAndCategory() {
        final String sql = REPORT_SQL_BY_CHANGESET.get("CGLT-537-fix-provisioning-by-category-by-run-sql");
        assertNotNull(sql);
        assertTrue(sql.contains("LEFT JOIN m_provisioning_criteria crit"));
        assertTrue(sql.contains("LEFT JOIN m_provision_category cat"));
        assertFalse(sql.contains("LEFT JOIN m_provisioning_criteria pc"));
        assertFalse(sql.contains("LEFT JOIN m_provision_category pc"));
    }

    @Test
    void latestRunUsesDistinctAliasesForCriteriaAndCategory() {
        final String sql = REPORT_SQL_BY_CHANGESET.get("CGLT-537-fix-provisioning-by-category-latest-run-sql");
        assertNotNull(sql);
        assertTrue(sql.contains("LEFT JOIN m_provisioning_criteria crit"));
        assertTrue(sql.contains("LEFT JOIN m_provision_category cat"));
        assertFalse(sql.contains("LEFT JOIN m_provisioning_criteria pc ON"));
        assertFalse(sql.contains("LEFT JOIN m_provision_category pc ON"));
    }

    @Test
    void categoryCorrectionsDoNotOverwriteTheLoanReport() throws Exception {
        final Document corrections = parse(resolveSource(CHANGELOG));
        final NodeList updates = corrections.getElementsByTagName("where");
        for (int i = 0; i < updates.getLength(); i++) {
            assertFalse(updates.item(i).getTextContent().contains("'Provisioning Report'"));
        }
        assertFalse(REPORT_SQL_BY_CHANGESET.values().stream().anyMatch(sql -> sql.contains("WITH run AS (")));
    }

    @Test
    void correctionsRunOnceAfterReportCreationAndCountColumn() throws Exception {
        final Path tenant = resolveSource("src/main/resources/db/changelog/tenant/changelog-tenant.xml");
        final Document document = parse(tenant);
        final NodeList includes = document.getDocumentElement().getChildNodes();
        int reportCreationIndex = -1;
        int countColumnIndex = -1;
        int overdueIndex = -1;
        int correctionIndex = -1;
        int overdueIncludes = 0;
        int correctionIncludes = 0;
        for (int i = 0; i < includes.getLength(); i++) {
            if (!(includes.item(i) instanceof Element)) {
                continue;
            }
            final Element include = (Element) includes.item(i);
            final String file = include.getAttribute("file");
            if ("includeAll".equals(include.getTagName()) && "custom-changelog".equals(include.getAttribute("path"))) {
                reportCreationIndex = i;
            } else if (file.endsWith("CGLT-749_add_report_count_sql_to_stretchy_report.xml")) {
                countColumnIndex = i;
            } else if (file.endsWith("CGLT-818_provisioning_report_overdue_columns.xml")) {
                overdueIndex = i;
                overdueIncludes++;
            } else if (file.endsWith("CGLT_537_fix_provisioning_report_sql.xml")) {
                correctionIndex = i;
                correctionIncludes++;
            }
        }
        assertTrue(reportCreationIndex >= 0);
        assertTrue(countColumnIndex > reportCreationIndex);
        assertTrue(overdueIndex > countColumnIndex);
        assertTrue(correctionIndex > overdueIndex);
        assertEquals(1, overdueIncludes);
        assertEquals(1, correctionIncludes);
        for (String name : List.of("CGLT-818_provisioning_report_overdue_columns.xml", "CGLT_537_fix_provisioning_report_sql.xml")) {
            assertFalse(Files.exists(tenant.getParent().resolve("custom-changelog").resolve(name)));
            final Document moved = parse(tenant.getParent().resolve("parts").resolve(name));
            assertEquals("db/changelog/tenant/custom-changelog/" + name, moved.getDocumentElement().getAttribute("logicalFilePath"));
            final NodeList conditions = moved.getElementsByTagName("preConditions");
            for (int i = 0; i < conditions.getLength(); i++) {
                assertEquals(name.startsWith("CGLT-818") ? "CONTINUE" : "HALT",
                        ((Element) conditions.item(i)).getAttribute("onFail"));
            }
        }
    }

    @Test
    void runOnChangeReportCannotRestoreVersionJoinOrBucketAverage() throws Exception {
        final String source = Files
                .readString(resolveSource("src/main/resources/db/changelog/tenant/parts/CGLT-818_provisioning_report_overdue_columns.xml"));
        assertTrue(source.contains("runOnChange=\"true\""));
        assertTrue(source.contains("ON d.id = m.criteria_definition_id"));
        assertTrue(source.contains("COALESCE(lpl.provisioning_amount, 0) AS `Provisioning Amount`"));
        assertFalse(source.contains("m.overdue_in_days BETWEEN"));
        assertFalse(source.contains("OVER (PARTITION BY m.id)"));
    }

    @Test
    void optionalLoanReportUpdateRetriesFailedPreconditionsButHaltsOnErrors() throws Exception {
        final Path report = resolveSource("src/main/resources/db/changelog/tenant/parts/CGLT-818_provisioning_report_overdue_columns.xml");
        final FileSystemResourceAccessor resources = new FileSystemResourceAccessor(report.getParent().toFile());
        final String name = report.getFileName().toString();
        final var log = ChangeLogParserFactory.getInstance().getParser(name, resources).parse(name,
                new ChangeLogParameters(new MySQLDatabase()), resources);
        final var changeSet = log.getChangeSets().get(0);
        assertEquals(PreconditionContainer.FailOption.CONTINUE, changeSet.getPreconditions().getOnFail());
        assertEquals(PreconditionContainer.ErrorOption.HALT, changeSet.getPreconditions().getOnError());
        assertTrue(changeSet.isRunOnChange());
    }

    @Test
    void originalCategoryReportsUseUnambiguousAliases() throws Exception {
        final String source = Files.readString(
                resolveSource("src/main/resources/db/changelog/tenant/custom-changelog/CGLT_537_provisioning_audit_visibility.xml"));
        assertTrue(source.contains("LEFT JOIN m_provisioning_criteria crit ON crit.id"));
        assertTrue(source.contains("LEFT JOIN m_provision_category cat ON cat.id"));
        assertFalse(source.contains("LEFT JOIN m_provisioning_criteria pc ON"));
        assertFalse(source.contains("LEFT JOIN m_provision_category pc ON"));
    }

    @Test
    void changedHistoricalReportsAcceptTheirPreviouslyAppliedChecksums() throws Exception {
        final Map<String, String> oldChecksums = Map.of("CGLT-537-stretchy-provisioning-by-category-by-run",
                "8:7b61695b8169b39379d7ddd368605ce3", "CGLT-537-update-latest-provisioning-report-snapshot-labels",
                "8:ea3e64f3360cad9f32d3a9e053650811", "CGLT-537-stretchy-provisioning-by-category-latest",
                "8:f31288b84f92c18b3b05dd646417b4b3");
        int checked = 0;
        for (String name : List.of("CGLT_537_provisioning_audit_visibility.xml", "CGLT_537_z_policy_reason_report_catchall.xml")) {
            final Path file = resolveSource("src/main/resources/db/changelog/tenant/custom-changelog/" + name);
            final FileSystemResourceAccessor resources = new FileSystemResourceAccessor(file.getParent().toFile());
            final var log = ChangeLogParserFactory.getInstance().getParser(name, resources).parse(name,
                    new ChangeLogParameters(new MySQLDatabase()), resources);
            for (final var changeSet : log.getChangeSets()) {
                final String checksum = oldChecksums.get(changeSet.getId());
                if (checksum != null) {
                    assertTrue(changeSet.isCheckSumValid(CheckSum.parse(checksum)), changeSet.getId());
                    checked++;
                }
            }
        }
        assertEquals(oldChecksums.size(), checked);
    }

    private static Document parse(Path path) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile());
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
