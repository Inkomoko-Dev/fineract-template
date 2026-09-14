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
