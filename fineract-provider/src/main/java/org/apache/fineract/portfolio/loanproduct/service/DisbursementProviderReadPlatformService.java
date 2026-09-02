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
import java.util.Optional;
import org.apache.fineract.portfolio.loanproduct.domain.DisbursementProvider;

public interface DisbursementProviderReadPlatformService {

    Collection<String> retrieveActiveProviderCodes();

    Collection<DisbursementProvider> retrieveActiveProviders();

    boolean isActiveProvider(String providerCode);

    boolean isThirdPartyDisbursementEnabled(Long loanProductId);

    Optional<String> findLoanDisbursementProviderCode(Long loanId);

    /**
     * @deprecated use {@link #isThirdPartyDisbursementEnabled(Long)}
     */
    @Deprecated
    boolean hasActiveThirdPartyDisbursementMapping(Long loanProductId);

    /**
     * @deprecated use {@link #findLoanDisbursementProviderCode(Long)}
     */
    @Deprecated
    Optional<String> findActiveMappedProviderCode(Long loanProductId);

    boolean isValidPartnerCode(String partnerCode);
}
