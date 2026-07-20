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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.SupplierDisbursementSnapshot;
import org.apache.fineract.portfolio.loanaccount.data.SupplierPaymentDetails;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSubStatus;
import org.apache.fineract.portfolio.loanaccount.exception.LoanDisbursalException;
import org.apache.fineract.portfolio.loanaccount.serialization.DisbursementInstructionDataValidator;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KifiyaDisbursementInstructionWritePlatformServiceImpl implements KifiyaDisbursementInstructionWritePlatformService {

    private final DisbursementInstructionDataValidator validator;
    private final SupplierPaymentDetailsValidator supplierPaymentDetailsValidator;
    private final LoanReadPlatformService loanReadPlatformService;
    private final LoanAssembler loanAssembler;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final SupplierRepository supplierRepository;
    private final SupplierDisbursementAuditService supplierDisbursementAuditService;
    private final PlatformSecurityContext context;

    @Override
    @Transactional
    public CommandProcessingResult createDisbursementInstruction(final JsonCommand command) {
        this.validator.validateCreateRequest(command.json());

        final String loanAccountNo = command.stringValueOfParameterNamed(DisbursementInstructionApiConstants.LOAN_ACCOUNT_NO);
        final String sourceSystem = normalizeSourceSystem(command.stringValueOfParameterNamed(DisbursementInstructionApiConstants.SOURCE_SYSTEM));
        final String supplierExternalId = command.stringValueOfParameterNamed(DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID)
                .trim();

        final LoanAccountData loanAccountData = this.loanReadPlatformService.retrieveLoanByLoanAccount(loanAccountNo);
        final Loan loan = this.loanAssembler.assembleFrom(loanAccountData.getId());
        validateLoanForDisbursementInstruction(loan);

        final Supplier supplier = this.supplierRepository.findBySourceSystemAndExternalId(sourceSystem, supplierExternalId)
                .orElse(null);
        final SupplierPaymentDetails paymentDetails = this.supplierPaymentDetailsValidator.validateAndExtract(supplier);

        final LoanDisbursementDetails disbursementDetail = loan.getDisbursementDetails().stream().findFirst()
                .orElseThrow(() -> new PlatformApiDataValidationException("validation.msg.disbursementInstruction.missingDisbursementDetails",
                        "Loan disbursement details are missing.",
                        List.of(ApiParameterError.generalError("validation.msg.disbursementInstruction.missingDisbursementDetails",
                                "Loan disbursement details are missing."))));
        final SupplierDisbursementSnapshot recipientSnapshotBeforeUpdate = SupplierDisbursementSnapshot.from(disbursementDetail);

        applySupplierPaymentDetails(disbursementDetail, supplier, paymentDetails);
        final AppUser currentUser = this.context.getAuthenticatedUserIfPresent();
        this.supplierDisbursementAuditService.recordChange(loan, disbursementDetail, recipientSnapshotBeforeUpdate,
                SupplierDisbursementSnapshot.from(disbursementDetail), SupplierDisbursementAuditService.CHANGE_SOURCE_DISBURSEMENT_INSTRUCTION,
                currentUser);
        loan.handleDisbursementRequest();
        this.loanRepositoryWrapper.saveAndFlush(loan);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(loan.getId())
                .withLoanId(loan.getId()).with(buildResponseChanges(loan, supplier)).build();
    }

    private void validateLoanForDisbursementInstruction(final Loan loan) {
        if (loan.isMultiDisburmentLoan()) {
            throw new LoanDisbursalException("Loan can't receive a disbursement instruction; it is a multi-disbursement loan.",
                    "validation.msg.disbursementInstruction.multiDisbursementNotSupported", loan.getApprovedPrincipal());
        }
        if (!loan.isApproved()) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.loan.notApproved",
                    "Loan must be approved and not yet disbursed.",
                    List.of(ApiParameterError.generalError("validation.msg.disbursementInstruction.loan.notApproved",
                            "Loan must be approved and not yet disbursed.")));
        }

        final LoanProduct loanProduct = loan.getLoanProduct();
        if (!loanProduct.isEnableThirdPartyDisbursement()
                || !Objects.equals(ThirdPartyDisbursementProvider.KIFIYA,
                        ThirdPartyDisbursementProvider.normalize(loanProduct.getThirdPartyDisbursementProvider()))) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.loanProduct.notKifiya",
                    "Loan product must have third-party disbursement enabled for provider KIFIYA.",
                    List.of(ApiParameterError.generalError("validation.msg.disbursementInstruction.loanProduct.notKifiya",
                            "Loan product must have third-party disbursement enabled for provider KIFIYA.")));
        }
    }

    private void applySupplierPaymentDetails(final LoanDisbursementDetails disbursementDetail, final Supplier supplier,
            final SupplierPaymentDetails paymentDetails) {
        disbursementDetail.setSupplier(supplier);
        disbursementDetail.setPaymentType(paymentDetails.getPaymentType());
        disbursementDetail.setClientPhoneNumber(paymentDetails.getPhoneNumber());
        disbursementDetail.setClientAccountNumber(paymentDetails.getAccountNumber());
        disbursementDetail.setClientBankName(paymentDetails.getBankName());
        disbursementDetail.setBeneficiaryName(paymentDetails.getBeneficiaryName());
        disbursementDetail.setPaymentTo(LoanDisbursementDetails.PaymentToType.SUPPLIER.getValue());
        disbursementDetail.setDisbursementType(LoanDisbursementDetails.DisbursementType.VENDOR.name());
    }

    private static Map<String, Object> buildResponseChanges(final Loan loan, final Supplier supplier) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(DisbursementInstructionApiConstants.LOAN_ID, loan.getId());
        changes.put(DisbursementInstructionApiConstants.LOAN_ACCOUNT_NO, loan.getAccountNumber());
        changes.put(DisbursementInstructionApiConstants.SUPPLIER_ID, supplier.getId());
        changes.put(DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID, supplier.getExternalId());
        changes.put(DisbursementInstructionApiConstants.SOURCE_SYSTEM, supplier.getSourceSystem());
        changes.put(DisbursementInstructionApiConstants.DISBURSEMENT_REQUEST_STATUS, LoanSubStatus.PENDINGDISBURSEMENT.name());
        changes.put(DisbursementInstructionApiConstants.SUCCESS, Boolean.TRUE);
        return changes;
    }

    private static String normalizeSourceSystem(final String sourceSystem) {
        return sourceSystem == null ? null : sourceSystem.trim().toUpperCase(Locale.ROOT);
    }
}
