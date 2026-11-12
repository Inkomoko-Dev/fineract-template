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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Interprets Africa's Talking WhatsApp template-create HTTP responses.
 * <p>
 * AT sometimes returns HTTP 5xx with {@code status=Failure} even when a {@code templateId} was
 * allocated and {@code templateStatus=Pending} (submitted for Meta/AT review). Treat that as
 * accepted submission so the local catalog can be updated.
 */
public final class AfricasTalkingWhatsAppTemplateCreateOutcome {

    private static final Set<String> ACCEPTED_TEMPLATE_STATUSES = Set.of("PENDING", "APPROVED", "SUBMITTED", "IN_REVIEW",
            "IN-REVIEW", "INREVIEW");

    private AfricasTalkingWhatsAppTemplateCreateOutcome() {}

    public static boolean isAccepted(final int statusCode, final String responseBody) {
        if (statusCode >= 200 && statusCode < 300) {
            return true;
        }
        final JsonObject body = tryParseObject(responseBody);
        if (body == null) {
            return false;
        }
        final String templateId = text(body, "templateId");
        final String templateStatus = text(body, "templateStatus");
        if (StringUtils.isBlank(templateId) || StringUtils.isBlank(templateStatus)) {
            return false;
        }
        return ACCEPTED_TEMPLATE_STATUSES.contains(templateStatus.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    public static String templateId(final String responseBody) {
        final JsonObject body = tryParseObject(responseBody);
        return body == null ? null : text(body, "templateId");
    }

    public static String templateStatus(final String responseBody) {
        final JsonObject body = tryParseObject(responseBody);
        return body == null ? null : text(body, "templateStatus");
    }

    private static JsonObject tryParseObject(final String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        try {
            final JsonElement parsed = JsonParser.parseString(responseBody);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    private static String text(final JsonObject body, final String field) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return null;
        }
        return StringUtils.trimToNull(body.get(field).getAsString());
    }
}
