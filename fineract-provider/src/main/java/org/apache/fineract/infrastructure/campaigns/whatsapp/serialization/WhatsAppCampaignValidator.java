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
package org.apache.fineract.infrastructure.campaigns.whatsapp.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignTriggerType;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.calendar.domain.CalendarFrequencyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppCampaignValidator {

    public static final String RESOURCE_NAME = "whatsapp";
    public static final String campaignName = "campaignName";
    public static final String campaignType = "campaignType";
    public static final String triggerType = "triggerType";
    public static final String runReportId = "runReportId";
    public static final String paramValue = "paramValue";
    public static final String message = "message";
    public static final String atTemplateName = "atTemplateName";
    public static final String languageCode = "languageCode";
    public static final String bodyVariableMapping = "bodyVariableMapping";
    public static final String activationDateParamName = "activationDate";
    public static final String recurrenceStartDate = "recurrenceStartDate";
    public static final String dateTimeFormat = "dateTimeFormat";
    public static final String submittedOnDateParamName = "submittedOnDate";
    public static final String closureDateParamName = "closureDate";
    public static final String recurrenceParamName = "recurrence";
    public static final String localeParamName = "locale";
    public static final String dateFormatParamName = "dateFormat";
    public static final String frequencyParamName = "frequency";
    public static final String intervalParamName = "interval";
    public static final String repeatsOnDayParamName = "repeatsOnDay";

    protected static final Set<String> supportedParams = new HashSet<>(Arrays.asList(campaignName, campaignType, localeParamName,
            dateFormatParamName, runReportId, paramValue, message, atTemplateName, languageCode, bodyVariableMapping, recurrenceStartDate,
            activationDateParamName, submittedOnDateParamName, closureDateParamName, recurrenceParamName, triggerType, frequencyParamName,
            intervalParamName, repeatsOnDayParamName, dateTimeFormat));

    protected static final Set<String> supportedParamsForUpdate = new HashSet<>(Arrays.asList(campaignName, campaignType, localeParamName,
            dateFormatParamName, runReportId, paramValue, message, atTemplateName, languageCode, bodyVariableMapping, recurrenceStartDate,
            activationDateParamName, recurrenceParamName, triggerType, dateTimeFormat, frequencyParamName, intervalParamName,
            repeatsOnDayParamName));

    protected static final Set<String> ACTIVATION_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, activationDateParamName));

    protected static final Set<String> CLOSE_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, closureDateParamName));

    protected static final Set<String> PREVIEW_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(paramValue, runReportId,
            atTemplateName, languageCode, bodyVariableMapping));

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public WhatsAppCampaignValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateCreate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParams);

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE_NAME);

        final String campaignNameValue = this.fromApiJsonHelper.extractStringNamed(campaignName, element);
        baseDataValidator.reset().parameter(campaignName).value(campaignNameValue).notBlank().notExceedingLengthOf(100);

        final Long campaignTypeValue = this.fromApiJsonHelper.extractLongNamed(campaignType, element);
        baseDataValidator.reset().parameter(campaignType).value(campaignTypeValue).notNull().integerGreaterThanZero();

        final Long triggerTypeValue = this.fromApiJsonHelper.extractLongNamed(triggerType, element);
        baseDataValidator.reset().parameter(triggerType).value(triggerTypeValue).notNull().integerGreaterThanZero();

        if (triggerTypeValue != null && triggerTypeValue.intValue() == WhatsAppCampaignTriggerType.SCHEDULE.getValue()) {
            final Integer frequencyParam = this.fromApiJsonHelper.extractIntegerWithLocaleNamed(frequencyParamName, element);
            baseDataValidator.reset().parameter(frequencyParamName).value(frequencyParam).notNull().integerGreaterThanZero();

            final String intervalParam = this.fromApiJsonHelper.extractStringNamed(intervalParamName, element);
            baseDataValidator.reset().parameter(intervalParamName).value(intervalParam).notBlank();

            if (frequencyParam != null && frequencyParam.equals(CalendarFrequencyType.WEEKLY.getValue())) {
                final String repeatsOnDayParam = this.fromApiJsonHelper.extractStringNamed(repeatsOnDayParamName, element);
                baseDataValidator.reset().parameter(repeatsOnDayParamName).value(repeatsOnDayParam).notBlank();
            }
            final String recurrenceStartDateValue = this.fromApiJsonHelper.extractStringNamed(recurrenceStartDate, element);
            baseDataValidator.reset().parameter(recurrenceStartDate).value(recurrenceStartDateValue).notBlank();
        }

        final Long runReportIdValue = this.fromApiJsonHelper.extractLongNamed(runReportId, element);
        baseDataValidator.reset().parameter(runReportId).value(runReportIdValue).notNull().integerGreaterThanZero();

        final String atTemplateNameValue = this.fromApiJsonHelper.extractStringNamed(atTemplateName, element);
        baseDataValidator.reset().parameter(atTemplateName).value(atTemplateNameValue).notBlank().notExceedingLengthOf(100);

        final String languageCodeValue = this.fromApiJsonHelper.extractStringNamed(languageCode, element);
        baseDataValidator.reset().parameter(languageCode).value(languageCodeValue).notBlank().notExceedingLengthOf(15);

        validateBodyVariableMapping(element, baseDataValidator);

        if (this.fromApiJsonHelper.parameterExists(message, element)) {
            final String messageValue = this.fromApiJsonHelper.extractStringNamed(message, element);
            baseDataValidator.reset().parameter(message).value(messageValue).notExceedingLengthOf(480);
        }

        final JsonElement paramValueJsonObject = this.fromApiJsonHelper.extractJsonObjectNamed(paramValue, element);
        if (triggerTypeValue != null && triggerTypeValue.intValue() != WhatsAppCampaignTriggerType.TRIGGERED.getValue()) {
            baseDataValidator.reset().parameter(paramValue).value(paramValueJsonObject).notBlank();
            if (paramValueJsonObject != null && paramValueJsonObject.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : paramValueJsonObject.getAsJsonObject().entrySet()) {
                    baseDataValidator.reset().parameter(entry.getKey()).value(entry.getValue()).notBlank();
                }
            }
        }

        if (this.fromApiJsonHelper.parameterExists(submittedOnDateParamName, element)) {
            final LocalDate submittedOnDate = this.fromApiJsonHelper.extractLocalDateNamed(submittedOnDateParamName, element);
            baseDataValidator.reset().parameter(submittedOnDateParamName).value(submittedOnDate).notNull();
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateForUpdate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParamsForUpdate);
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE_NAME);

        if (this.fromApiJsonHelper.parameterExists(campaignName, element)) {
            final String campaignNameValue = this.fromApiJsonHelper.extractStringNamed(campaignName, element);
            baseDataValidator.reset().parameter(campaignName).value(campaignNameValue).notBlank().notExceedingLengthOf(100);
        }

        if (this.fromApiJsonHelper.parameterExists(campaignType, element)) {
            final Long campaignTypeValue = this.fromApiJsonHelper.extractLongNamed(campaignType, element);
            baseDataValidator.reset().parameter(campaignType).value(campaignTypeValue).notNull().integerGreaterThanZero();
        }

        final Long triggerTypeValue = this.fromApiJsonHelper.extractLongNamed(triggerType, element);
        baseDataValidator.reset().parameter(triggerType).value(triggerTypeValue).notNull().integerGreaterThanZero();

        if (triggerTypeValue != null && triggerTypeValue.intValue() == WhatsAppCampaignTriggerType.SCHEDULE.getValue()) {
            if (this.fromApiJsonHelper.parameterExists(recurrenceParamName, element)) {
                final String recurrenceParamNameValue = this.fromApiJsonHelper.extractStringNamed(recurrenceParamName, element);
                baseDataValidator.reset().parameter(recurrenceParamName).value(recurrenceParamNameValue).notBlank();
            }
            if (this.fromApiJsonHelper.parameterExists(recurrenceStartDate, element)) {
                final String recurrenceStartDateValue = this.fromApiJsonHelper.extractStringNamed(recurrenceStartDate, element);
                baseDataValidator.reset().parameter(recurrenceStartDate).value(recurrenceStartDateValue).notBlank();
            }
        }

        if (this.fromApiJsonHelper.parameterExists(runReportId, element)) {
            final Long runReportIdValue = this.fromApiJsonHelper.extractLongNamed(runReportId, element);
            baseDataValidator.reset().parameter(runReportId).value(runReportIdValue).notNull().integerGreaterThanZero();
        }

        if (this.fromApiJsonHelper.parameterExists(atTemplateName, element)) {
            final String atTemplateNameValue = this.fromApiJsonHelper.extractStringNamed(atTemplateName, element);
            baseDataValidator.reset().parameter(atTemplateName).value(atTemplateNameValue).notBlank().notExceedingLengthOf(100);
        }

        if (this.fromApiJsonHelper.parameterExists(languageCode, element)) {
            final String languageCodeValue = this.fromApiJsonHelper.extractStringNamed(languageCode, element);
            baseDataValidator.reset().parameter(languageCode).value(languageCodeValue).notBlank().notExceedingLengthOf(15);
        }

        if (this.fromApiJsonHelper.parameterExists(bodyVariableMapping, element)) {
            validateBodyVariableMapping(element, baseDataValidator);
        }

        if (this.fromApiJsonHelper.parameterExists(message, element)) {
            final String messageValue = this.fromApiJsonHelper.extractStringNamed(message, element);
            baseDataValidator.reset().parameter(message).value(messageValue).notExceedingLengthOf(480);
        }

        final JsonElement paramValueJsonObject = this.fromApiJsonHelper.extractJsonObjectNamed(paramValue, element);
        if (triggerTypeValue != null && triggerTypeValue.intValue() != WhatsAppCampaignTriggerType.TRIGGERED.getValue()) {
            baseDataValidator.reset().parameter(paramValue).value(paramValueJsonObject).notBlank();
            if (paramValueJsonObject != null && paramValueJsonObject.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : paramValueJsonObject.getAsJsonObject().entrySet()) {
                    baseDataValidator.reset().parameter(entry.getKey()).value(entry.getValue()).notBlank();
                }
            }
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validatePreview(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, PREVIEW_REQUEST_DATA_PARAMETERS);

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE_NAME);

        final Long runReportIdValue = this.fromApiJsonHelper.extractLongNamed(runReportId, element);
        baseDataValidator.reset().parameter(runReportId).value(runReportIdValue).notNull().integerGreaterThanZero();

        final String atTemplateNameValue = this.fromApiJsonHelper.extractStringNamed(atTemplateName, element);
        baseDataValidator.reset().parameter(atTemplateName).value(atTemplateNameValue).notBlank();

        final String languageCodeValue = this.fromApiJsonHelper.extractStringNamed(languageCode, element);
        baseDataValidator.reset().parameter(languageCode).value(languageCodeValue).notBlank();

        validateBodyVariableMapping(element, baseDataValidator);

        final JsonElement paramValueJsonObject = this.fromApiJsonHelper.extractJsonObjectNamed(paramValue, element);
        baseDataValidator.reset().parameter(paramValue).value(paramValueJsonObject).notBlank();
        if (paramValueJsonObject != null && !paramValueJsonObject.isJsonNull() && paramValueJsonObject.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : paramValueJsonObject.getAsJsonObject().entrySet()) {
                baseDataValidator.reset().parameter(entry.getKey()).value(entry.getValue()).notBlank();
            }
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateClosedDate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, CLOSE_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE_NAME);

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate closeDate = this.fromApiJsonHelper.extractLocalDateNamed(closureDateParamName, element);
        baseDataValidator.reset().parameter(closureDateParamName).value(closeDate).notNull();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateActivation(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, ACTIVATION_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE_NAME);

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final LocalDate activationDate = this.fromApiJsonHelper.extractLocalDateNamed(activationDateParamName, element);
        baseDataValidator.reset().parameter(activationDateParamName).value(activationDate).notNull();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void validateBodyVariableMapping(final JsonElement element, final DataValidatorBuilder baseDataValidator) {
        final JsonElement mappingElement = this.fromApiJsonHelper.extractJsonObjectNamed(bodyVariableMapping, element);
        baseDataValidator.reset().parameter(bodyVariableMapping).value(mappingElement).notNull();
        if (mappingElement != null && !mappingElement.isJsonNull()) {
            if (!mappingElement.isJsonArray()) {
                baseDataValidator.reset().parameter(bodyVariableMapping).value(mappingElement).failWithCode("must.be.json.array");
            } else {
                final JsonArray array = mappingElement.getAsJsonArray();
                for (JsonElement item : array) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                        baseDataValidator.reset().parameter(bodyVariableMapping).value(item).failWithCode("must.be.array.of.strings");
                        break;
                    }
                }
            }
        }
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
