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
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.apache.fineract.infrastructure.security.utils.SQLInjectionException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeAccessScope;
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
    private static final String FULL_PARAMETER_LIST = "SELECT sp.parameter_name FROM stretchy_parameter sp "
            + "JOIN stretchy_report_parameter srp ON srp.parameter_id = sp.id "
            + "JOIN stretchy_report sr ON sr.id = srp.report_id WHERE sr.report_name in(${reportListing})";
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
        lenient().when(context.officeAccessScope()).thenReturn(OfficeAccessScope.hierarchical(List.of(".")));
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

        assertThat(sql).contains("(o.hierarchy like '.%')");
        assertThat(sql).doesNotContain("${currentUserHierarchy}");
    }

    @Test
    public void companionCountQueryCarriesTheSameOfficeHierarchy() {
        givenReportCountSql(SCOPED_COUNT);

        final String sql = service.buildCountSql("Portfolio Management", "report", SCOPED_REPORT, Map.of(), false);

        assertThat(sql).contains("(o.hierarchy like '.%')");
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
    public void parameterListLookupSubstitutesTheQuotedReportName() {
        final String sql = service.buildReportSql(FULL_PARAMETER_LIST, Map.of("${reportListing}", "'Loan Payment Details Report'"), false,
                null, null);

        assertThat(sql).contains("report_name in('Loan Payment Details Report')");
    }

    @Test
    public void quotedReportListingSubstitutesLoanDueInXDays() {
        final String sql = service.buildReportSql(FULL_PARAMETER_LIST, Map.of("${reportListing}", "'Loan Due in X days'"), false, null,
                null);

        assertThat(sql).contains("report_name in('Loan Due in X days')");
    }

    @Test
    public void quotedValueIsStillRejectedForOrdinaryParameters() {
        assertThatExceptionOfType(SQLInjectionException.class)
                .isThrownBy(() -> service.buildReportSql(PLAIN_REPORT, Map.of("${officeId}", "'OR 1 OR'"), false, null, null));
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
    public void loanRepaymentReportGroupsByAllSelectedNonAggregatedColumns() {
        final String loanRepaymentSql = "select ml.id as loanId,mc.id, mc.firstname, ifnull(mc.middlename,'') as middlename, "
                + "mc.lastname, mc.display_name as FullName, mobile_no as mobileNo, mc.group_name as GroupName, "
                + "round(ml.principal_amount, ml.currency_digits) as LoanAmount, round(ml.`total_outstanding_derived`, "
                + "ml.currency_digits) as LoanOutstanding, ml.`account_no` as LoanAccountId, round(mlt.amountPaid, "
                + "ml.currency_digits) as repaymentAmount FROM m_office mo JOIN m_office ounder ON ounder.hierarchy "
                + "LIKE CONCAT(mo.hierarchy, '%') AND ounder.hierarchy like CONCAT('.', '%') LEFT JOIN ( select "
                + "ml.id as loanId, ifnull(mc.id,mc2.id) as id, ifnull(mc.firstname,mc2.firstname) as firstname, "
                + "ifnull(mc.middlename,ifnull(mc2.middlename,(''))) as middlename, ifnull(mc.lastname,mc2.lastname) "
                + "as lastname, ifnull(mc.display_name,mc2.display_name) as display_name, ifnull(mc.status_enum,mc2."
                + "status_enum) as status_enum, ifnull(mc.mobile_no,mc2.mobile_no) as mobile_no, ifnull(mg.office_id,"
                + "mc2.office_id) as office_id, ifnull(mg.staff_id,mc2.staff_id) as staff_id, mg.id as group_id, "
                + "mg.display_name as group_name from m_loan ml left join m_group mg on mg.id = ml.group_id left "
                + "join m_group_client mgc on mgc.group_id = mg.id left join m_client mc on mc.id = mgc.client_id "
                + "left join m_client mc2 on mc2.id = ml.client_id order by loanId ) mc on mc.office_id = "
                + "ounder.id right join m_loan as ml on mc.loanId = ml.id right join( select mlt.amount as "
                + "amountPaid,mlt.id,mlt.loan_id from m_loan_transaction mlt where mlt.is_reversed = 0 group by "
                + "mlt.loan_id, mlt.id ) as mlt on mlt.loan_id = ml.id right join m_loan_repayment_schedule as mls1 on "
                + "ml.id = mls1.loan_id and mls1.`completed_derived` = 0 and mls1.installment = (SELECT MIN(installment) "
                + "from m_loan_repayment_schedule where loan_id = ml.id and duedate <= CURDATE() and "
                + "completed_derived=0) where mc.status_enum = 300 and mobile_no is not null and ml.`loan_status_id` "
                + "= 300 and (mo.id = ${officeId} or ${officeId} = -1) and (mc.staff_id = ${loanOfficerId} or "
                + "${loanOfficerId} = -1) and (ml.loan_type_enum = ${loanType} or ${loanType} = -1) and ml.id in "
                + "(select mla.loan_id from m_loan_arrears_aging mla) group by ml.id";

        final String sql = service.buildReportSql(loanRepaymentSql,
                Map.of("${officeId}", "120", "${loanOfficerId}", "-1", "${loanType}", "1"), false, null, null);

        assertThat(sql).contains("group by ml.id, mc.id, mc.firstname, mc.middlename, mc.lastname, "
                + "mc.display_name, mc.mobile_no, mc.group_name, mlt.amountPaid");
        assertThat(sql).contains("group by mlt.loan_id, mlt.id");
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
