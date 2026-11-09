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
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppProviderRouter;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppTransportResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationMessageDispatchService {

    private static final int HTTP_CLIENT_ERROR_MIN = 400;
    private static final int HTTP_CLIENT_ERROR_MAX = 500;

    private final WhatsAppProviderRouter whatsAppProviderRouter;
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
        final boolean templateMode = StringUtils.isNotBlank(message.getTemplateName());
        final String mode = templateMode ? "TEMPLATE" : "TEXT";
        try {
            WhatsAppTransportResult response;
            if (templateMode) {
                final List<String> bodyValues = parseBodyValues(message.getTemplateBodyValues());
                response = whatsAppProviderRouter.sendTemplate(message.getPhoneNumber(), message.getTemplateName(),
                        message.getTemplateLanguage(), bodyValues);
                if (!response.successful() && isClientError(response.statusCode()) && StringUtils.isNotBlank(message.getMessageBody())) {
                    log.warn(
                            "WhatsApp template→text fallback messageId={} phone={} templateName={} httpStatus={} detail={}",
                            message.getId(), CommunicationLogSanitizer.maskPhone(message.getPhoneNumber()), message.getTemplateName(),
                            response.statusCode(),
                            CommunicationLogSanitizer.truncateDetail(StringUtils.defaultIfBlank(response.errorDetail(), response.body())));
                    response = whatsAppProviderRouter.sendText(message.getPhoneNumber(), message.getMessageBody());
                    modeAfterFallback(message, response);
                    return;
                }
            } else {
                response = whatsAppProviderRouter.sendText(message.getPhoneNumber(), message.getMessageBody());
            }
            applyDispatchResult(message, mode, response);
        } catch (Exception e) {
            log.error("WhatsApp dispatch failed messageId={} phone={} mode={} templateName={}", message.getId(),
                    CommunicationLogSanitizer.maskPhone(message.getPhoneNumber()), mode, message.getTemplateName(), e);
            final CommunicationDispatchErrorClassifier.Classification classification = CommunicationDispatchErrorClassifier
                    .classifyException(e);
            applyClassification(message, classification, CommunicationLogSanitizer.truncateDetail(e.getMessage()));
        }
        communicationMessageRepository.save(message);
    }

    private void modeAfterFallback(final CommunicationMessage message, final WhatsAppTransportResult response) {
        applyDispatchResult(message, "TEXT_FALLBACK", response);
        communicationMessageRepository.save(message);
    }

    private void applyDispatchResult(final CommunicationMessage message, final String mode, final WhatsAppTransportResult response) {
        message.setProvider(response.provider() == null ? null : response.provider().name());
        if (response.successful()) {
            message.setStatus(CommunicationMessageStatus.SENT);
            message.setExternalId(response.externalId());
            message.setStatusDetail(null);
            log.info("WhatsApp dispatch succeeded messageId={} phone={} provider={} mode={} templateName={}", message.getId(),
                    CommunicationLogSanitizer.maskPhone(message.getPhoneNumber()), response.provider(), mode, message.getTemplateName());
        } else {
            final String rawDetail = StringUtils.defaultIfBlank(response.errorDetail(), response.body());
            final String prefixed = "mode=" + mode
                    + (StringUtils.isNotBlank(message.getTemplateName()) ? " template=" + message.getTemplateName() : "") + " "
                    + StringUtils.defaultString(rawDetail);
            log.warn("WhatsApp dispatch failed messageId={} phone={} httpStatus={} mode={} templateName={} detail={}", message.getId(),
                    CommunicationLogSanitizer.maskPhone(message.getPhoneNumber()), response.statusCode(), mode, message.getTemplateName(),
                    CommunicationLogSanitizer.truncateDetail(rawDetail));
            applyDispatchFailure(message, response.statusCode(), prefixed);
        }
    }

    private static boolean isClientError(final int statusCode) {
        return statusCode >= HTTP_CLIENT_ERROR_MIN && statusCode < HTTP_CLIENT_ERROR_MAX;
    }

    private void applyDispatchFailure(final CommunicationMessage message, final int statusCode, final String responseBody) {
        final CommunicationDispatchErrorClassifier.Classification classification = CommunicationDispatchErrorClassifier.classify(statusCode,
                responseBody);
        final String detail = classification.code() + ": "
                + CommunicationLogSanitizer.truncateDetail(
                        StringUtils.isNotBlank(responseBody) ? "HTTP " + statusCode + ": " + responseBody : "HTTP " + statusCode);
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
}
