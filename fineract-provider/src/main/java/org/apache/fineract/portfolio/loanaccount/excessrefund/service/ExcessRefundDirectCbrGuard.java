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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.springframework.stereotype.Component;

/**
 * Blocks direct API credit-balance-refund while any open excess refund exists (including PAID).
 * Domain CBR used by {@link ExcessRefundGlPoster} keeps the narrower pre-payment guard.
 */
@Component
@RequiredArgsConstructor
public class ExcessRefundDirectCbrGuard {

    private final LoanExcessRefundRepository loanExcessRefundRepository;

    public void assertDirectCbrAllowed(final Long loanId) {
        if (this.loanExcessRefundRepository.existsByLoanIdAndStatusEnumIn(loanId, LoanExcessRefundStatus.openStatusEnumValues())) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.blocks.credit.balance.refund",
                    "An open excess refund exists for this loan; credit balance refund is not allowed until it is resolved.");
        }
    }
}
