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
package org.apache.fineract.infrastructure.creditbureau.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.creditbureau.data.CreditBureauData;
import org.apache.fineract.infrastructure.creditbureau.domain.CrbPostingLogReportData;
import org.apache.fineract.infrastructure.creditbureau.domain.TransUnionCreditReportCsvData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.CRBPostingLoggerData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MultivaluedMap;

@Service
public class CreditBureauReadPlatformServiceImpl implements CreditBureauReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final LoanRepositoryWrapper loanRepositoryWrapper;

    @Autowired
    public CreditBureauReadPlatformServiceImpl(final PlatformSecurityContext context, final JdbcTemplate jdbcTemplate, LoanRepositoryWrapper loanRepositoryWrapper) {
        this.context = context;
        this.jdbcTemplate = jdbcTemplate;
        this.loanRepositoryWrapper = loanRepositoryWrapper;
    }

    private static final class CBMapper implements RowMapper<CreditBureauData> {

        public String schema() {
            return "cb.id as creditBureauID,cb.name as creditBureauName,cb.product as creditBureauProduct,"
                    + "cb.country as country,concat(cb.product,' - ',cb.name,' - ',cb.country) as cbSummary,cb.implementation_key as implementationKey from m_creditbureau cb";
        }

        @Override
        public CreditBureauData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final long id = rs.getLong("creditBureauID");
            final String name = rs.getString("creditBureauName");
            final String product = rs.getString("creditBureauProduct");
            final String country = rs.getString("country");
            final String cbSummary = rs.getString("cbSummary");
            final long implementationKey = rs.getLong("implementationKey");

            return CreditBureauData.instance(id, name, country, product, cbSummary, implementationKey);
        }
    }

    @Override
    public Collection<CreditBureauData> retrieveCreditBureau() {
        this.context.authenticatedUser();

        final CBMapper rm = new CBMapper();
        final String sql = "select " + rm.schema() + " order by id";

        return this.jdbcTemplate.query(sql, rm); // NOSONAR
    }

    @Override
    public List<CRBPostingLoggerData> retrieveCrbPostingLogs() {
        final CRBPostingLoggerRowMapper rm = new CRBPostingLoggerRowMapper();

        final String sql = "select "+ rm.schema() +"order by cpl.date desc";

        return this.jdbcTemplate.query(sql, rm);
    }

    @Override
    public void markCRBLogAsFixed(String loanId) {
        Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(Long.valueOf(loanId));

        loan.setStopConsumerCreditUploadToTransUnion(Boolean.FALSE);
        loan.setStopConsumerCreditUploadToTransUnionOn(DateUtils.getBusinessLocalDate());

        loanRepositoryWrapper.saveAndFlush(loan);
    }


    private static final class CRBPostingLoggerRowMapper
            implements RowMapper<CRBPostingLoggerData> {

        public String schema() {
            return """
                    cpl.id as id,
                    cpl.batch_id as batchId,
                    cpl.has_passed as hasPassed,
                    cpl.loan_id as loanId,
                    l.account_no as loanAccountNumber,
                    cpl.crb_response_id as crbResponseId,
                    cpl.error_logs as errorLogs,
                    cpl.pay_load as payload,
                    cpl.date as date,
                    cpl.created_on_utc as createdDate,
                    cpl.last_modified_on_utc as lastModifiedDate
                    from m_crb_posting_logger cpl
                    join m_loan l on cpl.loan_id = l.id
                    """;
        }

        @Override
        public CRBPostingLoggerData mapRow(final ResultSet rs, final int rowNum)
                throws SQLException {

            final CRBPostingLoggerData logger = new CRBPostingLoggerData();

            logger.setBatchId(rs.getString("batchId"));
            logger.setHasPassed(rs.getBoolean("hasPassed"));
            logger.setLoanId(rs.getInt("loanId"));
            logger.setLoanAccountNumber(rs.getString("loanAccountNumber"));
            logger.setCrbResponseId(rs.getString("crbResponseId"));
            logger.setErrorLogs(rs.getString("errorLogs"));
            logger.setPayload(rs.getString("payload"));
            logger.setDate(rs.getDate("date").toLocalDate());

            return logger;
        }
    }

    @Override
    public TransUnionCreditReportCsvData generateCsvReport(MultivaluedMap<String, String> queryParameters) {
        LocalDate fromDate = null;
        LocalDate toDate = null;
        Boolean posted = null;

        if (queryParameters.getFirst("fromDate") != null) {
            fromDate = LocalDate.parse(queryParameters.getFirst("fromDate"));
        }

        if (queryParameters.getFirst("toDate") != null) {
            toDate = LocalDate.parse(queryParameters.getFirst("toDate"));
        }

        if (queryParameters.getFirst("posted") != null) {
            posted = Boolean.valueOf(queryParameters.getFirst("posted"));
        }
        List<CrbPostingLogReportData> logs = this.fetchLogs(fromDate, toDate, posted);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writer.println("Loan ID,Batch ID,Loan Account No,Posted,Date Posted,Client Type,Days in Arrears,Last Payment Date,Error Logs");

        for (CrbPostingLogReportData log : logs) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,\"%s\"%n",
                    log.getLoanId(),
                    log.getBatchId(),
                    log.getLoanAccountNumber(),
                    log.getPosted() ? "Yes" : "No",
                    log.getDatePosted(),
                    log.getClientType(),
                    log.getDaysInArrears(),
                    log.getLastPaymentDate(),
                    log.getErrorLogs() != null ? log.getErrorLogs().replace("\"", "\"\"") : ""
            );
        }

        writer.flush();

        return new TransUnionCreditReportCsvData(
                new ByteArrayInputStream(out.toByteArray()),
                "crb-posting-logs-" + LocalDate.now(ZoneId.systemDefault()),
                "text/csv"
        );
    }

    public List<CrbPostingLogReportData> fetchLogs(LocalDate fromDate, LocalDate toDate, Boolean posted) {

        final CrbPostingLogReportRowMapper rm = new CrbPostingLogReportRowMapper();

        final String sql = rm.schema();

        return this.jdbcTemplate.query(sql, rm);
    }

    private static final class CrbPostingLogReportRowMapper
            implements RowMapper<CrbPostingLogReportData> {

        public String schema() {
            return """
                SELECT
                mcpl.loan_id,
                ml.account_no AS loan_account_number,
                mcpl.batch_id,
                mcpl.has_passed AS posted,
                mcpl.`date` AS date_posted,
                mcpl.error_logs,
                CASE
                    WHEN mc.legal_form_enum = 1 THEN 'Individual'
                    WHEN mc.legal_form_enum = 2 THEN 'Corporate'
                END AS client_type,
                CASE
                    WHEN mlaa.overdue_since_date_derived IS NULL THEN 0
                    ELSE DATEDIFF(CURDATE(), mlaa.overdue_since_date_derived)
                END AS days_in_arrears,
                last_payment.last_payment_date
            FROM m_crb_posting_logger mcpl
            JOIN m_loan ml ON ml.id = mcpl.loan_id
            JOIN m_client mc ON mc.id = ml.client_id
            LEFT JOIN m_loan_arrears_aging mlaa ON mlaa.loan_id = ml.id
            LEFT JOIN (
                SELECT loan_id, MAX(transaction_date) AS last_payment_date
                FROM m_loan_transaction
                WHERE transaction_type_enum = 2
                GROUP BY loan_id
            ) last_payment ON last_payment.loan_id = ml.id
            WHERE 1=1
            """;
        }

        @Override
        public CrbPostingLogReportData mapRow(final ResultSet rs, final int rowNum)
                throws SQLException {

            return new CrbPostingLogReportData(
                    rs.getLong("loan_id"),
                    rs.getString("batch_id"),
                    rs.getString("loan_account_number"),
                    rs.getBoolean("posted"),
                    rs.getDate("date_posted") != null ? rs.getDate("date_posted").toLocalDate() : null,
                    rs.getString("error_logs"),
                    rs.getString("client_type"),
                    rs.getInt("days_in_arrears"),
                    rs.getDate("last_payment_date") != null ? rs.getDate("last_payment_date").toLocalDate() : null
            );
        }
    }


}
