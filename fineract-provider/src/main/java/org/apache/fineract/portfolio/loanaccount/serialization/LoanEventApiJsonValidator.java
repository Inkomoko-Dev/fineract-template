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
package org.apache.fineract.portfolio.loanaccount.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.calendar.domain.Calendar;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstance;
import org.apache.fineract.portfolio.calendar.exception.NotValidRecurringDateException;
import org.apache.fineract.portfolio.calendar.service.CalendarUtils;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.exception.LoanRepaymentScheduleNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class LoanEventApiJsonValidator {

    private final FromJsonHelper fromApiJsonHelper;
    private final LoanApplicationCommandFromApiJsonHelper fromApiJsonDeserializer;
    private final LoanRepository loanRepository;

    @Autowired
    public LoanEventApiJsonValidator(final FromJsonHelper fromApiJsonHelper,
            final LoanApplicationCommandFromApiJsonHelper fromApiJsonDeserializer, final LoanRepository loanRepository) {
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
        this.loanRepository = loanRepository;
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }

    public void validateDisbursement(final String json, boolean isAccountTransfer) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        Set<String> disbursementParameters = null;

        if (isAccountTransfer) {
            disbursementParameters = new HashSet<>(Arrays.asList("actualDisbursementDate", "externalId", "note", "locale", "dateFormat",
                    "resultCode", LoanApiConstants.principalDisbursedParameterName, LoanApiConstants.emiAmountParameterName,
                    LoanApiConstants.disbursementNetDisbursalAmountParameterName, LoanApiConstants.disbursementTypeParameterName,
                    LoanApiConstants.fxRateParameterName, LoanApiConstants.usdAmountParameterName, LoanApiConstants.fxSourceParameterName,
                    LoanApiConstants.fxTimestampParameterName, LoanApiConstants.disbursementDataParameterName,
                    LoanApiConstants.disbursementDateParameterName, LoanApiConstants.approvedLoanAmountParameterName));
        } else {
            disbursementParameters = new HashSet<>(Arrays.asList("actualDisbursementDate", "externalId", "note", "locale", "dateFormat",
                    "resultCode", "paymentTypeId", "accountNumber", "checkNumber", "routingCode", "receiptNumber", "bankNumber",
                    "adjustRepaymentDate", LoanApiConstants.principalDisbursedParameterName, LoanApiConstants.emiAmountParameterName,
                    LoanApiConstants.postDatedChecks, LoanApiConstants.disbursementNetDisbursalAmountParameterName,
                    LoanApiConstants.disbursementTypeParameterName, LoanApiConstants.fxRateParameterName,
                    LoanApiConstants.usdAmountParameterName, LoanApiConstants.fxSourceParameterName, LoanApiConstants.fxTimestampParameterName,
                    LoanApiConstants.disbursementDataParameterName, LoanApiConstants.disbursementDateParameterName,
                    LoanApiConstants.approvedLoanAmountParameterName));
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.disbursement");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate actualDisbursementDate = this.fromApiJsonHelper.extractLocalDateNamed("actualDisbursementDate", element);
        baseDataValidator.reset().parameter("actualDisbursementDate").value(actualDisbursementDate).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).notExceedingLengthOf(1000);

        final BigDecimal principal = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(LoanApiConstants.principalDisbursedParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.principalDisbursedParameterName).value(principal).ignoreIfNull()
                .positiveAmount();

        final BigDecimal netDisbursalAmount = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(LoanApiConstants.disbursementNetDisbursalAmountParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.disbursementNetDisbursalAmountParameterName).value(netDisbursalAmount)
                .ignoreIfNull().positiveAmount();

        final BigDecimal emiAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(LoanApiConstants.emiAmountParameterName,
                element);
        baseDataValidator.reset().parameter(LoanApiConstants.emiAmountParameterName).value(emiAmount).ignoreIfNull().positiveAmount()
                .notGreaterThanMax(principal);

        validatePaymentDetails(baseDataValidator, element);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateDisbursementWithPostDatedChecks(final String json, final Long loanId) {
        final JsonElement jsonElement = this.fromApiJsonHelper.parse(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.disbursement");
        final Loan loan = this.loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        final List<LoanRepaymentScheduleInstallment> loanRepaymentScheduleInstallment = loan.getRepaymentScheduleInstallments();

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(jsonObject);
        if (jsonObject.has("postDatedChecks") && jsonObject.get("postDatedChecks").isJsonArray()) {
            JsonArray postDatedChecks = jsonObject.get("postDatedChecks").getAsJsonArray();
            for (int i = 0; i < postDatedChecks.size(); i++) {
                final JsonObject postDatedCheck = postDatedChecks.get(i).getAsJsonObject();

                final String name = this.fromApiJsonHelper.extractStringNamed("name", postDatedCheck);
                baseDataValidator.reset().parameter("name").value(name).notNull();

                final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed("amount", postDatedCheck, locale);
                baseDataValidator.reset().parameter("amount").value(amount).notNull().positiveAmount();

                final Long accountNo = this.fromApiJsonHelper.extractLongNamed("accountNo", postDatedCheck);
                baseDataValidator.reset().parameter("accountNo").value(accountNo).notNull().positiveAmount();

                final Long checkNo = this.fromApiJsonHelper.extractLongNamed("checkNo", postDatedCheck);
                baseDataValidator.reset().parameter("checkNo").value(checkNo).notNull().positiveAmount();

                final Integer installmentId = this.fromApiJsonHelper.extractIntegerNamed("installmentId", postDatedCheck, locale);
                final List<LoanRepaymentScheduleInstallment> installmentList = loanRepaymentScheduleInstallment.stream().filter(
                        repayment -> repayment.getInstallmentNumber().equals(installmentId) && repayment.getLoan().getId().equals(loanId))
                        .collect(Collectors.toList());
                if (installmentList.size() > 1) {
                    throw new PlatformDataIntegrityException("error.repayment.redundancy", "Multiple installment data found",
                            "postDatedChecks");
                } else if (installmentList.size() == 0) {
                    throw new LoanRepaymentScheduleNotFoundException(installmentId);
                }

            }

            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                        dataValidationErrors);
            }
        }
    }

    public void validateDisbursementDateWithMeetingDate(final LocalDate actualDisbursementDate, final CalendarInstance calendarInstance,
            Boolean isSkipRepaymentOnFirstMonth, Integer numberOfDays) {
        if (null != calendarInstance) {
            final Calendar calendar = calendarInstance.getCalendar();
            if (!calendar.isValidRecurringDate(actualDisbursementDate, isSkipRepaymentOnFirstMonth, numberOfDays)) {
                // Disbursement date should fall on a meeting date
                final String errorMessage = "Expected disbursement date '" + actualDisbursementDate.toString()
                        + "' does not fall on a meeting date.";
                throw new NotValidRecurringDateException("loan.actual.disbursement.date", errorMessage, actualDisbursementDate.toString(),
                        calendar.getTitle());
            }
        }
    }

    public void validateTransaction(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> transactionParameters = new HashSet<>(Arrays.asList("transactionDate", "transactionAmount", "externalId", "note",
                "locale", "dateFormat", "paymentTypeId", "accountNumber", "checkNumber", "routingCode", "receiptNumber", "bankNumber",
                "correctionDate", LoanApiConstants.disbursementTypeParameterName, LoanApiConstants.fxRateParameterName,
                LoanApiConstants.usdAmountParameterName, LoanApiConstants.fxSourceParameterName, LoanApiConstants.fxTimestampParameterName,
                LoanApiConstants.mfiCodeParameterName));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, transactionParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final BigDecimal transactionAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("transactionAmount", element);
        baseDataValidator.reset().parameter("transactionAmount").value(transactionAmount).notNull().zeroOrPositiveAmount();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).notExceedingLengthOf(1000);

        validatePaymentDetails(baseDataValidator, element);
        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateNewRepaymentTransaction(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> transactionParameters = new HashSet<>(
                Arrays.asList("transactionDate", "transactionAmount", "externalId", "note", "locale", "dateFormat", "paymentTypeId",
                        "accountNumber", "checkNumber", "routingCode", "receiptNumber", "bankNumber", "loanId",
                        "originalTransactionId", "correctionDate", LoanApiConstants.disbursementTypeParameterName,
                        LoanApiConstants.fxRateParameterName, LoanApiConstants.usdAmountParameterName,
                        LoanApiConstants.fxSourceParameterName, LoanApiConstants.fxTimestampParameterName,
                        LoanApiConstants.mfiCodeParameterName));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, transactionParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final BigDecimal transactionAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("transactionAmount", element);
        baseDataValidator.reset().parameter("transactionAmount").value(transactionAmount).notNull().positiveAmount();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).notExceedingLengthOf(1000);

        validatePaymentDetails(baseDataValidator, element);
        final Long originalTransactionId = this.fromApiJsonHelper.extractLongNamed("originalTransactionId", element);
        if (originalTransactionId != null) {
            baseDataValidator.reset().parameter("originalTransactionId").value(originalTransactionId).longGreaterThanZero();
        }
        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateRecoveryPaymentReversal(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> transactionParameters = new HashSet<>(
                Arrays.asList("transactionDate", "note", "locale", "dateFormat", "correctionDate"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, transactionParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateRepaymentDateWithMeetingDate(final LocalDate repaymentDate, final CalendarInstance calendarInstance) {
        if (null != calendarInstance) {
            final Calendar calendar = calendarInstance.getCalendar();
            if (calendar != null && repaymentDate != null) {
                // Disbursement date should fall on a meeting date
                if (!CalendarUtils.isValidRedurringDate(calendar.getRecurrence(), calendar.getStartDateLocalDate(), repaymentDate)) {
                    final String errorMessage = "Transaction date '" + repaymentDate.toString() + "' does not fall on a meeting date.";
                    throw new NotValidRecurringDateException("loan.transaction.date", errorMessage, repaymentDate.toString(),
                            calendar.getTitle());
                }

            }
        }
    }

    private void validatePaymentDetails(final DataValidatorBuilder baseDataValidator, final JsonElement element) {
        // Validate all string payment detail fields for max length
        final Integer paymentTypeId = this.fromApiJsonHelper.extractIntegerWithLocaleNamed("paymentTypeId", element);
        baseDataValidator.reset().parameter("paymentTypeId").value(paymentTypeId).ignoreIfNull().integerGreaterThanZero();
        final Set<String> paymentDetailParameters = new HashSet<>(
                Arrays.asList("accountNumber", "checkNumber", "routingCode", "receiptNumber", "bankNumber"));
        for (final String paymentDetailParameterName : paymentDetailParameters) {
            final String paymentDetailParameterValue = this.fromApiJsonHelper.extractStringNamed(paymentDetailParameterName, element);
            baseDataValidator.reset().parameter(paymentDetailParameterName).value(paymentDetailParameterValue).ignoreIfNull()
                    .notExceedingLengthOf(50);
        }
    }

    public void validateTransactionWithNoAmount(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(
                Arrays.asList("transactionDate", "note", "locale", "dateFormat", "writeoffReasonId"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validatePartialWriteOffTransaction(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> partialWriteOffParameters = new HashSet<>(
                Arrays.asList("transactionDate", "note", "locale", "dateFormat", "externalId", 
                        "principalPortion", "interestPortion", "feeChargesPortion", "penaltyChargesPortion", "reason"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, partialWriteOffParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).ignoreIfNull().notExceedingLengthOf(1000);

        final String reason = this.fromApiJsonHelper.extractStringNamed("reason", element);
        baseDataValidator.reset().parameter("reason").value(reason).notNull().notExceedingLengthOf(500);

        final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());

        final BigDecimal principalPortion = this.fromApiJsonHelper.extractBigDecimalNamed("principalPortion", element, locale);
        baseDataValidator.reset().parameter("principalPortion").value(principalPortion).ignoreIfNull().positiveAmount();

        final BigDecimal interestPortion = this.fromApiJsonHelper.extractBigDecimalNamed("interestPortion", element, locale);
        baseDataValidator.reset().parameter("interestPortion").value(interestPortion).ignoreIfNull().positiveAmount();

        final BigDecimal feeChargesPortion = this.fromApiJsonHelper.extractBigDecimalNamed("feeChargesPortion", element, locale);
        baseDataValidator.reset().parameter("feeChargesPortion").value(feeChargesPortion).ignoreIfNull().positiveAmount();

        final BigDecimal penaltyChargesPortion = this.fromApiJsonHelper.extractBigDecimalNamed("penaltyChargesPortion", element, locale);
        baseDataValidator.reset().parameter("penaltyChargesPortion").value(penaltyChargesPortion).ignoreIfNull().positiveAmount();

        // Validate that at least one portion is provided
        if (principalPortion == null && interestPortion == null && feeChargesPortion == null && penaltyChargesPortion == null) {
            baseDataValidator.reset().parameter("writeOffPortions").failWithCode("at.least.one.portion.required",
                    "At least one write-off portion (principal, interest, fees, or penalties) must be provided");
        }

        // Validate that total write-off amount is positive
        final BigDecimal totalWriteOffAmount = (principalPortion != null ? principalPortion : BigDecimal.ZERO)
                .add(interestPortion != null ? interestPortion : BigDecimal.ZERO)
                .add(feeChargesPortion != null ? feeChargesPortion : BigDecimal.ZERO)
                .add(penaltyChargesPortion != null ? penaltyChargesPortion : BigDecimal.ZERO);
        
        if (totalWriteOffAmount.compareTo(BigDecimal.ZERO) <= 0) {
            baseDataValidator.reset().parameter("totalWriteOffAmount").failWithCode("total.write.off.amount.must.be.positive",
                    "Total write-off amount must be greater than zero");
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validatePartialWriteOffTransactionForLoan(final String json, final Loan loan) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> partialWriteOffParameters = new HashSet<>(
                Arrays.asList("transactionDate", "note", "locale", "dateFormat", "externalId",
                        "principalPortion", "interestPortion", "feeChargesPortion", "penaltyChargesPortion", "reason"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, partialWriteOffParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).ignoreIfNull().notExceedingLengthOf(1000);

        final String reason = this.fromApiJsonHelper.extractStringNamed("reason", element);
        baseDataValidator.reset().parameter("reason").value(reason).notNull().notExceedingLengthOf(500);

        final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());

        final BigDecimal principalPortion = this.fromApiJsonHelper.extractBigDecimalNamed("principalPortion", element, locale);
        baseDataValidator.reset().parameter("principalPortion").value(principalPortion).ignoreIfNull().positiveAmount();

        final BigDecimal interestPortion = this.fromApiJsonHelper.extractBigDecimalNamed("interestPortion", element, locale);
        baseDataValidator.reset().parameter("interestPortion").value(interestPortion).ignoreIfNull().positiveAmount();

        final BigDecimal feeChargesPortion = this.fromApiJsonHelper.extractBigDecimalNamed("feeChargesPortion", element, locale);
        baseDataValidator.reset().parameter("feeChargesPortion").value(feeChargesPortion).ignoreIfNull().positiveAmount();

        final BigDecimal penaltyChargesPortion = this.fromApiJsonHelper.extractBigDecimalNamed("penaltyChargesPortion", element, locale);
        baseDataValidator.reset().parameter("penaltyChargesPortion").value(penaltyChargesPortion).ignoreIfNull().positiveAmount();

        // Validate that at least one portion is provided
        if (principalPortion == null && interestPortion == null && feeChargesPortion == null && penaltyChargesPortion == null) {
            baseDataValidator.reset().parameter("writeOffPortions").failWithCode("at.least.one.portion.required",
                    "At least one write-off portion (principal, interest, fees, or penalties) must be provided");
        }

        // Validate loan status - only active loans can have partial write-offs
        if (loan.status().compareTo(LoanStatus.ACTIVE) != 0 && loan.status().compareTo(LoanStatus.OVERPAID) != 0) {
            baseDataValidator.reset().parameter("loanStatus").failWithCode("loan.not.active",
                    "Partial write-off can only be performed on active or overpaid loans");
        }

        // Validate amounts don't exceed outstanding balance
        final BigDecimal totalWriteOffAmount = (principalPortion != null ? principalPortion : BigDecimal.ZERO)
                .add(interestPortion != null ? interestPortion : BigDecimal.ZERO)
                .add(feeChargesPortion != null ? feeChargesPortion : BigDecimal.ZERO)
                .add(penaltyChargesPortion != null ? penaltyChargesPortion : BigDecimal.ZERO);
        
        final BigDecimal outstandingBalance = loan.getLoanSummary().getTotalOutstanding(loan.getCurrency()).getAmount();
        if (totalWriteOffAmount.compareTo(outstandingBalance) > 0) {
            baseDataValidator.reset().parameter("totalWriteOffAmount").failWithCode("amount.exceeds.outstanding.balance",
                    "Total write-off amount cannot exceed outstanding loan balance of " + outstandingBalance);
        }

        // Validate individual components don't exceed their respective outstanding amounts
        if (principalPortion != null && principalPortion.compareTo(BigDecimal.ZERO) > 0) {
            final BigDecimal outstandingPrincipal = loan.getLoanSummary().getTotalPrincipalOutstanding();
            if (principalPortion.compareTo(outstandingPrincipal) > 0) {
                baseDataValidator.reset().parameter("principalPortion")
                        .failWithCode("principal.portion.exceeds.outstanding",
                        "Principal write-off portion cannot exceed outstanding principal of " + outstandingPrincipal);
            }
        }

        if (interestPortion != null && interestPortion.compareTo(BigDecimal.ZERO) > 0) {
            final BigDecimal outstandingInterest = loan.getLoanSummary().getTotalInterestOutstanding();
            if (interestPortion.compareTo(outstandingInterest) > 0) {
                baseDataValidator.reset().parameter("interestPortion")
                        .failWithCode("interest.portion.exceeds.outstanding",
                        "Interest write-off portion cannot exceed outstanding interest of " + outstandingInterest);
            }
        }

        if (feeChargesPortion != null && feeChargesPortion.compareTo(BigDecimal.ZERO) > 0) {
            final BigDecimal feeChargesOutstanding = loan.getLoanSummary().getTotalFeeChargesOutstanding();
            if (feeChargesPortion.compareTo(feeChargesOutstanding) > 0) {
                baseDataValidator.reset().parameter("feeChargesPortion")
                        .failWithCode("fee.portion.exceeds.outstanding",
                        "Fee write-off portion cannot exceed outstanding fees of " + feeChargesOutstanding);
            }
        }

        if (penaltyChargesPortion != null && penaltyChargesPortion.compareTo(BigDecimal.ZERO) > 0) {
            final BigDecimal penaltyChargesOutstanding = loan.getLoanSummary().getTotalPenaltyChargesOutstanding();
            if (penaltyChargesPortion.compareTo(penaltyChargesOutstanding) > 0) {
                baseDataValidator.reset().parameter("penaltyChargesPortion")
                        .failWithCode("penalty.portion.exceeds.outstanding",
                        "Penalty write-off portion cannot exceed outstanding penalties of " + penaltyChargesOutstanding);
            }
        }

        // Validate no duplicate partial write-off on same day for same loan
        if (transactionDate != null) {
            final boolean hasSameDayPartialWriteOff = loan.getLoanTransactions().stream()
                    .filter(t -> t.isPartialWriteOff() && t.isNotReversed())
                    .anyMatch(t -> t.getTransactionDate().equals(transactionDate));
            
            if (hasSameDayPartialWriteOff) {
                baseDataValidator.reset().parameter("transactionDate").failWithCode("duplicate.partial.writeoff",
                        "A partial write-off already exists for this loan on " + transactionDate + ". Multiple partial write-offs on the same day are not allowed.");
            }
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateAddLoanCharge(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(
                Arrays.asList("chargeId", "amount", "dueDate", "locale", "dateFormat", "externalId"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loanCharge");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final Long chargeId = this.fromApiJsonHelper.extractLongNamed("chargeId", element);
        baseDataValidator.reset().parameter("chargeId").value(chargeId).notNull().integerGreaterThanZero();

        final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("amount", element);
        baseDataValidator.reset().parameter("amount").value(amount).notNull().positiveAmount();

        if (this.fromApiJsonHelper.parameterExists("dueDate", element)) {
            final LocalDate dueDate = this.fromApiJsonHelper.extractLocalDateNamed("dueDate", element);
            baseDataValidator.reset().parameter("dueDate").value(dueDate).notBlank();
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateUpdateOfLoanCharge(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(Arrays.asList("amount", "dueDate", "locale", "dateFormat"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loanCharge");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("amount", element);
        baseDataValidator.reset().parameter("amount").value(amount).notNull().positiveAmount();

        if (this.fromApiJsonHelper.parameterExists("dueDate", element)) {
            this.fromApiJsonHelper.extractLocalDateNamed("dueDate", element);
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateUpdateOfLoanOfficer(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(
                Arrays.asList("assignmentDate", "fromLoanOfficerId", "toLoanOfficerId", "locale", "dateFormat"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loanOfficer");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final Long toLoanOfficerId = this.fromApiJsonHelper.extractLongNamed("toLoanOfficerId", element);
        baseDataValidator.reset().parameter("toLoanOfficerId").value(toLoanOfficerId).notNull().integerGreaterThanZero();

        final String assignmentDateStr = this.fromApiJsonHelper.extractStringNamed("assignmentDate", element);
        baseDataValidator.reset().parameter("assignmentDate").value(assignmentDateStr).notBlank();

        if (!StringUtils.isBlank(assignmentDateStr)) {
            final LocalDate assignmentDate = this.fromApiJsonHelper.extractLocalDateNamed("assignmentDate", element);
            baseDataValidator.reset().parameter("assignmentDate").value(assignmentDate).notNull();
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateForBulkLoanReassignment(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> supportedParameters = new HashSet<>(
                Arrays.asList("assignmentDate", "fromLoanOfficerId", "toLoanOfficerId", "loans", "locale", "dateFormat"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loanOfficer");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final LocalDate assignmentDate = this.fromApiJsonHelper.extractLocalDateNamed("assignmentDate", element);
        baseDataValidator.reset().parameter("assignmentDate").value(assignmentDate).notNull();
        final Long fromLoanOfficerId = this.fromApiJsonHelper.extractLongNamed("fromLoanOfficerId", element);
        baseDataValidator.reset().parameter("fromLoanOfficerId").value(fromLoanOfficerId).notNull().longGreaterThanZero();
        final Long toLoanOfficerId = this.fromApiJsonHelper.extractLongNamed("toLoanOfficerId", element);
        baseDataValidator.reset().parameter("toLoanOfficerId").value(toLoanOfficerId).notNull().longGreaterThanZero();
        final String[] loans = this.fromApiJsonHelper.extractArrayNamed("loans", element);
        baseDataValidator.reset().parameter("loans").value(loans).arrayNotEmpty();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateChargePaymentTransaction(final String json, final boolean isChargeIdIncluded) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        Set<String> transactionParameters = null;
        if (isChargeIdIncluded) {
            transactionParameters = new HashSet<>(
                    Arrays.asList("transactionDate", "locale", "dateFormat", "chargeId", "dueDate", "installmentNumber"));
        } else {
            transactionParameters = new HashSet<>(Arrays.asList("transactionDate", "locale", "dateFormat", "dueDate", "installmentNumber"));
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, transactionParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource("loan.charge.payment.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        if (isChargeIdIncluded) {
            final Long chargeId = this.fromApiJsonHelper.extractLongNamed("chargeId", element);
            baseDataValidator.reset().parameter("chargeId").value(chargeId).notNull().integerGreaterThanZero();
        }
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();
        final Integer installmentNumber = this.fromApiJsonHelper.extractIntegerWithLocaleNamed("installmentNumber", element);
        baseDataValidator.reset().parameter("installmentNumber").value(installmentNumber).ignoreIfNull().integerGreaterThanZero();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateInstallmentChargeTransaction(final String json) {

        if (StringUtils.isBlank(json)) {
            return;
        }
        Set<String> transactionParameters = new HashSet<>(
                Arrays.asList("dueDate", "locale", "dateFormat", "installmentNumber", LoanApiConstants.expectedResidualAmountParamName,
                        LoanApiConstants.reasonParamName, LoanApiConstants.noteParamName));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, transactionParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource("loan.charge.waive.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final Integer installmentNumber = this.fromApiJsonHelper.extractIntegerWithLocaleNamed("installmentNumber", element);
        baseDataValidator.reset().parameter("installmentNumber").value(installmentNumber).ignoreIfNull().integerGreaterThanZero();

        final BigDecimal expectedResidualAmount = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(LoanApiConstants.expectedResidualAmountParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.expectedResidualAmountParamName).value(expectedResidualAmount).ignoreIfNull()
                .zeroOrPositiveAmount();

        final String reason = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.reasonParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.reasonParamName).value(reason).ignoreIfNull().notExceedingLengthOf(1000);

        final String note = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.noteParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.noteParamName).value(note).ignoreIfNull().notExceedingLengthOf(1000);
        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    /**
     * CGLT-656. A whitelist of its own rather than a widening of
     * {@link #validateInstallmentChargeTransaction(String)}, so the standard waive endpoint keeps rejecting these
     * parameters. Whether the effective date falls inside the loan's own window is checked by the write service, which
     * has the loan.
     */
    public void validateHistoricalPenaltyWaiver(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> supportedParameters = new HashSet<>(Arrays.asList("locale", "dateFormat", "installmentNumber",
                LoanApiConstants.waiverAmountParamName, LoanApiConstants.waiverEffectiveDateParamName,
                LoanApiConstants.expectedPaidAmountParamName, LoanApiConstants.nextApproverUserIdParamName,
                LoanApiConstants.reasonParamName, LoanApiConstants.noteParamName));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource("loan.charge.historical.penalty.waiver");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final LocalDate waiverEffectiveDate = this.fromApiJsonHelper
                .extractLocalDateNamed(LoanApiConstants.waiverEffectiveDateParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.waiverEffectiveDateParamName).value(waiverEffectiveDate).notNull();

        final BigDecimal expectedPaidAmount = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(LoanApiConstants.expectedPaidAmountParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.expectedPaidAmountParamName).value(expectedPaidAmount).notNull()
                .zeroOrPositiveAmount();

        // Absent means waive whatever the penalty is worth; present means waive exactly this much.
        final BigDecimal waiverAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(LoanApiConstants.waiverAmountParamName,
                element);
        baseDataValidator.reset().parameter(LoanApiConstants.waiverAmountParamName).value(waiverAmount).ignoreIfNull().positiveAmount();

        final Integer installmentNumber = this.fromApiJsonHelper.extractIntegerWithLocaleNamed("installmentNumber", element);
        baseDataValidator.reset().parameter("installmentNumber").value(installmentNumber).ignoreIfNull().integerGreaterThanZero();

        final Long nextApproverUserId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.nextApproverUserIdParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.nextApproverUserIdParamName).value(nextApproverUserId).ignoreIfNull()
                .longGreaterThanZero();

        final String reason = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.reasonParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.reasonParamName).value(reason).notBlank().notExceedingLengthOf(1000);

        final String note = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.noteParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.noteParamName).value(note).ignoreIfNull().notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateUpdateDisbursementDateAndAmount(final String json, LoanDisbursementDetails loanDisbursementDetails) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(
                Arrays.asList("locale", "dateFormat", LoanApiConstants.disbursementDataParameterName,
                        LoanApiConstants.approvedLoanAmountParameterName, LoanApiConstants.updatedDisbursementDateParameterName,
                        LoanApiConstants.updatedDisbursementPrincipalParameterName, LoanApiConstants.disbursementDateParameterName,
                        "paymentTypeId", LoanApiConstants.paymentToParameterName, LoanApiConstants.beneficiaryNameParameterName,
                        "clientPhoneNumber", "clientAccountNumber", "clientBankName", LoanApiConstants.mfiCodeParameterName,
                        LoanApiConstants.disbursementTypeParameterName, LoanApiConstants.fxRateParameterName));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.update.disbursement");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate actualDisbursementDate = this.fromApiJsonHelper
                .extractLocalDateNamed(LoanApiConstants.disbursementDateParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.disbursementDateParameterName).value(actualDisbursementDate).notNull();

        final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());
        final BigDecimal principal = this.fromApiJsonHelper
                .extractBigDecimalNamed(LoanApiConstants.updatedDisbursementPrincipalParameterName, element, locale);
        baseDataValidator.reset().parameter(LoanApiConstants.disbursementPrincipalParameterName).value(principal).notNull();

        final BigDecimal approvedPrincipal = this.fromApiJsonHelper.extractBigDecimalNamed(LoanApiConstants.approvedLoanAmountParameterName,
                element, locale);
        if (loanDisbursementDetails.actualDisbursementDate() != null) {
            baseDataValidator.reset().parameter(LoanApiConstants.disbursementDateParameterName)
                    .failWithCode(LoanApiConstants.ALREADY_DISBURSED);
        }

        fromApiJsonDeserializer.validateLoanMultiDisbursementDate(element, baseDataValidator, actualDisbursementDate, approvedPrincipal);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateNewRefundTransaction(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> transactionParameters = new HashSet<>(Arrays.asList("transactionDate", "transactionAmount", "externalId", "note",
                "locale", "dateFormat", "paymentTypeId", "accountNumber", "checkNumber", "routingCode", "receiptNumber", "bankNumber"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, transactionParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.transaction");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final BigDecimal transactionAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("transactionAmount", element);
        baseDataValidator.reset().parameter("transactionAmount").value(transactionAmount).notNull().positiveAmount();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).notExceedingLengthOf(1000);

        final String externalId = this.fromApiJsonHelper.extractStringNamed("externalId", element);
        baseDataValidator.reset().parameter("externalId").value(externalId).notExceedingLengthOf(100);

        validatePaymentDetails(baseDataValidator, element);
        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateLoanForeclosure(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> foreclosureParameters = new HashSet<>(Arrays.asList("transactionDate", "note", "locale", "dateFormat"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, foreclosureParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        baseDataValidator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        baseDataValidator.reset().parameter("note").value(note).notExceedingLengthOf(1000);

        validatePaymentDetails(baseDataValidator, element);
        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }
}
