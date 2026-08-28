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
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.core.service.IntegrationHttpRetryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AfricasTalkingClient {

    private final AfricasTalkingProperties properties;
    private final OkHttpClient httpClient;
    private final IntegrationHttpRetryService integrationHttpRetryService;

    public AfricasTalkingClient(final AfricasTalkingProperties properties,
            @Qualifier("africasTalkingHttpClient") final OkHttpClient httpClient,
            final IntegrationHttpRetryService integrationHttpRetryService) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.integrationHttpRetryService = integrationHttpRetryService;
    }

    public AfricasTalkingApiResponse executeUserLookup() throws IOException {
        validateConfigured();
        final HttpUrl url = HttpUrl.parse(properties.getBaseUrl() + AfricasTalkingConstants.USER_PATH).newBuilder()
                .addQueryParameter("username", properties.getUsername()).build();
        final Request request = new Request.Builder().url(url).get().addHeader(AfricasTalkingConstants.API_KEY_HEADER, properties.getApiKey())
                .addHeader("Accept", "application/json").build();
        return executeRequest("user lookup", request);
    }

    public AfricasTalkingApiResponse sendWhatsAppMessage(final String recipientPhoneNumber, final String messageText) throws IOException {
        validateWhatsAppConfigured();
        // Official AT SDKs send camelCase: waNumber / phoneNumber (not snake_case).
        final JsonObject bodyContent = new JsonObject();
        bodyContent.addProperty("message", messageText);
        final JsonObject requestBody = new JsonObject();
        requestBody.addProperty("username", properties.getUsername());
        requestBody.addProperty("waNumber", properties.getWhatsapp().getSenderNumber());
        requestBody.addProperty("phoneNumber", recipientPhoneNumber);
        requestBody.add("body", bodyContent);
        final HttpUrl url = HttpUrl.parse(properties.getWhatsappBaseUrl() + AfricasTalkingConstants.WHATSAPP_SEND_PATH);
        final Request request = new Request.Builder().url(url).post(RequestBody.create(requestBody.toString(),
                MediaType.parse("application/json"))).addHeader(AfricasTalkingConstants.API_KEY_HEADER, properties.getApiKey())
                .addHeader("Accept", "application/json").addHeader("Content-Type", "application/json").build();
        return executeRequest("send WhatsApp message", request);
    }

    public AfricasTalkingApiResponse sendWhatsAppTemplate(final String recipientPhoneNumber, final String templateName,
            final String languageCode, final List<String> bodyValues) throws IOException {
        validateWhatsAppConfigured();
        final JsonObject body = new JsonObject();
        body.addProperty("templateId", templateName);
        if (StringUtils.isNotBlank(languageCode)) {
            body.addProperty("language", languageCode);
        }
        final JsonArray values = new JsonArray();
        if (bodyValues != null) {
            for (final String v : bodyValues) {
                values.add(v == null ? "" : v);
            }
        }
        body.add("bodyValues", values);

        final JsonObject requestBody = new JsonObject();
        requestBody.addProperty("username", properties.getUsername());
        requestBody.addProperty("waNumber", properties.getWhatsapp().getSenderNumber());
        requestBody.addProperty("phoneNumber", recipientPhoneNumber);
        requestBody.add("body", body);

        final HttpUrl url = HttpUrl.parse(properties.getWhatsappBaseUrl() + AfricasTalkingConstants.WHATSAPP_SEND_PATH);
        final Request request = new Request.Builder().url(url)
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .addHeader(AfricasTalkingConstants.API_KEY_HEADER, properties.getApiKey())
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json").build();
        return executeRequest("send WhatsApp template", request);
    }

    public void validateWhatsAppConfigured() {
        validateConfigured();
        if (StringUtils.isBlank(properties.getWhatsapp().getSenderNumber())) {
            throw new IllegalStateException(
                    "AfricasTalking WhatsApp sender number is not configured. Set AFRICASTALKING_WHATSAPP_SENDER.");
        }
    }

    public AfricasTalkingApiResponse initiateVoiceCall(final String recipientPhoneNumber, final String clientRequestId) throws IOException {
        validateVoiceConfigured();
        final String formBody = "username=" + urlEncode(properties.getUsername()) + "&from=" + urlEncode(properties.getVoice().getCallerId())
                + "&to=" + urlEncode(recipientPhoneNumber)
                + (StringUtils.isNotBlank(clientRequestId) ? "&clientRequestId=" + urlEncode(clientRequestId) : "");
        final HttpUrl url = HttpUrl.parse(properties.getVoiceBaseUrl() + AfricasTalkingConstants.VOICE_CALL_PATH);
        final Request request = new Request.Builder().url(url)
                .post(RequestBody.create(formBody, MediaType.parse("application/x-www-form-urlencoded")))
                .addHeader(AfricasTalkingConstants.API_KEY_HEADER_ALT, properties.getApiKey())
                .addHeader(AfricasTalkingConstants.API_KEY_HEADER, properties.getApiKey()).addHeader("Accept", "application/xml").build();
        return executeRequest("initiate voice call", request);
    }

    private AfricasTalkingApiResponse executeRequest(final String operationName, final Request request) throws IOException {
        try (Response response = integrationHttpRetryService.execute(AfricasTalkingConstants.USER_AGENT_PREFIX, operationName, httpClient,
                request)) {
            final String body = response.body() != null ? response.body().string() : "";
            return new AfricasTalkingApiResponse(response.code(), body);
        }
    }

    public void validateVoiceConfigured() {
        validateConfigured();
        if (StringUtils.isBlank(properties.getVoice().getCallerId())) {
            throw new IllegalStateException("AfricasTalking voice caller ID is not configured. Set AFRICASTALKING_VOICE_CALLER_ID.");
        }
    }

    private static String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public void validateConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("AfricasTalking integration is not configured. Set AFRICASTALKING_USERNAME and AFRICASTALKING_API_KEY.");
        }
    }

    public static String sha256Hex(final String payload) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean verifyHmacSha256(final String payload, final String signature, final String secret) {
        if (StringUtils.isAnyBlank(payload, signature, secret)) {
            return false;
        }
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            final String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(computedHex.getBytes(StandardCharsets.UTF_8), signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to verify AfricasTalking webhook signature", e);
            return false;
        }
    }

    public record AfricasTalkingApiResponse(int statusCode, String body) {

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
