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
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

final class AfricasTalkingPayloadParser {

    private AfricasTalkingPayloadParser() {}

    static Map<String, String> toMap(final String rawPayload) {
        if (StringUtils.isBlank(rawPayload)) {
            return Map.of();
        }
        final String trimmed = rawPayload.trim();
        if (trimmed.startsWith("{")) {
            final JsonObject object = JsonParser.parseString(trimmed).getAsJsonObject();
            return object.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> asString(entry.getValue())));
        }
        if (trimmed.contains("=")) {
            return Arrays.stream(trimmed.split("&")).map(part -> part.split("=", 2)).filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));
        }
        return Map.of("message", trimmed);
    }

    static String firstNonBlank(final Map<String, String> values, final String... keys) {
        for (final String key : keys) {
            final String value = values.get(key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String asString(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }
}
