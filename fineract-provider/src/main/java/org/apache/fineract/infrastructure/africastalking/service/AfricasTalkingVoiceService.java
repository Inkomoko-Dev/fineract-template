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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLog;
import org.apache.fineract.infrastructure.africastalking.domain.VoiceCallLogRepository;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingVoiceService {

    private final AfricasTalkingClient africasTalkingClient;
    private final AfricasTalkingProperties properties;
    private final VoiceCallLogRepository voiceCallLogRepository;
    private final RecipientResolutionService recipientResolutionService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final StaffRepositoryWrapper staffRepositoryWrapper;

    public String buildIvrResponse(final String rawPayload) {
        final Map<String, String> values = AfricasTalkingPayloadParser.toMap(rawPayload);
        if (!isWithinBusinessHours()) {
            return VoiceXmlBuilder.buildAfterHoursVoicemail();
        }
        final String dtmfDigits = AfricasTalkingPayloadParser.firstNonBlank(values, "dtmfDigits", "digits");
        if (StringUtils.isBlank(dtmfDigits)) {
            return VoiceXmlBuilder.buildMainMenu();
        }
        return routeDtmfSelection(dtmfDigits.trim());
    }

    @Transactional
    public void processInboundCallback(final String rawPayload) {
        final Map<String, String> values = AfricasTalkingPayloadParser.toMap(rawPayload);
        final String sessionId = AfricasTalkingPayloadParser.firstNonBlank(values, "sessionId", "callSessionId");
        if (StringUtils.isBlank(sessionId)) {
            log.warn("Ignoring AfricasTalking voice callback without sessionId");
            return;
        }
        final String callerNumber = normalizePhone(AfricasTalkingPayloadParser.firstNonBlank(values, "callerNumber", "from"));
        final String destinationNumber = AfricasTalkingPayloadParser.firstNonBlank(values, "destinationNumber", "to");
        final String dtmfDigits = AfricasTalkingPayloadParser.firstNonBlank(values, "dtmfDigits", "digits");
        final VoiceCallLog callLog = voiceCallLogRepository.findByExternalSessionId(sessionId).orElseGet(() -> {
            final ResolvedRecipientData recipient = recipientResolutionService.resolve(callerNumber);
            final Client client = recipient.getClientId() != null
                    ? clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId()) : null;
            final Staff staff = recipient.getStaffId() != null
                    ? staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId()) : null;
            return VoiceCallLog.inbound(sessionId, callerNumber, destinationNumber, client, staff);
        });
        if (StringUtils.isNotBlank(dtmfDigits)) {
            callLog.setDtmfDigits(dtmfDigits);
        }
        voiceCallLogRepository.save(callLog);
    }

    @Transactional
    public void processCallEvent(final String rawPayload) {
        final Map<String, String> values = AfricasTalkingPayloadParser.toMap(rawPayload);
        final String sessionId = AfricasTalkingPayloadParser.firstNonBlank(values, "sessionId", "callSessionId");
        if (StringUtils.isBlank(sessionId)) {
            log.warn("Ignoring AfricasTalking voice event without sessionId");
            return;
        }
        final String callerNumber = normalizePhone(AfricasTalkingPayloadParser.firstNonBlank(values, "callerNumber", "from"));
        final String destinationNumber = AfricasTalkingPayloadParser.firstNonBlank(values, "destinationNumber", "to");
        final String directionValue = StringUtils.defaultIfBlank(values.get("direction"), CommunicationDirection.INBOUND.name());
        final CommunicationDirection direction = "Outbound".equalsIgnoreCase(directionValue) ? CommunicationDirection.OUTBOUND
                : CommunicationDirection.INBOUND;
        final VoiceCallLog callLog = voiceCallLogRepository.findByExternalSessionId(sessionId).orElseGet(() -> {
            final ResolvedRecipientData recipient = recipientResolutionService.resolve(callerNumber);
            final Client client = recipient.getClientId() != null
                    ? clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId()) : null;
            final Staff staff = recipient.getStaffId() != null
                    ? staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId()) : null;
            return direction == CommunicationDirection.OUTBOUND
                    ? VoiceCallLog.outbound(sessionId, callerNumber, destinationNumber, client, staff)
                    : VoiceCallLog.inbound(sessionId, callerNumber, destinationNumber, client, staff);
        });
        callLog.applyEventUpdate(mapCallStatus(values), parseDuration(values), values.get("recordingUrl"),
                AfricasTalkingPayloadParser.firstNonBlank(values, "dtmfDigits", "digits"));
        voiceCallLogRepository.save(callLog);
    }

    @Transactional
    public CommandProcessingResult initiateOutboundCall(final String json) {
        validateOutboundRequest(json);
        final JsonObject element = JsonParser.parseString(json).getAsJsonObject();
        final String phoneNumber = resolveOutboundPhoneNumber(element);
        final ResolvedRecipientData recipient = recipientResolutionService.resolve(phoneNumber);
        final Client client = resolveClient(element, recipient);
        final Staff staff = resolveStaff(element, recipient);
        final String clientRequestId = UUID.randomUUID().toString();
        final VoiceCallLog callLog = VoiceCallLog.outbound(clientRequestId, properties.getVoice().getCallerId(), phoneNumber, client,
                staff);
        voiceCallLogRepository.saveAndFlush(callLog);
        try {
            final AfricasTalkingClient.AfricasTalkingApiResponse response = africasTalkingClient.initiateVoiceCall(phoneNumber,
                    clientRequestId);
            if (response.isSuccessful()) {
                callLog.setStatus(AfricasTalkingConstants.CALL_STATUS_RINGING);
                callLog.setExternalSessionId(extractSessionId(response.body(), clientRequestId));
            } else {
                callLog.setStatus(AfricasTalkingConstants.CALL_STATUS_FAILED);
            }
            voiceCallLogRepository.save(callLog);
            return CommandProcessingResult.resourceResult(requirePersistedId(callLog), null);
        } catch (IOException e) {
            callLog.setStatus(AfricasTalkingConstants.CALL_STATUS_FAILED);
            voiceCallLogRepository.save(callLog);
            throw AfricasTalkingValidation.parameterError("error.msg.africastalking.voice.call.failed",
                    "Failed to initiate AfricasTalking voice call", "phoneNumber");
        }
    }

    private static Long requirePersistedId(final VoiceCallLog callLog) {
        final Long id = callLog.getId();
        if (id == null) {
            throw new IllegalStateException("Voice call log was not assigned a database id after save");
        }
        return id;
    }

    private String routeDtmfSelection(final String dtmfDigits) {
        return switch (dtmfDigits) {
            case "1" -> dialDepartment("Loans", properties.getVoice().getLoansDepartmentNumber());
            case "2" -> dialDepartment("Client Support", properties.getVoice().getSupportDepartmentNumber());
            case "3" -> dialDepartment("Internal Staff", properties.getVoice().getInternalDepartmentNumber());
            default -> VoiceXmlBuilder.buildInvalidSelection();
        };
    }

    private String dialDepartment(final String departmentName, final String phoneNumber) {
        if (StringUtils.isBlank(phoneNumber)) {
            return VoiceXmlBuilder.buildUnavailableDepartment(departmentName);
        }
        return VoiceXmlBuilder.buildDial(phoneNumber);
    }

    private boolean isWithinBusinessHours() {
        final AfricasTalkingProperties.Voice voice = properties.getVoice();
        try {
            final ZoneId zoneId = ZoneId.of(voice.getBusinessTimeZone());
            final LocalTime now = ZonedDateTime.now(zoneId).toLocalTime();
            final LocalTime start = LocalTime.parse(voice.getBusinessHoursStart());
            final LocalTime end = LocalTime.parse(voice.getBusinessHoursEnd());
            return !now.isBefore(start) && now.isBefore(end);
        } catch (Exception e) {
            log.warn("Unable to evaluate AfricasTalking business hours; defaulting to open", e);
            return true;
        }
    }

    private String mapCallStatus(final Map<String, String> values) {
        final String sessionState = StringUtils.defaultIfBlank(values.get("callSessionState"), values.get("status"));
        if (StringUtils.isBlank(sessionState)) {
            return null;
        }
        return switch (sessionState.toLowerCase()) {
            case "ringing" -> AfricasTalkingConstants.CALL_STATUS_RINGING;
            case "answered", "active" -> AfricasTalkingConstants.CALL_STATUS_ANSWERED;
            case "completed" -> AfricasTalkingConstants.CALL_STATUS_COMPLETED;
            case "failed" -> AfricasTalkingConstants.CALL_STATUS_FAILED;
            default -> sessionState.toUpperCase();
        };
    }

    private Integer parseDuration(final Map<String, String> values) {
        final String duration = AfricasTalkingPayloadParser.firstNonBlank(values, "durationInSeconds", "duration");
        if (StringUtils.isBlank(duration)) {
            return null;
        }
        try {
            return Integer.parseInt(duration);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractSessionId(final String responseBody, final String fallback) {
        if (StringUtils.isBlank(responseBody)) {
            return fallback;
        }
        final String trimmed = responseBody.trim();
        if (trimmed.startsWith("{")) {
            final Map<String, String> values = AfricasTalkingPayloadParser.toMap(trimmed);
            final String sessionId = AfricasTalkingPayloadParser.firstNonBlank(values, "sessionId", "entries");
            if (StringUtils.isNotBlank(sessionId)) {
                return sessionId;
            }
        }
        return fallback;
    }

    private void validateOutboundRequest(final String json) {
        if (StringUtils.isBlank(json)) {
            throw AfricasTalkingValidation.parameterError("validation.msg.africastalking.voice.invalid",
                    "Voice call request body is required", "json");
        }
        if (!JsonParser.parseString(json).isJsonObject()) {
            throw AfricasTalkingValidation.parameterError("validation.msg.africastalking.voice.invalid",
                    "Voice call request body must be a JSON object", "json");
        }
    }

    private String resolveOutboundPhoneNumber(final JsonObject element) {
        if (element.has("phoneNumber") && !element.get("phoneNumber").isJsonNull()) {
            return normalizePhone(element.get("phoneNumber").getAsString());
        }
        if (element.has("clientId") && !element.get("clientId").isJsonNull()) {
            final Client client = clientRepositoryWrapper.findOneWithNotFoundDetection(element.get("clientId").getAsLong());
            if (StringUtils.isBlank(client.mobileNo())) {
                throw AfricasTalkingValidation.parameterError("validation.msg.africastalking.voice.client.phone.missing",
                        "Client does not have a mobile number configured", "clientId");
            }
            return normalizePhone(client.mobileNo());
        }
        if (element.has("staffId") && !element.get("staffId").isJsonNull()) {
            final Staff staff = staffRepositoryWrapper.findOneWithNotFoundDetection(element.get("staffId").getAsLong());
            if (StringUtils.isBlank(staff.mobileNo())) {
                throw AfricasTalkingValidation.parameterError("validation.msg.africastalking.voice.staff.phone.missing",
                        "Staff member does not have a mobile number configured", "staffId");
            }
            return normalizePhone(staff.mobileNo());
        }
        throw AfricasTalkingValidation.parameterError("validation.msg.africastalking.voice.phone.required",
                "phoneNumber, clientId, or staffId is required", "phoneNumber");
    }

    private Client resolveClient(final JsonObject element, final ResolvedRecipientData recipient) {
        if (element.has("clientId") && !element.get("clientId").isJsonNull()) {
            return clientRepositoryWrapper.findOneWithNotFoundDetection(element.get("clientId").getAsLong());
        }
        return recipient.getClientId() != null ? clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId()) : null;
    }

    private Staff resolveStaff(final JsonObject element, final ResolvedRecipientData recipient) {
        if (element.has("staffId") && !element.get("staffId").isJsonNull()) {
            return staffRepositoryWrapper.findOneWithNotFoundDetection(element.get("staffId").getAsLong());
        }
        return recipient.getStaffId() != null ? staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId()) : null;
    }

    private String normalizePhone(final String phoneNumber) {
        return phoneNumberNormalizer.normalize(phoneNumber);
    }
}
