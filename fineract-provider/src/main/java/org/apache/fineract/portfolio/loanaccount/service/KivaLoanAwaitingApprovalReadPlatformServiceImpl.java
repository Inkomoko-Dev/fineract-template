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
package org.apache.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.portfolio.loanaccount.data.KivaLoanAwaitingApprovalData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KivaLoanAwaitingApprovalReadPlatformServiceImpl implements KivaLoanAwaitingApprovalReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Collection<KivaLoanAwaitingApprovalData> retrieveAllKivaLoanAwaitingApproval() {
        final KivaLoanMapper mapper = new KivaLoanMapper();
        final String sql = "select " + mapper.schema() + " order by mklaa.id ";
        return this.jdbcTemplate.query(sql, mapper, new Object[] {});
    }

    @Override
    public Collection<KivaLoanAwaitingApprovalData> retrieveNotReportedLoans() {
        final NotReportedLoanMapper mapper = new NotReportedLoanMapper();
        final String sql = "select " + mapper.schema();
        return this.jdbcTemplate.query(sql, mapper, new Object[] {});
    }

    private static final class KivaLoanMapper implements RowMapper<KivaLoanAwaitingApprovalData> {

        public String schema() {
            return " mklaa.id as id, mklaa.internal_client_id AS client_id,mklaa.internal_loan_id AS loan_id, "
                    + " ml.principal_repaid_derived AS amount " + " FROM m_kiva_loan_awaiting_approval mklaa "
                    + " INNER JOIN m_loan ml on mklaa.internal_loan_id = ml.kiva_id";
        }

        @Override
        public KivaLoanAwaitingApprovalData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final BigDecimal amount = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "amount");
            final String loan_id = rs.getString("loan_id");
            final String client_id = rs.getString("client_id");

            return new KivaLoanAwaitingApprovalData(loan_id, client_id, amount);
        }
    }

    private static final class NotReportedLoanMapper implements RowMapper<KivaLoanAwaitingApprovalData> {

        public String schema() {
            return " mklaa.id as id, mklaa.internal_client_id AS client_id, mklaa.internal_loan_id AS loan_id, "
                    + " 0 AS amount " + " FROM m_kiva_loan_awaiting_approval mklaa "
                    + " LEFT JOIN m_loan ml on mklaa.internal_loan_id = ml.kiva_id " + " WHERE ml.id IS NULL";
        }

        @Override
        public KivaLoanAwaitingApprovalData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final BigDecimal amount = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "amount");
            final String loan_id = rs.getString("loan_id");
            final String client_id = rs.getString("client_id");

            return new KivaLoanAwaitingApprovalData(loan_id, client_id, amount);
        }
    }
}
