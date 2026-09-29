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
package org.apache.fineract.portfolio.collateralmanagement.service;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.collateralmanagement.data.LoanCollateralResponseData;
import org.apache.fineract.portfolio.collateralmanagement.domain.CollateralManagementDomain;
import org.apache.fineract.portfolio.collateralmanagement.exception.LoanCollateralManagementNotFoundException;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagement;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagementRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LoanCollateralManagementReadPlatformServiceImpl implements LoanCollateralManagementReadPlatformService {

    private final PlatformSecurityContext context;
    private final LoanCollateralManagementRepository loanCollateralManagementRepository;
    private final LoanRepository loanRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LoanCollateralManagementReadPlatformServiceImpl(final PlatformSecurityContext context,
            final LoanCollateralManagementRepository loanCollateralManagementRepository, final LoanRepository loanRepository,
            final JdbcTemplate jdbcTemplate) {
        this.context = context;
        this.loanCollateralManagementRepository = loanCollateralManagementRepository;
        this.loanRepository = loanRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LoanCollateralManagement> getLoanCollaterals(Long loanId) {
        this.context.authenticatedUser();
        Loan loan = this.loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        return this.loanCollateralManagementRepository.findByLoan(loan);
    }

    @Override
    public LoanCollateralResponseData getLoanCollateralResponseData(Long collateralId) {
        this.context.authenticatedUser();
        LoanCollateralManagement loanCollateralManagement = this.loanCollateralManagementRepository.findById(collateralId)
                .orElseThrow(() -> new LoanCollateralManagementNotFoundException(collateralId));
        final CollateralManagementDomain collateralManagementDomain = loanCollateralManagement.getClientCollateralManagement()
                .getCollaterals();
        BigDecimal quantity = loanCollateralManagement.getQuantity();
        BigDecimal total = quantity.multiply(collateralManagementDomain.getBasePrice());
        BigDecimal totalCollateral = total.multiply(collateralManagementDomain.getPctToBase()).divide(BigDecimal.valueOf(100));
        return LoanCollateralResponseData.instanceOf(loanCollateralManagement, total, totalCollateral);
    }

    @Override
    public List<LoanCollateralResponseData> getLoanCollateralResponseDataList(Long loanId) {
        this.context.authenticatedUser();
        if (!this.loanRepository.existsById(loanId)) {
            throw new LoanNotFoundException(loanId);
        }
        final String sql = "select lcm.id as collateralId, lcm.quantity as quantity, ccm.id as clientCollateralId, "
                + "cm.base_price as basePrice, cm.pct_to_base as pctToBase "
                + "from m_loan_collateral_management lcm "
                + "join m_client_collateral_management ccm on ccm.id = lcm.client_collateral_id "
                + "join m_collateral_management cm on cm.id = ccm.collateral_id " + "where lcm.loan_id = ?";
        return this.jdbcTemplate.query(sql, (rs, rowNum) -> {
            final Long collateralId = rs.getLong("collateralId");
            final BigDecimal quantity = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "quantity");
            final Long clientCollateralId = rs.getLong("clientCollateralId");
            final BigDecimal basePrice = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "basePrice");
            final BigDecimal pctToBase = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "pctToBase");
            final BigDecimal total = quantity.multiply(basePrice);
            final BigDecimal totalCollateral = total.multiply(pctToBase).divide(BigDecimal.valueOf(100));
            return LoanCollateralResponseData.instance(collateralId, quantity, total, totalCollateral, clientCollateralId);
        }, loanId);
    }

}
