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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

@ExtendWith(MockitoExtension.class)
public class ReadReportingServiceImplTest {

    private static final String PAGED_REPORT = "SELECT id FROM m_loan ORDER BY id LIMIT ${limit} OFFSET ${offset}";
    private static final String PLAIN_REPORT = "SELECT id FROM m_loan WHERE office_id = ${officeId}";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private GenericDataService genericDataService;
    @Mock
    private SqlInjectionPreventerService sqlInjectionPreventerService;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private DatabaseTypeResolver databaseTypeResolver;
    @Mock
    private AppUser appUser;
    @Mock
    private Office office;
    @Mock
    private SqlRowSet sqlRowSet;

    private ReadReportingServiceImpl service;

    @BeforeEach
    public void setUp() {
        service = new ReadReportingServiceImpl(jdbcTemplate, context, genericDataService, sqlInjectionPreventerService, sqlGenerator,
                databaseTypeResolver, new FineractProperties());

        lenient().when(genericDataService.replace(anyString(), anyString(), anyString()))
                .thenAnswer(i -> StringUtils.replace(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        lenient().when(genericDataService.wrapSQL(anyString())).thenAnswer(i -> "select x.* from (" + i.getArgument(0) + ") x");
        lenient().when(context.authenticatedUser()).thenReturn(appUser);
        lenient().when(appUser.getOffice()).thenReturn(office);
        lenient().when(office.getHierarchy()).thenReturn(".");
        lenient().when(appUser.getId()).thenReturn(1L);
        lenient().when(sqlGenerator.currentBusinessDate()).thenReturn("'2026-09-03'");
        lenient().when(sqlGenerator.currentTenantDateTime()).thenReturn("'2026-09-03 00:00:00'");
    }

    @Test
    public void enginePagesReportsThatDoNotDeclarePagingPlaceholders() {
        final String sql = service.buildReportSql(PLAIN_REPORT, Map.of("${officeId}", "1"), false, 100, 200);

        assertThat(sql).endsWith(" LIMIT 100 OFFSET 200");
        assertThat(StringUtils.countMatches(sql, "select x.* from (")).isEqualTo(1);
    }

    @Test
    public void reportDeclaringPagingPlaceholdersPagesItself() {
        final String sql = service.buildReportSql(PAGED_REPORT, Map.of(), false, 100, 200);

        assertThat(sql).contains("LIMIT 100 OFFSET 200");
        assertThat(sql).doesNotEndWith(" LIMIT 100 OFFSET 200");
        assertThat(StringUtils.countMatches(sql, "LIMIT 100 OFFSET 200")).isEqualTo(1);
    }

    @Test
    public void unpagedRequestNeutralisesPagingPlaceholders() {
        final String sql = service.buildReportSql(PAGED_REPORT, Map.of(), false, null, null);

        assertThat(sql).contains("LIMIT " + Integer.MAX_VALUE + " OFFSET 0");
        assertThat(sql).doesNotContain("${limit}");
        assertThat(sql).doesNotContain("${offset}");
    }

    @Test
    public void countUsesTheCompanionCountQueryWhenTheReportDeclaresOne() {
        givenReportCountSql("SELECT COUNT(*) FROM m_loan WHERE office_id = ${officeId}");

        final String sql = service.buildCountSql("Portfolio Management", "report", PAGED_REPORT, Map.of("${officeId}", "5"), false);

        assertThat(sql).isEqualTo("select x.* from (SELECT COUNT(*) FROM m_loan WHERE office_id = 5) x");
        assertThat(sql).doesNotContain("LIMIT");
    }

    @Test
    public void countFallsBackToCountingTheUnpagedReport() {
        givenReportCountSql(null);

        final String sql = service.buildCountSql("Portfolio Management", "report", PAGED_REPORT, Map.of(), false);

        assertThat(sql).startsWith("SELECT COUNT(*) FROM (");
        assertThat(sql).endsWith(") AS temp");
        assertThat(sql).contains("LIMIT " + Integer.MAX_VALUE + " OFFSET 0");
    }

    @Test
    public void parameterTypeReportsHaveNoCompanionCountQuery() {
        final String sql = service.buildCountSql("OfficeIdSelectOne", "parameter", PLAIN_REPORT, Map.of("${officeId}", "1"), false);

        assertThat(sql).startsWith("SELECT COUNT(*) FROM (");
        assertThat(sql).doesNotContain("LIMIT");
    }

    @Test
    public void csvQuotesTextEscapesQuotesAndLeavesNumbersBare() throws Exception {
        final StringWriter writer = new StringWriter();
        final ResultSet rs = resultSet(new String[] { "Client", "Balance" }, new String[] { "VARCHAR", "DECIMAL" },
                new String[] { "Doe, \"Jane\"", "1500.25" });

        final long rows = service.writeCsv(rs, writer);

        assertThat(rows).isEqualTo(1);
        assertThat(writer.toString()).isEqualTo("\"Client\",\"Balance\"\n\"Doe, \"\"Jane\"\"\",1500.25\n");
    }

    @Test
    public void csvEmitsAnEmptyFieldForNullValues() throws Exception {
        final StringWriter writer = new StringWriter();
        final ResultSet rs = resultSet(new String[] { "Client", "Balance" }, new String[] { "VARCHAR", "DECIMAL" },
                new String[] { null, null });

        service.writeCsv(rs, writer);

        assertThat(writer.toString()).isEqualTo("\"Client\",\"Balance\"\n,\n");
    }

    private void givenReportCountSql(final String countSql) {
        given(sqlInjectionPreventerService.encodeSql(anyString())).willAnswer(i -> i.getArgument(0));
        given(jdbcTemplate.queryForRowSet(anyString(), any(Object.class))).willReturn(sqlRowSet);
        given(sqlRowSet.next()).willReturn(true);
        given(sqlRowSet.getString("the_sql")).willReturn(countSql);
    }

    private ResultSet resultSet(final String[] labels, final String[] types, final String[] values) throws Exception {
        final ResultSetMetaData metaData = org.mockito.Mockito.mock(ResultSetMetaData.class);
        given(metaData.getColumnCount()).willReturn(labels.length);
        for (int i = 0; i < labels.length; i++) {
            given(metaData.getColumnLabel(i + 1)).willReturn(labels[i]);
            given(metaData.getColumnTypeName(i + 1)).willReturn(types[i]);
        }

        final ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        given(rs.getMetaData()).willReturn(metaData);
        given(rs.next()).willReturn(true, false);
        for (int i = 0; i < values.length; i++) {
            given(rs.getObject(i + 1)).willReturn(values[i]);
        }
        return rs;
    }
}
