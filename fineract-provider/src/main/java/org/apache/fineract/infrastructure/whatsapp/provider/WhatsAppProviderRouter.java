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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.whatsapp.provider.config.WhatsAppProviderProperties;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WhatsAppProviderRouter {

    private final WhatsAppProviderProperties properties;
    private final Map<WhatsAppProvider, WhatsAppTransport> transports;

    public WhatsAppProviderRouter(final WhatsAppProviderProperties properties, final List<WhatsAppTransport> transportList) {
        this.properties = properties;
        this.transports = new EnumMap<>(WhatsAppProvider.class);
        for (final WhatsAppTransport transport : transportList) {
            this.transports.put(transport.provider(), transport);
        }
    }

    public WhatsAppTransportResult sendText(final String recipientPhoneNumber, final String messageText) {
        return send(recipientPhoneNumber, messageText, null, null, null, false);
    }

    public WhatsAppTransportResult sendTemplate(final String recipientPhoneNumber, final String templateName, final String languageCode,
            final List<String> bodyValues) {
        return send(recipientPhoneNumber, null, templateName, languageCode, bodyValues, true);
    }

    private WhatsAppTransportResult send(final String phone, final String text, final String templateName, final String language,
            final List<String> bodyValues, final boolean template) {
        final WhatsAppProvider primary = WhatsAppProvider.fromConfig(properties.getPrimaryProviderName());
        final WhatsAppTransportResult primaryResult = dispatch(primary, phone, text, templateName, language, bodyValues, template);
        if (primaryResult.successful()) {
            return primaryResult;
        }
        if (!properties.isFallbackEnabled()) {
            return primaryResult;
        }
        final WhatsAppProvider fallback = WhatsAppProvider.fromConfig(properties.getFallbackProviderName());
        if (fallback == primary) {
            return primaryResult;
        }
        log.warn("Primary WhatsApp provider {} failed; attempting fallback {}", primary, fallback);
        final WhatsAppTransportResult fallbackResult = dispatch(fallback, phone, text, templateName, language, bodyValues, template);
        if (fallbackResult.successful()) {
            return fallbackResult;
        }
        return primaryResult.statusCode() != 0 ? primaryResult : fallbackResult;
    }

    private WhatsAppTransportResult dispatch(final WhatsAppProvider provider, final String phone, final String text,
            final String templateName, final String language, final List<String> bodyValues, final boolean template) {
        final WhatsAppTransport transport = transports.get(provider);
        if (transport == null || !transport.isConfigured()) {
            return WhatsAppTransportResult.failure(provider, 0, null, provider + " WhatsApp transport is not configured");
        }
        return template ? transport.sendTemplate(phone, templateName, language, bodyValues) : transport.sendText(phone, text);
    }

    public WhatsAppProvider primaryProvider() {
        return WhatsAppProvider.fromConfig(properties.getPrimaryProviderName());
    }

    public Map<String, Object> connectivitySnapshot() {
        final Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("primaryProvider", primaryProvider().name());
        snapshot.put("fallbackProvider", WhatsAppProvider.fromConfig(properties.getFallbackProviderName()).name());
        snapshot.put("fallbackEnabled", properties.isFallbackEnabled());
        for (final WhatsAppProvider provider : WhatsAppProvider.values()) {
            final WhatsAppTransport transport = transports.get(provider);
            snapshot.put(provider.name() + "_configured", transport != null && transport.isConfigured());
        }
        // Meta cannot send Fineract-Platform-TenantId; callback URL must include tenant query param.
        snapshot.put("webhookCallbackPath", "/api/v1/meta/whatsapp/webhook?tenantIdentifier=<tenant>");
        return snapshot;
    }
}
