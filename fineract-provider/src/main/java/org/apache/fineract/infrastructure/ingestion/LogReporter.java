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
package org.apache.fineract.infrastructure.ingestion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// sends failed commands and scheduled jobs to ingestion-service, which posts them to Slack; never throws
@Slf4j
@Component
public class LogReporter {

    private static final String SOURCE = "CBS";
    private static final int MAX_STACK_FRAMES = 5;
    // request fields safe to send: never names, phone numbers or ID numbers
    private static final Map<String, List<String>> REQUEST_FIELDS = Map.of("CLIENT", List.of("officeId", "externalId", "legalFormId"),
            "JOURNALENTRY", List.of("officeId", "referenceNumber", "transactionDate", "currencyCode"));

    @Value("${fineract.integrations.ingestion.enabled}")
    private boolean enabled;

    @Value("${fineract.integrations.ingestion.base-url}")
    private String baseUrl;

    @Value("${fineract.integrations.ingestion.api-key}")
    private String apiKey;

    @Value("${fineract.integrations.ingestion.commands}")
    private String commands;

    private Set<String> reportedCommands;
    private OkHttpClient httpClient;
    // overflow drops the event: alerting must never hold up a request or a job
    private ThreadPoolExecutor executor;

    @PostConstruct
    public void init() {
        this.reportedCommands = Arrays.stream(commands.split(",")).map(String::trim).filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        this.httpClient = new OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build();
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000),
                new ThreadPoolExecutor.DiscardPolicy());
    }

    @PreDestroy
    public void shutdown() {
        this.executor.shutdown();
    }

    public void commandFailed(CommandWrapper wrapper, JsonCommand command, Throwable error) {
        if (!isEnabled() || !reportedCommands.contains(wrapper.entityName() + ":" + wrapper.actionName())) {
            return;
        }
        try {
            JsonObject payload = context();
            payload.addProperty("entity", wrapper.entityName());
            payload.addProperty("action", wrapper.actionName());
            payload.addProperty("href", wrapper.getHref());
            payload.addProperty("entityId", command.entityId());
            payload.add("request", requestFields(wrapper.entityName(), command.json()));
            addError(payload, error);
            send(wrapper.entityName() + "." + wrapper.actionName(), messageOf(error), payload);
        } catch (Exception e) {
            log.warn("Failed command {} {} not reported to ingestion-service: {}", wrapper.entityName(), wrapper.actionName(),
                    e.getMessage());
        }
    }

    public void jobFailed(String jobName, Throwable error) {
        if (!isEnabled()) {
            return;
        }
        try {
            JsonObject payload = context();
            payload.addProperty("job", jobName);
            addError(payload, error);
            send("JOB." + jobName, messageOf(error), payload);
        } catch (Exception e) {
            log.warn("Failed job {} not reported to ingestion-service: {}", jobName, e.getMessage());
        }
    }

    private boolean isEnabled() {
        return enabled && StringUtils.isNotBlank(baseUrl) && StringUtils.isNotBlank(apiKey);
    }

    // tenant and user live in thread locals, so they're read here before the hop to the executor
    private JsonObject context() {
        JsonObject payload = new JsonObject();
        FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        payload.addProperty("tenant", tenant == null ? null : tenant.getTenantIdentifier());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        payload.addProperty("user", auth == null ? null : auth.getName());
        return payload;
    }

    private JsonObject requestFields(String entityName, String json) {
        JsonObject fields = new JsonObject();
        List<String> allowed = REQUEST_FIELDS.get(entityName);
        if (allowed == null || json == null || !json.trim().startsWith("{")) {
            return fields;
        }
        JsonObject request = JsonParser.parseString(json).getAsJsonObject();
        for (String field : allowed) {
            JsonElement value = request.get(field);
            if (value != null && !value.isJsonNull()) {
                fields.add(field, value);
            }
        }
        return fields;
    }

    private void addError(JsonObject payload, Throwable error) {
        payload.addProperty("exception", error.getClass().getName());
        if (error instanceof AbstractPlatformException platformError) {
            payload.addProperty("errorCode", platformError.getGlobalisationMessageCode());
        }
        // validation errors list the failing parameters, without the submitted values
        if (error instanceof PlatformApiDataValidationException validationError && validationError.getErrors() != null) {
            JsonArray errors = new JsonArray();
            validationError.getErrors().forEach(e -> {
                JsonObject item = new JsonObject();
                item.addProperty("parameter", e.getParameterName());
                item.addProperty("code", e.getUserMessageGlobalisationCode());
                errors.add(item);
            });
            payload.add("errors", errors);
        }
        // a business rule failure explains itself; an unexpected one needs to show where it broke
        if (!(error instanceof AbstractPlatformException)) {
            Throwable root = rootCause(error);
            payload.addProperty("rootCause", root.toString());
            JsonArray frames = new JsonArray();
            Arrays.stream(root.getStackTrace()).filter(frame -> frame.getClassName().startsWith("org.apache.fineract"))
                    .limit(MAX_STACK_FRAMES).forEach(frame -> frames.add(frame.toString()));
            payload.add("stackTrace", frames);
        }
    }

    private static String messageOf(Throwable error) {
        if (error instanceof PlatformApiDataValidationException validationError && validationError.getErrors() != null
                && !validationError.getErrors().isEmpty()) {
            return validationError.getErrors().get(0).getDefaultUserMessage();
        }
        if (error instanceof AbstractPlatformException platformError && StringUtils.isNotBlank(platformError.getDefaultUserMessage())) {
            return platformError.getDefaultUserMessage();
        }
        Throwable root = rootCause(error);
        return StringUtils.defaultIfBlank(root.getMessage(), root.getClass().getName());
    }

    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private void send(String eventType, String message, JsonObject payload) {
        JsonObject body = new JsonObject();
        body.addProperty("source", SOURCE);
        body.addProperty("level", "ERROR");
        body.addProperty("eventType", eventType);
        body.addProperty("message", StringUtils.abbreviate(message, 2000));
        body.add("payload", payload);
        executor.execute(() -> post(eventType, body.toString()));
    }

    private void post(String eventType, String body) {
        Request request = new Request.Builder().url(StringUtils.removeEnd(baseUrl, "/") + "/api/v1/logs")
                .post(RequestBody.create(MediaType.parse("application/json"), body)).addHeader("Authorization", "Bearer " + apiKey).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("ingestion-service rejected {} with HTTP {}", eventType, response.code());
            }
        } catch (Exception e) {
            log.warn("{} not sent to ingestion-service: {}", eventType, e.getMessage());
        }
    }
}
