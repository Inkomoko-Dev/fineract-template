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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.DisbursementInstructionStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstruction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstructionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSubStatus;
import org.apache.fineract.portfolio.loanaccount.exception.DisbursementInstructionIdempotencyConflictException;
import org.apache.fineract.portfolio.loanaccount.serialization.DisbursementInstructionDataValidator;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementPartnerAccessService;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KifiyaDisbursementInstructionWritePlatformServiceImplTest {

    private static final String REQUEST_HASH = KifiyaDisbursementInstructionWritePlatformServiceImpl.hashRequest("KIFIYA", "000000001",
            "SUP-001");

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
    private DisbursementPartnerAccessService disbursementPartnerAccessService;
    @Mock
    private LoanDisbursementInstructionRepository loanDisbursementInstructionRepository;
    @Mock
    private DisbursementInstructionFailureService disbursementInstructionFailureService;
    @Mock
    private Loan loan;
    @Mock
    private AppUser appUser;

    @InjectMocks
    private KifiyaDisbursementInstructionWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        given(this.loan.isMultiDisburmentLoan()).willReturn(false);
        given(this.loan.isApproved()).willReturn(true);
        given(this.loan.getLoanSubStatus()).willReturn(null);
        given(this.loan.productId()).willReturn(5L);
        given(this.loan.getId()).willReturn(10L);
        given(this.loan.getAccountNumber()).willReturn("000000001");
        given(this.loanDisbursementInstructionRepository.existsByLoanIdAndStatusIn(eq(10L), any())).willReturn(false);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void rejectsWhenProductHasNoActiveMapping() {
        given(this.disbursementProviderReadPlatformService.findActiveMappedProviderCode(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getDefaultUserMessage()).asString()
                .contains("not configured for third-party disbursement");
    }

    @Test
    void rejectsWhenSourceSystemDoesNotMatchMappedProvider() {
        given(this.disbursementProviderReadPlatformService.findActiveMappedProviderCode(5L)).willReturn(Optional.of("OTHER"));

        assertThatThrownBy(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getDefaultUserMessage()).asString()
                .contains("does not match the instruction source system");
    }

    @Test
    void acceptsWhenProviderAndProductMappingMatch() {
        given(this.disbursementProviderReadPlatformService.findActiveMappedProviderCode(5L)).willReturn(Optional.of("KIFIYA"));

        assertThatCode(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA")).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenLoanAlreadyHasSubStatus() {
        given(this.loan.getLoanSubStatus()).willReturn(LoanSubStatus.PENDINGDISBURSEMENT.getValue());

        assertThatThrownBy(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getGlobalisationMessageCode())
                .isEqualTo("validation.msg.disbursementInstruction.loan.alreadyPending");
    }

    @Test
    void rejectsWhenOpenInstructionExists() {
        given(this.loanDisbursementInstructionRepository.existsByLoanIdAndStatusIn(eq(10L),
                eq(List.of(DisbursementInstructionStatus.RECEIVED, DisbursementInstructionStatus.PENDING_DISBURSEMENT))))
                        .willReturn(true);

        assertThatThrownBy(() -> this.underTest.validateLoanForDisbursementInstruction(this.loan, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getGlobalisationMessageCode())
                .isEqualTo("validation.msg.disbursementInstruction.loan.openInstructionExists");
    }

    @Test
    void rejectsWhenCallerNotBoundToProvider() {
        given(this.disbursementPartnerAccessService.resolveProviderCodeForUser(this.appUser)).willReturn(Optional.empty());

        assertThatThrownBy(() -> this.underTest.assertCallerBoundToSourceSystem(this.appUser, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getGlobalisationMessageCode())
                .isEqualTo("validation.msg.disbursementInstruction.partnerBinding.required");
    }

    @Test
    void rejectsWhenCallerBindingMismatchesSourceSystem() {
        given(this.disbursementPartnerAccessService.resolveProviderCodeForUser(this.appUser)).willReturn(Optional.of("OTHER"));

        assertThatThrownBy(() -> this.underTest.assertCallerBoundToSourceSystem(this.appUser, "KIFIYA"))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getGlobalisationMessageCode())
                .isEqualTo("validation.msg.disbursementInstruction.partnerBinding.mismatch");
    }

    @Test
    void replayReturnsExistingWithoutConflict() {
        final LoanDisbursementInstruction existing = LoanDisbursementInstruction.createReceived(10L, "KIFIYA", 3L, "SUP-001", "idem-1",
                REQUEST_HASH, 1L);
        ReflectionTestUtils.setField(existing, "id", 55L);
        existing.markPendingDisbursement(99L);
        given(this.loanAssembler.assembleFrom(10L)).willReturn(this.loan);

        final CommandProcessingResult result = this.underTest.replayOrConflict(existing, "000000001", "SUP-001", REQUEST_HASH, 7L);

        assertThat(result.resourceId()).isEqualTo(55L);
        assertThat(result.getChanges().get(DisbursementInstructionApiConstants.REPLAYED)).isEqualTo(Boolean.TRUE);
        assertThat(result.getChanges().get(DisbursementInstructionApiConstants.SUCCESS)).isEqualTo(Boolean.TRUE);
        assertThat(result.getChanges().get(DisbursementInstructionApiConstants.INSTRUCTION_STATUS))
                .isEqualTo(DisbursementInstructionStatus.PENDING_DISBURSEMENT.name());
    }

    @Test
    void replayRejectsPayloadConflict() {
        final LoanDisbursementInstruction existing = LoanDisbursementInstruction.createReceived(10L, "KIFIYA", 3L, "SUP-001", "idem-1",
                REQUEST_HASH, 1L);
        given(this.loanAssembler.assembleFrom(10L)).willReturn(this.loan);

        assertThatThrownBy(() -> this.underTest.replayOrConflict(existing, "000000001", "SUP-OTHER", REQUEST_HASH, 7L))
                .isInstanceOf(DisbursementInstructionIdempotencyConflictException.class)
                .extracting(ex -> ((DisbursementInstructionIdempotencyConflictException) ex).getGlobalisationMessageCode())
                .isEqualTo("validation.msg.disbursementInstruction.idempotencyKey.payloadConflict");
    }
}
