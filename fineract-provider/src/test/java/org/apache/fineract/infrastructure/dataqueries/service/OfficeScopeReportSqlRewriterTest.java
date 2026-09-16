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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.fineract.organisation.office.domain.OfficeAccessScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OfficeScopeReportSqlRewriterTest {

    private static final OfficeAccessScope TWO_OFFICES = OfficeAccessScope.hierarchical(Arrays.asList(".1.2.5.", ".1.2.6."));
    private static final OfficeAccessScope ONE_OFFICE = OfficeAccessScope.hierarchical(List.of(".1.2."));

    // Scenario 4 - reporting across every location the user may see
    @Test
    @DisplayName("The stock report office restriction is widened to every assigned office")
    public void rewritesTheStockConcatIdiom() {
        final String sql = "select x from m_office ounder\nwhere ounder.hierarchy like concat('${currentUserHierarchy}', '%')\norder by 1";

        final String rewritten = OfficeScopeReportSqlRewriter.rewrite(sql, TWO_OFFICES);

        assertEquals("select x from m_office ounder\n"
                + "where (ounder.hierarchy like '.1.2.5.%' or ounder.hierarchy like '.1.2.6.%')\norder by 1", rewritten);
    }

    @Test
    @DisplayName("Every occurrence of the restriction is widened, whatever the alias or spacing")
    public void rewritesEveryOccurrence() {
        final String sql = "a.hierarchy like concat('${currentUserHierarchy}','%') and "
                + "b.hierarchy   LIKE   CONCAT( '${currentUserHierarchy}' , '%' )";

        final String rewritten = OfficeScopeReportSqlRewriter.rewrite(sql, ONE_OFFICE);

        assertEquals("(a.hierarchy like '.1.2.%') and (b.hierarchy like '.1.2.%')", rewritten);
    }

    @Test
    @DisplayName("The inline LIKE form of the restriction is widened too")
    public void rewritesTheInlineLikeIdiom() {
        final String sql = "where o.hierarchy like '${currentUserHierarchy}%'";

        assertEquals("where (o.hierarchy like '.1.2.5.%' or o.hierarchy like '.1.2.6.%')",
                OfficeScopeReportSqlRewriter.rewrite(sql, TWO_OFFICES));
    }

    @Test
    @DisplayName("A restricted user's report is narrowed to their own office only")
    public void rewritesToEqualityForAnExactScope() {
        final String sql = "where ounder.hierarchy like concat('${currentUserHierarchy}', '%')";

        assertEquals("where (ounder.hierarchy = '.1.2.')",
                OfficeScopeReportSqlRewriter.rewrite(sql, OfficeAccessScope.exact(List.of(".1.2."))));
    }

    @Test
    @DisplayName("Office restrictions that do not reference the current user are left alone")
    public void leavesUnrelatedHierarchyJoinsAlone() {
        final String sql = "join m_office ounder on ounder.hierarchy like concat(o.hierarchy, '%')";

        assertEquals(sql, OfficeScopeReportSqlRewriter.rewrite(sql, TWO_OFFICES));
    }

    @Test
    @DisplayName("Other uses of the placeholder are left for the ordinary substitution")
    public void leavesOtherPlaceholderUsesAlone() {
        final String sql = "select '${currentUserHierarchy}' as h from m_office";

        assertEquals(sql, OfficeScopeReportSqlRewriter.rewrite(sql, TWO_OFFICES));
    }

    @Test
    @DisplayName("A single office scope rewrites to exactly the restriction the report had before")
    public void singleOfficeScopeIsEquivalentToTheOriginalRestriction() {
        final String sql = "where ounder.hierarchy like concat('${currentUserHierarchy}', '%')";

        final String rewritten = OfficeScopeReportSqlRewriter.rewrite(sql, ONE_OFFICE);

        assertTrue(rewritten.contains("ounder.hierarchy like '.1.2.%'"));
        assertFalse(rewritten.contains("currentUserHierarchy"));
    }

    @Test
    @DisplayName("Blank report sql is handled")
    public void handlesBlankSql() {
        assertEquals(null, OfficeScopeReportSqlRewriter.rewrite(null, TWO_OFFICES));
        assertEquals("", OfficeScopeReportSqlRewriter.rewrite("", TWO_OFFICES));
    }
}
