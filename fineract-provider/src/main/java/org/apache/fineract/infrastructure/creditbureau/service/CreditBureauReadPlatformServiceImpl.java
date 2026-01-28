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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.creditbureau.data.CreditBureauData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.CRBPostingLoggerData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

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

//            final Timestamp createdTs = rs.getTimestamp("createdDate");
//            if (createdTs != null) {
//                logger.setCreatedDate(
//                        OffsetDateTime.from(LocalDate.ofInstant(createdTs.toInstant(), ZoneId.systemDefault()))
//                );
//            }
//            logger.setLastModifiedDate(
//                    rs.getTimestamp("lastModifiedDate") != null
//                            ? OffsetDateTime.from(rs.getTimestamp("lastModifiedDate").toLocalDateTime())
//                            : null
//            );

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




}
