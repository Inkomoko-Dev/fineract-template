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
import java.util.UUID;
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
        final TriggerResult unavailable = unavailable(configuration, transactionId, false);
        if (unavailable != null) {
            return unavailable;
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", workflowId);
        body.put("to", subscriber);
        body.put("payload", payload);
        body.put("transactionId", transactionId);
        return execute(configuration, "POST", "/v1/events/trigger", body, transactionId);
    }

    public TriggerResult upsertSubscriber(final Map<String, Object> subscriber) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        final String transactionId = "subscriber-" + subscriber.get("subscriberId");
        final TriggerResult unavailable = unavailable(configuration, transactionId, false);
        if (unavailable != null) {
            return unavailable;
        }
        return execute(configuration, "POST", "/v1/subscribers", subscriber, transactionId, 200, 201, 409);
    }

    public TriggerResult upsertProviderCredentials(final String subscriberId, final Map<String, Object> credentials) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        final String transactionId = "credentials-" + subscriberId + "-" + UUID.randomUUID();
        final TriggerResult unavailable = unavailable(configuration, transactionId, false);
        if (unavailable != null) {
            return unavailable;
        }
        return execute(configuration, "PATCH", "/v1/subscribers/" + encode(subscriberId) + "/credentials", credentials, transactionId);
    }

    public TriggerResult ensureWorkflow(final String workflowId, final String campaignName, final List<String> channels) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        final TriggerResult unavailable = unavailable(configuration, workflowId, true);
        if (unavailable != null) {
            return new TriggerResult(true, workflowId, "Novu is disabled; workflow was not created");
        }
        if (StringUtils.isBlank(workflowId)) {
            return new TriggerResult(false, workflowId, "workflowId is required");
        }
        if (execute(configuration, "GET", workflowPath(workflowId), null, workflowId, 200).isSuccessful()) {
            return new TriggerResult(true, workflowId, "Workflow already exists");
        }
        final List<Map<String, Object>> steps = workflowSteps(channels);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", StringUtils.defaultIfBlank(campaignName, workflowId));
        body.put("workflowId", workflowId);
        body.put("active", true);
        body.put("steps", steps);
        return execute(configuration, "POST", "/v2/workflows", body, workflowId, 201, 200, 409);
    }

    public TriggerResult deleteWorkflow(final String workflowId) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        if (StringUtils.isBlank(workflowId)) {
            return new TriggerResult(true, workflowId, "Workflow delete skipped");
        }
        final TriggerResult unavailable = unavailable(configuration, workflowId, true);
        if (unavailable != null) {
            return new TriggerResult(true, workflowId, "Workflow delete skipped");
        }
        return execute(configuration, "DELETE", workflowPath(workflowId), null, workflowId, 200, 204, 404);
    }

    public BulkSubscriberResult upsertSubscribers(final List<Map<String, Object>> subscribers) {
        final NovuConfigurationData configuration = configurationService.getConfiguration();
        if (unavailable(configuration, UUID.randomUUID().toString(), false) != null) {
            return new BulkSubscriberResult(0, subscribers.size());
        }
        final Map<String, Object> body = Map.of("subscribers", subscribers);
        final TriggerResult result = execute(configuration, "POST", "/v1/subscribers/bulk", body, UUID.randomUUID().toString());
        if (!result.isSuccessful() || StringUtils.isBlank(result.getMessage())) {
            return new BulkSubscriberResult(0, subscribers.size());
        }
        try {
            final JsonObject parsed = JsonParser.parseString(result.getMessage()).getAsJsonObject();
            final int created = arraySize(parsed, "created");
            final int updated = arraySize(parsed, "updated");
            final int failed = arraySize(parsed, "failed");
            return new BulkSubscriberResult(created + updated, failed);
        } catch (final RuntimeException e) {
            return new BulkSubscriberResult(0, subscribers.size());
        }
    }

    private List<Map<String, Object>> workflowSteps(final List<String> channels) {
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
        return steps;
    }

    private Map<String, Object> step(final String type, final String name, final Map<String, Object> controlValues) {
        final Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", name);
        step.put("type", type);
        step.put("controlValues", controlValues);
        return step;
    }

    private TriggerResult unavailable(final NovuConfigurationData configuration, final String transactionId, final boolean treatAsSuccess) {
        if (configuration.isEnabled() && StringUtils.isNotBlank(configuration.getApiKey())) {
            return null;
        }
        final String message = configuration.isEnabled() ? "Novu API key is not configured" : "Novu integration is disabled";
        return new TriggerResult(treatAsSuccess, transactionId, message);
    }

    private TriggerResult execute(final NovuConfigurationData configuration, final String method, final String path,
            final Object body, final String transactionId, final int... successCodes) {
        final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(configuration.getTimeoutSeconds(), TimeUnit.SECONDS).build();
        final Request.Builder builder = new Request.Builder().url(stripTrailingSlash(configuration.getApiUrl()) + path)
                .header("Authorization", "ApiKey " + configuration.getApiKey()).header("Idempotency-Key",
                        StringUtils.defaultIfBlank(transactionId, UUID.randomUUID().toString()));
        final RequestBody requestBody = body == null ? null : RequestBody.create(gson.toJson(body), JSON);
        switch (method) {
            case "POST":
                builder.post(requestBody);
                break;
            case "PATCH":
                builder.patch(requestBody);
                break;
            case "DELETE":
                builder.delete();
                break;
            default:
                builder.get();
                break;
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            final String responseBody = response.body() == null ? "" : response.body().string();
            boolean successful = successCodes.length == 0 ? response.isSuccessful() : false;
            for (final int code : successCodes) {
                if (response.code() == code) {
                    successful = true;
                    break;
                }
            }
            return new TriggerResult(successful, transactionId, responseBody);
        } catch (final IOException e) {
            return new TriggerResult(false, transactionId, e.getMessage());
        }
    }

    private String workflowPath(final String workflowId) {
        return "/v2/workflows/" + encode(workflowId);
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private int arraySize(final JsonObject object, final String field) {
        return object.has(field) ? object.getAsJsonArray(field).size() : 0;
    }

    private String stripTrailingSlash(final String value) {
        return StringUtils.removeEnd(StringUtils.defaultIfBlank(value, "https://api.novu.co"), "/");
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

        public int getSynced() {
            return synced;
        }

        public int getFailed() {
            return failed;
        }
    }
}
