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
package org.apache.fineract.infrastructure.africastalking.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.infrastructure.notifications.data.NotificationResult;
import org.apache.fineract.infrastructure.notifications.service.NotificationCommandService;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInboundConversationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingWhatsAppService {

    private final CommunicationMessageRepository communicationMessageRepository;
    private final RecipientResolutionService recipientResolutionService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final StaffRepositoryWrapper staffRepositoryWrapper;
    private final FromJsonHelper fromJsonHelper;
    private final NotificationCommandService notificationCommandService;
    private final WhatsAppInboundConversationService inboundConversationService;

    @Transactional
    public CommandProcessingResult queueOutboundMessage(final String json) {
        validateOutboundRequest(json);
        final JsonObject element = JsonParser.parseString(json).getAsJsonObject();
        final String messageBody = extractRequiredString(element, "message");
        final String phoneNumber = resolveOutboundPhoneNumber(element);
        final ResolvedRecipientData recipient = recipientResolutionService.resolve(phoneNumber);
        final Client client = recipient.getClientId() != null ? clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId())
                : null;
        final Staff staff = recipient.getStaffId() != null ? staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId())
                : null;
        final boolean sendImmediately = element.has("sendImmediately") && !element.get("sendImmediately").isJsonNull()
                && element.get("sendImmediately").getAsBoolean();

        final NotificationCommand command;
        if (element.has("templateName") && !element.get("templateName").isJsonNull()) {
            final String templateName = element.get("templateName").getAsString();
            final String language = element.has("language") && !element.get("language").isJsonNull() ? element.get("language").getAsString()
                    : null;
            final String bodyValuesJson = serializeBodyValues(element);
            command = NotificationCommand.templateWhatsApp(NotificationPurpose.AD_HOC, recipient.getNormalizedPhoneNumber(),
                    recipient.getRecipientType(), client, staff, templateName, language, bodyValuesJson, messageBody, null, sendImmediately);
        } else {
            command = NotificationCommand.freeformWhatsApp(NotificationPurpose.AD_HOC, recipient.getNormalizedPhoneNumber(),
                    recipient.getRecipientType(), client, staff, messageBody, sendImmediately);
        }

        final NotificationResult result = notificationCommandService.send(command);
        if (!result.isAccepted()) {
            throw new IllegalStateException(result.getRejectionReason());
        }
        return CommandProcessingResult.resourceResult(result.getResourceId(), null);
    }

    private String serializeBodyValues(final JsonObject element) {
        if (!element.has("bodyValues") || element.get("bodyValues").isJsonNull()) {
            return "[]";
        }
        final JsonElement bodyValues = element.get("bodyValues");
        if (bodyValues.isJsonArray()) {
            return bodyValues.toString();
        }
        return "[]";
    }

    @Transactional
    public void processInboundMessage(final String payload) {
        final Map<String, String> values = AfricasTalkingPayloadParser.toMap(payload);
        final String phoneNumber = AfricasTalkingPayloadParser.firstNonBlank(values, "from", "phoneNumber", "waId", "sender");
        final String messageBody = AfricasTalkingPayloadParser.firstNonBlank(values, "message", "text", "body");
        final String externalId = AfricasTalkingPayloadParser.firstNonBlank(values, "messageId", "id");
        if (StringUtils.isAnyBlank(phoneNumber, messageBody)) {
            log.warn("Ignoring incomplete WhatsApp inbound payload");
            return;
        }
        final ResolvedRecipientData recipient = recipientResolutionService.resolve(phoneNumber);
        final Client client = recipient.getClientId() != null ? clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId())
                : null;
        final Staff staff = recipient.getStaffId() != null ? staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId())
                : null;
        if (StringUtils.isNotBlank(externalId) && communicationMessageRepository.findByExternalId(externalId).isPresent()) {
            return;
        }
        final CommunicationMessage message = CommunicationMessage.inboundWhatsApp(recipient.getNormalizedPhoneNumber(),
                recipient.getRecipientType(), client, staff, messageBody, externalId);
        communicationMessageRepository.save(message);
        inboundConversationService.handleInbound(recipient.getNormalizedPhoneNumber(), messageBody, recipient.getRecipientType(), client,
                staff);
    }

    @Transactional
    public void processStatusUpdate(final String payload) {
        final Map<String, String> values = AfricasTalkingPayloadParser.toMap(payload);
        final String externalId = AfricasTalkingPayloadParser.firstNonBlank(values, "messageId", "id");
        final String status = AfricasTalkingPayloadParser.firstNonBlank(values, "status", "deliveryStatus");
        if (StringUtils.isAnyBlank(externalId, status)) {
            log.warn("Ignoring incomplete WhatsApp status payload");
            return;
        }
        communicationMessageRepository.findByExternalId(externalId).ifPresentOrElse(message -> applyStatus(message, status, values),
                () -> log.debug("No communication message found for external id {}", externalId));
    }

    private void applyStatus(final CommunicationMessage message, final String status, final Map<String, String> values) {
        final String normalizedStatus = status.trim().toUpperCase();
        switch (normalizedStatus) {
            case "SENT", "QUEUED" -> message.setStatus(CommunicationMessageStatus.SENT);
            case "DELIVERED", "SUCCESS" -> {
                message.setStatus(CommunicationMessageStatus.DELIVERED);
                message.setDeliveredDate(DateUtils.getLocalDateTimeOfTenant());
            }
            case "READ" -> {
                message.setStatus(CommunicationMessageStatus.READ);
                message.setReadDate(DateUtils.getLocalDateTimeOfTenant());
            }
            case "FAILED", "REJECTED" -> {
                message.setStatus(CommunicationMessageStatus.FAILED);
                message.setStatusDetail(CommunicationLogSanitizer.truncateDetail(
                        AfricasTalkingPayloadParser.firstNonBlank(values, "failureReason", "reason")));
            }
            default -> log.debug("Unhandled WhatsApp status {} for message {}", normalizedStatus, message.getId());
        }
        communicationMessageRepository.save(message);
    }

    private String resolveOutboundPhoneNumber(final JsonObject element) {
        if (element.has("phoneNumber") && !element.get("phoneNumber").isJsonNull()) {
            return phoneNumberNormalizer.normalize(element.get("phoneNumber").getAsString());
        }
        if (element.has("clientId") && !element.get("clientId").isJsonNull()) {
            final Client client = clientRepositoryWrapper.findOneWithNotFoundDetection(element.get("clientId").getAsLong());
            if (StringUtils.isBlank(client.mobileNo())) {
                throw AfricasTalkingValidation.parameterError("validation.msg.communication.client.mobile.missing",
                        "Client does not have a mobile number", "clientId");
            }
            return phoneNumberNormalizer.normalize(client.mobileNo());
        }
        if (element.has("staffId") && !element.get("staffId").isJsonNull()) {
            final Staff staff = staffRepositoryWrapper.findOneWithNotFoundDetection(element.get("staffId").getAsLong());
            if (StringUtils.isBlank(staff.mobileNo())) {
                throw AfricasTalkingValidation.parameterError("validation.msg.communication.staff.mobile.missing",
                        "Staff does not have a mobile number", "staffId");
            }
            return phoneNumberNormalizer.normalize(staff.mobileNo());
        }
        throw AfricasTalkingValidation.parameterError("validation.msg.communication.recipient.missing",
                "Provide phoneNumber, clientId, or staffId", "phoneNumber");
    }

    private String extractRequiredString(final JsonObject element, final String parameterName) {
        if (!this.fromJsonHelper.parameterExists(parameterName, element)) {
            throw AfricasTalkingValidation.parameterError("validation.msg.communication.parameter.missing", "Required parameter missing",
                    parameterName);
        }
        return element.get(parameterName).getAsString();
    }

    private void validateOutboundRequest(final String json) {
        if (StringUtils.isBlank(json)) {
            throw AfricasTalkingValidation.parameterError("validation.msg.communication.json.invalid", "Request body is required", "json");
        }
        final JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw AfricasTalkingValidation.parameterError("validation.msg.communication.json.invalid", "Request body must be a JSON object",
                    "json");
        }
    }
}
