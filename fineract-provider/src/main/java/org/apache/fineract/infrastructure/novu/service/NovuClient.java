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
package org.apache.fineract.infrastructure.novu.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.novu.data.NovuConfigurationData;
import org.springframework.stereotype.Component;

@Component
public class NovuClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final NovuConfigurationService configurationService;
    private final Gson gson = new Gson();

    public NovuClient(final NovuConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public TriggerResult trigger(final String workflowId, final Map<String, Object> subscriber, final Map<String, Object> payload,
            final String transactionId) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        if (!configuration.isEnabled()) {
            return new TriggerResult(false, transactionId, "Novu integration is disabled");
        }
        if (StringUtils.isBlank(configuration.getApiKey())) {
            return new TriggerResult(false, transactionId, "Novu API key is not configured");
        }

        final JsonObject body = new JsonObject();
        body.addProperty("name", workflowId);
        body.add("to", gson.toJsonTree(subscriber));
        body.add("payload", gson.toJsonTree(payload));
        body.addProperty("transactionId", transactionId);

        final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS).build();
        final Request request = new Request.Builder().url(stripTrailingSlash(configuration.getApiUrl()) + "/v1/events/trigger")
                .header("Authorization", "ApiKey " + configuration.getApiKey()).header("Idempotency-Key", transactionId)
                .post(RequestBody.create(gson.toJson(body), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            final String responseBody = response.body() == null ? "" : response.body().string();
            return new TriggerResult(response.isSuccessful(), transactionId, truncate(responseBody));
        } catch (final IOException e) {
            return new TriggerResult(false, transactionId, truncate(e.getMessage()));
        }
    }

    public TriggerResult upsertSubscriber(final Map<String, Object> subscriber) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        final String transactionId = "subscriber-" + subscriber.get("subscriberId");
        if (!configuration.isEnabled() || StringUtils.isBlank(configuration.getApiKey())) {
            return new TriggerResult(false, transactionId, "Novu is disabled or its API key is missing");
        }
        final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS).build();
        final Request request = new Request.Builder().url(stripTrailingSlash(configuration.getApiUrl()) + "/v1/subscribers")
                .header("Authorization", "ApiKey " + configuration.getApiKey())
                .post(RequestBody.create(gson.toJson(subscriber), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            final String responseBody = response.body() == null ? "" : response.body().string();
            // Creating an existing subscriber is idempotent for the CBS sync use case.
            return new TriggerResult(response.isSuccessful() || response.code() == 409, transactionId, truncate(responseBody));
        } catch (final IOException e) {
            return new TriggerResult(false, transactionId, truncate(e.getMessage()));
        }
    }

    public TriggerResult upsertProviderCredentials(final String subscriberId, final Map<String, Object> credentials) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        final String transactionId = "credentials-" + subscriberId + "-" + java.util.UUID.randomUUID();
        if (!configuration.isEnabled() || StringUtils.isBlank(configuration.getApiKey())) {
            return new TriggerResult(false, transactionId, "Novu is disabled or its API key is missing");
        }
        final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS).build();
        final Request request = new Request.Builder()
                .url(stripTrailingSlash(configuration.getApiUrl()) + "/v1/subscribers/" + subscriberId + "/credentials")
                .header("Authorization", "ApiKey " + configuration.getApiKey()).header("Idempotency-Key", transactionId)
                .patch(RequestBody.create(gson.toJson(credentials), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            final String responseBody = response.body() == null ? "" : response.body().string();
            return new TriggerResult(response.isSuccessful(), transactionId, truncate(responseBody));
        } catch (final IOException e) {
            return new TriggerResult(false, transactionId, truncate(e.getMessage()));
        }
    }

    public TriggerResult ensureWorkflow(final String workflowId, final String campaignName, final List<String> channels) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        if (!configuration.isEnabled() || StringUtils.isBlank(configuration.getApiKey())) {
            return new TriggerResult(true, workflowId, "Novu is disabled; workflow was not created");
        }
        if (StringUtils.isBlank(workflowId)) {
            return new TriggerResult(false, workflowId, "workflowId is required");
        }
        if (workflowExists(configuration, workflowId)) {
            return new TriggerResult(true, workflowId, "Workflow already exists");
        }
        final List<Map<String, Object>> steps = new ArrayList<>();
        for (final String channel : channels) {
            switch (channel) {
                case "SMS":
                    steps.add(step("sms", "SMS", Map.of("body", "{{payload.smsBody}}")));
                    break;
                case "EMAIL":
                    steps.add(step("email", "Email",
                            Map.of("subject", "{{payload.emailSubject}}", "body", "{{payload.emailBody}}", "editorType", "html")));
                    break;
                case "IN_APP":
                    steps.add(step("in_app", "In-app", Map.of("subject", "Notification", "body", "{{payload.inAppBody}}")));
                    break;
                case "WHATSAPP":
                case "TELEGRAM":
                case "SLACK":
                    steps.add(step("chat", channel, Map.of("body", "{{payload.chatBody}}")));
                    break;
                default:
                    break;
            }
        }
        if (steps.isEmpty()) {
            steps.add(step("sms", "SMS", Map.of("body", "{{payload.smsBody}}")));
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", StringUtils.defaultIfBlank(campaignName, workflowId));
        body.put("workflowId", workflowId);
        body.put("active", true);
        body.put("steps", steps);
        return execute(configuration, "POST", "/v2/workflows", body, workflowId, 201, 200, 409);
    }

    public TriggerResult deleteWorkflow(final String workflowId) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        if (!configuration.isEnabled() || StringUtils.isBlank(configuration.getApiKey()) || StringUtils.isBlank(workflowId)) {
            return new TriggerResult(true, workflowId, "Workflow delete skipped");
        }
        return execute(configuration, "DELETE", workflowPath(workflowId), null, workflowId, 200, 204, 404);
    }

    private boolean workflowExists(final NovuConfigurationData configuration, final String workflowId) {
        final TriggerResult result = execute(configuration, "GET", workflowPath(workflowId), null, workflowId, 200);
        return result.isSuccessful();
    }

    private String workflowPath(final String workflowId) {
        return "/v2/workflows/" + URLEncoder.encode(workflowId, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Map<String, Object> step(final String type, final String name, final Map<String, Object> controlValues) {
        final Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", name);
        step.put("type", type);
        step.put("controlValues", controlValues);
        return step;
    }

    private TriggerResult execute(final NovuConfigurationData configuration, final String method, final String path,
            final Map<String, Object> body, final String transactionId, final int... successCodes) {
        final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS).build();
        final Request.Builder builder = new Request.Builder().url(stripTrailingSlash(configuration.getApiUrl()) + path)
                .header("Authorization", "ApiKey " + configuration.getApiKey()).header("Idempotency-Key",
                        StringUtils.defaultIfBlank(transactionId, java.util.UUID.randomUUID().toString()));
        final RequestBody requestBody = body == null ? null : RequestBody.create(gson.toJson(body), JSON);
        if ("POST".equals(method)) {
            builder.post(requestBody);
        } else if ("DELETE".equals(method)) {
            builder.delete();
        } else {
            builder.get();
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            final String responseBody = response.body() == null ? "" : response.body().string();
            boolean successful = false;
            for (final int code : successCodes) {
                if (response.code() == code) {
                    successful = true;
                    break;
                }
            }
            return new TriggerResult(successful, transactionId, truncate(responseBody));
        } catch (final IOException e) {
            return new TriggerResult(false, transactionId, truncate(e.getMessage()));
        }
    }

    public BulkSubscriberResult upsertSubscribers(final List<Map<String, Object>> subscribers) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        if (!configuration.isEnabled() || StringUtils.isBlank(configuration.getApiKey())) {
            return new BulkSubscriberResult(0, subscribers.size());
        }
        final JsonObject body = new JsonObject();
        body.add("subscribers", gson.toJsonTree(subscribers));
        final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS).build();
        final Request request = new Request.Builder().url(stripTrailingSlash(configuration.getApiUrl()) + "/v1/subscribers/bulk")
                .header("Authorization", "ApiKey " + configuration.getApiKey())
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .post(RequestBody.create(gson.toJson(body), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return new BulkSubscriberResult(0, subscribers.size());
            }
            final JsonObject result = JsonParser.parseString(response.body().string()).getAsJsonObject();
            final int created = result.has("created") ? result.getAsJsonArray("created").size() : 0;
            final int updated = result.has("updated") ? result.getAsJsonArray("updated").size() : 0;
            final int failed = result.has("failed") ? result.getAsJsonArray("failed").size() : 0;
            return new BulkSubscriberResult(created + updated, failed);
        } catch (final IOException | RuntimeException e) {
            return new BulkSubscriberResult(0, subscribers.size());
        }
    }

    private String stripTrailingSlash(final String value) {
        return StringUtils.removeEnd(StringUtils.defaultIfBlank(value, "https://api.novu.co"), "/");
    }

    private String truncate(final String value) {
        return StringUtils.abbreviate(StringUtils.defaultString(value), 990);
    }

    public static final class TriggerResult {

        private final boolean successful;
        private final String transactionId;
        private final String message;

        public TriggerResult(final boolean successful, final String transactionId, final String message) {
            this.successful = successful;
            this.transactionId = transactionId;
            this.message = message;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class BulkSubscriberResult {
        private final int synced;
        private final int failed;

        public BulkSubscriberResult(final int synced, final int failed) {
            this.synced = synced;
            this.failed = failed;
        }

        public int getSynced() { return synced; }
        public int getFailed() { return failed; }
    }
}
