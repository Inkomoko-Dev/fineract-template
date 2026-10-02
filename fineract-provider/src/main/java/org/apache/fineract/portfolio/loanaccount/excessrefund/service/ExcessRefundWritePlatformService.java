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

import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.ACCOUNT_NUMBER_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.AMOUNT_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.BANK_CODE_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.BANK_NAME_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.BENEFICIARY_NAME_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.BENEFICIARY_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.CHANNEL_BANK;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.CHANNEL_MOBILE;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.CHANNEL_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.EXTERNAL_ID_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.LOAN_ID_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.MSISDN_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.NOTE_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.PAID_ON_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.PAYMENT_MODE_HUB;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.PAYMENT_MODE_MANUAL;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.PAYMENT_MODE_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.PAYMENT_REF_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.PAYMENT_TYPE_ID_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.TRANSACTION_DATE_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus.APPROVED;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus.CANCELLED;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus.PAID;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus.PAYMENT_FAILED;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus.PAYMENT_SUBMITTED;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus.PENDING_APPROVAL;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus.POSTED;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus.REJECTED;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundPaymentMode;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExcessRefundWritePlatformService {

    private static final List<Integer> OPEN_STATUS_VALUES = Arrays.stream(LoanExcessRefundStatus.values())
            .filter(LoanExcessRefundStatus::isOpen).map(LoanExcessRefundStatus::getValue).collect(Collectors.toList());

    private final PlatformSecurityContext context;
    private final LoanRepositoryWrapper loanRepository;
    private final LoanExcessRefundRepository refundRepository;
    private final NoteRepository noteRepository;
    private final FromJsonHelper fromJsonHelper;
    private final ExcessRefundGlPoster glPoster;
    private final ExcessRefundPaymentHubService paymentHubService;
    private final OfficeHierarchyService officeHierarchyService;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("CREATE_EXCESS_REFUND");

        final Long loanId = command.longValueOfParameterNamed(LOAN_ID_PARAM);
        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(loanId);
        validateOverpaymentAmount(loan, command.bigDecimalValueOfParameterNamed(AMOUNT_PARAM));
        assertNoOpenRefund(loanId);

        final String beneficiaryJson = validateAndSnapshotBeneficiary(command);
        final LoanExcessRefundPaymentMode paymentMode = resolvePaymentMode(command.stringValueOfParameterNamed(PAYMENT_MODE_PARAM));
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();

        final LoanExcessRefund refund = new LoanExcessRefund();
        refund.setLoanId(loan.getId());
        refund.setClientId(loan.getClientId());
        refund.setAmount(command.bigDecimalValueOfParameterNamed(AMOUNT_PARAM));
        refund.setCurrencyCode(loan.getCurrencyCode());
        refund.setPaymentMode(paymentMode);
        refund.setPaymentTypeId(command.longValueOfParameterNamed(PAYMENT_TYPE_ID_PARAM));
        refund.setStatus(PENDING_APPROVAL);
        refund.setBeneficiaryJson(beneficiaryJson);
        refund.setSubmittedBy(user);
        refund.setSubmittedAt(now);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        try {
            this.refundRepository.saveAndFlush(refund);
        } catch (DataIntegrityViolationException ex) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.already.open",
                    "An open excess refund already exists for loan " + loanId);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.concurrent.update",
                    "Excess refund was updated by another user. Refresh and try again.");
        }

        saveLoanNote(loan, "Excess refund #" + refund.getId() + " initiated for " + refund.getAmount());
        return result(refund, loan);
    }

    @Transactional
    public CommandProcessingResult approve(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("APPROVE_EXCESS_REFUND");
        final LoanExcessRefund refund = findRefund(command.entityId());
        assertStatus(refund, PENDING_APPROVAL);
        assertMakerChecker(refund, user);
        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
        assertOfficeAccess(user, loan);

        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        refund.setStatus(APPROVED);
        refund.setApprovedBy(user);
        refund.setApprovedAt(now);
        refund.setUpdatedAt(now);
        saveRefund(refund);

        saveLoanNote(loan, "Excess refund #" + refund.getId() + " approved by " + user.getUsername());
        return result(refund, loan);
    }

    @Transactional
    public CommandProcessingResult reject(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("REJECT_EXCESS_REFUND");
        final LoanExcessRefund refund = findRefund(command.entityId());
        assertStatus(refund, PENDING_APPROVAL);
        assertMakerChecker(refund, user);
        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
        assertOfficeAccess(user, loan);

        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        refund.setStatus(REJECTED);
        refund.setRejectedBy(user);
        refund.setRejectedAt(now);
        refund.setRejectionNote(command.stringValueOfParameterNamedAllowingNull(NOTE_PARAM));
        refund.setUpdatedAt(now);
        saveRefund(refund);

        saveLoanNote(loan, "Excess refund #" + refund.getId() + " rejected: " + StringUtils.defaultString(refund.getRejectionNote()));
        return result(refund, loan);
    }

    @Transactional
    public CommandProcessingResult cancel(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("CANCEL_EXCESS_REFUND");
        final LoanExcessRefund refund = findRefund(command.entityId());
        if (!(refund.getStatus() == PENDING_APPROVAL || refund.getStatus() == APPROVED || refund.getStatus() == PAYMENT_FAILED)) {
            throw invalidStatus(refund);
        }
        refund.setStatus(CANCELLED);
        refund.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
        saveRefund(refund);
        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
        saveLoanNote(loan, "Excess refund #" + refund.getId() + " cancelled");
        return result(refund, loan);
    }

    @Transactional
    public CommandProcessingResult recordPayment(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("RECORD_PAYMENT_EXCESS_REFUND");
        final LoanExcessRefund refund = findRefund(command.entityId());
        if (!(refund.getStatus() == APPROVED || refund.getStatus() == PAYMENT_FAILED)) {
            throw invalidStatus(refund);
        }
        final String paymentRef = command.stringValueOfParameterNamed(PAYMENT_REF_PARAM);
        if (StringUtils.isBlank(paymentRef)) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.payment.reference.required",
                    "Payment reference is required for manual excess refund payment.");
        }
        final LocalDate paidOn = command.localDateValueOfParameterNamed(PAID_ON_PARAM);
        refund.setManualPaymentRef(paymentRef);
        refund.setPaidOn(paidOn != null ? paidOn : DateUtils.getBusinessLocalDate());
        refund.setStatus(PAID);
        refund.setPaymentMode(LoanExcessRefundPaymentMode.MANUAL);
        refund.setFailureReason(null);
        refund.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
        saveRefund(refund);
        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
        saveLoanNote(loan, "Excess refund #" + refund.getId() + " marked paid (manual ref " + paymentRef + ")");
        return result(refund, loan);
    }

    @Transactional
    public CommandProcessingResult sendToPaymentHub(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("SEND_TO_PAYMENTHUB_EXCESS_REFUND");
        final LoanExcessRefund refund = findRefund(command.entityId());
        if (!(refund.getStatus() == APPROVED || refund.getStatus() == PAYMENT_FAILED)) {
            throw invalidStatus(refund);
        }
        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
        final String requestId = this.paymentHubService.submitRefund(loan, refund);
        refund.setHubRequestId(requestId);
        refund.setStatus(PAYMENT_SUBMITTED);
        refund.setPaymentMode(LoanExcessRefundPaymentMode.PAYMENT_HUB);
        refund.setFailureReason(null);
        refund.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
        saveRefund(refund);
        saveLoanNote(loan, "Excess refund #" + refund.getId() + " sent to Payment Hub requestId=" + requestId);
        return result(refund, loan);
    }

    @Transactional
    public CommandProcessingResult applyHubCallback(final String hubRequestId, final boolean success, final String transactionRef,
            final String message) {
        final LoanExcessRefund refund = this.refundRepository.findByHubRequestId(hubRequestId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.excess.refund.not.found",
                        "Excess refund not found for hub request " + hubRequestId));
        if (refund.getStatus() == PAID || refund.getStatus() == POSTED) {
            final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
            return result(refund, loan);
        }
        if (refund.getStatus() != PAYMENT_SUBMITTED && refund.getStatus() != PAYMENT_FAILED) {
            throw invalidStatus(refund);
        }
        if (success) {
            refund.setStatus(PAID);
            refund.setHubTransactionRef(transactionRef);
            refund.setPaidOn(DateUtils.getBusinessLocalDate());
            refund.setFailureReason(null);
        } else {
            refund.setStatus(PAYMENT_FAILED);
            refund.setFailureReason(StringUtils.abbreviate(message, 500));
        }
        refund.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
        saveRefund(refund);
        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
        saveLoanNote(loan, success ? "Excess refund #" + refund.getId() + " Payment Hub success ref=" + transactionRef
                : "Excess refund #" + refund.getId() + " Payment Hub failed: " + message);
        return result(refund, loan);
    }

    @Transactional
    public CommandProcessingResult post(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("POST_EXCESS_REFUND");
        final LoanExcessRefund refund = findRefund(command.entityId());
        assertStatus(refund, PAID);
        if (refund.getPostedTransactionId() != null) {
            final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
            return result(refund, loan);
        }

        final Loan loan = this.loanRepository.findOneWithNotFoundDetection(refund.getLoanId());
        validateOverpaymentAmount(loan, refund.getAmount());

        LocalDate transactionDate = command.hasParameter(TRANSACTION_DATE_PARAM)
                ? command.localDateValueOfParameterNamed(TRANSACTION_DATE_PARAM)
                : (refund.getPaidOn() != null ? refund.getPaidOn() : DateUtils.getBusinessLocalDate());
        final String externalId = command.stringValueOfParameterNamedAllowingNull(EXTERNAL_ID_PARAM);
        final String note = "Excess refund #" + refund.getId() + " posted";
        try {
            final Long transactionId = this.glPoster.post(loan.getId(), transactionDate, refund.getAmount(), note, externalId);
            refund.setPostedTransactionId(transactionId);
            refund.setStatus(POSTED);
            refund.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
            saveRefund(refund);
            saveLoanNote(loan, note + " transactionId=" + transactionId);
            return result(refund, loan);
        } catch (RuntimeException ex) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.post.failed",
                    "Failed to post excess refund: " + ex.getMessage());
        }
    }

    private void validateOverpaymentAmount(final Loan loan, final BigDecimal amount) {
        final BigDecimal overpaid = loan.getTotalOverpaid() == null ? BigDecimal.ZERO : loan.getTotalOverpaid();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(overpaid) > 0) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.insufficient.overpayment",
                    "Refund amount must be > 0 and <= loan overpayment (" + overpaid + ").");
        }
    }

    private void assertNoOpenRefund(final Long loanId) {
        if (this.refundRepository.existsByLoanIdAndStatusEnumIn(loanId, OPEN_STATUS_VALUES)) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.already.open",
                    "An open excess refund already exists for loan " + loanId);
        }
    }

    private String validateAndSnapshotBeneficiary(final JsonCommand command) {
        final JsonElement element = command.parsedJson();
        if (element == null || !element.isJsonObject() || !element.getAsJsonObject().has(BENEFICIARY_PARAM)) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.beneficiary.invalid",
                    "Beneficiary details are required.");
        }
        final JsonObject beneficiary = element.getAsJsonObject().getAsJsonObject(BENEFICIARY_PARAM);
        final String channel = this.fromJsonHelper.extractStringNamed(CHANNEL_PARAM, beneficiary);
        if (CHANNEL_BANK.equalsIgnoreCase(channel)) {
            final String account = this.fromJsonHelper.extractStringNamed(ACCOUNT_NUMBER_PARAM, beneficiary);
            final String bankCode = this.fromJsonHelper.extractStringNamed(BANK_CODE_PARAM, beneficiary);
            final String bankName = this.fromJsonHelper.extractStringNamed(BANK_NAME_PARAM, beneficiary);
            if (StringUtils.isAnyBlank(account, bankCode) && StringUtils.isBlank(bankName)) {
                throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.beneficiary.invalid",
                        "Bank transfer requires account number and bank details.");
            }
            if (StringUtils.isBlank(account) || (StringUtils.isBlank(bankCode) && StringUtils.isBlank(bankName))) {
                throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.beneficiary.invalid",
                        "Bank transfer requires account number and bank details.");
            }
        } else if (CHANNEL_MOBILE.equalsIgnoreCase(channel)) {
            final String msisdn = this.fromJsonHelper.extractStringNamed(MSISDN_PARAM, beneficiary);
            if (StringUtils.isBlank(msisdn)) {
                throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.beneficiary.invalid",
                        "Mobile money requires MSISDN.");
            }
        } else {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.beneficiary.invalid",
                    "Beneficiary channel must be BANK_TRANSFER or MOBILE_MONEY.");
        }
        if (!beneficiary.has(BENEFICIARY_NAME_PARAM)
                || StringUtils.isBlank(this.fromJsonHelper.extractStringNamed(BENEFICIARY_NAME_PARAM, beneficiary))) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.beneficiary.invalid",
                    "Beneficiary name is required.");
        }
        return beneficiary.toString();
    }

    private LoanExcessRefundPaymentMode resolvePaymentMode(final String mode) {
        if (PAYMENT_MODE_HUB.equalsIgnoreCase(mode) || "HUB".equalsIgnoreCase(mode)) {
            return LoanExcessRefundPaymentMode.PAYMENT_HUB;
        }
        if (PAYMENT_MODE_MANUAL.equalsIgnoreCase(mode) || StringUtils.isBlank(mode)) {
            return LoanExcessRefundPaymentMode.MANUAL;
        }
        throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.payment.mode.invalid",
                "Payment mode must be MANUAL or PAYMENT_HUB.");
    }

    private void assertMakerChecker(final LoanExcessRefund refund, final AppUser user) {
        if (refund.getSubmittedBy() != null && refund.getSubmittedBy().getId().equals(user.getId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.approver.same.as.maker",
                    "Maker and checker must be different users.");
        }
    }

    private void assertOfficeAccess(final AppUser user, final Loan loan) {
        Long officeId = loan.getOfficeId();
        if (officeId == null && loan.getClient() != null) {
            officeId = loan.getClient().getOffice() == null ? null : loan.getClient().getOffice().getId();
        }
        if (officeId == null || !this.officeHierarchyService.validateUserAccessToOffice(user, officeId)) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.office.denied",
                    "User does not have access to the loan office for this excess refund.");
        }
    }

    private void assertStatus(final LoanExcessRefund refund, final LoanExcessRefundStatus expected) {
        if (refund.getStatus() != expected) {
            throw invalidStatus(refund);
        }
    }

    private GeneralPlatformDomainRuleException invalidStatus(final LoanExcessRefund refund) {
        return new GeneralPlatformDomainRuleException("error.msg.excess.refund.invalid.status",
                "Excess refund is in invalid status for this action: " + refund.getStatus());
    }

    private LoanExcessRefund findRefund(final Long id) {
        return this.refundRepository.findById(id).orElseThrow(
                () -> new GeneralPlatformDomainRuleException("error.msg.excess.refund.not.found", "Excess refund not found: " + id));
    }

    private void saveRefund(final LoanExcessRefund refund) {
        try {
            this.refundRepository.saveAndFlush(refund);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.concurrent.update",
                    "Excess refund was updated by another user. Refresh and try again.");
        }
    }

    private void saveLoanNote(final Loan loan, final String text) {
        this.noteRepository.saveAndFlush(Note.loanNote(loan, text));
    }

    private CommandProcessingResult result(final LoanExcessRefund refund, final Loan loan) {
        final Map<String, Object> changes = new HashMap<>();
        changes.put("status", refund.getStatus().name());
        changes.put("amount", refund.getAmount());
        return new CommandProcessingResultBuilder().withEntityId(refund.getId()).withLoanId(loan.getId()).withClientId(loan.getClientId())
                .withOfficeId(loan.getOfficeId()).with(changes).build();
    }
}
