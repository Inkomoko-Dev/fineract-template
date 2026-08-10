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
package org.apache.fineract.portfolio.loanproduct.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductData;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductApiConstants;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductData;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.exception.LoanProductNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThirdPartyDisbursementProductReadPlatformServiceImpl implements ThirdPartyDisbursementProductReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PaginationHelper paginationHelper;
    private final LoanProductReadPlatformService loanProductReadPlatformService;
    private final ThirdPartyDisbursementProductMapper productMapper;

    @Override
    public Page<ThirdPartyDisbursementProductData> retrieveAll(final String provider, final Boolean includeInactive,
            final Integer offset, final Integer limit) {
        validateProvider(provider);
        final int safeOffset = offset == null || offset < 0 ? 0 : offset;
        final int safeLimit = limit == null || limit <= 0 ? 15 : Math.min(limit, 200);

        final StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("select ").append(this.sqlGenerator.calcFoundRows()).append(" lp.id as productId ");
        sqlBuilder.append(" from m_product_loan lp ");
        sqlBuilder.append(" where lp.enable_third_party_disbursement = true ");

        final List<Object> params = new ArrayList<>();
        if (!Boolean.TRUE.equals(includeInactive)) {
            sqlBuilder.append(" and (lp.close_date is null or lp.close_date >= ?) ");
            params.add(DateUtils.getBusinessLocalDate());
        }

        sqlBuilder.append(" order by lp.name asc, lp.id asc ");
        sqlBuilder.append(this.sqlGenerator.limit(safeLimit, safeOffset));

        final Page<Long> productIdPage = this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), params.toArray(),
                new ProductIdMapper());

        final List<ThirdPartyDisbursementProductData> products = new ArrayList<>();
        for (final Long productId : productIdPage.getPageItems()) {
            products.add(retrieveOne(productId));
        }
        return new Page<>(products, productIdPage.getTotalFilteredRecords());
    }

    @Override
    public ThirdPartyDisbursementProductData retrieveOne(final Long productId) {
        final LoanProductData product = this.loanProductReadPlatformService.retrieveLoanProduct(productId);
        if (!Boolean.TRUE.equals(product.getEnableThirdPartyDisbursement())) {
            throw new LoanProductNotFoundException(productId);
        }
        return this.productMapper.toPartnerData(product);
    }

    private static void validateProvider(final String provider) {
        final String normalizedProvider = ThirdPartyDisbursementProvider.normalize(provider);
        if (StringUtils.isBlank(normalizedProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementProduct.provider.required",
                    "Disbursement provider query parameter is required.",
                    List.of(ApiParameterError.parameterError("validation.msg.thirdPartyDisbursementProduct.provider.required",
                            "Disbursement provider query parameter is required.", ThirdPartyDisbursementProductApiConstants.PROVIDER,
                            provider)));
        }
    }

    private static final class ProductIdMapper implements RowMapper<Long> {

        @Override
        public Long mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return JdbcSupport.getLong(rs, "productId");
        }
    }
}
