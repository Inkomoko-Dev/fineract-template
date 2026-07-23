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
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementLoanData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
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
        sqlBuilder.append(" join m_loan_product_disbursement_provider_mapping lpdpm on lpdpm.loan_product_id = lp.id ");
        sqlBuilder.append(" left join m_client c on c.id = l.client_id ");
        sqlBuilder.append(" where lpdpm.is_active = true ");
        sqlBuilder.append(" and upper(trim(lpdpm.disbursement_provider_code)) = ? ");

        final List<Object> params = new ArrayList<>();
        params.add(normalizedProvider);
        sqlBuilder.append(" and l.loan_status_id = ? ");
        params.add(effectiveStatusId);

        if (Boolean.TRUE.equals(readyForInstruction)) {
            sqlBuilder.append(" and l.loan_sub_status_id is null ");
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
                + " lpdpm.disbursement_provider_code as thirdPartyDisbursementProvider, "
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
