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
    public boolean isActiveProvider(final String providerCode) {
        final String normalized = ThirdPartyDisbursementProvider.normalize(providerCode);
        if (normalized == null) {
            return false;
        }
        return this.disbursementProviderRepository.findActiveByCode(normalized).isPresent();
    }

    @Override
    public Optional<String> findActiveMappedProviderCode(final Long loanProductId) {
        if (loanProductId == null) {
            return Optional.empty();
        }
        final List<String> codes = this.jdbcTemplate.queryForList(
                "select disbursement_provider_code from m_loan_product_disbursement_provider_mapping "
                        + "where loan_product_id = ? and is_active = true",
                String.class, loanProductId);
        if (codes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ThirdPartyDisbursementProvider.normalize(codes.get(0)));
    }

    @Override
    public boolean hasActiveThirdPartyDisbursementMapping(final Long loanProductId) {
        return findActiveMappedProviderCode(loanProductId).isPresent();
    }
}
