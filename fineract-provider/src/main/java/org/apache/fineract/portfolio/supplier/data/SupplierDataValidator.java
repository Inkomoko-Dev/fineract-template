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
package org.apache.fineract.portfolio.supplier.data;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
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
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SupplierDataValidator {

    private static final Set<String> UPSERT_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(SupplierApiConstants.SOURCE_SYSTEM,
            SupplierApiConstants.EXTERNAL_ID, SupplierApiConstants.NAME, SupplierApiConstants.DISPLAY_NAME,
            SupplierApiConstants.BUSINESS_LICENSE_NUMBER, SupplierApiConstants.SUPPLIER_TYPE, SupplierApiConstants.BUSINESS_SECTOR,
            SupplierApiConstants.CATEGORY, SupplierApiConstants.COUNTRY, SupplierApiConstants.TIN, SupplierApiConstants.STATUS,
            SupplierApiConstants.PAYMENT_TYPE_ID, SupplierApiConstants.PAYMENT_PHONE_NUMBER, SupplierApiConstants.PAYMENT_ACCOUNT_NUMBER,
            SupplierApiConstants.PAYMENT_BANK_NAME));

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public SupplierDataValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForUpsert(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, UPSERT_REQUEST_DATA_PARAMETERS);
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SupplierApiConstants.RESOURCE_NAME);

        final String sourceSystem = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.SOURCE_SYSTEM, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.SOURCE_SYSTEM).value(sourceSystem).notBlank().notExceedingLengthOf(50);

        final String externalId = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.EXTERNAL_ID, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.EXTERNAL_ID).value(externalId).notBlank().notExceedingLengthOf(100);

        final String name = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.NAME, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.NAME).value(name).notBlank().notExceedingLengthOf(255);

        final String displayName = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.DISPLAY_NAME, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.DISPLAY_NAME).value(displayName).notBlank().notExceedingLengthOf(255);

        final String license = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.BUSINESS_LICENSE_NUMBER, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.BUSINESS_LICENSE_NUMBER).value(license).notBlank()
                .notExceedingLengthOf(100);

        final String supplierType = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.SUPPLIER_TYPE, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.SUPPLIER_TYPE).value(supplierType).notBlank().notExceedingLengthOf(100);

        final String sector = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.BUSINESS_SECTOR, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.BUSINESS_SECTOR).value(sector).notBlank().notExceedingLengthOf(100);

        final String category = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.CATEGORY, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.CATEGORY).value(category).notBlank().notExceedingLengthOf(100);

        final String country = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.COUNTRY, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.COUNTRY).value(country).notBlank().notExceedingLengthOf(100);

        final String tin = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.TIN, element);
        baseDataValidator.reset().parameter(SupplierApiConstants.TIN).value(tin).notBlank().notExceedingLengthOf(50);

        if (this.fromApiJsonHelper.parameterExists(SupplierApiConstants.STATUS, element)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.STATUS, element);
            if (StringUtils.isNotBlank(status)) {
                baseDataValidator.reset().parameter(SupplierApiConstants.STATUS).value(status.trim().toUpperCase(Locale.ROOT))
                        .isOneOfEnumValues(SupplierStatus.class);
            }
        }
        if (this.fromApiJsonHelper.parameterExists(SupplierApiConstants.PAYMENT_TYPE_ID, element)) {
            final Long paymentTypeId = this.fromApiJsonHelper.extractLongNamed(SupplierApiConstants.PAYMENT_TYPE_ID, element);
            baseDataValidator.reset().parameter(SupplierApiConstants.PAYMENT_TYPE_ID).value(paymentTypeId).ignoreIfNull()
                    .integerGreaterThanZero();
        }
        if (this.fromApiJsonHelper.parameterExists(SupplierApiConstants.PAYMENT_PHONE_NUMBER, element)) {
            final String paymentPhoneNumber = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.PAYMENT_PHONE_NUMBER, element);
            baseDataValidator.reset().parameter(SupplierApiConstants.PAYMENT_PHONE_NUMBER).value(paymentPhoneNumber).ignoreIfNull()
                    .notExceedingLengthOf(50);
        }
        if (this.fromApiJsonHelper.parameterExists(SupplierApiConstants.PAYMENT_ACCOUNT_NUMBER, element)) {
            final String paymentAccountNumber = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.PAYMENT_ACCOUNT_NUMBER,
                    element);
            baseDataValidator.reset().parameter(SupplierApiConstants.PAYMENT_ACCOUNT_NUMBER).value(paymentAccountNumber).ignoreIfNull()
                    .notExceedingLengthOf(150);
        }
        if (this.fromApiJsonHelper.parameterExists(SupplierApiConstants.PAYMENT_BANK_NAME, element)) {
            final String paymentBankName = this.fromApiJsonHelper.extractStringNamed(SupplierApiConstants.PAYMENT_BANK_NAME, element);
            baseDataValidator.reset().parameter(SupplierApiConstants.PAYMENT_BANK_NAME).value(paymentBankName).ignoreIfNull()
                    .notExceedingLengthOf(150);
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
