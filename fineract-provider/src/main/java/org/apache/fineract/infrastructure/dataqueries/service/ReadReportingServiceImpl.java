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

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.ws.rs.core.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ReportData;
import org.apache.fineract.infrastructure.dataqueries.data.ReportParameterData;
import org.apache.fineract.infrastructure.dataqueries.data.ReportParameterJoinData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
import org.apache.fineract.infrastructure.dataqueries.exception.ReportNotFoundException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.apache.fineract.infrastructure.security.utils.SQLInjectionValidator;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.UnixCodec;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReadReportingServiceImpl implements ReadReportingService {

    private static final String LIMIT_PLACEHOLDER = "${limit}";
    private static final String OFFSET_PLACEHOLDER = "${offset}";
    private static final String REPORT_TYPE = "report";
    private static final String PARAMETER_TYPE = "parameter";
    private static final int UNPAGED_LIMIT = Integer.MAX_VALUE;
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_EXPORT_FETCH_SIZE = 1000;
    private static final int ROW_BY_ROW_FETCH_SIZE = Integer.MIN_VALUE;
    private static final int CSV_BUFFER_SIZE = 32 * 1024;
    private static final String MYSQL_CONNECTOR_DRIVER_NAME = "MySQL Connector";
    private static final String CURSOR_FETCH_PARAMETER = "useCursorFetch=true";
    private static final String TMP_DISK_TABLES_STATUS = "SHOW SESSION STATUS LIKE 'Created_tmp_disk_tables'";
    private static final String REPORT_METRICS_LOG = "REPORT name={} type={} rows={} totalRows={} limit={} offset={} includeCount={} "
            + "dataQueryMs={} countQueryMs={} totalMs={} tmpDiskTables={}";
    private static final String EXPORT_METRICS_LOG = "REPORT export=csv name={} type={} rows={} bytes={} queryMs={} streamMs={} "
            + "totalMs={} tmpDiskTables={}";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final GenericDataService genericDataService;
    private final SqlInjectionPreventerService sqlInjectionPreventerService;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final DatabaseTypeResolver databaseTypeResolver;
    private final FineractProperties fineractProperties;

    private JdbcTemplate reportJdbcTemplate;

    @PostConstruct
    public void configureReportJdbcTemplate() {
        this.reportJdbcTemplate = new JdbcTemplate(this.jdbcTemplate.getDataSource());
        this.reportJdbcTemplate.setQueryTimeout(queryTimeoutSeconds());
    }

    @Override
    public StreamingOutput retrieveReportCSV(final String name, final String type, final Map<String, String> queryParams,
            final boolean isSelfServiceUserReport, final Integer limit, final Integer offset) {

        final ReportSql report = getReportSql(name, type);
        final String sql = buildReportSql(report.sql, queryParams, isSelfServiceUserReport, limit, offset);
        final String storedName = report.name;
        final String storedType = storedReportType(type);

        return out -> {
            final CountingOutputStream sink = new CountingOutputStream(out);
            final Writer writer = new BufferedWriter(new OutputStreamWriter(sink, StandardCharsets.UTF_8), CSV_BUFFER_SIZE);
            final long startTime = System.currentTimeMillis();
            final ExportMetrics metrics = new ExportMetrics();
            try {
                streamCsv(sql, writer, metrics);
                writer.flush();
                log.info(EXPORT_METRICS_LOG, storedName, storedType, metrics.rows, sink.getCount(), metrics.queryMs, metrics.streamMs,
                        System.currentTimeMillis() - startTime, metrics.tmpDiskTables);
            } catch (final Exception e) {
                if (sink.getCount() == 0) {
                    throw new PlatformDataIntegrityException("error.msg.exception.error", e.getMessage(), e);
                }
                log.error("Report CSV export aborted after {} bytes: {}", sink.getCount(), storedName, e);
                throw new IOException("Report CSV export aborted", e);
            }
        };
    }

    private void streamCsv(final String sql, final Writer writer, final ExportMetrics metrics) {
        reportJdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            final long tmpDiskTablesBefore = readCreatedTmpDiskTables(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                statement.setQueryTimeout(queryTimeoutSeconds());
                applyExportFetchSize(connection, statement);
                streamResultSet(statement, writer, metrics);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            metrics.tmpDiskTables = readCreatedTmpDiskTables(connection) - tmpDiskTablesBefore;
            return null;
        });
    }

    void streamResultSet(final PreparedStatement statement, final Writer writer, final ExportMetrics metrics)
            throws SQLException, IOException {
        final long queryStartTime = System.currentTimeMillis();
        try (ResultSet rs = statement.executeQuery()) {
            metrics.queryMs = System.currentTimeMillis() - queryStartTime;
            final long streamStartTime = System.currentTimeMillis();
            try {
                metrics.rows = writeCsv(rs, writer);
            } catch (final IOException e) {
                abandonQuery(statement);
                throw e;
            } finally {
                metrics.streamMs = System.currentTimeMillis() - streamStartTime;
            }
        }
    }

    private void abandonQuery(final Statement statement) {
        try {
            statement.cancel();
        } catch (final SQLException e) {
            log.debug("Could not cancel the abandoned report export query", e);
        }
    }

    long writeCsv(final ResultSet rs, final Writer writer) throws SQLException, IOException {
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();
        final String[] columnTypes = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columnTypes[i] = metaData.getColumnTypeName(i + 1);
            if (i > 0) {
                writer.write(',');
            }
            writeQuoted(writer, metaData.getColumnLabel(i + 1));
        }
        writer.write('\n');

        long rows = 0;
        while (rs.next()) {
            for (int i = 0; i < columnCount; i++) {
                if (i > 0) {
                    writer.write(',');
                }
                final String value = columnValue(rs, i + 1);
                if (value == null) {
                    continue;
                }
                if (isNumericColumn(columnTypes[i])) {
                    writer.write(value);
                } else {
                    writeQuoted(writer, value);
                }
            }
            writer.write('\n');
            rows++;
        }
        return rows;
    }

    private String columnValue(final ResultSet rs, final int columnIndex) throws SQLException {
        final Object value = rs.getObject(columnIndex);
        return value == null ? null : value.toString();
    }

    private void writeQuoted(final Writer writer, final String value) throws IOException {
        writer.write('"');
        writer.write(StringUtils.replace(value, "\"", "\"\""));
        writer.write('"');
    }

    private boolean isNumericColumn(final String columnType) {
        return "DECIMAL".equals(columnType) || "DOUBLE".equals(columnType) || "BIGINT".equals(columnType) || "SMALLINT".equals(columnType)
                || "INT".equals(columnType);
    }

    @Override
    public GenericResultsetData retrieveGenericResultset(final String name, final String type, final Map<String, String> queryParams,
            final boolean isSelfServiceUserReport, final Integer limit, final Integer offset, final boolean includeCount) {

        final long startTime = System.currentTimeMillis();
        final ReportSql report = getReportSql(name, type);
        final String reportSql = report.sql;
        final String sql = buildReportSql(reportSql, queryParams, isSelfServiceUserReport, limit, offset);

        final long[] tmpDiskTables = new long[1];
        final GenericResultsetData result = fillReportResultset(sql, tmpDiskTables);
        final long dataQueryElapsed = System.currentTimeMillis() - startTime;

        long countQueryElapsed = 0;
        if (shouldRunCountQuery(includeCount, limit)) {
            final long countStartTime = System.currentTimeMillis();
            result.setCount(countRows(name, type, reportSql, queryParams, isSelfServiceUserReport));
            countQueryElapsed = System.currentTimeMillis() - countStartTime;
        } else if (isUnpaged(limit)) {
            result.setCount(result.getData().size());
        }

        log.info(REPORT_METRICS_LOG, report.name, storedReportType(type), result.getData().size(), result.getCount(), limit, offset,
                includeCount, dataQueryElapsed, countQueryElapsed, System.currentTimeMillis() - startTime, tmpDiskTables[0]);
        return result;
    }

    boolean shouldRunCountQuery(final boolean includeCount, final Integer limit) {
        return includeCount && !isUnpaged(limit);
    }

    private static boolean isUnpaged(final Integer limit) {
        return limit == null;
    }

    private GenericResultsetData fillReportResultset(final String sql, final long[] tmpDiskTables) {
        try {
            return reportJdbcTemplate.execute((ConnectionCallback<GenericResultsetData>) connection -> {
                final long before = readCreatedTmpDiskTables(connection);
                final GenericResultsetData resultset;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(queryTimeoutSeconds());
                    try (ResultSet rs = statement.executeQuery()) {
                        final ResultSetMetaData metaData = rs.getMetaData();
                        final int columnCount = metaData.getColumnCount();

                        final List<ResultsetColumnHeaderData> columnHeaders = new ArrayList<>(columnCount);
                        for (int i = 0; i < columnCount; i++) {
                            columnHeaders.add(
                                    ResultsetColumnHeaderData.basic(metaData.getColumnLabel(i + 1), metaData.getColumnTypeName(i + 1)));
                        }

                        final List<ResultsetRowData> rows = new ArrayList<>();
                        while (rs.next()) {
                            final List<String> columnValues = new ArrayList<>(columnCount);
                            for (int i = 0; i < columnCount; i++) {
                                columnValues.add(columnValue(rs, i + 1));
                            }
                            rows.add(ResultsetRowData.create(columnValues));
                        }
                        resultset = new GenericResultsetData(columnHeaders, rows);
                    }
                }
                tmpDiskTables[0] = readCreatedTmpDiskTables(connection) - before;
                return resultset;
            });
        } catch (final DataAccessException e) {
            log.error("Reporting error: {}", e.getMessage());
            throw new PlatformDataIntegrityException("error.msg.report.unknown.data.integrity.issue", e.getClass().getName(), e);
        }
    }

    private long readCreatedTmpDiskTables(final Connection connection) {
        if (!databaseTypeResolver.isMySQL()) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement(TMP_DISK_TABLES_STATUS); ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(2) : 0;
        } catch (final SQLException e) {
            return 0;
        }
    }

    private Integer countRows(final String name, final String type, final String reportSql, final Map<String, String> queryParams,
            final boolean isSelfServiceUserReport) {
        final Integer count = reportJdbcTemplate
                .queryForObject(buildCountSql(name, type, reportSql, queryParams, isSelfServiceUserReport), Integer.class);
        return count != null ? count : 0;
    }

    String buildCountSql(final String name, final String type, final String reportSql, final Map<String, String> queryParams,
            final boolean isSelfServiceUserReport) {
        final String reportCountSql = getReportCountSql(name, type);
        if (StringUtils.isNotBlank(reportCountSql)) {
            return this.genericDataService.wrapSQL(applyReportParameters(reportCountSql, queryParams, isSelfServiceUserReport, null, null));
        }
        return "SELECT COUNT(*) FROM (" + buildReportSql(reportSql, queryParams, isSelfServiceUserReport, null, null) + ") AS temp";
    }

    String buildReportSql(final String reportSql, final Map<String, String> queryParams, final boolean isSelfServiceUserReport,
            final Integer limit, final Integer offset) {
        final boolean pagedByReport = reportSql.contains(LIMIT_PLACEHOLDER);
        final String sql = this.genericDataService
                .wrapSQL(applyReportParameters(reportSql, queryParams, isSelfServiceUserReport, limit, offset));

        if (!pagedByReport && limit != null) {
            return sql + " LIMIT " + limit + " OFFSET " + (offset != null ? offset : 0);
        }
        return sql;
    }

    String applyReportParameters(final String reportSql, final Map<String, String> queryParams,
            final boolean isSelfServiceUserReport, final Integer limit, final Integer offset) {

        String sql = reportSql;
        for (final String key : queryParams.keySet()) {
            final String value = queryParams.get(key);
            SQLInjectionValidator.validateReportParameter(value);
            sql = this.genericDataService.replace(sql, key, value);
        }

        sql = this.genericDataService.replace(sql, LIMIT_PLACEHOLDER, Integer.toString(limit != null ? limit : UNPAGED_LIMIT));
        sql = this.genericDataService.replace(sql, OFFSET_PLACEHOLDER, Integer.toString(offset != null ? offset : 0));

        final AppUser currentUser = this.context.authenticatedUser();
        // Allows sql query to restrict data by office hierarchy if required
        sql = this.genericDataService.replace(sql, "${currentUserHierarchy}", currentUser.getOffice().getHierarchy());
        // Allows sql query to restrict data by current user Id if required
        // (typically used to return report lists containing only reports
        // permitted to be run by the user
        sql = this.genericDataService.replace(sql, "${currentUserId}", currentUser.getId().toString());
        sql = this.genericDataService.replace(sql, "${isSelfServiceUser}", Boolean.toString(isSelfServiceUserReport));
        sql = this.genericDataService.replace(sql, "${currentDate}", sqlGenerator.currentBusinessDate());
        sql = StringUtils.replaceIgnoreCase(sql, "NOW()", sqlGenerator.currentTenantDateTime());
        sql = StringUtils.replaceIgnoreCase(sql, "curdate()", sqlGenerator.currentBusinessDate());
        sql = StringUtils.replaceIgnoreCase(sql, "CURRENT_DATE", sqlGenerator.currentBusinessDate());
        return sql;
    }

    private void applyExportFetchSize(final Connection connection, final PreparedStatement statement) throws SQLException {
        final int fetchSize = exportFetchSize();
        if (fetchSize != 0) {
            statement.setFetchSize(streamsOnPositiveFetchSize(connection) ? fetchSize : ROW_BY_ROW_FETCH_SIZE);
        }
    }

    private boolean streamsOnPositiveFetchSize(final Connection connection) {
        try {
            final DatabaseMetaData metaData = connection.getMetaData();
            return streamsOnPositiveFetchSize(metaData.getDriverName(), metaData.getURL());
        } catch (final SQLException e) {
            log.debug("Could not resolve the JDBC driver for report export streaming", e);
            return true;
        }
    }

    boolean streamsOnPositiveFetchSize(final String driverName, final String url) {
        if (!StringUtils.containsIgnoreCase(driverName, MYSQL_CONNECTOR_DRIVER_NAME)) {
            return true;
        }
        return StringUtils.containsIgnoreCase(url, CURSOR_FETCH_PARAMETER);
    }

    private int queryTimeoutSeconds() {
        final FineractProperties.FineractReportProperties report = fineractProperties.getReport();
        return report == null || report.getQueryTimeoutSeconds() <= 0 ? DEFAULT_QUERY_TIMEOUT_SECONDS : report.getQueryTimeoutSeconds();
    }

    private int exportFetchSize() {
        final FineractProperties.FineractReportProperties report = fineractProperties.getReport();
        return report == null ? DEFAULT_EXPORT_FETCH_SIZE : report.getExportFetchSize();
    }

    private String getSql(final String name, final String type) {
        return getReportSql(name, type).sql;
    }

    private ReportSql getReportSql(final String name, final String type) {
        final String encodedName = sqlInjectionPreventerService.encodeSql(name);
        final String encodedType = sqlInjectionPreventerService.encodeSql(type);

        final String inputSql = "select " + encodedType + "_name as the_name, " + encodedType + "_sql as the_sql from stretchy_"
                + encodedType + " where " + encodedType + "_name = ?";

        final String inputSqlWrapped = this.genericDataService.wrapSQL(inputSql);

        // the return statement contains the exact sql required
        final SqlRowSet rs = this.jdbcTemplate.queryForRowSet(inputSqlWrapped, encodedName);

        if (rs.next() && rs.getString("the_sql") != null) {
            return new ReportSql(rs.getString("the_name"), rs.getString("the_sql"));
        }
        throw new ReportNotFoundException(encodedName);
    }

    private static String storedReportType(final String type) {
        return REPORT_TYPE.equalsIgnoreCase(type) ? REPORT_TYPE : PARAMETER_TYPE;
    }

    private String getReportCountSql(final String name, final String type) {
        if (!REPORT_TYPE.equalsIgnoreCase(type)) {
            return null;
        }

        final String sql = this.genericDataService
                .wrapSQL("select report_count_sql as the_sql from stretchy_report where report_name = ?");
        final SqlRowSet rs = this.jdbcTemplate.queryForRowSet(sql, sqlInjectionPreventerService.encodeSql(name));

        return rs.next() ? rs.getString("the_sql") : null;
    }

    @Override
    public String getReportType(final String reportName, final boolean isSelfServiceUserReport, final boolean isParameterType) {
        String reportType = "Table";
        if (isParameterType) {
            return "Table";
        }

        final String sql = "SELECT coalesce(report_type,'') AS report_type FROM stretchy_report WHERE report_name = ? AND self_service_user_report = ?";

        final String sqlWrapped = this.genericDataService.wrapSQL(sql);

        final SqlRowSet rs = this.jdbcTemplate.queryForRowSet(sqlWrapped, reportName, isSelfServiceUserReport);

        if (rs.next()) {
            reportType = rs.getString("report_type");
        }
        return reportType;
    }

    @Override
    public String retrieveReportPDF(final String reportName, final String type, final Map<String, String> queryParams,
            final boolean isSelfServiceUserReport, final Integer limit, final Integer offset) {

        final String fileLocation = fineractProperties.getContent().getFilesystem().getRootFolder() + File.separator + "";
        if (!new File(fileLocation).isDirectory()) {
            new File(fileLocation).mkdirs();
        }

        final String genaratePdf = fileLocation + File.separator + reportName + ".pdf";

        try {
            final GenericResultsetData result = retrieveGenericResultset(reportName, type, queryParams, isSelfServiceUserReport, limit,
                    offset, false);

            final List<ResultsetColumnHeaderData> columnHeaders = result.getColumnHeaders();
            final List<ResultsetRowData> data = result.getData();
            List<String> row;

            log.info("NO. of Columns: {}", columnHeaders.size());
            final Integer chSize = columnHeaders.size();

            final Document document = new Document(PageSize.B0.rotate());

            String validatedFileName = ESAPI.encoder().encodeForOS(new UnixCodec(), reportName);
            PdfWriter.getInstance(document, new FileOutputStream(fileLocation + validatedFileName + ".pdf"));
            document.open();

            final PdfPTable table = new PdfPTable(chSize);
            table.setWidthPercentage(100);

            for (int i = 0; i < chSize; i++) {

                table.addCell(columnHeaders.get(i).getColumnName());

            }
            table.completeRow();

            Integer rSize;
            String currColType;
            String currVal;
            log.info("NO. of Rows: {}", data.size());
            for (ResultsetRowData element : data) {
                row = element.getRow();
                rSize = row.size();
                for (int j = 0; j < rSize; j++) {
                    currColType = columnHeaders.get(j).getColumnType();
                    currVal = row.get(j);
                    if (currVal != null) {
                        if (currColType.equals("DECIMAL") || currColType.equals("DOUBLE") || currColType.equals("BIGINT")
                                || currColType.equals("SMALLINT") || currColType.equals("INT")) {

                            table.addCell(currVal.toString());
                        } else {
                            table.addCell(currVal.toString());
                        }
                    }
                }
            }
            table.completeRow();
            document.add(table);
            document.close();
            return genaratePdf;
        } catch (final Exception e) {
            log.error("error.msg.reporting.error:", e);
            throw new PlatformDataIntegrityException("error.msg.exception.error", e.getMessage(), e);
        }
    }

    @Override
    public ReportData retrieveReport(final Long id) {
        final Collection<ReportData> reports = retrieveReports(id);

        for (final ReportData report : reports) {
            return report;
        }
        return null;
    }

    @Override
    public Collection<ReportData> retrieveReportList() {
        return retrieveReports(null);
    }

    private Collection<ReportData> retrieveReports(final Long id) {

        final ReportParameterJoinMapper rm = new ReportParameterJoinMapper();

        final String sql = rm.schema(id);

        final Collection<ReportParameterJoinData> rpJoins = this.jdbcTemplate.query(sql, rm,
                id != null ? new Object[] { id } : new Object[] {});

        final Collection<ReportData> reportList = new ArrayList<>();
        if (rpJoins == null || rpJoins.size() == 0) {
            return reportList;
        }

        Collection<ReportParameterData> reportParameters = null;

        Long reportId = null;
        String reportName = null;
        String reportType = null;
        String reportSubType = null;
        String reportCategory = null;
        String description = null;
        Boolean coreReport = null;
        Boolean useReport = null;
        String reportSql = null;

        Long prevReportId = (long) -1234;
        Boolean firstReport = true;
        for (final ReportParameterJoinData rpJoin : rpJoins) {

            if (rpJoin.getReportId().equals(prevReportId)) {
                // more than one parameter for report
                if (reportParameters == null) {
                    reportParameters = new ArrayList<>();
                }
                reportParameters.add(new ReportParameterData(rpJoin.getReportParameterId(), rpJoin.getParameterId(),
                        rpJoin.getReportParameterName(), rpJoin.getParameterName()));

            } else {
                if (firstReport) {
                    firstReport = false;
                } else {
                    // write report entry
                    reportList.add(new ReportData(reportId, reportName, reportType, reportSubType, reportCategory, description, reportSql,
                            coreReport, useReport, reportParameters));
                }

                prevReportId = rpJoin.getReportId();

                reportId = rpJoin.getReportId();
                reportName = rpJoin.getReportName();
                reportType = rpJoin.getReportType();
                reportSubType = rpJoin.getReportSubType();
                reportCategory = rpJoin.getReportCategory();
                description = rpJoin.getDescription();
                reportSql = rpJoin.getReportSql();
                coreReport = rpJoin.getCoreReport();
                useReport = rpJoin.getUseReport();

                if (rpJoin.getReportParameterId() != null) {
                    // report has at least one parameter
                    reportParameters = new ArrayList<>();
                    reportParameters.add(new ReportParameterData(rpJoin.getReportParameterId(), rpJoin.getParameterId(),
                            rpJoin.getReportParameterName(), rpJoin.getParameterName()));
                } else {
                    reportParameters = null;
                }
            }

        }
        // write last report
        reportList.add(new ReportData(reportId, reportName, reportType, reportSubType, reportCategory, description, reportSql, coreReport,
                useReport, reportParameters));

        return reportList;
    }

    @Override
    public Collection<ReportParameterData> getAllowedParameters() {
        final ReportParameterMapper rm = new ReportParameterMapper();
        final String sql = rm.schema();
        final Collection<ReportParameterData> parameters = this.jdbcTemplate.query(sql, rm);
        return parameters;
    }

    private static final class ReportParameterJoinMapper implements RowMapper<ReportParameterJoinData> {

        public String schema(final Long reportId) {

            String sql = "select r.id as reportId, r.report_name as reportName, r.report_type as reportType, "
                    + " r.report_subtype as reportSubType, r.report_category as reportCategory, r.description, r.core_report as coreReport, r.use_report as useReport, "
                    + " rp.id as reportParameterId, rp.parameter_id as parameterId, rp.report_parameter_name as reportParameterName, p.parameter_name as parameterName";

            if (reportId != null) {
                sql += ", r.report_sql as reportSql ";
            }

            sql += " from stretchy_report r" + " left join stretchy_report_parameter rp on rp.report_id = r.id"
                    + " left join stretchy_parameter p on p.id = rp.parameter_id";
            if (reportId != null) {
                sql += " where r.id = ?";
            } else {
                sql += " order by r.id, rp.parameter_id";
            }

            return sql;

            /*
             * used to only return reports that the use can run as done in report UI but not necessary as there is a
             * read_report permission which should give user access to look all reports + " where exists" +
             * " (select 'f'" + " from m_appuser_role ur " + " join m_role r on r.id = ur.role_id" +
             * " left join m_role_permission rp on rp.role_id = r.id" +
             * " left join m_permission p on p.id = rp.permission_id" + " where ur.appuser_id = " + userId +
             * " and (p.code in ('ALL_FUNCTIONS', 'ALL_FUNCTIONS_READ') or p.code = concat('READ_', r.report_name))) " ;
             */
        }

        @Override
        public ReportParameterJoinData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Long reportId = rs.getLong("reportId");
            final String reportName = rs.getString("reportName");
            final String reportType = rs.getString("reportType");
            final String reportSubType = rs.getString("reportSubType");
            final String reportCategory = rs.getString("reportCategory");
            final String description = rs.getString("description");
            final Boolean coreReport = rs.getBoolean("coreReport");
            final Boolean useReport = rs.getBoolean("useReport");

            String reportSql;
            // reportSql might not be on the select list of columns
            try {
                reportSql = rs.getString("reportSql");
            } catch (final SQLException e) {
                reportSql = null;
            }

            final Long reportParameterId = JdbcSupport.getLong(rs, "reportParameterId");
            final Long parameterId = JdbcSupport.getLong(rs, "parameterId");
            final String reportParameterName = rs.getString("reportParameterName");
            final String parameterName = rs.getString("parameterName");

            return new ReportParameterJoinData(reportId, reportName, reportType, reportSubType, reportCategory, description, reportSql,
                    coreReport, useReport, reportParameterId, parameterId, reportParameterName, parameterName);
        }
    }

    private static final class ReportParameterMapper implements RowMapper<ReportParameterData> {

        public String schema() {
            return "select p.id as id, p.parameter_name as parameterName from stretchy_parameter p where coalesce(p.special,'') != 'Y' order by p.id";
        }

        @Override
        public ReportParameterData mapRow(final ResultSet rs, final int rowNum) throws SQLException {

            final Long id = rs.getLong("id");
            final String parameterName = rs.getString("parameterName");

            return new ReportParameterData(id, null, null, parameterName);
        }
    }

    @Override
    public GenericResultsetData retrieveGenericResultSetForSmsEmailCampaign(String name, String type, Map<String, String> queryParams) {
        final long startTime = System.currentTimeMillis();
        log.info("STARTING REPORT: {}   Type: {}", name, type);

        final String sql = sqlToRunForSmsEmailCampaign(name, type, queryParams);

        final GenericResultsetData result = this.genericDataService.fillGenericResultSet(sql);

        final long elapsed = System.currentTimeMillis() - startTime;
        log.info("FINISHING Report/Request Name: {} - {}     Elapsed Time: {}", name, type, elapsed);
        return result;
    }

    private String sqlToRunForSmsEmailCampaign(final String name, final String type, final Map<String, String> queryParams) {
        String sql = getSql(name, type);

        final Set<String> keys = queryParams.keySet();
        for (String key : keys) {
            final String pValue = queryParams.get(key);
            key = "${" + key + "}";
            sql = this.genericDataService.replace(sql, key, pValue);
        }

        sql = this.genericDataService.wrapSQL(sql);

        return sql;
    }

    @Override
    public ByteArrayOutputStream generatePentahoReportAsOutputStream(final String reportName, final String outputTypeParam,
            final Map<String, String> queryParams, final Locale locale, final AppUser runReportAsUser, final StringBuilder errorLog) {
        // This complete implementation should be moved to Pentaho Report
        // Service
        /*
         * String outputType = "HTML"; if (StringUtils.isNotBlank(outputTypeParam)) { outputType = outputTypeParam; }
         *
         * if (!(outputType.equalsIgnoreCase("HTML") || outputType.equalsIgnoreCase("PDF") ||
         * outputType.equalsIgnoreCase("XLS") || outputType .equalsIgnoreCase("CSV"))) { throw new
         * PlatformDataIntegrityException("error.msg.invalid.outputType", "No matching Output Type: " + outputType); }
         *
         * if (this.noPentaho) { throw new PlatformDataIntegrityException("error.msg.no.pentaho",
         * "Pentaho is not enabled", "Pentaho is not enabled"); }
         *
         * final String reportPath = FileSystemContentRepository.FINERACT_BASE_DIR + File.separator + "pentahoReports" +
         * File.separator + reportName + ".prpt"; LOG.info("Report path: {}", reportPath);
         *
         * // load report definition final ResourceManager manager = new ResourceManager(); manager.registerDefaults();
         * Resource res;
         *
         * try { res = manager.createDirectly(reportPath, MasterReport.class); final MasterReport masterReport =
         * (MasterReport) res.getResource(); final DefaultReportEnvironment reportEnvironment =
         * (DefaultReportEnvironment) masterReport.getReportEnvironment();
         *
         * if (locale != null) { reportEnvironment.setLocale(locale); } addParametersToReport(masterReport, queryParams,
         * runReportAsUser, errorLog);
         *
         * final ByteArrayOutputStream baos = new ByteArrayOutputStream();
         *
         * if ("PDF".equalsIgnoreCase(outputType)) { PdfReportUtil.createPDF(masterReport, baos); return baos; }
         *
         * if ("XLS".equalsIgnoreCase(outputType)) { ExcelReportUtil.createXLS(masterReport, baos); return baos; }
         *
         * if ("CSV".equalsIgnoreCase(outputType)) { CSVReportUtil.createCSV(masterReport, baos, "UTF-8"); return baos;
         * }
         *
         * if ("HTML".equalsIgnoreCase(outputType)) { HtmlReportUtil.createStreamHTML(masterReport, baos); return baos;
         * }
         *
         * } catch (final ResourceException e) { errorLog.
         * append("ReadReportingServiceImpl.generatePentahoReportAsOutputStream method threw a Pentaho ResourceException "
         * + "exception: " + e.getMessage() + " ---------- "); throw new
         * PlatformDataIntegrityException("error.msg.reporting.error", e.getMessage()); } catch (final
         * ReportProcessingException e) { errorLog.
         * append("ReadReportingServiceImpl.generatePentahoReportAsOutputStream method threw a Pentaho ReportProcessingException "
         * + "exception: " + e.getMessage() + " ---------- "); throw new
         * PlatformDataIntegrityException("error.msg.reporting.error", e.getMessage()); } catch (final IOException e) {
         * errorLog. append("ReadReportingServiceImpl.generatePentahoReportAsOutputStream method threw an IOException "
         * + "exception: " + e.getMessage() + " ---------- "); throw new
         * PlatformDataIntegrityException("error.msg.reporting.error", e.getMessage()); }
         *
         * errorLog.
         * append("ReadReportingServiceImpl.generatePentahoReportAsOutputStream method threw a PlatformDataIntegrityException "
         * + "exception: No matching Output Type: " + outputType + " ---------- "); throw new
         * PlatformDataIntegrityException("error.msg.invalid.outputType", "No matching Output Type: " + outputType);
         *
         */
        return null;
    }

    @Override
    public byte[] retrieveReportXLSX(final String reportName, final String type, final Map<String, String> queryParams,
            final boolean isSelfServiceUserReport, final Integer limit, final Integer offset) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Create a sheet
            Sheet sheet = workbook.createSheet("Sheet 1");

            // Retrieve data from your service
            final GenericResultsetData result = retrieveGenericResultset(reportName, type, queryParams, isSelfServiceUserReport, limit,
                    offset, false);

            // Generate header row
            List<ResultsetColumnHeaderData> columnHeaders = result.getColumnHeaders();
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columnHeaders.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnHeaders.get(i).getColumnName());
            }

            // Generate data rows
            List<ResultsetRowData> data = result.getData();
            for (int i = 0; i < data.size(); i++) {
                Row dataRow = sheet.createRow(i + 1);
                List<String> row = data.get(i).getRow();
                for (int j = 0; j < row.size(); j++) {
                    Cell cell = dataRow.createCell(j);
                    String currColType = columnHeaders.get(j).getColumnType();
                    String currVal = row.get(j);
                    if (currVal != null) {
                        if (currColType.equals("DECIMAL") || currColType.equals("DOUBLE") || currColType.equals("BIGINT")
                                || currColType.equals("SMALLINT") || currColType.equals("INT")) {

                            cell.setCellValue(Double.parseDouble(currVal));
                        } else {
                            cell.setCellValue(genericDataService.replace(currVal, "\"", "\"\""));
                        }
                    }
                }
            }

            // Write workbook to the output stream
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (final IOException e) {
            throw new PlatformDataIntegrityException("error.msg.reporting.error", "Table Report failed: " + e.getMessage());
        }
    }

    private static final class ReportSql {

        private final String name;
        private final String sql;

        private ReportSql(final String name, final String sql) {
            this.name = name;
            this.sql = sql;
        }
    }

    static final class ExportMetrics {

        private long rows;
        private long queryMs;
        private long streamMs;
        private long tmpDiskTables;

        long getRows() {
            return rows;
        }
    }

    private static final class CountingOutputStream extends FilterOutputStream {

        private long count;

        private CountingOutputStream(final OutputStream out) {
            super(out);
        }

        @Override
        public void write(final int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        private long getCount() {
            return count;
        }
    }

}
