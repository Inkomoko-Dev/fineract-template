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

import com.google.gson.JsonElement;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class LoanApplicationTransitionApiJsonValidator {

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public LoanApplicationTransitionApiJsonValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }

    public void validateApproval(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(
                Arrays.asList(LoanApiConstants.loanIdTobeApproved, LoanApiConstants.approvedLoanAmountParameterName,
                        LoanApiConstants.approvedOnDateParameterName, LoanApiConstants.disbursementNetDisbursalAmountParameterName,
                        LoanApiConstants.noteParameterName, LoanApiConstants.localeParameterName, LoanApiConstants.dateFormatParameterName,
                        LoanApiConstants.disbursementDataParameterName, LoanApiConstants.disbursementDateParameterName,
                        "paymentTypeId", "accountNumber", "checkNumber", "routingCode", "receiptNumber", "bankNumber",
                        "clientPhoneNumber", "clientAccountNumber","clientBankName","transactionAmount"));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loanapplication");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final BigDecimal principal = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(LoanApiConstants.approvedLoanAmountParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.approvedLoanAmountParameterName).value(principal).ignoreIfNull()
                .positiveAmount();

        final BigDecimal netDisbursalAmount = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(LoanApiConstants.disbursementNetDisbursalAmountParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.disbursementNetDisbursalAmountParameterName).value(netDisbursalAmount)
                .ignoreIfNull().positiveAmount();

        final LocalDate approvedOnDate = this.fromApiJsonHelper.extractLocalDateNamed(LoanApiConstants.approvedOnDateParameterName,
                element);
        baseDataValidator.reset().parameter(LoanApiConstants.approvedOnDateParameterName).value(approvedOnDate).notNull();

        final LocalDate expectedDisbursementDate = this.fromApiJsonHelper
                .extractLocalDateNamed(LoanApiConstants.disbursementDateParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.disbursementDateParameterName).value(expectedDisbursementDate).ignoreIfNull();

        final String note = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.noteParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.noteParameterName).value(note).notExceedingLengthOf(1000);

        final Long paymentTypeId = this.fromApiJsonHelper.extractLongNamed("paymentTypeId", element);
        baseDataValidator.reset().parameter("paymentTypeId").value(paymentTypeId).notNull();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateRejection(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(Arrays.asList(LoanApiConstants.rejectedOnDateParameterName,
                LoanApiConstants.noteParameterName, LoanApiConstants.localeParameterName, LoanApiConstants.dateFormatParameterName));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loanapplication");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate rejectedOnDate = this.fromApiJsonHelper.extractLocalDateNamed(LoanApiConstants.rejectedOnDateParameterName,
                element);
        baseDataValidator.reset().parameter(LoanApiConstants.rejectedOnDateParameterName).value(rejectedOnDate).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.noteParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.noteParameterName).value(note).notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateApplicantWithdrawal(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(Arrays.asList(LoanApiConstants.withdrawnOnDateParameterName,
                LoanApiConstants.noteParameterName, LoanApiConstants.localeParameterName, LoanApiConstants.dateFormatParameterName));

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loanapplication");

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate withdrawnOnDate = this.fromApiJsonHelper.extractLocalDateNamed(LoanApiConstants.withdrawnOnDateParameterName,
                element);
        baseDataValidator.reset().parameter(LoanApiConstants.withdrawnOnDateParameterName).value(withdrawnOnDate).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.noteParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.noteParameterName).value(note).notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateWithdrawalFromRedraw(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(Arrays.asList(LoanApiConstants.transactionDateParamName,
                LoanApiConstants.noteParameterName, LoanApiConstants.principalDisbursedParameterName, LoanApiConstants.localeParameterName,
                LoanApiConstants.dateFormatParameterName));
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(LoanApiConstants.withdrawFromRedraw);

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed(LoanApiConstants.transactionDateParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.withdrawnOnDateParameterName).value(transactionDate).notNull();

        final BigDecimal transactionAmount = this.fromApiJsonHelper.extractBigDecimalNamed(LoanApiConstants.principalDisbursedParameterName,
                element, Locale.US);
        baseDataValidator.reset().parameter(LoanApiConstants.withdrawnOnDateParameterName).value(transactionAmount).notNull();

        final String note = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.noteParameterName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.noteParameterName).value(note).notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateApplyRedrawPayment(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Set<String> disbursementParameters = new HashSet<>(
                Arrays.asList(LoanApiConstants.transactionDateParamName, LoanApiConstants.principalDisbursedParameterName,
                        LoanApiConstants.localeParameterName, LoanApiConstants.dateFormatParameterName, "notes"));
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, disbursementParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(LoanApiConstants.withdrawFromRedraw);

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed(LoanApiConstants.transactionDateParamName, element);
        baseDataValidator.reset().parameter(LoanApiConstants.withdrawnOnDateParameterName).value(transactionDate).notNull();

        final BigDecimal transactionAmount = this.fromApiJsonHelper.extractBigDecimalNamed(LoanApiConstants.principalDisbursedParameterName,
                element, Locale.US);
        baseDataValidator.reset().parameter(LoanApiConstants.withdrawnOnDateParameterName).value(transactionAmount).notNull();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }
}
