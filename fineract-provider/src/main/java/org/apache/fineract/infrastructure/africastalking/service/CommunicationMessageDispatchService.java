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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationMessageDispatchService {

    private final AfricasTalkingClient africasTalkingClient;
    private final CommunicationMessageRepository communicationMessageRepository;

    @Transactional
    public void dispatchPendingMessages() {
        final var pendingMessages = communicationMessageRepository
                .findTop200ByStatusAndChannelOrderByCreatedDateAsc(CommunicationMessageStatus.PENDING,
                        org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel.WHATSAPP);
        for (final CommunicationMessage message : pendingMessages) {
            dispatchMessage(message);
        }
    }

    @Transactional
    public void dispatchMessage(final CommunicationMessage message) {
        if (message.getStatus() != CommunicationMessageStatus.PENDING) {
            return;
        }
        try {
            final AfricasTalkingClient.AfricasTalkingApiResponse response = africasTalkingClient
                    .sendWhatsAppMessage(message.getPhoneNumber(), message.getMessageBody());
            if (response.isSuccessful()) {
                message.setStatus(CommunicationMessageStatus.SENT);
                message.setExternalId(extractExternalId(response.body()));
            } else {
                message.setStatus(CommunicationMessageStatus.FAILED);
                message.setStatusDetail("HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to dispatch WhatsApp message {}", message.getId(), e);
            message.setStatus(CommunicationMessageStatus.FAILED);
            message.setStatusDetail(StringUtils.left(e.getMessage(), 255));
        }
        communicationMessageRepository.save(message);
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
