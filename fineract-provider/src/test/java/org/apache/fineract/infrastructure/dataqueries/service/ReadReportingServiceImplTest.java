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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.apache.fineract.infrastructure.security.utils.SQLInjectionException;
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
    private static final int HEADER_WRITES = 4;
    private static final String SCOPED_REPORT = "SELECT l.id FROM m_loan l "
            + "JOIN m_office o ON o.hierarchy LIKE CONCAT('${currentUserHierarchy}', '%')";
    private static final String SCOPED_COUNT = "SELECT COUNT(*) FROM m_loan l "
            + "JOIN m_office o ON o.hierarchy LIKE CONCAT('${currentUserHierarchy}', '%')";

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

    @Test
    public void pagedRequestCountsWhenTheCallerAsksForIt() {
        assertThat(service.shouldRunCountQuery(true, 100)).isTrue();
    }

    @Test
    public void pagedRequestSkipsTheCountWhenTheCallerOptsOut() {
        assertThat(service.shouldRunCountQuery(false, 100)).isFalse();
    }

    @Test
    public void unpagedRequestNeverRunsACountQuery() {
        assertThat(service.shouldRunCountQuery(true, null)).isFalse();
        assertThat(service.shouldRunCountQuery(false, null)).isFalse();
    }

    @Test
    public void limitWithoutOffsetStillPagesFromTheStart() {
        final String sql = service.buildReportSql(PLAIN_REPORT, Map.of("${officeId}", "1"), false, 100, null);

        assertThat(sql).endsWith(" LIMIT 100 OFFSET 0");
    }

    @Test
    public void pagedReportSqlStillCarriesTheAuthenticatedUsersOfficeHierarchy() {
        final String sql = service.buildReportSql(SCOPED_REPORT, Map.of(), false, 100, 200);

        assertThat(sql).contains("hierarchy LIKE CONCAT('.', '%')");
        assertThat(sql).doesNotContain("${currentUserHierarchy}");
    }

    @Test
    public void companionCountQueryCarriesTheSameOfficeHierarchy() {
        givenReportCountSql(SCOPED_COUNT);

        final String sql = service.buildCountSql("Portfolio Management", "report", SCOPED_REPORT, Map.of(), false);

        assertThat(sql).contains("hierarchy LIKE CONCAT('.', '%')");
        assertThat(sql).doesNotContain("${currentUserHierarchy}");
    }

    @Test
    public void loggedReportTypeCollapsesToOneOfTwoKnownValues() {
        assertThat(service.storedReportType("report")).isEqualTo("report");
        assertThat(service.storedReportType("REPORT")).isEqualTo("report");
        assertThat(service.storedReportType("parameter")).isEqualTo("parameter");
        assertThat(service.storedReportType("<script>alert(1)</script>\n")).isEqualTo("parameter");
        assertThat(service.storedReportType(null)).isEqualTo("parameter");
    }

    @Test
    public void parameterValuesAreRevalidatedAtTheSubstitutionPoint() {
        assertThatExceptionOfType(SQLInjectionException.class)
                .isThrownBy(() -> service.buildReportSql(PLAIN_REPORT, Map.of("${officeId}", "1 UNION SELECT password FROM m_appuser"),
                        false, null, null));
    }

    @Test
    public void legitimateParameterValuesStillSubstitute() {
        final String sql = service.buildReportSql(PLAIN_REPORT, Map.of("${officeId}", "2026-09-11"), false, null, null);

        assertThat(sql).contains("office_id = 2026-09-11");
    }

    @Test
    public void mariaDbStreamsOnThePositiveFetchSize() {
        assertThat(service.streamsOnPositiveFetchSize("MariaDB Connector/J", "jdbc:mariadb://localhost:3306/fineract")).isTrue();
    }

    @Test
    public void mysqlConnectorNeedsRowByRowStreaming() {
        assertThat(service.streamsOnPositiveFetchSize("MySQL Connector/J", "jdbc:mysql://localhost:3306/fineract")).isFalse();
    }

    @Test
    public void mysqlConnectorKeepsThePositiveFetchSizeWithServerSideCursors() {
        assertThat(service.streamsOnPositiveFetchSize("MySQL Connector/J", "jdbc:mysql://localhost:3306/fineract?useCursorFetch=true"))
                .isTrue();
    }

    @Test
    public void unknownDriverKeepsTheConfiguredFetchSize() {
        assertThat(service.streamsOnPositiveFetchSize(null, null)).isTrue();
    }

    @Test
    public void exportCancelsTheQueryWhenTheClientStopsReading() throws Exception {
        final PreparedStatement statement = org.mockito.Mockito.mock(PreparedStatement.class);
        final ResultSet rs = resultSet(new String[] { "Client" }, new String[] { "VARCHAR" }, new String[] { "Doe" });
        given(statement.executeQuery()).willReturn(rs);

        assertThatIOException().isThrownBy(
                () -> service.streamResultSet(statement, writerFailingAfter(HEADER_WRITES), new ReadReportingServiceImpl.ExportMetrics()))
                .withMessage("client gone");

        verify(statement).cancel();
    }

    @Test
    public void exportDoesNotCancelTheQueryOnASuccessfulStream() throws Exception {
        final PreparedStatement statement = org.mockito.Mockito.mock(PreparedStatement.class);
        final ResultSet rs = resultSet(new String[] { "Client" }, new String[] { "VARCHAR" }, new String[] { "Doe" });
        given(statement.executeQuery()).willReturn(rs);

        final ReadReportingServiceImpl.ExportMetrics metrics = new ReadReportingServiceImpl.ExportMetrics();
        service.streamResultSet(statement, new StringWriter(), metrics);

        assertThat(metrics.getRows()).isEqualTo(1);
        verify(statement, never()).cancel();
    }

    private Writer writerFailingAfter(final int writes) {
        return new Writer() {

            private int written;

            private void countOrFail() throws IOException {
                if (++written > writes) {
                    throw new IOException("client gone");
                }
            }

            @Override
            public void write(final char[] buffer, final int offset, final int length) throws IOException {
                countOrFail();
            }

            @Override
            public void write(final int c) throws IOException {
                countOrFail();
            }

            @Override
            public void write(final String value) throws IOException {
                countOrFail();
            }

            @Override
            public void flush() throws IOException {
                // nothing buffered
            }

            @Override
            public void close() throws IOException {
                // nothing to release
            }
        };
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
