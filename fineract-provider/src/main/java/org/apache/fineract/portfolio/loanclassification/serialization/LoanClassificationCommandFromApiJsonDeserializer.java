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
package org.apache.fineract.portfolio.loanclassification.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import org.apache.fineract.portfolio.loanclassification.api.LoanClassificationApiConstants;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationCodes;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationThreshold;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LoanClassificationCommandFromApiJsonDeserializer {

    private static final Set<String> COUNTRY_PARAMS = new HashSet<>(
            Arrays.asList(LoanClassificationApiConstants.COUNTRY_ID_PARAM, LoanClassificationApiConstants.THRESHOLDS_PARAM, "locale"));
    private static final Set<String> OVERRIDE_PARAMS = new HashSet<>(
            Arrays.asList(LoanClassificationApiConstants.CLASSIFICATION_PARAM, LoanClassificationApiConstants.REASON_PARAM, "locale"));

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public LoanClassificationCommandFromApiJsonDeserializer(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForCreate(final String json) {
        validateCountryConfig(json, true);
    }

    public void validateForUpdate(final String json) {
        validateCountryConfig(json, false);
    }

    public List<LoanClassificationThreshold> extractThresholds(final String json) {
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        return parseThresholds(element, new DataValidatorBuilder(new ArrayList<>()).resource("loanclassificationconfig"));
    }

    public void validateOverride(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, OVERRIDE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("loanclassification");
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final Integer classification = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(LoanClassificationApiConstants.CLASSIFICATION_PARAM,
                element);
        validator.reset().parameter(LoanClassificationApiConstants.CLASSIFICATION_PARAM).value(classification).notNull().inMinMaxRange(1, 6);
        if (classification != null && classification == 0) {
            validator.reset().parameter(LoanClassificationApiConstants.CLASSIFICATION_PARAM).failWithCode("cannot.be.zero",
                    "Classification cannot be 0");
        }
        final String reason = this.fromApiJsonHelper.extractStringNamed(LoanClassificationApiConstants.REASON_PARAM, element);
        validator.reset().parameter(LoanClassificationApiConstants.REASON_PARAM).value(reason).notBlank().notExceedingLengthOf(500);
        throwIfErrors(errors);
    }

    private void validateCountryConfig(final String json, final boolean create) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, COUNTRY_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("loanclassificationconfig");
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        if (create || this.fromApiJsonHelper.parameterExists(LoanClassificationApiConstants.COUNTRY_ID_PARAM, element)) {
            final Long countryId = this.fromApiJsonHelper.extractLongNamed(LoanClassificationApiConstants.COUNTRY_ID_PARAM, element);
            validator.reset().parameter(LoanClassificationApiConstants.COUNTRY_ID_PARAM).value(countryId).notNull().integerGreaterThanZero();
        }
        parseThresholds(element, validator);
        throwIfErrors(errors);
    }

    private List<LoanClassificationThreshold> parseThresholds(final JsonElement element, final DataValidatorBuilder validator) {
        final JsonArray array = this.fromApiJsonHelper.extractJsonArrayNamed(LoanClassificationApiConstants.THRESHOLDS_PARAM, element);
        validator.reset().parameter(LoanClassificationApiConstants.THRESHOLDS_PARAM).value(array).notNull();
        final List<LoanClassificationThreshold> thresholds = new ArrayList<>();
        if (array == null || array.size() == 0) {
            validator.reset().parameter(LoanClassificationApiConstants.THRESHOLDS_PARAM).failWithCode("at.least.one.required",
                    "At least one arrears threshold is required");
            throwIfErrors(validator.getDataValidationErrors());
            return thresholds;
        }
        for (int i = 0; i < array.size(); i++) {
            final JsonObject item = array.get(i).getAsJsonObject();
            final Integer classification = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(LoanClassificationApiConstants.CLASSIFICATION_PARAM,
                    item);
            final Integer minDays = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(LoanClassificationApiConstants.MIN_DAYS_PARAM, item);
            final Integer maxDays = this.fromApiJsonHelper.parameterExists(LoanClassificationApiConstants.MAX_DAYS_PARAM, item)
                    ? this.fromApiJsonHelper.extractIntegerSansLocaleNamed(LoanClassificationApiConstants.MAX_DAYS_PARAM, item)
                    : null;
            validator.reset().parameter(LoanClassificationApiConstants.THRESHOLDS_PARAM + "[" + i + "]."
                    + LoanClassificationApiConstants.CLASSIFICATION_PARAM).value(classification).notNull().inMinMaxRange(1, 5);
            validator.reset()
                    .parameter(LoanClassificationApiConstants.THRESHOLDS_PARAM + "[" + i + "]." + LoanClassificationApiConstants.MIN_DAYS_PARAM)
                    .value(minDays).notNull().integerZeroOrGreater();
            if (maxDays != null) {
                validator.reset().parameter(
                        LoanClassificationApiConstants.THRESHOLDS_PARAM + "[" + i + "]." + LoanClassificationApiConstants.MAX_DAYS_PARAM)
                        .value(maxDays).integerZeroOrGreater();
                if (minDays != null && maxDays < minDays) {
                    validator.reset().parameter(LoanClassificationApiConstants.THRESHOLDS_PARAM + "[" + i + "]").failWithCode(
                            "max.less.than.min", "Maximum days in arrears cannot be less than minimum days");
                }
            }
            if (classification != null && minDays != null && LoanClassificationCodes.isValid(classification)
                    && classification != LoanClassificationCodes.WRITE_OFF.getCode()) {
                thresholds.add(LoanClassificationThreshold.create(classification, minDays, maxDays));
            }
        }
        validateNoOverlap(thresholds, validator);
        throwIfErrors(validator.getDataValidationErrors());
        return thresholds;
    }

    private void validateNoOverlap(final List<LoanClassificationThreshold> thresholds, final DataValidatorBuilder validator) {
        for (int i = 0; i < thresholds.size(); i++) {
            for (int j = i + 1; j < thresholds.size(); j++) {
                if (overlaps(thresholds.get(i), thresholds.get(j))) {
                    validator.reset().parameter(LoanClassificationApiConstants.THRESHOLDS_PARAM).failWithCode("overlapping.bands",
                            "Arrears threshold bands must not overlap");
                    return;
                }
            }
        }
    }

    private boolean overlaps(final LoanClassificationThreshold left, final LoanClassificationThreshold right) {
        final int leftMin = left.getMinDaysInArrears();
        final int rightMin = right.getMinDaysInArrears();
        final Integer leftMax = left.getMaxDaysInArrears();
        final Integer rightMax = right.getMaxDaysInArrears();
        final int leftEnd = leftMax == null ? Integer.MAX_VALUE : leftMax;
        final int rightEnd = rightMax == null ? Integer.MAX_VALUE : rightMax;
        return leftMin <= rightEnd && rightMin <= leftEnd;
    }

    private void throwIfErrors(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
