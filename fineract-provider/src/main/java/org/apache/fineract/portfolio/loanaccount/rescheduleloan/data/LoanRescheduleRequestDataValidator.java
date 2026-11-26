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
package org.apache.fineract.portfolio.loanaccount.rescheduleloan.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanRepaymentFrequency;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.service.RescheduleFromInstallmentResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LoanRescheduleRequestDataValidator {

    private final FromJsonHelper fromJsonHelper;
    private final LoanRescheduleRequestRepository loanRescheduleRequestRepository;
    private static final Set<String> CREATE_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(
            RescheduleLoansApiConstants.localeParamName, RescheduleLoansApiConstants.dateFormatParamName,
            RescheduleLoansApiConstants.graceOnPrincipalParamName,
            RescheduleLoansApiConstants.recurringMoratoriumOnPrincipalPeriodsParamName,
            RescheduleLoansApiConstants.graceOnInterestParamName, RescheduleLoansApiConstants.extraTermsParamName,
            RescheduleLoansApiConstants.rescheduleFromDateParamName, RescheduleLoansApiConstants.newInterestRateParamName,
            RescheduleLoansApiConstants.rescheduleReasonIdParamName, RescheduleLoansApiConstants.rescheduleReasonCommentParamName,
            RescheduleLoansApiConstants.submittedOnDateParamName, RescheduleLoansApiConstants.loanIdParamName,
            RescheduleLoansApiConstants.adjustedDueDateParamName, RescheduleLoansApiConstants.recalculateInterestParamName,
            RescheduleLoansApiConstants.recalculateEvenIfObligationsMetParamName,
            RescheduleLoansApiConstants.endDateParamName, RescheduleLoansApiConstants.emiParamName,
            RescheduleLoansApiConstants.newPrincipalDueFixedAmount, RescheduleLoansApiConstants.newFixedPrincipalPercentagePerInstallment,
            RescheduleLoansApiConstants.overdueChargeHandlingParamName, RescheduleLoansApiConstants.carryForwardChargeIdParamName, RescheduleLoansApiConstants.carryForwardChargeDueDateParamName,
            RescheduleLoansApiConstants.repaymentEveryParamName, RescheduleLoansApiConstants.repaymentFrequencyTypeParamName,
            RescheduleLoansApiConstants.preserveLoanTermDurationParamName,
            RescheduleLoansApiConstants.acknowledgeMultiTrancheRescheduleParamName));

    private static final Set<String> REJECT_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(RescheduleLoansApiConstants.localeParamName, RescheduleLoansApiConstants.dateFormatParamName,
                    RescheduleLoansApiConstants.rejectedOnDateParam));

    private static final Set<String> APPROVE_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(RescheduleLoansApiConstants.localeParamName, RescheduleLoansApiConstants.dateFormatParamName,
                    RescheduleLoansApiConstants.approvedOnDateParam,
                    RescheduleLoansApiConstants.acknowledgeMultiTrancheRescheduleParamName));

    public static final Set<String> UNDO_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(RescheduleLoansApiConstants.notesParamName));

    @Autowired
    public LoanRescheduleRequestDataValidator(FromJsonHelper fromJsonHelper,
            LoanRescheduleRequestRepository loanRescheduleRequestRepository) {
        this.fromJsonHelper = fromJsonHelper;
        this.loanRescheduleRequestRepository = loanRescheduleRequestRepository;
    }

    /**
     * Validates the request to create a new loan reschedule entry
     *
     * @param jsonCommand
     *            the JSON command object (instance of the JsonCommand class)
     *
     **/
    public void validateForCreateAction(final JsonCommand jsonCommand, final Loan loan) {

        String jsonString = jsonCommand.json();

        if (StringUtils.isBlank(jsonString)) {
            jsonString = fromJsonHelper.toJson(jsonCommand.parsedJson());
            if (StringUtils.isBlank(jsonString))
                throw new InvalidJsonException();
        }

        final Type typeToken = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromJsonHelper.checkForUnsupportedParameters(typeToken, jsonString, CREATE_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder dataValidatorBuilder = new DataValidatorBuilder(dataValidationErrors)
                .resource(StringUtils.lowerCase(RescheduleLoansApiConstants.ENTITY_NAME));

        final JsonElement jsonElement = jsonCommand.parsedJson();

        if (!loan.status().isActive()) {
            dataValidatorBuilder.reset().failWithCodeNoParameterAddedToErrorCode("loan.is.not.active", "Loan is not active");
        }

        if (loan.getId() != null && this.loanRescheduleRequestRepository != null) {
            final List<LoanRescheduleRequest> pending = this.loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(loan.getId(),
                    LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getValue());
            if (pending != null && !pending.isEmpty()) {
                dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.loanIdParamName).failWithCode(
                        "loan.already.has.pending.reschedule.request", "Loan already has a pending reschedule request");
            }
        }

        final Long loanId = this.fromJsonHelper.extractLongNamed(RescheduleLoansApiConstants.loanIdParamName, jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.loanIdParamName).value(loanId).notNull()
                .integerGreaterThanZero();

        final LocalDate submittedOnDate = this.fromJsonHelper.extractLocalDateNamed(RescheduleLoansApiConstants.submittedOnDateParamName,
                jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.submittedOnDateParamName).value(submittedOnDate).notNull();

        if (submittedOnDate != null && loan.getDisbursementDate().isAfter(submittedOnDate)) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.submittedOnDateParamName)
                    .failWithCode("before.loan.disbursement.date", "Submission date cannot be before the loan disbursement date");
        }

        final LocalDate rescheduleFromDate = this.fromJsonHelper
                .extractLocalDateNamed(RescheduleLoansApiConstants.rescheduleFromDateParamName, jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rescheduleFromDateParamName).value(rescheduleFromDate).notNull();

        final Integer graceOnPrincipal = this.fromJsonHelper
                .extractIntegerWithLocaleNamed(RescheduleLoansApiConstants.graceOnPrincipalParamName, jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.graceOnPrincipalParamName).value(graceOnPrincipal).ignoreIfNull()
                .integerGreaterThanZero();

        final Integer graceOnInterest = this.fromJsonHelper
                .extractIntegerWithLocaleNamed(RescheduleLoansApiConstants.graceOnInterestParamName, jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.graceOnInterestParamName).value(graceOnInterest).ignoreIfNull()
                .integerGreaterThanZero();

        final Boolean preserveLoanTermDuration = this.fromJsonHelper
                .extractBooleanNamed(RescheduleLoansApiConstants.preserveLoanTermDurationParamName, jsonElement);
        final Integer extraTerms = this.fromJsonHelper.extractIntegerWithLocaleNamed(RescheduleLoansApiConstants.extraTermsParamName,
                jsonElement);
        if (!Boolean.TRUE.equals(preserveLoanTermDuration)) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.extraTermsParamName).value(extraTerms).ignoreIfNull()
                    .integerGreaterThanZero();
        }

        final Long rescheduleReasonId = this.fromJsonHelper.extractLongNamed(RescheduleLoansApiConstants.rescheduleReasonIdParamName,
                jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rescheduleReasonIdParamName).value(rescheduleReasonId).notNull()
                .integerGreaterThanZero();

        final String rescheduleReasonComment = this.fromJsonHelper
                .extractStringNamed(RescheduleLoansApiConstants.rescheduleReasonCommentParamName, jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rescheduleReasonCommentParamName).value(rescheduleReasonComment)
                .ignoreIfNull().notExceedingLengthOf(500);

        final Integer repaymentEvery = this.fromJsonHelper
                .extractIntegerWithLocaleNamed(RescheduleLoansApiConstants.repaymentEveryParamName, jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.repaymentEveryParamName).value(repaymentEvery).ignoreIfNull()
                .integerGreaterThanZero();

        final Integer repaymentFrequencyType = this.fromJsonHelper
                .extractIntegerWithLocaleNamed(RescheduleLoansApiConstants.repaymentFrequencyTypeParamName, jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.repaymentFrequencyTypeParamName).value(repaymentFrequencyType)
                .ignoreIfNull().inMinMaxRange(PeriodFrequencyType.DAYS.getValue(), PeriodFrequencyType.YEARS.getValue());

        final boolean hasRepaymentEvery = this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.repaymentEveryParamName,
                jsonElement);
        final boolean hasRepaymentFrequencyType = this.fromJsonHelper
                .parameterExists(RescheduleLoansApiConstants.repaymentFrequencyTypeParamName, jsonElement);
        if (hasRepaymentEvery || hasRepaymentFrequencyType) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.repaymentEveryParamName).value(repaymentEvery).notNull();
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.repaymentFrequencyTypeParamName)
                    .value(repaymentFrequencyType).notNull();
            if (repaymentEvery != null && repaymentFrequencyType != null) {
                LoanRepaymentFrequency.validateLoan(dataValidationErrors, loan.getTermFrequency(), loan.getTermPeriodFrequencyType(),
                        loan.getNumberOfRepayments(), repaymentEvery, repaymentFrequencyType);
            }
        }

        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.preserveLoanTermDurationParamName)
                .value(preserveLoanTermDuration).ignoreIfNull();

        // Validation: preserveLoanTermDuration can only be used when changing repayment frequency
        if (Boolean.TRUE.equals(preserveLoanTermDuration)) {
            if (extraTerms != null && extraTerms != 0) {
                dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.extraTermsParamName)
                        .failWithCode("incompatible.with.preserve.loan.term.duration",
                                "Extra terms must be zero when preserving the loan term duration");
            }
            for (final String parameter : List.of(RescheduleLoansApiConstants.adjustedDueDateParamName,
                    RescheduleLoansApiConstants.emiParamName, RescheduleLoansApiConstants.newPrincipalDueFixedAmount,
                    RescheduleLoansApiConstants.newFixedPrincipalPercentagePerInstallment)) {
                if (this.fromJsonHelper.parameterExists(parameter, jsonElement)) {
                    dataValidatorBuilder.reset().parameter(parameter)
                            .failWithCode("incompatible.with.preserve.loan.term.duration",
                                    "This adjustment cannot be combined with preserving the original maturity");
                }
            }
            if (repaymentEvery == null || repaymentFrequencyType == null) {
                dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.preserveLoanTermDurationParamName)
                        .failWithCode("preserve.loan.term.duration.requires.frequency.change",
                                "preserveLoanTermDuration can only be used when changing repayment frequency");
            }
        }

        if (this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.emiParamName, jsonElement)
                || this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.endDateParamName, jsonElement)) {
            final LocalDate endDate = jsonCommand.localDateValueOfParameterNamed(RescheduleLoansApiConstants.endDateParamName);
            final BigDecimal emi = jsonCommand.bigDecimalValueOfParameterNamed(RescheduleLoansApiConstants.emiParamName);

            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.endDateParamName).value(endDate).notNull();
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.emiParamName).value(emi).notNull().positiveAmount();

            if (endDate != null) {
                LoanRepaymentScheduleInstallment endInstallment = loan.getRepaymentScheduleInstallment(endDate);

                if (endInstallment == null) {
                    dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.endDateParamName)
                            .failWithCode("repayment.schedule.installment.does.not.exist", "Repayment schedule installment does not exist");
                }
            }

        }

        validateFixedPrincipalChange(dataValidatorBuilder, jsonCommand, jsonElement);

        final LocalDate adjustedDueDate = this.fromJsonHelper.extractLocalDateNamed(RescheduleLoansApiConstants.adjustedDueDateParamName,
                jsonElement);

        if (adjustedDueDate != null && rescheduleFromDate != null && adjustedDueDate.isBefore(rescheduleFromDate)) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rescheduleFromDateParamName).failWithCode(
                    "adjustedDueDate.before.rescheduleFromDate", "Adjusted due date cannot be before the reschedule from date");
        }

        // at least one of the following must be provided => graceOnPrincipal,
        // graceOnInterest, extraTerms,
        // newInterestRate,newFixedPrincipalPercentagePerInstallment,newPrincipalDueFixedAmount
        if (!this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.graceOnPrincipalParamName, jsonElement)
                && !this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.graceOnInterestParamName, jsonElement)
                && !this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.extraTermsParamName, jsonElement)
                && !this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.newInterestRateParamName, jsonElement)
                && !this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.adjustedDueDateParamName, jsonElement)
                && !this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.emiParamName, jsonElement)
                && !this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.newPrincipalDueFixedAmount, jsonElement)
                && !this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.newFixedPrincipalPercentagePerInstallment,
                        jsonElement)
                && !hasRepaymentEvery
                && !hasRepaymentFrequencyType) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.graceOnPrincipalParamName).notNull();
        }
        if (rescheduleFromDate != null && loan.getDisbursementDate() != null
                && rescheduleFromDate.isBefore(loan.getDisbursementDate())) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rescheduleFromDateParamName)
                    .failWithCode("before.loan.disbursement.date",
                            "Reschedule from date cannot be before the loan disbursement date");
        }

        LoanRepaymentScheduleInstallment installment = null;
        if (rescheduleFromDate != null) {

            installment = loan.getRepaymentScheduleInstallment(rescheduleFromDate);

            if (installment == null) {
                // CGLT-857: exact due-date miss is allowed only when a later/equal due date exists
                // (date before first installment → installment 1; between dues → next installment).
                final var resolvedInstallmentNumber = RescheduleFromInstallmentResolver
                        .resolveInstallmentNumber(loan.getRepaymentScheduleInstallments(), rescheduleFromDate);
                if (resolvedInstallmentNumber.isEmpty()) {
                    dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rescheduleFromDateParamName)
                            .failWithCode("repayment.schedule.installment.does.not.exist",
                                    "Repayment schedule installment does not exist");
                } else {
                    final LoanRepaymentScheduleInstallment resolvedInstallment = loan
                            .fetchRepaymentScheduleInstallment(resolvedInstallmentNumber.get());
                    if (resolvedInstallment != null && resolvedInstallment.isObligationsMet()
                            && !isRecalculateEvenIfObligationsMet(jsonElement)) {
                        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rescheduleFromDateParamName)
                                .failWithCode("repayment.schedule.installment.obligation.met",
                                        "Repayment schedule installment obligation met");
                    }
                }
            } else if (installment.isObligationsMet() && !isRecalculateEvenIfObligationsMet(jsonElement)) {
                dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rescheduleFromDateParamName)
                        .failWithCode("repayment.schedule.installment.obligation.met",
                                "Repayment schedule installment obligation met");
            }

        }

        final Boolean acknowledgeMultiTrancheReschedule = this.fromJsonHelper
                .extractBooleanNamed(RescheduleLoansApiConstants.acknowledgeMultiTrancheRescheduleParamName, jsonElement);
        rejectUnacknowledgedMultiTrancheReschedule(dataValidationErrors, loan, rescheduleFromDate, acknowledgeMultiTrancheReschedule);
        if (loan.getTotal_extensions() != null && loan.getLoanProduct().getMaxNumberOfLoanExtensionsAllowed() != null) {
            if (loan.getTotal_extensions() >= loan.getLoanProduct().getMaxNumberOfLoanExtensionsAllowed()) {
                dataValidatorBuilder.reset().parameter("totalExtensions").failWithCode("maximum.number.of.loan.extensions.exceeded",
                        "maximum number of loan extensions exceeded");
            }
        }

        validateForOverdueCharges(dataValidatorBuilder, loan, rescheduleFromDate, jsonCommand);
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    private void rejectUnacknowledgedMultiTrancheReschedule(final List<ApiParameterError> dataValidationErrors, final Loan loan,
            final LocalDate rescheduleFromDate, final Boolean acknowledged) {
        final String rejection = MultiTrancheRescheduleRules.rejection(loan, rescheduleFromDate, acknowledged);
        if (rejection == null) {
            return;
        }
        final String code = MultiTrancheRescheduleRules.NOTHING_DISBURSED.equals(rejection)
                ? MultiTrancheRescheduleRules.NOTHING_DISBURSED
                : MultiTrancheRescheduleRules.ACKNOWLEDGEMENT_REQUIRED;
        final String message = MultiTrancheRescheduleRules.NOTHING_DISBURSED.equals(rejection)
                ? "This multi-tranche loan has no disbursed tranche, so there is no principal to reschedule."
                : rejection;
        dataValidationErrors.add(ApiParameterError.parameterError(code, message,
                RescheduleLoansApiConstants.acknowledgeMultiTrancheRescheduleParamName));
    }

    private void validateFixedPrincipalChange(final DataValidatorBuilder dataValidatorBuilder, final JsonCommand jsonCommand,
            final JsonElement jsonElement) {
        final boolean hasFixedAmount = this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.newPrincipalDueFixedAmount,
                jsonElement);
        final boolean hasFixedPercentage = this.fromJsonHelper
                .parameterExists(RescheduleLoansApiConstants.newFixedPrincipalPercentagePerInstallment, jsonElement);

        if (hasFixedAmount) {
            final BigDecimal fixedAmount = jsonCommand
                    .bigDecimalValueOfParameterNamed(RescheduleLoansApiConstants.newPrincipalDueFixedAmount);
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.newPrincipalDueFixedAmount).value(fixedAmount).notNull()
                    .positiveAmount();
        }

        if (hasFixedPercentage) {
            final BigDecimal fixedPercentage = jsonCommand
                    .bigDecimalValueOfParameterNamed(RescheduleLoansApiConstants.newFixedPrincipalPercentagePerInstallment);
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.newFixedPrincipalPercentagePerInstallment)
                    .value(fixedPercentage).notNull().positiveAmount().notGreaterThanMax(BigDecimal.valueOf(100));
        }

        if (hasFixedAmount && hasFixedPercentage) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.newFixedPrincipalPercentagePerInstallment).failWithCode(
                    "cannot.be.combined.with.newPrincipalDueFixedAmount",
                    "Provide either a new fixed principal amount or a new fixed principal percentage, not both");
        }

        if ((hasFixedAmount || hasFixedPercentage)
                && this.fromJsonHelper.parameterExists(RescheduleLoansApiConstants.emiParamName, jsonElement)) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.emiParamName).failWithCode(
                    "cannot.be.combined.with.fixed.principal", "An EMI change cannot be combined with a fixed principal change");
        }
    }

