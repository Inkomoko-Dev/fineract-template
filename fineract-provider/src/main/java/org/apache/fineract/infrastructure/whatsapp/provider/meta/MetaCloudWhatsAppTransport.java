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

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppProvider;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppTransport;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppTransportResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetaCloudWhatsAppTransport implements WhatsAppTransport {

    private final MetaCloudWhatsAppClient client;
    private final MetaWhatsAppProperties properties;

    @Override
    public WhatsAppProvider provider() {
        return WhatsAppProvider.META;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public WhatsAppTransportResult sendText(final String recipientPhoneNumber, final String messageText) {
        try {
            final MetaCloudWhatsAppClient.MetaApiResponse response = client.sendText(recipientPhoneNumber, messageText);
            return toResult(response);
        } catch (Exception e) {
            log.warn("Meta WhatsApp text send failed", e);
            return WhatsAppTransportResult.exception(provider(), e);
        }
    }

    @Override
    public WhatsAppTransportResult sendTemplate(final String recipientPhoneNumber, final String templateName, final String languageCode,
            final List<String> bodyValues) {
        try {
            final MetaCloudWhatsAppClient.MetaApiResponse response = client.sendTemplate(recipientPhoneNumber, templateName, languageCode,
                    bodyValues);
            return toResult(response);
        } catch (Exception e) {
            log.warn("Meta WhatsApp template send failed", e);
            return WhatsAppTransportResult.exception(provider(), e);
        }
    }

    private WhatsAppTransportResult toResult(final MetaCloudWhatsAppClient.MetaApiResponse response) {
        if (response.isSuccessful()) {
            return WhatsAppTransportResult.success(provider(), response.statusCode(), response.body(),
                    MetaCloudWhatsAppClient.extractMessageId(response.body()));
        }
        return WhatsAppTransportResult.failure(provider(), response.statusCode(), response.body(),
                "HTTP " + response.statusCode() + ": " + StringUtils.defaultString(response.body()));
    }
}
