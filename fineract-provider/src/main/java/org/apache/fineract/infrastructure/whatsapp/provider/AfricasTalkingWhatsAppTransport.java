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
package org.apache.fineract.infrastructure.whatsapp.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingWhatsAppTransport implements WhatsAppTransport {

    private final AfricasTalkingClient africasTalkingClient;
    private final AfricasTalkingProperties properties;
    private final WhatsAppLanguageMapper languageMapper;

    @Override
    public WhatsAppProvider provider() {
        return WhatsAppProvider.AFRICASTALKING;
    }

    @Override
    public boolean isConfigured() {
        return properties.isEnabled() && properties.isConfigured() && StringUtils.isNotBlank(properties.getWhatsapp().getSenderNumber());
    }

    @Override
    public WhatsAppTransportResult sendText(final String recipientPhoneNumber, final String messageText) {
        try {
            final AfricasTalkingClient.AfricasTalkingApiResponse response = africasTalkingClient.sendWhatsAppMessage(recipientPhoneNumber,
                    messageText);
            return toResult(response);
        } catch (Exception e) {
            log.warn("AT WhatsApp text send failed", e);
            return WhatsAppTransportResult.exception(provider(), e);
        }
    }

    @Override
    public WhatsAppTransportResult sendTemplate(final String recipientPhoneNumber, final String templateName, final String languageCode,
            final List<String> bodyValues) {
        try {
            final AfricasTalkingClient.AfricasTalkingApiResponse response = africasTalkingClient.sendWhatsAppTemplate(recipientPhoneNumber,
                    templateName, languageMapper.toAfricaTalkingLanguageCode(languageCode), bodyValues);
            return toResult(response);
        } catch (Exception e) {
            log.warn("AT WhatsApp template send failed", e);
            return WhatsAppTransportResult.exception(provider(), e);
        }
    }

    private WhatsAppTransportResult toResult(final AfricasTalkingClient.AfricasTalkingApiResponse response) {
        if (response.isSuccessful()) {
            return WhatsAppTransportResult.success(provider(), response.statusCode(), response.body(), extractExternalId(response.body()));
        }
        return WhatsAppTransportResult.failure(provider(), response.statusCode(), response.body(),
                "HTTP " + response.statusCode() + ": " + StringUtils.defaultString(response.body()));
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
