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
package org.apache.fineract.infrastructure.whatsapp.provider.meta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEvent;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationWebhookEventRepository;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.africastalking.service.RecipientResolutionService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInboundConversationService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSharedNumberDisambiguationService;
import org.apache.fineract.infrastructure.whatsapp.provider.CommunicationMessageStatusUpdater;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppProvider;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetaWhatsAppWebhookService {

    private final CommunicationMessageRepository communicationMessageRepository;
    private final CommunicationWebhookEventRepository webhookEventRepository;
    private final RecipientResolutionService recipientResolutionService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final StaffRepositoryWrapper staffRepositoryWrapper;
    private final WhatsAppInboundConversationService inboundConversationService;
    private final WhatsAppSharedNumberDisambiguationService sharedNumberDisambiguationService;
    private final CommunicationMessageStatusUpdater statusUpdater;

    @Transactional
    public void processWebhookPayload(final String payload) {
        if (StringUtils.isBlank(payload) || !payload.trim().startsWith("{")) {
            log.warn("Ignoring non-JSON Meta WhatsApp webhook payload");
            return;
        }
        final JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
        if (!root.has("entry") || !root.get("entry").isJsonArray()) {
            return;
        }
        for (final JsonElement entryElement : root.getAsJsonArray("entry")) {
            final JsonObject entry = entryElement.getAsJsonObject();
            if (!entry.has("changes") || !entry.get("changes").isJsonArray()) {
                continue;
            }
            for (final JsonElement changeElement : entry.getAsJsonArray("changes")) {
                final JsonObject change = changeElement.getAsJsonObject();
                if (!change.has("value") || !change.get("value").isJsonObject()) {
                    continue;
                }
                final JsonObject value = change.getAsJsonObject("value");
                processMessages(value);
                processStatuses(value);
            }
        }
    }

    private void processMessages(final JsonObject value) {
        if (!value.has("messages") || !value.get("messages").isJsonArray()) {
            return;
        }
        final JsonArray messages = value.getAsJsonArray("messages");
        for (final JsonElement messageElement : messages) {
            final JsonObject message = messageElement.getAsJsonObject();
            final String type = message.has("type") ? message.get("type").getAsString() : "text";
            if (!"text".equals(type)) {
                log.debug("Ignoring unsupported Meta inbound message type {}", type);
                continue;
            }
            final String from = message.has("from") ? message.get("from").getAsString() : null;
            final String externalId = message.has("id") ? message.get("id").getAsString() : null;
            String body = null;
            if (message.has("text") && message.get("text").isJsonObject() && message.getAsJsonObject("text").has("body")) {
                body = message.getAsJsonObject("text").get("body").getAsString();
            }
            if (StringUtils.isAnyBlank(from, body)) {
                continue;
            }
            handleInbound(from, body, externalId);
        }
    }

    private void handleInbound(final String from, final String body, final String externalId) {
        if (StringUtils.isNotBlank(externalId) && !claimWebhookEvent("META_INBOUND:" + externalId, "META_WHATSAPP_INBOUND")) {
            return;
        }
        final String normalizedPhone = phoneNumberNormalizer.normalize(from.startsWith("+") ? from : "+" + from);
        if (StringUtils.isNotBlank(externalId) && communicationMessageRepository.findByExternalId(externalId).isPresent()) {
            return;
        }
        final ResolvedRecipientData recipient = recipientResolutionService.resolve(normalizedPhone);
        final Client client = recipient.getClientId() != null ? clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId())
                : null;
        final Staff staff = recipient.getStaffId() != null ? staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId())
                : null;
        final CommunicationMessage inbound = CommunicationMessage.inboundWhatsApp(recipient.getNormalizedPhoneNumber(),
                recipient.getRecipientType(), client, staff, body, externalId);
        inbound.setProvider(WhatsAppProvider.META.name());
        communicationMessageRepository.save(inbound);
        if (recipient.getRecipientType() == RecipientType.MULTI_CLIENT || recipient.getRecipientType() == RecipientType.CLIENT_AND_STAFF) {
            sharedNumberDisambiguationService.handleAmbiguousInbound(recipient.getNormalizedPhoneNumber(), body, recipient);
            return;
        }
        inboundConversationService.handleInbound(recipient.getNormalizedPhoneNumber(), body, recipient.getRecipientType(), client, staff);
    }

    private void processStatuses(final JsonObject value) {
        if (!value.has("statuses") || !value.get("statuses").isJsonArray()) {
            return;
        }
        for (final JsonElement statusElement : value.getAsJsonArray("statuses")) {
            final JsonObject statusObject = statusElement.getAsJsonObject();
            final String externalId = statusObject.has("id") ? statusObject.get("id").getAsString() : null;
            final String status = statusObject.has("status") ? statusObject.get("status").getAsString() : null;
            if (StringUtils.isAnyBlank(externalId, status)) {
                continue;
            }
            final String eventId = "META_STATUS:" + externalId + ":" + status.toUpperCase();
            if (webhookEventRepository.existsByEventId(eventId)) {
                log.debug("Ignoring duplicate Meta WhatsApp webhook event {}", eventId);
                continue;
            }
            String failureReason = null;
            if (statusObject.has("errors") && statusObject.get("errors").isJsonArray()
                    && statusObject.getAsJsonArray("errors").size() > 0) {
                final JsonObject error = statusObject.getAsJsonArray("errors").get(0).getAsJsonObject();
                if (error.has("title")) {
                    failureReason = error.get("title").getAsString();
                } else if (error.has("message")) {
                    failureReason = error.get("message").getAsString();
                }
            }
            // Claim only after a matching outbound row is updated so early Meta status retries are not lost.
            if (statusUpdater.applyProviderStatus(externalId, status, failureReason)) {
                claimWebhookEvent(eventId, "META_WHATSAPP_STATUS");
            }
        }
    }

    private boolean claimWebhookEvent(final String eventId, final String eventType) {
        if (webhookEventRepository.existsByEventId(eventId)) {
            log.debug("Ignoring duplicate Meta WhatsApp webhook event {}", eventId);
            return false;
        }
        webhookEventRepository.save(CommunicationWebhookEvent.create(eventId, eventType, sha256Hex(eventId)));
        return true;
    }

    private static String sha256Hex(final String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return value;
        }
    }
}
