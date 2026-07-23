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

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartySupplierDisbursementApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThirdPartySupplierDisbursementGuard {

    private final DisbursementProviderReadPlatformService disbursementProviderReadPlatformService;
    private final FromJsonHelper fromJsonHelper;

    public boolean isThirdPartyDisbursementProduct(final Loan loan) {
        if (loan == null || loan.productId() == null) {
            return false;
        }
        return this.disbursementProviderReadPlatformService.hasActiveThirdPartyDisbursementMapping(loan.productId());
    }

    public Optional<String> findMappedProviderCode(final Loan loan) {
        if (loan == null || loan.productId() == null) {
            return Optional.empty();
        }
        return this.disbursementProviderReadPlatformService.findActiveMappedProviderCode(loan.productId());
    }

    public boolean hasOverridePermission(final AppUser user) {
        return user != null && user.hasSpecificPermissionTo(ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE);
    }

    public boolean allowsManualRecipientEdit(final Loan loan, final AppUser user) {
        return !isThirdPartyDisbursementProduct(loan) || hasOverridePermission(user);
    }

    public void assertManualRecipientEditAllowed(final Loan loan, final JsonCommand command, final AppUser user) {
        if (!isThirdPartyDisbursementProduct(loan) || hasOverridePermission(user)) {
            return;
        }
        if (!commandContainsRecipientFields(command)) {
            return;
        }
        throw new PlatformApiDataValidationException("validation.msg.loan.thirdPartyDisbursement.recipientEdit.notAllowed",
                "Supplier disbursement details cannot be edited manually for third-party disbursement loans.",
                List.of(ApiParameterError.generalError("validation.msg.loan.thirdPartyDisbursement.recipientEdit.notAllowed",
                        "Supplier disbursement details cannot be edited manually for third-party disbursement loans. "
                                + "Use the partner disbursement instruction API or request override permission "
                                + ThirdPartySupplierDisbursementApiConstants.PERMISSION_CODE + ".")));
    }

    public boolean isSupplierRecipientDisbursement(final LoanDisbursementDetails disbursementDetail) {
        if (disbursementDetail == null) {
            return false;
        }
        return LoanDisbursementDetails.DisbursementType.VENDOR.name().equals(disbursementDetail.getDisbursementType())
                || Integer.valueOf(LoanDisbursementDetails.PaymentToType.SUPPLIER.getValue()).equals(disbursementDetail.getPaymentTo())
                || disbursementDetail.getSupplier() != null;
    }

    private boolean commandContainsRecipientFields(final JsonCommand command) {
        if (command == null || StringUtils.isBlank(command.json())) {
            return false;
        }
        final com.google.gson.JsonElement element = this.fromJsonHelper.parse(command.json());
        return this.fromJsonHelper.parameterExists(LoanApiConstants.paymentToParameterName, element)
                || this.fromJsonHelper.parameterExists(LoanApiConstants.beneficiaryNameParameterName, element)
                || this.fromJsonHelper.parameterExists(LoanApiConstants.disbursementTypeParameterName, element)
                || this.fromJsonHelper.parameterExists("paymentTypeId", element)
                || this.fromJsonHelper.parameterExists("clientPhoneNumber", element)
                || this.fromJsonHelper.parameterExists("clientAccountNumber", element)
                || this.fromJsonHelper.parameterExists("clientBankName", element);
    }
}
