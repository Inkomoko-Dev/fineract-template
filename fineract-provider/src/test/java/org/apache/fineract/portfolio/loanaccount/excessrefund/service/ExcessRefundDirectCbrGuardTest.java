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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExcessRefundDirectCbrGuardTest {

    @Mock
    private LoanExcessRefundRepository loanExcessRefundRepository;

    @InjectMocks
    private ExcessRefundDirectCbrGuard guard;

    @Test
    void blocksDirectCbrWhenPaidOpenRefundExists() {
        when(this.loanExcessRefundRepository.existsByLoanIdAndStatusEnumIn(eq(42L), eq(LoanExcessRefundStatus.openStatusEnumValues())))
                .thenReturn(true);

        assertThatThrownBy(() -> this.guard.assertDirectCbrAllowed(42L)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("open excess refund");
        verify(this.loanExcessRefundRepository).existsByLoanIdAndStatusEnumIn(eq(42L), eq(LoanExcessRefundStatus.openStatusEnumValues()));
    }

    @Test
    void allowsDirectCbrWhenNoOpenRefund() {
        when(this.loanExcessRefundRepository.existsByLoanIdAndStatusEnumIn(eq(42L), eq(LoanExcessRefundStatus.openStatusEnumValues())))
                .thenReturn(false);

        assertThatCode(() -> this.guard.assertDirectCbrAllowed(42L)).doesNotThrowAnyException();
    }
}
