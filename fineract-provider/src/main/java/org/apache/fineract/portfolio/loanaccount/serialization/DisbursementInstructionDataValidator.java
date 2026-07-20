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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionApiConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DisbursementInstructionDataValidator {

    private static final Set<String> REQUEST_PARAMETERS = new HashSet<>(Arrays.asList(DisbursementInstructionApiConstants.LOAN_ACCOUNT_NO,
            DisbursementInstructionApiConstants.SOURCE_SYSTEM, DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID,
            DisbursementInstructionApiConstants.ACTUAL_DISBURSEMENT_DATE, DisbursementInstructionApiConstants.LOCALE,
            DisbursementInstructionApiConstants.DATE_FORMAT));

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public DisbursementInstructionDataValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateCreateRequest(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, REQUEST_PARAMETERS);
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(DisbursementInstructionApiConstants.RESOURCE_NAME);

        final String loanAccountNo = this.fromApiJsonHelper.extractStringNamed(DisbursementInstructionApiConstants.LOAN_ACCOUNT_NO, element);
        baseDataValidator.reset().parameter(DisbursementInstructionApiConstants.LOAN_ACCOUNT_NO).value(loanAccountNo).notBlank()
                .notExceedingLengthOf(50);

        final String sourceSystem = this.fromApiJsonHelper.extractStringNamed(DisbursementInstructionApiConstants.SOURCE_SYSTEM, element);
        baseDataValidator.reset().parameter(DisbursementInstructionApiConstants.SOURCE_SYSTEM).value(sourceSystem).notBlank()
                .notExceedingLengthOf(50);

        final String supplierExternalId = this.fromApiJsonHelper.extractStringNamed(DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID,
                element);
        baseDataValidator.reset().parameter(DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID).value(supplierExternalId).notBlank()
                .notExceedingLengthOf(100);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
