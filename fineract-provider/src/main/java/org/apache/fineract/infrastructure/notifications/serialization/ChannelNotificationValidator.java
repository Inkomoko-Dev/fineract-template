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
package org.apache.fineract.infrastructure.notifications.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.africastalking.service.RecipientResolutionService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.notifications.constants.NotificationChannel;
import org.apache.fineract.infrastructure.notifications.constants.NotificationIntent;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.springframework.stereotype.Component;

@Component
public class ChannelNotificationValidator {

    public static final String CHANNEL = "channel";
    public static final String INTENT = "intent";
    public static final String PURPOSE = "purpose";
    public static final String PHONE_NUMBER = "phoneNumber";
    public static final String CLIENT_ID = "clientId";
    public static final String STAFF_ID = "staffId";
    public static final String MESSAGE = "message";
    public static final String TEMPLATE_NAME = "templateName";
    public static final String LANGUAGE = "language";
    public static final String BODY_VALUES = "bodyValues";
    public static final String CAMPAIGN_ID = "campaignId";
    public static final String SEND_IMMEDIATELY = "sendImmediately";

    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final RecipientResolutionService recipientResolutionService;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final StaffRepositoryWrapper staffRepositoryWrapper;

    public ChannelNotificationValidator(final PhoneNumberNormalizer phoneNumberNormalizer,
            final RecipientResolutionService recipientResolutionService, final ClientRepositoryWrapper clientRepositoryWrapper,
            final StaffRepositoryWrapper staffRepositoryWrapper) {
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.recipientResolutionService = recipientResolutionService;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.staffRepositoryWrapper = staffRepositoryWrapper;
    }

    public NotificationCommand parseCommand(final String json) {
        if (StringUtils.isBlank(json)) {
            throw validationError("validation.msg.channel.notification.json.invalid", "Request body is required", "json");
        }
        final JsonObject element = JsonParser.parseString(json).getAsJsonObject();

        final NotificationChannel channel = NotificationChannel.fromString(extractRequiredString(element, CHANNEL));
        if (channel == null) {
            throw validationError("validation.msg.channel.notification.channel.invalid", "Invalid channel", CHANNEL);
        }

        final NotificationIntent intent = NotificationIntent.fromString(
                element.has(INTENT) && !element.get(INTENT).isJsonNull() ? element.get(INTENT).getAsString()
                        : NotificationIntent.FREEFORM.name());
        final NotificationPurpose purpose = NotificationPurpose.fromString(
                element.has(PURPOSE) && !element.get(PURPOSE).isJsonNull() ? element.get(PURPOSE).getAsString()
                        : NotificationPurpose.AD_HOC.name());

        final String phoneNumber = resolvePhoneNumber(element);
        final var resolved = recipientResolutionService.resolve(phoneNumber);
        Client client = null;
        Staff staff = null;
        if (element.has(CLIENT_ID) && !element.get(CLIENT_ID).isJsonNull()) {
            client = clientRepositoryWrapper.findOneWithNotFoundDetection(element.get(CLIENT_ID).getAsLong());
        } else if (resolved.getClientId() != null) {
            client = clientRepositoryWrapper.findOneWithNotFoundDetection(resolved.getClientId());
        }
        if (element.has(STAFF_ID) && !element.get(STAFF_ID).isJsonNull()) {
            staff = staffRepositoryWrapper.findOneWithNotFoundDetection(element.get(STAFF_ID).getAsLong());
        } else if (resolved.getStaffId() != null) {
            staff = staffRepositoryWrapper.findOneWithNotFoundDetection(resolved.getStaffId());
        }

        final RecipientType recipientType = resolved.getRecipientType();
        final boolean sendImmediately = element.has(SEND_IMMEDIATELY) && !element.get(SEND_IMMEDIATELY).isJsonNull()
                && element.get(SEND_IMMEDIATELY).getAsBoolean();
        final Long campaignId = element.has(CAMPAIGN_ID) && !element.get(CAMPAIGN_ID).isJsonNull() ? element.get(CAMPAIGN_ID).getAsLong()
                : null;

        if (intent == NotificationIntent.TEMPLATE) {
            final String templateName = extractRequiredString(element, TEMPLATE_NAME);
            final String language = element.has(LANGUAGE) && !element.get(LANGUAGE).isJsonNull() ? element.get(LANGUAGE).getAsString() : null;
            final String bodyValuesJson = serializeBodyValues(element);
            final String auditMessage = element.has(MESSAGE) && !element.get(MESSAGE).isJsonNull() ? element.get(MESSAGE).getAsString()
                    : templateName;
            return NotificationCommand.templateWhatsApp(purpose, phoneNumber, recipientType, client, staff, templateName, language,
                    bodyValuesJson, auditMessage, campaignId);
        }

        final String message = extractRequiredString(element, MESSAGE);
        return NotificationCommand.freeformWhatsApp(purpose, phoneNumber, recipientType, client, staff, message, sendImmediately);
    }

    private String resolvePhoneNumber(final JsonObject element) {
        if (element.has(PHONE_NUMBER) && !element.get(PHONE_NUMBER).isJsonNull()) {
            return phoneNumberNormalizer.normalize(element.get(PHONE_NUMBER).getAsString());
        }
        if (element.has(CLIENT_ID) && !element.get(CLIENT_ID).isJsonNull()) {
            final Client client = clientRepositoryWrapper.findOneWithNotFoundDetection(element.get(CLIENT_ID).getAsLong());
            if (StringUtils.isBlank(client.mobileNo())) {
                throw validationError("validation.msg.communication.client.mobile.missing", "Client does not have a mobile number",
                        CLIENT_ID);
            }
            return phoneNumberNormalizer.normalize(client.mobileNo());
        }
        if (element.has(STAFF_ID) && !element.get(STAFF_ID).isJsonNull()) {
            final Staff staff = staffRepositoryWrapper.findOneWithNotFoundDetection(element.get(STAFF_ID).getAsLong());
            if (StringUtils.isBlank(staff.mobileNo())) {
                throw validationError("validation.msg.communication.staff.mobile.missing", "Staff does not have a mobile number", STAFF_ID);
            }
            return phoneNumberNormalizer.normalize(staff.mobileNo());
        }
        throw validationError("validation.msg.communication.recipient.missing", "Provide phoneNumber, clientId, or staffId", PHONE_NUMBER);
    }

    private String serializeBodyValues(final JsonObject element) {
        if (!element.has(BODY_VALUES) || element.get(BODY_VALUES).isJsonNull()) {
            return "[]";
        }
        final JsonElement bodyValues = element.get(BODY_VALUES);
        if (bodyValues.isJsonArray()) {
            return bodyValues.toString();
        }
        throw validationError("validation.msg.channel.notification.bodyValues.invalid", "bodyValues must be a JSON array", BODY_VALUES);
    }

    private static String extractRequiredString(final JsonObject element, final String parameterName) {
        if (!element.has(parameterName) || element.get(parameterName).isJsonNull()) {
            throw validationError("validation.msg.channel.notification.parameter.missing", "Required parameter missing", parameterName);
        }
        return element.get(parameterName).getAsString();
    }

    private static PlatformApiDataValidationException validationError(final String globalisationMessageCode,
            final String defaultUserMessage, final String parameterName) {
        return new PlatformApiDataValidationException(globalisationMessageCode, defaultUserMessage,
                List.of(ApiParameterError.parameterError(globalisationMessageCode, defaultUserMessage, parameterName)));
    }
}
