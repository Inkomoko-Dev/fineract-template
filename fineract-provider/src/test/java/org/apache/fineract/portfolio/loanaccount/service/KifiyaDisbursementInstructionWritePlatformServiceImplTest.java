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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.serialization.DisbursementInstructionDataValidator;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KifiyaDisbursementInstructionWritePlatformServiceImplTest {

    @Mock
    private DisbursementInstructionDataValidator validator;
    @Mock
    private SupplierPaymentDetailsValidator supplierPaymentDetailsValidator;
    @Mock
    private LoanReadPlatformService loanReadPlatformService;
    @Mock
    private LoanAssembler loanAssembler;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private SupplierDisbursementAuditService supplierDisbursementAuditService;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private DisbursementProviderReadPlatformService disbursementProviderReadPlatformService;
    @Mock
    private Loan loan;

    @InjectMocks
    private KifiyaDisbursementInstructionWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        given(this.loan.isMultiDisburmentLoan()).willReturn(false);
        given(this.loan.isApproved()).willReturn(true);
        given(this.loan.productId()).willReturn(5L);
    }

    @Test
    void rejectsInactiveProvider() {
        given(this.disbursementProviderReadPlatformService.isActiveProvider("KIFIYA")).willReturn(false);

        assertThatThrownBy(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getDefaultUserMessage())
                .asString().contains("not registered or inactive");
    }

    @Test
    void rejectsWhenProductHasNoActiveMapping() {
        given(this.disbursementProviderReadPlatformService.isActiveProvider("KIFIYA")).willReturn(true);
        given(this.disbursementProviderReadPlatformService.findActiveMappedProviderCode(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getDefaultUserMessage())
                .asString().contains("not configured for third-party disbursement");
    }

    @Test
    void rejectsWhenSourceSystemDoesNotMatchMappedProvider() {
        given(this.disbursementProviderReadPlatformService.isActiveProvider("KIFIYA")).willReturn(true);
        given(this.disbursementProviderReadPlatformService.findActiveMappedProviderCode(5L)).willReturn(Optional.of("OTHER"));

        assertThatThrownBy(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getDefaultUserMessage())
                .asString().contains("does not match the instruction source system");
    }

    @Test
    void acceptsWhenProviderAndProductMappingMatch() {
        given(this.disbursementProviderReadPlatformService.isActiveProvider("KIFIYA")).willReturn(true);
        given(this.disbursementProviderReadPlatformService.findActiveMappedProviderCode(5L)).willReturn(Optional.of("KIFIYA"));

        assertThatCode(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA")).doesNotThrowAnyException();
    }
}
