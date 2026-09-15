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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationMessageDispatchService {

    private final AfricasTalkingClient africasTalkingClient;
    private final CommunicationMessageRepository communicationMessageRepository;
    private final WhatsAppPhoneWhitelistService whatsAppPhoneWhitelistService;
    private final AfricasTalkingProperties properties;

    @Transactional
    public void dispatchPendingMessages() {
        final var pendingMessages = communicationMessageRepository.findTop200ByStatusAndChannelOrderByCreatedDateAsc(
                CommunicationMessageStatus.PENDING, org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel.WHATSAPP);
        for (final CommunicationMessage message : pendingMessages) {
            dispatchMessage(message);
        }
    }

    @Transactional
    public void dispatchMessage(final CommunicationMessage message) {
        if (message.getStatus() != CommunicationMessageStatus.PENDING) {
            return;
        }
        if (!whatsAppPhoneWhitelistService.isAllowed(message.getPhoneNumber())) {
            log.info("Blocked outbound WhatsApp message {} to {}", message.getId(),
                    CommunicationLogSanitizer.maskPhone(message.getPhoneNumber()));
            message.setStatus(CommunicationMessageStatus.FAILED);
            message.setStatusDetail(WhatsAppPhoneWhitelistService.BLOCKED_ERROR_MESSAGE);
            communicationMessageRepository.save(message);
            return;
        }
        try {
            final AfricasTalkingClient.AfricasTalkingApiResponse response;
            if (StringUtils.isNotBlank(message.getTemplateName())) {
                final List<String> bodyValues = parseBodyValues(message.getTemplateBodyValues());
                response = africasTalkingClient.sendWhatsAppTemplate(message.getPhoneNumber(), message.getTemplateName(),
                        message.getTemplateLanguage(), bodyValues);
            } else {
                response = africasTalkingClient.sendWhatsAppMessage(message.getPhoneNumber(), message.getMessageBody());
            }
            if (response.isSuccessful()) {
                message.setStatus(CommunicationMessageStatus.SENT);
                message.setExternalId(extractExternalId(response.body()));
                message.setStatusDetail(null);
                log.info("WhatsApp dispatch succeeded messageId={} phone={}", message.getId(),
                        CommunicationLogSanitizer.maskPhone(message.getPhoneNumber()));
            } else {
                applyDispatchFailure(message, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("WhatsApp dispatch failed messageId={} phone={}", message.getId(),
                    CommunicationLogSanitizer.maskPhone(message.getPhoneNumber()), e);
            final CommunicationDispatchErrorClassifier.Classification classification = CommunicationDispatchErrorClassifier
                    .classifyException(e);
            applyClassification(message, classification, CommunicationLogSanitizer.truncateDetail(e.getMessage()));
        }
        communicationMessageRepository.save(message);
    }

    private void applyDispatchFailure(final CommunicationMessage message, final int statusCode, final String responseBody) {
        final CommunicationDispatchErrorClassifier.Classification classification = CommunicationDispatchErrorClassifier.classify(statusCode,
                responseBody);
        final String detail = classification.code() + ": "
                + CommunicationLogSanitizer.truncateDetail(
                        StringUtils.isNotBlank(responseBody) ? "HTTP " + statusCode + ": " + responseBody : "HTTP " + statusCode);
        log.warn("WhatsApp dispatch failed messageId={} phone={} httpStatus={} category={}", message.getId(),
                CommunicationLogSanitizer.maskPhone(message.getPhoneNumber()), statusCode, classification.category());
        applyClassification(message, classification, detail);
    }

    private void applyClassification(final CommunicationMessage message,
            final CommunicationDispatchErrorClassifier.Classification classification, final String detail) {
        if (classification.retryable() && message.getDispatchRetryCount() < properties.getDispatch().getMaxRetries()) {
            message.setDispatchRetryCount(message.getDispatchRetryCount() + 1);
            message.setStatus(CommunicationMessageStatus.PENDING);
            message.setStatusDetail(StringUtils.left(classification.code() + " retry " + message.getDispatchRetryCount(), 255));
            return;
        }
        message.setStatus(CommunicationMessageStatus.FAILED);
        message.setStatusDetail(StringUtils.left(detail, 255));
    }

    private List<String> parseBodyValues(final String templateBodyValuesJson) {
        final List<String> bodyValues = new ArrayList<>();
        if (StringUtils.isBlank(templateBodyValuesJson)) {
            return bodyValues;
        }
        final JsonArray array = JsonParser.parseString(templateBodyValuesJson).getAsJsonArray();
        for (final var element : array) {
            bodyValues.add(element.isJsonNull() ? "" : element.getAsString());
        }
        return bodyValues;
    }

    private String extractExternalId(final String responseBody) {
        if (StringUtils.isBlank(responseBody) || !responseBody.trim().startsWith("{")) {
            return null;
        }
        final JsonObject object = JsonParser.parseString(responseBody).getAsJsonObject();
        if (object.has("messageId")) {
            return object.get("messageId").getAsString();
        }
        if (object.has("id")) {
            return object.get("id").getAsString();
        }
        return null;
    }
}
