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

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartySupplierDisbursementApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.DisbursementInstructionStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstruction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstructionRepository;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThirdPartySupplierDisbursementGuard {

    private static final Set<DisbursementInstructionStatus> OPEN_INSTRUCTION_STATUSES = EnumSet
            .of(DisbursementInstructionStatus.RECEIVED, DisbursementInstructionStatus.PENDING_DISBURSEMENT);

    private final DisbursementProviderReadPlatformService disbursementProviderReadPlatformService;
    private final LoanDisbursementInstructionRepository loanDisbursementInstructionRepository;
    private final FromJsonHelper fromJsonHelper;

    public boolean isThirdPartyDisbursementProduct(final Loan loan) {
        if (loan == null || loan.productId() == null) {
            return false;
        }
        return this.disbursementProviderReadPlatformService.isThirdPartyDisbursementEnabled(loan.productId());
    }

    public Optional<String> findMappedProviderCode(final Loan loan) {
        if (loan == null || loan.getId() == null) {
            return Optional.empty();
        }
        return this.disbursementProviderReadPlatformService.findLoanDisbursementProviderCode(loan.getId());
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

    /**
     * Provider is chosen at application time and must not change after approval or once a partner instruction exists.
     */
    public void assertThirdPartyDisbursementProviderChangeAllowed(final Loan loan, final JsonCommand command, final AppUser user) {
        if (loan == null || command == null || !command.parameterExists(LoanProductConstants.THIRD_PARTY_DISBURSEMENT_PROVIDER)) {
            return;
        }
        final String newProvider = ThirdPartyDisbursementProvider
                .normalize(command.stringValueOfParameterNamedAllowingNull(LoanProductConstants.THIRD_PARTY_DISBURSEMENT_PROVIDER));
        final String existingProvider = loan.getThirdPartyDisbursementProvider();
        if (existingProvider == null || Objects.equals(existingProvider, newProvider)) {
            return;
        }
        if (hasOverridePermission(user)) {
            return;
        }
        if (!loan.isSubmittedAndPendingApproval()) {
            throw providerChangeNotAllowed("Loan is no longer in submitted status.");
        }
        if (loan.getId() != null && this.loanDisbursementInstructionRepository.existsByLoanIdAndStatusIn(loan.getId(),
                OPEN_INSTRUCTION_STATUSES)) {
            throw providerChangeNotAllowed("A partner disbursement instruction already exists for this loan.");
        }
    }

    private PlatformApiDataValidationException providerChangeNotAllowed(final String reason) {
        return new PlatformApiDataValidationException("validation.msg.loan.thirdPartyDisbursement.providerChange.notAllowed",
                "Third-party disbursement provider cannot be changed. " + reason,
                List.of(ApiParameterError.parameterError("validation.msg.loan.thirdPartyDisbursement.providerChange.notAllowed",
                        "Third-party disbursement provider cannot be changed. " + reason,
                        LoanProductConstants.THIRD_PARTY_DISBURSEMENT_PROVIDER, null)));
    }

    /**
     * Staff disbursement paths must wait for a partner disbursement instruction on third-party products.
     */
    public void assertPartnerInstructionReceivedBeforeStaffDisbursement(final Loan loan) {
        if (!isThirdPartyDisbursementProduct(loan) || loan == null || loan.getId() == null) {
            return;
        }
        final boolean hasOpenInstruction = this.loanDisbursementInstructionRepository.existsByLoanIdAndStatusIn(loan.getId(),
                OPEN_INSTRUCTION_STATUSES);
        if (hasOpenInstruction) {
            return;
        }
        throw new PlatformApiDataValidationException("validation.msg.loan.thirdPartyDisbursement.instruction.required",
                "Staff disbursement is not allowed until a partner disbursement instruction is received.",
                List.of(ApiParameterError.generalError("validation.msg.loan.thirdPartyDisbursement.instruction.required",
                        "This loan uses third-party disbursement. Wait for the partner disbursement instruction before "
                                + "disbursing from CBS/staff actions.")));
    }

    /**
     * Close open partner instructions once staff disbursement succeeds.
     */
    public void completeOpenInstructionsAfterDisburse(final Loan loan) {
        if (!isThirdPartyDisbursementProduct(loan) || loan == null || loan.getId() == null) {
            return;
        }
        final List<LoanDisbursementInstruction> openInstructions = this.loanDisbursementInstructionRepository
                .findByLoanIdAndStatusIn(loan.getId(), OPEN_INSTRUCTION_STATUSES);
        for (final LoanDisbursementInstruction instruction : openInstructions) {
            instruction.markDisbursed();
            this.loanDisbursementInstructionRepository.save(instruction);
        }
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
        // paymentTypeId alone is often a UI default on approve; recipient fields are the payout identity.
        return hasNonBlankParameter(element, LoanApiConstants.paymentToParameterName)
                || hasNonBlankParameter(element, LoanApiConstants.beneficiaryNameParameterName)
                || hasNonBlankParameter(element, LoanApiConstants.disbursementTypeParameterName)
                || hasNonBlankParameter(element, "clientPhoneNumber")
                || hasNonBlankParameter(element, "clientAccountNumber")
                || hasNonBlankParameter(element, "clientBankName");
    }

    private boolean hasNonBlankParameter(final com.google.gson.JsonElement element, final String parameterName) {
        if (!this.fromJsonHelper.parameterExists(parameterName, element)) {
            return false;
        }
        if (element.getAsJsonObject().get(parameterName).isJsonNull()) {
            return false;
        }
        final String asString = this.fromJsonHelper.extractStringNamed(parameterName, element);
        return StringUtils.isNotBlank(asString);
    }
}
