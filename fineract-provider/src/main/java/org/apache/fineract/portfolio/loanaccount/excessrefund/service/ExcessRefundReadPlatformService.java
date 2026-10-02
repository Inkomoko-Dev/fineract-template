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
package org.apache.fineract.portfolio.loanaccount.excessrefund.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundData;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundPaymentMode;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExcessRefundReadPlatformService {

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;
    private final LoanRepositoryWrapper loanRepository;

    public ExcessRefundData retrieveOne(final Long id) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_EXCESS_REFUND");
        final ExcessRefundMapper mapper = new ExcessRefundMapper();
        final List<ExcessRefundData> results = this.jdbcTemplate.query(mapper.schema() + " where r.id = ?", mapper, id);
        if (results.isEmpty()) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.not.found", "Excess refund not found: " + id);
        }
        return results.get(0);
    }

    public Collection<ExcessRefundData> retrieveAll(final Long loanId, final Integer status, final Long batchId) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_EXCESS_REFUND");
        final ExcessRefundMapper mapper = new ExcessRefundMapper();
        final StringBuilder sql = new StringBuilder(mapper.schema());
        final List<Object> params = new ArrayList<>();
        sql.append(" where 1=1");
        if (loanId != null) {
            sql.append(" and r.loan_id = ?");
            params.add(loanId);
        }
        if (status != null) {
            sql.append(" and r.status_enum = ?");
            params.add(status);
        }
        if (batchId != null) {
            sql.append(" and r.batch_id = ?");
            params.add(batchId);
        }
        sql.append(" order by r.submitted_at desc");
        return this.jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    public Map<String, Object> retrieveTemplate(final Long loanId) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_EXCESS_REFUND");
        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(loanId);
        final Map<String, Object> template = new HashMap<>();
        template.put("loanId", loan.getId());
        template.put("accountNo", loan.getAccountNumber());
        template.put("clientId", loan.getClientId());
        template.put("currencyCode", loan.getCurrencyCode());
        template.put("totalOverpaid", loan.getTotalOverpaid() == null ? BigDecimal.ZERO : loan.getTotalOverpaid());
        template.put("paymentModeOptions", List.of("MANUAL", "PAYMENT_HUB"));
        template.put("channelOptions", List.of("BANK_TRANSFER", "MOBILE_MONEY"));
        return template;
    }

    public String exportCsv(final Integer status) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_EXCESS_REFUND");
        final Collection<ExcessRefundData> rows = retrieveAll(null, status, null);
        final StringBuilder csv = new StringBuilder(
                "id,loanId,loanAccountNo,clientId,amount,currency,status,paymentMode,hubRequestId,manualPaymentRef,paidOn,submittedAt\n");
        for (final ExcessRefundData row : rows) {
            csv.append(row.getId()).append(',').append(row.getLoanId()).append(',').append(nullToEmpty(row.getLoanAccountNo())).append(',')
                    .append(row.getClientId()).append(',').append(row.getAmount()).append(',').append(row.getCurrencyCode()).append(',')
                    .append(row.getStatusLabel()).append(',').append(row.getPaymentModeLabel()).append(',')
                    .append(nullToEmpty(row.getHubRequestId())).append(',').append(nullToEmpty(row.getManualPaymentRef())).append(',')
                    .append(row.getPaidOn() == null ? "" : row.getPaidOn()).append(',')
                    .append(row.getSubmittedAt() == null ? "" : row.getSubmittedAt()).append('\n');
        }
        return csv.toString();
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value.replace(',', ' ');
    }

    private static final class ExcessRefundMapper implements RowMapper<ExcessRefundData> {

        public String schema() {
            return "select r.id as id, r.loan_id as loanId, l.account_no as loanAccountNo, r.client_id as clientId, "
                    + "c.display_name as clientDisplayName, r.batch_id as batchId, r.amount as amount, r.currency_code as currencyCode, "
                    + "r.payment_mode_enum as paymentMode, r.payment_type_id as paymentTypeId, r.status_enum as status, "
                    + "r.beneficiary_json as beneficiaryJson, r.hub_request_id as hubRequestId, r.hub_transaction_ref as hubTransactionRef, "
                    + "r.manual_payment_ref as manualPaymentRef, r.paid_on as paidOn, r.submitted_by as submittedById, "
                    + "u.username as submittedByUsername, r.submitted_at as submittedAt, r.approved_by as approvedById, "
                    + "r.approved_at as approvedAt, r.posted_transaction_id as postedTransactionId, r.failure_reason as failureReason, "
                    + "l.total_overpaid_derived as loanTotalOverpaid "
                    + "from m_loan_excess_refund r "
                    + "join m_loan l on l.id = r.loan_id "
                    + "left join m_client c on c.id = r.client_id "
                    + "left join m_appuser u on u.id = r.submitted_by";
        }

        @Override
        public ExcessRefundData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Integer status = JdbcSupport.getInteger(rs, "status");
            final Integer paymentMode = JdbcSupport.getInteger(rs, "paymentMode");
            final LoanExcessRefundStatus statusEnum = LoanExcessRefundStatus.fromInt(status);
            final LoanExcessRefundPaymentMode modeEnum = LoanExcessRefundPaymentMode.fromInt(paymentMode);
            final LocalDate paidOn = JdbcSupport.getLocalDate(rs, "paidOn");
            final LocalDateTime submittedAt = rs.getTimestamp("submittedAt") == null ? null
                    : rs.getTimestamp("submittedAt").toLocalDateTime();
            final LocalDateTime approvedAt = rs.getTimestamp("approvedAt") == null ? null
                    : rs.getTimestamp("approvedAt").toLocalDateTime();
            return ExcessRefundData.builder().id(rs.getLong("id")).loanId(rs.getLong("loanId")).loanAccountNo(rs.getString("loanAccountNo"))
                    .clientId(rs.getLong("clientId")).clientDisplayName(rs.getString("clientDisplayName"))
                    .batchId(JdbcSupport.getLong(rs, "batchId")).amount(rs.getBigDecimal("amount")).currencyCode(rs.getString("currencyCode"))
                    .paymentMode(paymentMode).paymentModeLabel(modeEnum == null ? null : modeEnum.name()).paymentTypeId(JdbcSupport.getLong(rs, "paymentTypeId"))
                    .status(status).statusLabel(statusEnum == null ? null : statusEnum.name()).beneficiaryJson(rs.getString("beneficiaryJson"))
                    .hubRequestId(rs.getString("hubRequestId")).hubTransactionRef(rs.getString("hubTransactionRef"))
                    .manualPaymentRef(rs.getString("manualPaymentRef")).paidOn(paidOn).submittedById(JdbcSupport.getLong(rs, "submittedById"))
                    .submittedByUsername(rs.getString("submittedByUsername")).submittedAt(submittedAt)
                    .approvedById(JdbcSupport.getLong(rs, "approvedById")).approvedAt(approvedAt)
                    .postedTransactionId(JdbcSupport.getLong(rs, "postedTransactionId")).failureReason(rs.getString("failureReason"))
                    .loanTotalOverpaid(rs.getBigDecimal("loanTotalOverpaid")).build();
        }
    }
}
