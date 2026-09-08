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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanproduct.domain.DisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.domain.DisbursementProviderRepository;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisbursementProviderReadPlatformServiceImpl implements DisbursementProviderReadPlatformService {

    private final DisbursementProviderRepository disbursementProviderRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Collection<String> retrieveActiveProviderCodes() {
        return this.disbursementProviderRepository.findAllActiveCodes();
    }

    @Override
    public Collection<DisbursementProvider> retrieveActiveProviders() {
        return this.disbursementProviderRepository.findAllActive();
    }

    @Override
    public boolean isActiveProvider(final String providerCode) {
        final String normalized = ThirdPartyDisbursementProvider.normalize(providerCode);
        if (normalized == null) {
            return false;
        }
        return this.disbursementProviderRepository.findActiveByCode(normalized).isPresent();
    }

    @Override
    public boolean isThirdPartyDisbursementEnabled(final Long loanProductId) {
        if (loanProductId == null) {
            return false;
        }
        final List<Boolean> flags = this.jdbcTemplate.queryForList(
                "select enable_third_party_disbursement from m_product_loan where id = ?", Boolean.class, loanProductId);
        return !flags.isEmpty() && Boolean.TRUE.equals(flags.get(0));
    }

    @Override
    public Optional<String> findLoanDisbursementProviderCode(final Long loanId) {
        if (loanId == null) {
            return Optional.empty();
        }
        final List<String> codes = this.jdbcTemplate.queryForList(
                "select third_party_disbursement_provider from m_loan where id = ?", String.class, loanId);
        if (codes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ThirdPartyDisbursementProvider.normalize(codes.get(0)));
    }

    @Override
    @Deprecated
    public boolean hasActiveThirdPartyDisbursementMapping(final Long loanProductId) {
        return isThirdPartyDisbursementEnabled(loanProductId);
    }

    @Override
    @Deprecated
    public Optional<String> findActiveMappedProviderCode(final Long loanProductId) {
        return Optional.empty();
    }

    @Override
    public boolean isValidPartnerCode(final String partnerCode) {
        return isActiveProvider(partnerCode);
    }
}
