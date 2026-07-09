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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEvent;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingWebhookService {

    private final AfricasTalkingWebhookVerifier webhookVerifier;
    private final CommunicationWebhookEventRepository webhookEventRepository;

    @Transactional
    public WebhookProcessingResult processWebhook(final String eventType, final String rawPayload, final String signatureHeader,
            final String userAgent) {
        webhookVerifier.verify(rawPayload, signatureHeader, userAgent);
        final String payloadHash = AfricasTalkingClient.sha256Hex(rawPayload);
        final String eventId = extractEventId(rawPayload, payloadHash);
        if (webhookEventRepository.existsByEventId(eventId)) {
            log.debug("Ignoring duplicate AfricasTalking webhook event {}", eventId);
            return WebhookProcessingResult.duplicate(eventId);
        }
        final CommunicationWebhookEvent event = CommunicationWebhookEvent.create(eventId, eventType, payloadHash);
        webhookEventRepository.save(event);
        log.info("Stored AfricasTalking webhook event type={} eventId={}", eventType, eventId);
        return WebhookProcessingResult.processed(eventId);
    }

    String extractEventId(final String rawPayload, final String payloadHash) {
        if (StringUtils.isBlank(rawPayload)) {
            return payloadHash;
        }
        final String trimmed = rawPayload.trim();
        if (trimmed.startsWith("{")) {
            try {
                final JsonElement element = JsonParser.parseString(trimmed);
                if (element.isJsonObject()) {
                    final JsonObject object = element.getAsJsonObject();
                    for (final String key : new String[] { "id", "messageId", "sessionId", "callSessionId", "eventId" }) {
                        if (object.has(key) && !object.get(key).isJsonNull()) {
                            final String value = object.get(key).getAsString();
                            if (StringUtils.isNotBlank(value)) {
                                return value;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Unable to parse webhook JSON payload for event id extraction", e);
            }
        }
        if (trimmed.contains("=")) {
            final Map<String, String> formValues = parseFormPayload(trimmed);
            final String sessionId = formValues.get("sessionId");
            if (StringUtils.isNotBlank(sessionId)) {
                final String dtmfDigits = StringUtils.defaultString(formValues.get("dtmfDigits"));
                final String callSessionState = StringUtils.defaultString(formValues.get("callSessionState"));
                return sessionId + ":" + dtmfDigits + ":" + callSessionState;
            }
            for (final String key : new String[] { "id", "sessionId", "callSessionState", "callerNumber" }) {
                if (formValues.containsKey(key) && StringUtils.isNotBlank(formValues.get(key))) {
                    return formValues.get(key) + ":" + payloadHash.substring(0, 16);
                }
            }
        }
        return payloadHash;
    }

    private Map<String, String> parseFormPayload(final String rawPayload) {
        return java.util.Arrays.stream(rawPayload.split("&")).map(part -> part.split("=", 2)).filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));
    }

    public record WebhookProcessingResult(boolean duplicate, String eventId) {

        public static WebhookProcessingResult duplicate(final String eventId) {
            return new WebhookProcessingResult(true, eventId);
        }

        public static WebhookProcessingResult processed(final String eventId) {
            return new WebhookProcessingResult(false, eventId);
        }
    }
}
