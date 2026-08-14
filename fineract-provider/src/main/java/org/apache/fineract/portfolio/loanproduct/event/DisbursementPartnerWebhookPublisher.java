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
package org.apache.fineract.portfolio.loanproduct.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductData;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisbursementPartnerWebhookPublisher {

    private static final String EVENT_TYPE = "CBS_LOAN_PRODUCT_THIRD_PARTY_DISBURSEMENT";
    private static final String SOURCE = "CBS";

    private final RestTemplate restTemplate;
    private final ConfigurationReadPlatformService configurationReadPlatformService;
    
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateSerializer())
            .registerTypeAdapter(LocalDate.class, new LocalDateDeserializer())
            .create();

    private static final String KIFIYA_WEBHOOK_URL = "kifiya-webhook-url";
    private static final String KIFIYA_WEBHOOK_ENABLED = "kifiya-webhook-enabled";
    private static final String KIFIYA_WEBHOOK_API_KEY = "kifiya-webhook-api-key";
    private static final String KIFIYA_WEBHOOK_TIMEOUT = "kifiya-webhook-timeout";
    private static final String PARTNER_WEBHOOK_CONFIG = "partner-webhook-config";

    public void publish(final String partnerCode, final String action, final ThirdPartyDisbursementProductData product) {
        final PartnerWebhookConfig config = getPartnerConfig(partnerCode);
        if (config == null || !config.isEnabled() || config.getUrl() == null || config.getUrl().isBlank()) {
            log.debug("Skipping loan product event for partner {} - webhook not configured or disabled", partnerCode);
            return;
        }

        final String eventId = UUID.randomUUID().toString();
        final JsonObject envelope = new JsonObject();
        envelope.addProperty("eventId", eventId);
        envelope.addProperty("eventType", EVENT_TYPE);
        envelope.addProperty("action", action);
        envelope.addProperty("occurredAt", Instant.now().toString());
        envelope.addProperty("source", SOURCE);
        envelope.addProperty("partnerCode", partnerCode);
        envelope.addProperty("schemaVersion", 1);
        envelope.add("payload", JsonParser.parseString(this.gson.toJson(product)));

        log.info("Publishing loan product event {} for partner {} (action={}) to webhook {}", eventId, partnerCode, action, config.getUrl());
        try {
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                headers.set("X-API-Key", config.getApiKey());
            }
            if (config.getHeaders() != null) {
                config.getHeaders().forEach(headers::set);
            }

            final HttpEntity<String> request = new HttpEntity<>(envelope.toString(), headers);
            
            final ResponseEntity<String> response = this.restTemplate.exchange(
                    config.getUrl(),
                    HttpMethod.POST,
                    request,
                    String.class);
            
            log.info("Published loan product event {} for partner {} (action={}). Response: {}", eventId, partnerCode, action, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to publish loan product event {} for partner {}: {}", eventId, partnerCode, e.getMessage(), e);
        }
    }

    private PartnerWebhookConfig getPartnerConfig(final String partnerCode) {
        // First try generic partner configuration
        final GlobalConfigurationPropertyData partnerConfig = this.configurationReadPlatformService.retrieveGlobalConfiguration(PARTNER_WEBHOOK_CONFIG);
        if (partnerConfig != null && partnerConfig.isEnabled()) {
            try {
                // Try to get string value first
                String configValue = partnerConfig.getStringValue();
                if (configValue != null && !configValue.isBlank()) {
                    final Map<String, PartnerWebhookConfig> configs = this.gson.fromJson(configValue, Map.class);
                    final PartnerWebhookConfig config = configs.get(partnerCode);
                    if (config != null) {
                        return config;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse partner webhook configuration: {}", e.getMessage());
            }
        }

        // Fallback to Kifiya-specific configuration for backward compatibility
        if ("KIFIYA".equalsIgnoreCase(partnerCode)) {
            return getKifiyaConfig();
        }

        return null;
    }

    private PartnerWebhookConfig getKifiyaConfig() {
        final GlobalConfigurationPropertyData urlConfig = this.configurationReadPlatformService.retrieveGlobalConfiguration(KIFIYA_WEBHOOK_URL);
        final GlobalConfigurationPropertyData enabledConfig = this.configurationReadPlatformService.retrieveGlobalConfiguration(KIFIYA_WEBHOOK_ENABLED);
        final GlobalConfigurationPropertyData apiKeyConfig = this.configurationReadPlatformService.retrieveGlobalConfiguration(KIFIYA_WEBHOOK_API_KEY);
        final GlobalConfigurationPropertyData timeoutConfig = this.configurationReadPlatformService.retrieveGlobalConfiguration(KIFIYA_WEBHOOK_TIMEOUT);

        // Get the string value for URL (stored in string_value column)
        String urlValue = null;
        if (urlConfig != null) {
            urlValue = urlConfig.getStringValue();
        }
        
        if (urlValue == null || urlValue.isBlank()) {
            return null;
        }

        final PartnerWebhookConfig config = new PartnerWebhookConfig();
        config.setUrl(urlValue);
        config.setEnabled(enabledConfig != null && enabledConfig.isEnabled());
        
        // Get the string value for API key (stored in string_value column)
        String apiKeyValue = null;
        if (apiKeyConfig != null) {
            apiKeyValue = apiKeyConfig.getStringValue();
        }
        config.setApiKey(apiKeyValue);
        
        if (timeoutConfig != null && timeoutConfig.getValue() != null) {
            try {
                config.setTimeoutMs(timeoutConfig.getValue());
            } catch (NumberFormatException e) {
                config.setTimeoutMs(30000L); // default
            }
        } else {
            config.setTimeoutMs(30000L); // default
        }

        return config;
    }

    public static class PartnerWebhookConfig {
        private String url;
        private boolean enabled = false;
        private long timeoutMs = 30000;
        private String apiKey;
        private Map<String, String> headers;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }
    }

    private static class LocalDateSerializer implements JsonSerializer<LocalDate> {
        @Override
        public com.google.gson.JsonElement serialize(LocalDate date, Type type, com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }

    private static class LocalDateDeserializer implements JsonDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(com.google.gson.JsonElement json, Type type, com.google.gson.JsonDeserializationContext context) {
            return LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }
}