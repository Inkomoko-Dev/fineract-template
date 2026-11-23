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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.IntegrationHttpRetryService;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppLanguageMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MetaCloudWhatsAppClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final MetaWhatsAppProperties properties;
    private final OkHttpClient httpClient;
    private final IntegrationHttpRetryService integrationHttpRetryService;
    private final WhatsAppLanguageMapper languageMapper;

    public MetaCloudWhatsAppClient(final MetaWhatsAppProperties properties,
            @Qualifier("metaWhatsAppHttpClient") final OkHttpClient httpClient,
            final IntegrationHttpRetryService integrationHttpRetryService, final WhatsAppLanguageMapper languageMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.integrationHttpRetryService = integrationHttpRetryService;
        this.languageMapper = languageMapper;
    }

    public MetaApiResponse sendText(final String recipientPhoneNumber, final String messageText) throws IOException {
        validateConfigured();
        final JsonObject body = new JsonObject();
        body.addProperty("messaging_product", "whatsapp");
        body.addProperty("to", stripPlus(recipientPhoneNumber));
        body.addProperty("type", "text");
        final JsonObject text = new JsonObject();
        text.addProperty("preview_url", false);
        text.addProperty("body", messageText);
        body.add("text", text);
        return execute("send Meta WhatsApp text", body);
    }

    public MetaApiResponse sendTemplate(final String recipientPhoneNumber, final String templateName, final String languageCode,
            final List<String> bodyValues) throws IOException {
        validateConfigured();
        final JsonObject body = new JsonObject();
        body.addProperty("messaging_product", "whatsapp");
        body.addProperty("to", stripPlus(recipientPhoneNumber));
        body.addProperty("type", "template");
        final JsonObject template = new JsonObject();
        template.addProperty("name", templateName);
        final JsonObject language = new JsonObject();
        language.addProperty("code", languageMapper.toMetaLanguageCode(languageCode));
        template.add("language", language);
        if (bodyValues != null && !bodyValues.isEmpty()) {
            final JsonArray components = new JsonArray();
            final JsonObject bodyComponent = new JsonObject();
            bodyComponent.addProperty("type", "body");
            final JsonArray parameters = new JsonArray();
            for (final String value : bodyValues) {
                final JsonObject parameter = new JsonObject();
                parameter.addProperty("type", "text");
                parameter.addProperty("text", value == null ? "" : value);
                parameters.add(parameter);
            }
            bodyComponent.add("parameters", parameters);
            components.add(bodyComponent);
            template.add("components", components);
        }
        body.add("template", template);
        return execute("send Meta WhatsApp template", body);
    }

    public String buildMessagesUrl() {
        return properties.getGraphBaseUrl() + "/" + properties.getApiVersion() + "/" + properties.getPhoneNumberId() + "/messages";
    }

    private MetaApiResponse execute(final String operation, final JsonObject body) throws IOException {
        final Request request = new Request.Builder().url(buildMessagesUrl()).post(RequestBody.create(body.toString(), JSON))
                .addHeader("Authorization", "Bearer " + properties.getAccessToken()).addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json").build();
        try (Response response = integrationHttpRetryService.execute("MetaWhatsApp", operation, httpClient, request)) {
            final String responseBody = response.body() != null ? response.body().string() : "";
            return new MetaApiResponse(response.code(), responseBody);
        }
    }

    public void validateConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "Meta WhatsApp is not configured. Set META_WHATSAPP_ENABLED, META_WHATSAPP_ACCESS_TOKEN, META_WHATSAPP_PHONE_NUMBER_ID.");
        }
    }

    public static String extractMessageId(final String responseBody) {
        if (StringUtils.isBlank(responseBody) || !responseBody.trim().startsWith("{")) {
            return null;
        }
        final JsonObject object = JsonParser.parseString(responseBody).getAsJsonObject();
        if (object.has("messages") && object.get("messages").isJsonArray() && object.getAsJsonArray("messages").size() > 0) {
            final JsonObject first = object.getAsJsonArray("messages").get(0).getAsJsonObject();
            if (first.has("id")) {
                return first.get("id").getAsString();
            }
        }
        return null;
    }

    private static String stripPlus(final String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.startsWith("+") ? phoneNumber.substring(1) : phoneNumber;
    }

    public record MetaApiResponse(int statusCode, String body) {
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
