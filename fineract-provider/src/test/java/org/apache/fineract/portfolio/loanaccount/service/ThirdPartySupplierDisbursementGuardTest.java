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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartySupplierDisbursementApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstruction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstructionRepository;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThirdPartySupplierDisbursementGuardTest {

    private ThirdPartySupplierDisbursementGuard underTest;
    private DisbursementProviderReadPlatformService disbursementProviderReadPlatformService;
    private LoanDisbursementInstructionRepository loanDisbursementInstructionRepository;
    private Loan loan;
    private AppUser user;

    @BeforeEach
    void setUp() {
        this.disbursementProviderReadPlatformService = mock(DisbursementProviderReadPlatformService.class);
        this.loanDisbursementInstructionRepository = mock(LoanDisbursementInstructionRepository.class);
        this.underTest = new ThirdPartySupplierDisbursementGuard(this.disbursementProviderReadPlatformService,
                this.loanDisbursementInstructionRepository, new FromJsonHelper());
        this.loan = mock(Loan.class);
        this.user = mock(AppUser.class);
        when(this.loan.productId()).thenReturn(5L);
        when(this.loan.getId()).thenReturn(99L);
    }

    @Test
    void blocksManualRecipientEditForThirdPartyProductWithoutOverride() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(false);
        final JsonCommand command = JsonCommand.from("{\"beneficiaryName\":\"Vendor A\"}");

        assertThrows(PlatformApiDataValidationException.class,
                () -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
    }

    @Test
    void allowsManualRecipientEditForNonThirdPartyProduct() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(false);
        final JsonCommand command = JsonCommand.from("{\"beneficiaryName\":\"Vendor A\"}");

        assertDoesNotThrow(() -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
        assertTrue(this.underTest.allowsManualRecipientEdit(this.loan, this.user));
    }

    @Test
    void allowsManualRecipientEditWhenOverridePermissionPresent() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(true);
        final JsonCommand command = JsonCommand.from("{\"beneficiaryName\":\"Vendor A\"}");

        assertDoesNotThrow(() -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
        assertTrue(this.underTest.allowsManualRecipientEdit(this.loan, this.user));
    }

    @Test
    void doesNotBlockThirdPartyApprovalWithoutRecipientFields() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(false);
        final JsonCommand command = JsonCommand.from("{\"approvedLoanAmount\":1000}");

        assertDoesNotThrow(() -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
        assertFalse(this.underTest.allowsManualRecipientEdit(this.loan, this.user));
    }

    @Test
    void doesNotTreatNullRecipientFieldsAsManualEdit() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(false);
        final JsonCommand command = JsonCommand.from(
                "{\"approvedLoanAmount\":1000,\"paymentTypeId\":null,\"clientPhoneNumber\":null,\"beneficiaryName\":\"\"}");

        assertDoesNotThrow(() -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
    }

    @Test
    void blocksStaffDisbursementUntilPartnerInstructionExists() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(true);
        when(this.loanDisbursementInstructionRepository.existsByLoanIdAndStatusIn(eq(99L), any())).thenReturn(false);

        assertThrows(PlatformApiDataValidationException.class,
                () -> this.underTest.assertPartnerInstructionReceivedBeforeStaffDisbursement(this.loan));
    }

    @Test
    void allowsStaffDisbursementAfterPartnerInstruction() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(true);
        when(this.loanDisbursementInstructionRepository.existsByLoanIdAndStatusIn(eq(99L), any())).thenReturn(true);

        assertDoesNotThrow(() -> this.underTest.assertPartnerInstructionReceivedBeforeStaffDisbursement(this.loan));
    }

    @Test
    void doesNotTreatPaymentTypeIdAloneAsManualRecipientEdit() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(false);
        final JsonCommand command = JsonCommand.from("{\"approvedLoanAmount\":1000,\"paymentTypeId\":1}");

        assertDoesNotThrow(() -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
    }

    @Test
    void blocksThirdPartyDisbursementProviderChangeWhenInstructionExists() {
        when(this.loan.getThirdPartyDisbursementProvider()).thenReturn("KIFIYA");
        when(this.loan.isSubmittedAndPendingApproval()).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(false);
        when(this.loanDisbursementInstructionRepository.existsByLoanIdAndStatusIn(eq(99L), any())).thenReturn(true);
        final JsonCommand command = JsonCommand.from("{\"thirdPartyDisbursementProvider\":\"OTHER\"}");

        assertThrows(PlatformApiDataValidationException.class,
                () -> this.underTest.assertThirdPartyDisbursementProviderChangeAllowed(this.loan, command, this.user));
    }

    @Test
    void allowsThirdPartyDisbursementProviderChangeWhileSubmittedWithoutInstruction() {
        when(this.loan.getThirdPartyDisbursementProvider()).thenReturn("KIFIYA");
        when(this.loan.isSubmittedAndPendingApproval()).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(false);
        when(this.loanDisbursementInstructionRepository.existsByLoanIdAndStatusIn(eq(99L), any())).thenReturn(false);
        final JsonCommand command = JsonCommand.from("{\"thirdPartyDisbursementProvider\":\"OTHER\"}");

        assertDoesNotThrow(() -> this.underTest.assertThirdPartyDisbursementProviderChangeAllowed(this.loan, command, this.user));
    }

    @Test
    void allowsThirdPartyDisbursementProviderChangeWithOverridePermission() {
        when(this.loan.getThirdPartyDisbursementProvider()).thenReturn("KIFIYA");
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(true);
        final JsonCommand command = JsonCommand.from("{\"thirdPartyDisbursementProvider\":\"OTHER\"}");

        assertDoesNotThrow(() -> this.underTest.assertThirdPartyDisbursementProviderChangeAllowed(this.loan, command, this.user));
    }

    @Test
    void completesOpenInstructionsAfterDisburse() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(true);
        final LoanDisbursementInstruction instruction = mock(LoanDisbursementInstruction.class);
        when(this.loanDisbursementInstructionRepository.findByLoanIdAndStatusIn(eq(99L), any()))
                .thenReturn(List.of(instruction));

        this.underTest.completeOpenInstructionsAfterDisburse(this.loan);

        verify(instruction).markDisbursed();
        verify(this.loanDisbursementInstructionRepository).save(instruction);
    }

    @Test
    void allowsStaffDisbursementForNonThirdPartyProduct() {
        when(this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(5L)).thenReturn(false);

        assertDoesNotThrow(() -> this.underTest.assertPartnerInstructionReceivedBeforeStaffDisbursement(this.loan));
    }
}
