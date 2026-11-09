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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.core.service.IntegrationHttpRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AfricasTalkingClientTest {

    @Mock
    private AfricasTalkingProperties properties;

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private IntegrationHttpRetryService integrationHttpRetryService;

    @Mock
    private okhttp3.Call call;

    private AfricasTalkingClient client;

    @BeforeEach
    void setUp() {
        when(properties.getUsername()).thenReturn("Inkomoko-Capital");
        when(properties.getWhatsappBaseUrl()).thenReturn("https://chat.africastalking.com");
        final AfricasTalkingProperties.Whatsapp whatsapp = new AfricasTalkingProperties.Whatsapp();
        whatsapp.setSenderNumber("+254711082867");
        when(properties.getWhatsapp()).thenReturn(whatsapp);
        when(properties.getTemplatePlaceholder()).thenReturn("[Unknown]");

        client = new AfricasTalkingClient(properties, httpClient, integrationHttpRetryService);
    }

    @Test
    void sendWhatsAppTemplateSubstitutesPlaceholderForNullBodyValues() throws IOException {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(
                new Response.Builder().request(new Request.Builder().url("https://chat.africastalking.com/whatsapp/message/send").build())
                        .protocol(okhttp3.Protocol.HTTP_1_1).code(200)
                        .message("OK").body(ResponseBody.create(MediaType.parse("application/json"),
                                "{\"messageId\":\"ATXid_123\",\"phoneNumber\":\"+254712345678\",\"status\":\"SENT\"}")).build());

        when(integrationHttpRetryService.execute(any(), any(), any(), any())).thenAnswer(invocation -> {
            final Request req = invocation.getArgument(3);
            final String bodyJson = readRequestBody(req.body());
            final var bodyObj = JsonParser.parseString(bodyJson).getAsJsonObject();
            final var bodyContent = bodyObj.getAsJsonObject("body");
            final var bodyValues = bodyContent.getAsJsonArray("bodyValues");
            // No empty strings should be present; nulls become "[Unknown]"
            boolean hasEmpty = false;
            for (var el : bodyValues) {
                if (el.getAsString().isEmpty()) {
                    hasEmpty = true;
                }
            }
            assertEquals(false, hasEmpty, "bodyValues should not contain empty strings");
            assertEquals(List.of("John", "[Unknown]"),
                    List.of(bodyValues.get(0).getAsString(), bodyValues.get(1).getAsString()));
            return invocation.getArgument(3, Request.class) != null
                    ? new Response.Builder().request(invocation.getArgument(3)).protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200).message("OK")
                            .body(ResponseBody.create(MediaType.parse("application/json"),
                                    "{\"messageId\":\"ATXid_123\"}"))
                            .build()
                    : null;
        });

        client.sendWhatsAppTemplate("+254712345678", "loan_due", "en", List.of("John", null));

        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void sendWhatsAppTemplateUsesCustomPlaceholder() throws IOException {
        when(properties.getTemplatePlaceholder()).thenReturn("[N/A]");
        client = new AfricasTalkingClient(properties, httpClient, integrationHttpRetryService);

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(
                new Response.Builder().request(new Request.Builder().url("https://chat.africastalking.com/whatsapp/message/send").build())
                        .protocol(okhttp3.Protocol.HTTP_1_1).code(200)
                        .message("OK").body(ResponseBody.create(MediaType.parse("application/json"),
                                "{\"messageId\":\"ATXid_456\",\"phoneNumber\":\"+254712345678\",\"status\":\"SENT\"}")).build());

        when(integrationHttpRetryService.execute(any(), any(), any(), any())).thenAnswer(invocation -> {
            final Request req = invocation.getArgument(3);
            final String bodyJson = readRequestBody(req.body());
            final var bodyObj = JsonParser.parseString(bodyJson).getAsJsonObject();
            final var bodyValues = bodyObj.getAsJsonObject("body").getAsJsonArray("bodyValues");
            assertEquals(List.of("John", "[N/A]"),
                    List.of(bodyValues.get(0).getAsString(), bodyValues.get(1).getAsString()));
            return new Response.Builder().request(invocation.getArgument(3)).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"), "{\"messageId\":\"ATXid_456\"}"))
                    .build();
        });

        client.sendWhatsAppTemplate("+254712345678", "loan_due", "en", List.of("John", null));

        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void createWhatsAppTemplateSendsCorrectPayload() throws IOException {
        final String jsonPayload = """
            {
              "name": "loan_reminder",
              "language": "en",
              "category": "UTILITY",
              "components": {
                "header": { "type": "HEADER", "format": "TEXT", "text": "Reminder {{1}}" },
                "body": { "type": "BODY", "kind": "TEXT", "text": "Your loan {{1}} is due on {{2}}" },
                "footer": { "type": "FOOTER", "kind": "TEXT", "text": "Thank you for using our service." }
              }
            }
            """;
        final JsonObject expectedRequestBody = new JsonObject();
        expectedRequestBody.addProperty("username", "Inkomoko-Capital");
        expectedRequestBody.addProperty("waNumber", "+254711082867");
        expectedRequestBody.addProperty("name", "loan_reminder");
        expectedRequestBody.addProperty("language", "en");
        expectedRequestBody.addProperty("category", "UTILITY");
        expectedRequestBody.add("components", new JsonParser().parse(jsonPayload).getAsJsonObject().getAsJsonObject("components"));

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(
                new Response.Builder().request(new Request.Builder().url("https://chat.africastalking.com/whatsapp/template/send").build())
                        .protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),
                                "{\"templateId\":\"loan_reminder\",\"status\":\"PENDING_APPROVAL\"}")).build());

        when(integrationHttpRetryService.execute(any(), any(), any(), any())).thenAnswer(invocation -> {
            final Request req = invocation.getArgument(3);
            final String bodyJson = readRequestBody(req.body());
            final var bodyObj = JsonParser.parseString(bodyJson).getAsJsonObject();
            assertEquals("Inkomoko-Capital", bodyObj.get("username").getAsString());
            assertEquals("+254711082867", bodyObj.get("waNumber").getAsString());
            assertEquals("loan_reminder", bodyObj.get("name").getAsString());
            assertEquals("en", bodyObj.get("language").getAsString());
            assertEquals("UTILITY", bodyObj.get("category").getAsString());
            return new Response.Builder().request(req).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"),
                            "{\"templateId\":\"loan_reminder\",\"status\":\"PENDING_APPROVAL\"}"))
                    .build();
        });

        client.createWhatsAppTemplate("loan_reminder", "en", "UTILITY", new JsonParser().parse(jsonPayload).getAsJsonObject()
                .getAsJsonObject("components"));

        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void createWhatsAppTemplateFromJsonPassesThroughBody() throws IOException {
        final String apiRequestBody = """
            {
              "name": "promo_offer",
              "language": "en",
              "category": "MARKETING",
              "components": {
                "body": { "type": "BODY", "kind": "TEXT", "text": "Hello {{1}}, your discount code is {{2}}" }
              }
            }
            """;
        final JsonObject expectedBody = JsonParser.parseString(apiRequestBody).getAsJsonObject();
        expectedBody.addProperty("username", "Inkomoko-Capital");
        expectedBody.addProperty("waNumber", "+254711082867");

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(
                new Response.Builder().request(new Request.Builder().url("https://chat.africastalking.com/whatsapp/template/send").build())
                        .protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),
                                "{\"templateId\":\"promo_offer\",\"status\":\"PENDING_APPROVAL\"}")).build());

        when(integrationHttpRetryService.execute(any(), any(), any(), any())).thenAnswer(invocation -> {
            final Request req = invocation.getArgument(3);
            final String bodyJson = readRequestBody(req.body());
            final var bodyObj = JsonParser.parseString(bodyJson).getAsJsonObject();
            assertEquals("Inkomoko-Capital", bodyObj.get("username").getAsString());
            assertEquals("+254711082867", bodyObj.get("waNumber").getAsString());
            assertEquals("promo_offer", bodyObj.get("name").getAsString());
            assertEquals("en", bodyObj.get("language").getAsString());
            assertEquals("MARKETING", bodyObj.get("category").getAsString());
            return new Response.Builder().request(req).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"),
                            "{\"templateId\":\"promo_offer\",\"status\":\"PENDING_APPROVAL\"}"))
                    .build();
        });

        client.createWhatsAppTemplateFromJson(apiRequestBody);

        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void createWhatsAppTemplateFromJsonOverwritesClientSuppliedCredentials() throws IOException {
        final String apiRequestBody = """
            {
              "username": "client-supplied-username",
              "waNumber": "+19999999999",
              "name": "promo_offer",
              "language": "en",
              "category": "MARKETING",
              "components": {
                "body": { "type": "BODY", "kind": "TEXT", "text": "Hello" }
              }
            }
            """;

        when(integrationHttpRetryService.execute(any(), any(), any(), any())).thenAnswer(invocation -> {
            final Request req = invocation.getArgument(3);
            final var bodyObj = JsonParser.parseString(readRequestBody(req.body())).getAsJsonObject();
            assertEquals("Inkomoko-Capital", bodyObj.get("username").getAsString());
            assertEquals("+254711082867", bodyObj.get("waNumber").getAsString());
            return new Response.Builder().request(req).protocol(okhttp3.Protocol.HTTP_1_1).code(201).message("Created")
                    .body(ResponseBody.create(MediaType.parse("application/json"), "{\"status\":\"PENDING_APPROVAL\"}"))
                    .build();
        });

        client.createWhatsAppTemplateFromJson(apiRequestBody);
    }

    private static String readRequestBody(final RequestBody body) throws IOException {
        final Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return buffer.readUtf8();
    }
}