//    private void validateForOverdueCharges(DataValidatorBuilder dataValidatorBuilder, final Loan loan,
//            final LoanRepaymentScheduleInstallment installment) {
//        if (installment != null) {
//            LocalDate rescheduleFromDate = installment.getFromDate();
//            Collection<LoanCharge> charges = loan.getLoanCharges();
//            for (LoanCharge loanCharge : charges) {
//                if (loanCharge.isOverdueInstallmentCharge() && loanCharge.getDueLocalDate().isAfter(rescheduleFromDate)
//                        && !loanCharge.isWaived() && !loanCharge.isPaid()) {
//                    dataValidatorBuilder.failWithCodeNoParameterAddedToErrorCode("not.allowed.due.to.overdue.charges");
//                    break;
//                }
//            }
//        }
//    }

    private void validateForOverdueCharges(DataValidatorBuilder dataValidatorBuilder, final Loan loan,
            final LocalDate rescheduleFromDate, final JsonCommand command) {
        if (rescheduleFromDate == null) {
            return;
        }
        // Prefer installment.fromDate when exact match exists; otherwise use request date (CGLT-857).
        LocalDate threshold = rescheduleFromDate;
        LoanRepaymentScheduleInstallment matched = loan.getRepaymentScheduleInstallment(rescheduleFromDate);
        if (matched != null && matched.getFromDate() != null) {
            threshold = matched.getFromDate();
        }

        Collection<LoanCharge> charges = loan.getLoanCharges();
        final JsonElement chargeHandlingObject = command.jsonElement("overdueChargeHandling");

        boolean hasOverdueCharges = false;
        for (LoanCharge loanCharge : charges) {
            if (loanCharge.isOverdueInstallmentCharge() && loanCharge.getDueLocalDate().isAfter(threshold)
                    && !loanCharge.isWaived() && !loanCharge.isPaid()) {
                hasOverdueCharges = true;
                break;
            }
        }

        if (chargeHandlingObject == null) {
            dataValidatorBuilder.failWithCodeNoParameterAddedToErrorCode(
                    "not.allowed.due.to.overdue.charges.must.choose.option");
            return;
        }

        final JsonObject chargeHandlingjson = chargeHandlingObject.getAsJsonObject();
        String chargeHandling = chargeHandlingjson.get("name").getAsString();

        if (hasOverdueCharges) {
            if (!RescheduleLoansApiConstants.IGNORE_CHARGES.equalsIgnoreCase(chargeHandling)
                    && !RescheduleLoansApiConstants.CARRY_CHARGES_FORWARD.equalsIgnoreCase(chargeHandling)) {
                dataValidatorBuilder.failWithCodeNoParameterAddedToErrorCode(
                        "not.allowed.due.to.overdue.charges.must.choose.option");
            } else if (RescheduleLoansApiConstants.CARRY_CHARGES_FORWARD.equalsIgnoreCase(chargeHandling)) {
                Long carryForwardChargeDefId = command.longValueOfParameterNamed("carryForwardChargeId");
                if (carryForwardChargeDefId == null) {
                    dataValidatorBuilder.failWithCodeNoParameterAddedToErrorCode(
                            "not.allowed.due.to.overdue.charges.must.choose.charge");
                }
            }
        }
    }


    /**
     * Validates a user request to approve a loan reschedule request
     *
     * @param jsonCommand
     *            the JSON command object (instance of the JsonCommand class)
     *
     **/
    public void validateForApproveAction(final JsonCommand jsonCommand, LoanRescheduleRequest loanRescheduleRequest) {
        final String jsonString = jsonCommand.json();

        if (StringUtils.isBlank(jsonString)) {
            throw new InvalidJsonException();
        }

        final Type typeToken = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromJsonHelper.checkForUnsupportedParameters(typeToken, jsonString, APPROVE_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder dataValidatorBuilder = new DataValidatorBuilder(dataValidationErrors)
                .resource(StringUtils.lowerCase(RescheduleLoansApiConstants.ENTITY_NAME));

        final JsonElement jsonElement = jsonCommand.parsedJson();

        final LocalDate approvedOnDate = this.fromJsonHelper.extractLocalDateNamed(RescheduleLoansApiConstants.approvedOnDateParam,
                jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.approvedOnDateParam).value(approvedOnDate).notNull();

        if (approvedOnDate != null && loanRescheduleRequest.getSubmittedOnDate().isAfter(approvedOnDate)) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.approvedOnDateParam).failWithCode("before.submission.date",
                    "Approval date cannot be before the request submission date.");
        }

        LoanRescheduleRequestStatusEnumData loanRescheduleRequestStatusEnumData = LoanRescheduleRequestEnumerations
                .status(loanRescheduleRequest.getStatusEnum());

        if (!loanRescheduleRequestStatusEnumData.isPendingApproval()) {
            dataValidatorBuilder.reset().failWithCodeNoParameterAddedToErrorCode("request.is.not.in.submitted.and.pending.state",
                    "Loan reschedule request approval is not allowed. "
                            + "Loan reschedule request is not in submitted and pending approval state.");
        }

        LocalDate rescheduleFromDate = loanRescheduleRequest.getRescheduleFromDate();
        final Loan loan = loanRescheduleRequest.getLoan();
        LoanRepaymentScheduleInstallment installment = null;
        if (loan != null) {

            if (!loan.status().isActive()) {
                dataValidatorBuilder.reset().failWithCodeNoParameterAddedToErrorCode("loan.is.not.active", "Loan is not active");
            }

            final Boolean acknowledgeMultiTrancheReschedule = this.fromJsonHelper
                    .extractBooleanNamed(RescheduleLoansApiConstants.acknowledgeMultiTrancheRescheduleParamName, jsonElement);
            rejectUnacknowledgedMultiTrancheReschedule(dataValidationErrors, loan, rescheduleFromDate, acknowledgeMultiTrancheReschedule);

            if (rescheduleFromDate != null) {
                installment = loan.getRepaymentScheduleInstallment(rescheduleFromDate);

                if (installment == null) {
                    final var resolvedInstallmentNumber = RescheduleFromInstallmentResolver
                            .resolveInstallmentNumber(loan.getRepaymentScheduleInstallments(), rescheduleFromDate);
                    if (resolvedInstallmentNumber.isEmpty()) {
                        dataValidatorBuilder.reset().failWithCodeNoParameterAddedToErrorCode(
                                "repayment.schedule.installment.does.not.exist",
                                "Repayment schedule installment does not exist");
                    } else {
                        final LoanRepaymentScheduleInstallment resolvedInstallment = loan
                                .fetchRepaymentScheduleInstallment(resolvedInstallmentNumber.get());
                        if (resolvedInstallment != null && resolvedInstallment.isObligationsMet()
                                && !loanRescheduleRequest.isRecalculateEvenIfObligationsMet()) {
                            dataValidatorBuilder.reset().failWithCodeNoParameterAddedToErrorCode(
                                    "repayment.schedule.installment.obligation.met",
                                    "Repayment schedule installment obligation met");
                        }
                    }
                } else if (installment.isObligationsMet() && !loanRescheduleRequest.isRecalculateEvenIfObligationsMet()) {
                    dataValidatorBuilder.reset().failWithCodeNoParameterAddedToErrorCode("repayment.schedule.installment.obligation.met",
                            "Repayment schedule installment obligation met");
                }
            }
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    /**
     * Validates a user request to reject a loan reschedule request
     *
     * @param jsonCommand
     *            the JSON command object (instance of the JsonCommand class)
     *
     **/
    public void validateForRejectAction(final JsonCommand jsonCommand, LoanRescheduleRequest loanRescheduleRequest) {
        final String jsonString = jsonCommand.json();

        if (StringUtils.isBlank(jsonString)) {
            throw new InvalidJsonException();
        }

        final Type typeToken = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromJsonHelper.checkForUnsupportedParameters(typeToken, jsonString, REJECT_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder dataValidatorBuilder = new DataValidatorBuilder(dataValidationErrors)
                .resource(StringUtils.lowerCase(RescheduleLoansApiConstants.ENTITY_NAME));

        final JsonElement jsonElement = jsonCommand.parsedJson();

        final LocalDate rejectedOnDate = this.fromJsonHelper.extractLocalDateNamed(RescheduleLoansApiConstants.rejectedOnDateParam,
                jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rejectedOnDateParam).value(rejectedOnDate).notNull();

        if (rejectedOnDate != null && loanRescheduleRequest.getSubmittedOnDate().isAfter(rejectedOnDate)) {
            dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.rejectedOnDateParam).failWithCode("before.submission.date",
                    "Rejection date cannot be before the request submission date.");
        }

        LoanRescheduleRequestStatusEnumData loanRescheduleRequestStatusEnumData = LoanRescheduleRequestEnumerations
                .status(loanRescheduleRequest.getStatusEnum());

        if (!loanRescheduleRequestStatusEnumData.isPendingApproval()) {
            dataValidatorBuilder.reset().failWithCodeNoParameterAddedToErrorCode("request.is.not.in.submitted.and.pending.state",
                    "Loan reschedule request rejection is not allowed. "
                            + "Loan reschedule request is not in submitted and pending approval state.");
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    /**
     * Validates a user request to undo a loan reschedules
     *
     * @param jsonCommand
     *            the JSON command object (instance of the JsonCommand class)
     *
     **/
    public void validateForUndoAction(final JsonCommand jsonCommand) {
        final String jsonString = jsonCommand.json();

        if (StringUtils.isBlank(jsonString)) {
            throw new InvalidJsonException();
        }
        final Type typeToken = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromJsonHelper.checkForUnsupportedParameters(typeToken, jsonString, UNDO_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder dataValidatorBuilder = new DataValidatorBuilder(dataValidationErrors)
                .resource(StringUtils.lowerCase(RescheduleLoansApiConstants.ENTITY_NAME));

        final JsonElement jsonElement = jsonCommand.parsedJson();

        final String notes = this.fromJsonHelper.extractStringNamed(RescheduleLoansApiConstants.notesParamName, jsonElement);
        dataValidatorBuilder.reset().parameter(RescheduleLoansApiConstants.notesParamName).value(notes).notNull();
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    private boolean isRecalculateEvenIfObligationsMet(final JsonElement jsonElement) {
        return Boolean.TRUE.equals(this.fromJsonHelper.extractBooleanNamed(
                RescheduleLoansApiConstants.recalculateEvenIfObligationsMetParamName, jsonElement));
    }
}
