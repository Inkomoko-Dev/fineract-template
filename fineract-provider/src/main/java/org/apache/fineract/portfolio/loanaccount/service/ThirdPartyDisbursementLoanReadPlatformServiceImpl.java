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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementLoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementLoanByClientData;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementLoanData;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementRepaymentData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSubStatus;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThirdPartyDisbursementLoanReadPlatformServiceImpl implements ThirdPartyDisbursementLoanReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PaginationHelper paginationHelper;

    @Override
    public Page<ThirdPartyDisbursementLoanData> retrieveAll(final String provider, final String status, final Boolean readyForInstruction,
            final String loanAccountNo, final String externalId, final Integer offset, final Integer limit) {
        final String normalizedProvider = ThirdPartyDisbursementProvider.normalize(provider);
        if (StringUtils.isBlank(normalizedProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.provider.required",
                    "Disbursement provider query parameter is required.",
                    List.of(ApiParameterError.parameterError("validation.msg.thirdPartyDisbursementLoan.provider.required",
                            "Disbursement provider query parameter is required.", ThirdPartyDisbursementLoanApiConstants.PROVIDER,
                            provider)));
        }
        final Integer parsedStatusId = parseStatusId(status);
        final Integer effectiveStatusId = parsedStatusId != null ? parsedStatusId : LoanStatus.APPROVED.getValue();
        final int safeOffset = offset == null || offset < 0 ? 0 : offset;
        final int safeLimit = limit == null || limit <= 0 ? 15 : Math.min(limit, 200);

        final StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("select ").append(this.sqlGenerator.calcFoundRows()).append(" ");
        sqlBuilder.append(buildSelectColumns());
        sqlBuilder.append(" from m_loan l ");
        sqlBuilder.append(" join m_product_loan lp on lp.id = l.product_id ");
        sqlBuilder.append(" left join m_client c on c.id = l.client_id ");
        sqlBuilder.append(" where lp.enable_third_party_disbursement = true ");
        sqlBuilder.append(" and l.third_party_disbursement_provider is not null ");
        sqlBuilder.append(" and upper(trim(l.third_party_disbursement_provider)) = ? ");

        final List<Object> params = new ArrayList<>();
        params.add(normalizedProvider);
        sqlBuilder.append(" and l.loan_status_id = ? ");
        params.add(effectiveStatusId);

        if (Boolean.TRUE.equals(readyForInstruction)) {
            sqlBuilder.append(" and l.loan_sub_status_id is null ");
            sqlBuilder.append(" and not exists (select 1 from m_loan_disbursement_instruction i ");
            sqlBuilder.append(" where i.loan_id = l.id and i.status in ('RECEIVED', 'PENDING_DISBURSEMENT')) ");
        }
        if (StringUtils.isNotBlank(loanAccountNo)) {
            sqlBuilder.append(" and l.account_no = ? ");
            params.add(loanAccountNo.trim());
        }
        if (StringUtils.isNotBlank(externalId)) {
            sqlBuilder.append(" and l.external_id = ? ");
            params.add(externalId.trim());
        }

        sqlBuilder.append(" order by l.approvedon_date desc, l.id desc ");
        sqlBuilder.append(this.sqlGenerator.limit(safeLimit, safeOffset));

        return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), params.toArray(), new ThirdPartyDisbursementLoanMapper());
    }

    @Override
    public ThirdPartyDisbursementLoanByClientData retrieveByClient(final String provider, final String status, final Long clientId,
            final String clientAccountNo, final String clientExternalId, final String phone, final Integer offset, final Integer limit) {
        final String normalizedProvider = requireProvider(provider);
        final Integer parsedStatusId = "ALL".equalsIgnoreCase(StringUtils.trimToEmpty(status)) ? null : parseStatusId(status);
        final Integer statusId = "ALL".equalsIgnoreCase(StringUtils.trimToEmpty(status)) ? null
                : (parsedStatusId == null ? LoanStatus.APPROVED.getValue() : parsedStatusId);
        final int safeOffset = offset == null || offset < 0 ? 0 : offset;
        final int safeLimit = limit == null || limit <= 0 ? 15 : Math.min(limit, 200);
        final ClientLookup lookup = resolveClientLookup(clientId, clientAccountNo, clientExternalId, phone);

        final List<Object> params = new ArrayList<>();
        final String fromWhere = buildProviderClientFromWhere(normalizedProvider, statusId, lookup, params);
        final String loanSql = "select " + buildSelectColumns() + fromWhere
                + " order by l.approvedon_date desc, l.id desc " + this.sqlGenerator.limit(safeLimit, safeOffset);
        final List<ThirdPartyDisbursementLoanData> loans = this.jdbcTemplate.query(loanSql, new ThirdPartyDisbursementLoanMapper(),
                params.toArray());

        final String totalsSql = "select l.currency_code as currencyCode, coalesce(sum(l.approved_principal), 0) as totalApprovedPrincipal, "
                + "coalesce(sum(l.total_outstanding_derived), 0) as totalOutstanding" + fromWhere
                + " group by l.currency_code order by l.currency_code";
        final List<ThirdPartyDisbursementLoanByClientData.CurrencyTotal> totals = this.jdbcTemplate.query(totalsSql,
                (rs, rowNum) -> new ThirdPartyDisbursementLoanByClientData.CurrencyTotal(rs.getString("currencyCode"),
                        rs.getBigDecimal("totalApprovedPrincipal"), rs.getBigDecimal("totalOutstanding")),
                params.toArray());
        return new ThirdPartyDisbursementLoanByClientData(loans, totals);
    }

    @Override
    public Page<ThirdPartyDisbursementRepaymentData> retrieveRepayments(final String provider, final Long loanId,
            final LocalDate fromDate, final LocalDate toDate, final boolean includeReversed, final Integer offset, final Integer limit) {
        final String normalizedProvider = requireProvider(provider);
        if (loanId == null || loanId <= 0) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.loanId.invalid",
                    "loanId must be a positive number.",
                    List.of(ApiParameterError.parameterErrorWithValue("validation.msg.thirdPartyDisbursementLoan.loanId.invalid",
                            "loanId must be a positive number.", "loanId", String.valueOf(loanId))));
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.transactionDate.invalid",
                    "fromDate must not be after toDate.", List.of(ApiParameterError.generalError(
                            "validation.msg.thirdPartyDisbursementLoan.transactionDate.invalid", "fromDate must not be after toDate.")));
        }
        final int safeOffset = offset == null || offset < 0 ? 0 : offset;
        final int safeLimit = limit == null || limit <= 0 ? 15 : Math.min(limit, 200);
        final List<Object> params = new ArrayList<>();
        final StringBuilder sql = new StringBuilder("select ").append(this.sqlGenerator.calcFoundRows()).append(" ")
                .append("tr.id as transactionId, tr.original_transaction_id as originalTransactionId, tr.transaction_date as transactionDate, ")
                .append("tr.amount as amount, tr.principal_portion_derived as principalPortion, ")
                .append("tr.interest_portion_derived as interestPortion, tr.fee_charges_portion_derived as feePortion, ")
                .append("tr.penalty_charges_portion_derived as penaltyPortion, tr.outstanding_loan_balance_derived as outstandingBalance, ")
                .append("l.currency_code as currencyCode, tr.is_reversed as reversed, tr.is_reversal as reversalTransaction ")
                .append("from m_loan_transaction tr join m_loan l on l.id = tr.loan_id ")
                .append("join m_product_loan lp on lp.id = l.product_id where l.id = ? ")
                .append("and lp.enable_third_party_disbursement = true ")
                .append("and upper(trim(l.third_party_disbursement_provider)) = ? ")
                .append("and tr.transaction_type_enum = ? ");
        params.add(loanId);
        params.add(normalizedProvider);
        params.add(LoanTransactionType.REPAYMENT.getValue());
        if (fromDate != null) {
            sql.append("and tr.transaction_date >= ? ");
            params.add(fromDate);
        }
        if (toDate != null) {
            sql.append("and tr.transaction_date <= ? ");
            params.add(toDate);
        }
        if (!includeReversed) {
            sql.append("and tr.is_reversed = false and tr.is_reversal = false ");
        }
        sql.append("order by tr.transaction_date asc, tr.created_on_utc asc, tr.id asc ");
        sql.append(this.sqlGenerator.limit(safeLimit, safeOffset));
        return this.paginationHelper.fetchPage(this.jdbcTemplate, sql.toString(), params.toArray(), (rs, rowNum) ->
                new ThirdPartyDisbursementRepaymentData(rs.getLong("transactionId"), JdbcSupport.getLong(rs, "originalTransactionId"),
                        JdbcSupport.getLocalDate(rs, "transactionDate"), rs.getBigDecimal("amount"),
                        rs.getBigDecimal("principalPortion"), rs.getBigDecimal("interestPortion"), rs.getBigDecimal("feePortion"),
                        rs.getBigDecimal("penaltyPortion"), rs.getBigDecimal("outstandingBalance"), rs.getString("currencyCode"),
                        rs.getBoolean("reversed"), rs.getBoolean("reversalTransaction")));
    }

    private String requireProvider(final String provider) {
        final String normalizedProvider = ThirdPartyDisbursementProvider.normalize(provider);
        if (StringUtils.isBlank(normalizedProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.provider.required",
                    "Disbursement provider query parameter is required.",
                    List.of(ApiParameterError.parameterError("validation.msg.thirdPartyDisbursementLoan.provider.required",
                            "Disbursement provider query parameter is required.", ThirdPartyDisbursementLoanApiConstants.PROVIDER,
                            provider)));
        }
        return normalizedProvider;
    }

    private static ClientLookup resolveClientLookup(final Long clientId, final String clientAccountNo, final String clientExternalId,
            final String phone) {
        final List<ClientLookup> lookups = new ArrayList<>();
        if (clientId != null) {
            if (clientId <= 0) {
                throw invalidClientLookup("clientId must be a positive number.");
            }
            lookups.add(new ClientLookup("c.id", clientId));
        }
        addStringLookup(lookups, "c.account_no", clientAccountNo);
        addStringLookup(lookups, "c.external_id", clientExternalId);
        addStringLookup(lookups, "c.mobile_no", phone);
        if (lookups.size() != 1) {
            throw invalidClientLookup("Exactly one of clientId, clientAccountNo, clientExternalId, or phone is required.");
        }
        return lookups.get(0);
    }

    private static void addStringLookup(final List<ClientLookup> lookups, final String column, final String value) {
        if (StringUtils.isNotBlank(value)) {
            lookups.add(new ClientLookup(column, value.trim()));
        }
    }

    private static PlatformApiDataValidationException invalidClientLookup(final String message) {
        return new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.clientLookup.invalid", message,
                List.of(ApiParameterError.generalError("validation.msg.thirdPartyDisbursementLoan.clientLookup.invalid", message)));
    }

    private static String buildProviderClientFromWhere(final String provider, final Integer statusId, final ClientLookup lookup,
            final List<Object> params) {
        final StringBuilder sql = new StringBuilder(" from m_loan l join m_product_loan lp on lp.id = l.product_id ");
        sql.append(" join m_client c on c.id = l.client_id where lp.enable_third_party_disbursement = true ");
        sql.append(" and l.third_party_disbursement_provider is not null ");
        sql.append(" and upper(trim(l.third_party_disbursement_provider)) = ? ");
        params.add(provider);
        if (statusId != null) {
            sql.append(" and l.loan_status_id = ? ");
            params.add(statusId);
        }
        sql.append(" and ").append(lookup.column()).append(" = ? ");
        params.add(lookup.value());
        return sql.toString();
    }

    private record ClientLookup(String column, Object value) {}

    static Integer parseStatusId(final String status) {
        if (StringUtils.isBlank(status)) {
            return null;
        }
        final String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (StringUtils.isNumeric(normalized)) {
            return Integer.valueOf(normalized);
        }
        for (final LoanStatus loanStatus : LoanStatus.values()) {
            if (loanStatus.name().equals(normalized) || loanStatus.getCode().equalsIgnoreCase(status.trim())) {
                return loanStatus.getValue();
            }
        }
        throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.status.invalid",
                "Unrecognized loan status filter.",
                List.of(ApiParameterError.parameterError("validation.msg.thirdPartyDisbursementLoan.status.invalid",
                        "Unrecognized loan status filter.", ThirdPartyDisbursementLoanApiConstants.STATUS, status)));
    }

    private static String buildSelectColumns() {
        return " l.id as loanId, l.account_no as loanAccountNo, l.external_id as externalId, "
                + " l.loan_status_id as loanStatusId, l.loan_sub_status_id as loanSubStatusId, "
                + " l.third_party_disbursement_provider as thirdPartyDisbursementProvider, "
                + " lp.id as loanProductId, lp.name as loanProductName, "
                + " l.approved_principal as approvedPrincipal, l.currency_code as currencyCode, "
                + " l.approvedon_date as approvedOnDate, c.id as clientId, c.display_name as clientName, c.external_id as clientExternalId ";
    }

    private static final class ThirdPartyDisbursementLoanMapper implements RowMapper<ThirdPartyDisbursementLoanData> {

        @Override
        public ThirdPartyDisbursementLoanData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Integer lifeCycleStatusId = JdbcSupport.getInteger(rs, "loanStatusId");
            final LoanStatusEnumData status = LoanEnumerations.status(lifeCycleStatusId);
            final Integer loanSubStatusId = JdbcSupport.getInteger(rs, "loanSubStatusId");
            final EnumOptionData subStatus = loanSubStatusId == null ? null : LoanSubStatus.loanSubStatus(loanSubStatusId);
            final BigDecimal approvedPrincipal = rs.getBigDecimal("approvedPrincipal");
            final LocalDate approvedOnDate = JdbcSupport.getLocalDate(rs, "approvedOnDate");
            return new ThirdPartyDisbursementLoanData(rs.getLong("loanId"), rs.getString("loanAccountNo"), rs.getString("externalId"),
                    status, subStatus, rs.getString("thirdPartyDisbursementProvider"), rs.getLong("loanProductId"),
                    rs.getString("loanProductName"), approvedPrincipal, rs.getString("currencyCode"), approvedOnDate,
                    JdbcSupport.getLong(rs, "clientId"), rs.getString("clientName"), rs.getString("clientExternalId"));
        }
    }
}
