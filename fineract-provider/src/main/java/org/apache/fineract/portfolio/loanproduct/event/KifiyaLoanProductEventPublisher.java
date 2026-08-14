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
public class KifiyaLoanProductEventPublisher {

    private static final String EVENT_TYPE = "CBS_LOAN_PRODUCT_THIRD_PARTY_DISBURSEMENT";
    private static final String SOURCE = "CBS";
    private static final long SEND_TIMEOUT_SECONDS = 30;

    private final RestTemplate restTemplate;
    private final ConfigurationReadPlatformService configurationReadPlatformService;
    
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateSerializer())
            .registerTypeAdapter(LocalDate.class, new LocalDateDeserializer())
            .create();

    private static final String WEBHOOK_URL = "kifiya-webhook-url";
    private static final String WEBHOOK_ENABLED = "kifiya-webhook-enabled";
    private static final String WEBHOOK_API_KEY = "kifiya-webhook-api-key";

    public void publish(final String action, final ThirdPartyDisbursementProductData product) {
        final GlobalConfigurationPropertyData urlConfig = this.configurationReadPlatformService.retrieveGlobalConfiguration(WEBHOOK_URL);
        final GlobalConfigurationPropertyData enabledConfig = this.configurationReadPlatformService.retrieveGlobalConfiguration(WEBHOOK_ENABLED);
        final GlobalConfigurationPropertyData apiKeyConfig = this.configurationReadPlatformService.retrieveGlobalConfiguration(WEBHOOK_API_KEY);

        // Get the string value for URL (stored in string_value column)
        String urlValue = null;
        if (urlConfig != null) {
            urlValue = urlConfig.getStringValue();
        }

        if (enabledConfig == null || !enabledConfig.isEnabled() || urlValue == null || urlValue.isBlank()) {
            log.debug("Skipping Kifiya loan product event for product {} because webhook is disabled or URL not configured", product.getId());
            return;
        }

        final String eventId = UUID.randomUUID().toString();
        final JsonObject envelope = new JsonObject();
        envelope.addProperty("eventId", eventId);
        envelope.addProperty("eventType", EVENT_TYPE);
        envelope.addProperty("action", action);
        envelope.addProperty("occurredAt", Instant.now().toString());
        envelope.addProperty("source", SOURCE);
        envelope.addProperty("schemaVersion", 1);
        envelope.add("payload", JsonParser.parseString(this.gson.toJson(product)));

        log.info("Publishing Kifiya loan product event {} (action={}) to webhook {}", eventId, action, urlValue);
        try {
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Get the string value for API key (stored in string_value column)
            String apiKeyValue = null;
            if (apiKeyConfig != null) {
                apiKeyValue = apiKeyConfig.getStringValue();
            }
            
            if (apiKeyValue != null && !apiKeyValue.isBlank()) {
                headers.set("X-API-Key", apiKeyValue);
            }

            final HttpEntity<String> request = new HttpEntity<>(envelope.toString(), headers);
            
            final ResponseEntity<String> response = this.restTemplate.exchange(
                    urlValue,
                    HttpMethod.POST,
                    request,
                    String.class);
            
            log.info("Published Kifiya loan product event {} (action={}). Response: {}", eventId, action, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to publish Kifiya loan product event {} for product {}: {}", eventId, product.getId(), e.getMessage(), e);
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
