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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.data.SupplierDisbursementSnapshot;
import org.apache.fineract.portfolio.loanaccount.data.SupplierPaymentDetails;
import org.apache.fineract.portfolio.loanaccount.domain.DisbursementInstructionStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstruction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstructionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSubStatus;
import org.apache.fineract.portfolio.loanaccount.exception.DisbursementInstructionIdempotencyConflictException;
import org.apache.fineract.portfolio.loanaccount.exception.LoanDisbursalException;
import org.apache.fineract.portfolio.loanaccount.serialization.DisbursementInstructionDataValidator;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementPartnerAccessService;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
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
    private final DisbursementProviderReadPlatformService disbursementProviderReadPlatformService;
    private final DisbursementPartnerAccessService disbursementPartnerAccessService;
    private final LoanDisbursementInstructionRepository loanDisbursementInstructionRepository;

    @Override
    @Transactional
    public CommandProcessingResult createDisbursementInstruction(final JsonCommand command) {
        this.validator.validateCreateRequest(command.json());

        final String loanAccountNo = command.stringValueOfParameterNamed(DisbursementInstructionApiConstants.LOAN_ACCOUNT_NO);
        final String sourceSystem = normalizeSourceSystem(command.stringValueOfParameterNamed(DisbursementInstructionApiConstants.SOURCE_SYSTEM));
        final String supplierExternalId = command.stringValueOfParameterNamed(DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID)
                .trim();
        final String idempotencyKey = StringUtils.trimToNull(command.stringValueOfParameterNamed(DisbursementInstructionApiConstants.IDEMPOTENCY_KEY));

        final AppUser currentUser = this.context.getAuthenticatedUserIfPresent();
        assertCallerBoundToSourceSystem(currentUser, sourceSystem);

        final Optional<LoanDisbursementInstruction> existingByKey = this.loanDisbursementInstructionRepository
                .findByDisbursementProviderCodeAndIdempotencyKey(sourceSystem, idempotencyKey);
        if (existingByKey.isPresent()) {
            return replayOrConflict(existingByKey.get(), loanAccountNo, supplierExternalId, command.commandId());
        }

        final LoanAccountData loanAccountData = this.loanReadPlatformService.retrieveLoanByLoanAccount(loanAccountNo);
        final Loan loan = this.loanAssembler.assembleFrom(loanAccountData.getId());
        validateLoanForDisbursementInstruction(loan, sourceSystem);

        final Supplier supplier = this.supplierRepository.findBySourceSystemAndExternalId(sourceSystem, supplierExternalId)
                .orElse(null);
        final SupplierPaymentDetails paymentDetails = this.supplierPaymentDetailsValidator.validateAndExtract(supplier);

        final LoanDisbursementDetails disbursementDetail = loan.getDisbursementDetails().stream().findFirst()
                .orElseThrow(() -> new PlatformApiDataValidationException("validation.msg.disbursementInstruction.missingDisbursementDetails",
                        "Loan disbursement details are missing.",
                        List.of(ApiParameterError.generalError("validation.msg.disbursementInstruction.missingDisbursementDetails",
                                "Loan disbursement details are missing."))));

        final Long createdById = currentUser == null ? null : currentUser.getId();
        final LoanDisbursementInstruction instruction = LoanDisbursementInstruction.createReceived(loan.getId(), sourceSystem,
                supplier.getId(), supplierExternalId, idempotencyKey, createdById);
        try {
            this.loanDisbursementInstructionRepository.saveAndFlush(instruction);
        } catch (final DataIntegrityViolationException | JpaSystemException ex) {
            final Optional<LoanDisbursementInstruction> raced = this.loanDisbursementInstructionRepository
                    .findByDisbursementProviderCodeAndIdempotencyKey(sourceSystem, idempotencyKey);
            if (raced.isPresent()) {
                return replayOrConflict(raced.get(), loanAccountNo, supplierExternalId, command.commandId());
            }
            throw ex;
        }

        try {
            final SupplierDisbursementSnapshot recipientSnapshotBeforeUpdate = SupplierDisbursementSnapshot.from(disbursementDetail);
            applySupplierPaymentDetails(disbursementDetail, supplier, paymentDetails);
            this.supplierDisbursementAuditService.recordChange(loan, disbursementDetail, recipientSnapshotBeforeUpdate,
                    SupplierDisbursementSnapshot.from(disbursementDetail),
                    SupplierDisbursementAuditService.CHANGE_SOURCE_DISBURSEMENT_INSTRUCTION, currentUser);
            loan.handleDisbursementRequest();
            this.loanRepositoryWrapper.saveAndFlush(loan);

            instruction.markPendingDisbursement(disbursementDetail.getId());
            this.loanDisbursementInstructionRepository.saveAndFlush(instruction);
        } catch (final RuntimeException ex) {
            instruction.markFailed(ex.getMessage());
            this.loanDisbursementInstructionRepository.saveAndFlush(instruction);
            throw ex;
        }

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(instruction.getId())
                .withLoanId(loan.getId()).with(buildResponseChanges(loan, supplier, instruction, false)).build();
    }

    CommandProcessingResult replayOrConflict(final LoanDisbursementInstruction existing, final String loanAccountNo,
            final String supplierExternalId, final Long commandId) {
        final Loan loan = this.loanAssembler.assembleFrom(existing.getLoanId());
        if (!Objects.equals(loan.getAccountNumber(), loanAccountNo)
                || !Objects.equals(existing.getSupplierExternalId(), supplierExternalId)) {
            throw new DisbursementInstructionIdempotencyConflictException(
                    "validation.msg.disbursementInstruction.idempotencyKey.payloadConflict",
                    "Idempotency-Key was already used with a different loan or supplier payload.",
                    List.of(ApiParameterError.parameterError("validation.msg.disbursementInstruction.idempotencyKey.payloadConflict",
                            "Idempotency-Key was already used with a different loan or supplier payload.",
                            DisbursementInstructionApiConstants.IDEMPOTENCY_KEY_HEADER, existing.getIdempotencyKey())));
        }
        final Supplier supplier = existing.getSupplierId() == null ? null
                : this.supplierRepository.findById(existing.getSupplierId()).orElse(null);
        return new CommandProcessingResultBuilder().withCommandId(commandId).withEntityId(existing.getId()).withLoanId(loan.getId())
                .with(buildResponseChanges(loan, supplier, existing, true)).build();
    }

    void assertCallerBoundToSourceSystem(final AppUser currentUser, final String sourceSystem) {
        final String boundProvider = this.disbursementPartnerAccessService.resolveProviderCodeForUser(currentUser).orElse(null);
        if (boundProvider == null) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.partnerBinding.required",
                    "Authenticated user is not bound to a disbursement provider.",
                    List.of(ApiParameterError.generalError("validation.msg.disbursementInstruction.partnerBinding.required",
                            "Authenticated user is not bound to a disbursement provider. "
                                    + "Seed m_disbursement_provider_appuser_mapping for this app user.")));
        }
        if (!Objects.equals(boundProvider, sourceSystem)) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.partnerBinding.mismatch",
                    "Instruction sourceSystem does not match the authenticated partner binding.",
                    List.of(ApiParameterError.parameterError("validation.msg.disbursementInstruction.partnerBinding.mismatch",
                            "Instruction sourceSystem does not match the authenticated partner binding.",
                            DisbursementInstructionApiConstants.SOURCE_SYSTEM, sourceSystem)));
        }
    }

    void validateLoanForDisbursementInstruction(final Loan loan, final String sourceSystem) {
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

        final String mappedProvider = this.disbursementProviderReadPlatformService.findActiveMappedProviderCode(loan.productId())
                .orElse(null);
        if (mappedProvider == null) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.loanProduct.notThirdPartyDisbursement",
                    "Loan product is not configured for third-party disbursement.",
                    List.of(ApiParameterError.generalError("validation.msg.disbursementInstruction.loanProduct.notThirdPartyDisbursement",
                            "Loan product is not configured for third-party disbursement.")));
        }

        if (!Objects.equals(mappedProvider, sourceSystem)) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.loanProduct.providerMismatch",
                    "Loan product disbursement provider does not match the instruction source system.",
                    List.of(ApiParameterError.parameterError("validation.msg.disbursementInstruction.loanProduct.providerMismatch",
                            "Loan product disbursement provider does not match the instruction source system.",
                            DisbursementInstructionApiConstants.SOURCE_SYSTEM, sourceSystem)));
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

    private static Map<String, Object> buildResponseChanges(final Loan loan, final Supplier supplier,
            final LoanDisbursementInstruction instruction, final boolean replayed) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(DisbursementInstructionApiConstants.INSTRUCTION_ID, instruction.getId());
        changes.put(DisbursementInstructionApiConstants.INSTRUCTION_STATUS, instruction.getStatus().name());
        changes.put(DisbursementInstructionApiConstants.IDEMPOTENCY_KEY, instruction.getIdempotencyKey());
        changes.put(DisbursementInstructionApiConstants.REPLAYED, replayed);
        changes.put(DisbursementInstructionApiConstants.LOAN_ID, loan.getId());
        changes.put(DisbursementInstructionApiConstants.LOAN_ACCOUNT_NO, loan.getAccountNumber());
        if (supplier != null) {
            changes.put(DisbursementInstructionApiConstants.SUPPLIER_ID, supplier.getId());
            changes.put(DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID, supplier.getExternalId());
            changes.put(DisbursementInstructionApiConstants.SOURCE_SYSTEM, supplier.getSourceSystem());
        } else {
            changes.put(DisbursementInstructionApiConstants.SUPPLIER_ID, instruction.getSupplierId());
            changes.put(DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID, instruction.getSupplierExternalId());
            changes.put(DisbursementInstructionApiConstants.SOURCE_SYSTEM, instruction.getDisbursementProviderCode());
        }
        if (instruction.getStatus() == DisbursementInstructionStatus.PENDING_DISBURSEMENT) {
            changes.put(DisbursementInstructionApiConstants.DISBURSEMENT_REQUEST_STATUS, LoanSubStatus.PENDINGDISBURSEMENT.name());
        }
        changes.put(DisbursementInstructionApiConstants.SUCCESS, Boolean.TRUE);
        return changes;
    }

    private static String normalizeSourceSystem(final String sourceSystem) {
        return sourceSystem == null ? null : sourceSystem.trim().toUpperCase(Locale.ROOT);
    }
}
