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
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EditDisbursementInsuranceCommandFromApiJsonDeserializer {

    private static final Set<String> SUPPORTED_PARAMS = new HashSet<>(
            Arrays.asList("loanChargeId", "amount", "transactionDate", "externalId", "note", "notes", "locale", "dateFormat",
                    "paymentTypeId", "accountNumber", "checkNumber", "routingCode", "receiptNumber", "bankNumber",
                    "correctionDate"));

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForEdit(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, SUPPORTED_PARAMS);

        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("loan.transaction.editDisbursementInsurance");
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final Long loanChargeId = this.fromApiJsonHelper.extractLongNamed("loanChargeId", element);
        validator.reset().parameter("loanChargeId").value(loanChargeId).notNull().longGreaterThanZero();

        final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("amount", element);
        validator.reset().parameter("amount").value(amount).notNull().zeroOrPositiveAmount();

        final LocalDate transactionDate = this.fromApiJsonHelper.extractLocalDateNamed("transactionDate", element);
        validator.reset().parameter("transactionDate").value(transactionDate).notNull();

        final String externalId = this.fromApiJsonHelper.extractStringNamed("externalId", element);
        validator.reset().parameter("externalId").value(externalId).ignoreIfNull().notExceedingLengthOf(100);

        final String note = this.fromApiJsonHelper.extractStringNamed("note", element);
        validator.reset().parameter("note").value(note).ignoreIfNull().notExceedingLengthOf(1000);

        final String notes = this.fromApiJsonHelper.extractStringNamed("notes", element);
        validator.reset().parameter("notes").value(notes).ignoreIfNull().notExceedingLengthOf(1000);

        final Integer paymentTypeId = this.fromApiJsonHelper.extractIntegerWithLocaleNamed("paymentTypeId", element);
        validator.reset().parameter("paymentTypeId").value(paymentTypeId).ignoreIfNull().integerGreaterThanZero();

        final Set<String> paymentDetailParameters = new HashSet<>(
                Arrays.asList("accountNumber", "checkNumber", "routingCode", "receiptNumber", "bankNumber"));
        for (final String paymentDetailParameterName : paymentDetailParameters) {
            final String paymentDetailParameterValue = this.fromApiJsonHelper.extractStringNamed(paymentDetailParameterName, element);
            validator.reset().parameter(paymentDetailParameterName).value(paymentDetailParameterValue).ignoreIfNull()
                    .notExceedingLengthOf(50);
        }

        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
