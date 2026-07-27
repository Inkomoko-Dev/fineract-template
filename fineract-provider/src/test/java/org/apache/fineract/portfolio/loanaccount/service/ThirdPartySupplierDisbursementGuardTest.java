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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartySupplierDisbursementApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThirdPartySupplierDisbursementGuardTest {

    private ThirdPartySupplierDisbursementGuard underTest;
    private DisbursementProviderReadPlatformService disbursementProviderReadPlatformService;
    private Loan loan;
    private AppUser user;

    @BeforeEach
    void setUp() {
        this.disbursementProviderReadPlatformService = mock(DisbursementProviderReadPlatformService.class);
        this.underTest = new ThirdPartySupplierDisbursementGuard(this.disbursementProviderReadPlatformService, new FromJsonHelper());
        this.loan = mock(Loan.class);
        this.user = mock(AppUser.class);
        when(this.loan.productId()).thenReturn(5L);
    }

    @Test
    void blocksManualRecipientEditForThirdPartyProductWithoutOverride() {
        when(this.disbursementProviderReadPlatformService.hasActiveThirdPartyDisbursementMapping(5L)).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(false);
        final JsonCommand command = JsonCommand.from("{\"beneficiaryName\":\"Vendor A\"}");

        assertThrows(PlatformApiDataValidationException.class,
                () -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
    }

    @Test
    void allowsManualRecipientEditForNonThirdPartyProduct() {
        when(this.disbursementProviderReadPlatformService.hasActiveThirdPartyDisbursementMapping(5L)).thenReturn(false);
        final JsonCommand command = JsonCommand.from("{\"beneficiaryName\":\"Vendor A\"}");

        assertDoesNotThrow(() -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
        assertTrue(this.underTest.allowsManualRecipientEdit(this.loan, this.user));
    }

    @Test
    void allowsManualRecipientEditWhenOverridePermissionPresent() {
        when(this.disbursementProviderReadPlatformService.hasActiveThirdPartyDisbursementMapping(5L)).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(true);
        final JsonCommand command = JsonCommand.from("{\"beneficiaryName\":\"Vendor A\"}");

        assertDoesNotThrow(() -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
        assertTrue(this.underTest.allowsManualRecipientEdit(this.loan, this.user));
    }

    @Test
    void doesNotBlockThirdPartyApprovalWithoutRecipientFields() {
        when(this.disbursementProviderReadPlatformService.hasActiveThirdPartyDisbursementMapping(5L)).thenReturn(true);
        when(this.user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE)).thenReturn(false);
        final JsonCommand command = JsonCommand.from("{\"approvedLoanAmount\":1000}");

        assertDoesNotThrow(() -> this.underTest.assertManualRecipientEditAllowed(this.loan, command, this.user));
        assertFalse(this.underTest.allowsManualRecipientEdit(this.loan, this.user));
    }
}
