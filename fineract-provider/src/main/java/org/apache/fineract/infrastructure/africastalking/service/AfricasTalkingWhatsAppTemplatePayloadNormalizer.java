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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Aligns create-template JSON with Africa's Talking docs:
 * {@code example.body_text} must be one sample string per {@code {{n}}} placeholder.
 */
public final class AfricasTalkingWhatsAppTemplatePayloadNormalizer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\d+)\\s*\\}\\}");
    private static final String[] DEFAULT_SAMPLES = { "Jane Doe", "1500.00", "000012345", "8500.00", "Sample5", "Sample6", "Sample7",
            "Sample8" };

    private AfricasTalkingWhatsAppTemplatePayloadNormalizer() {}

    public static String normalize(final String apiRequestBodyAsJson) {
        if (StringUtils.isBlank(apiRequestBodyAsJson)) {
            return apiRequestBodyAsJson;
        }
        final JsonElement parsed = JsonParser.parseString(apiRequestBodyAsJson);
        if (parsed == null || !parsed.isJsonObject()) {
            return apiRequestBodyAsJson;
        }
        final JsonObject root = parsed.getAsJsonObject();
        if (!root.has("components") || !root.get("components").isJsonObject()) {
            return root.toString();
        }
        final JsonObject components = root.getAsJsonObject("components");
        normalizeHeaderExample(components);
        normalizeBodyExample(components);
        return root.toString();
    }

    private static void normalizeHeaderExample(final JsonObject components) {
        if (!components.has("header") || !components.get("header").isJsonObject()) {
            return;
        }
        final JsonObject header = components.getAsJsonObject("header");
        final String text = header.has("text") && !header.get("text").isJsonNull() ? header.get("text").getAsString() : "";
        final int count = maxPlaceholderIndex(text);
        if (count <= 0) {
            header.remove("example");
            return;
        }
        // AT create_template docs use a single string for header_text when one variable is present.
        final JsonObject example = new JsonObject();
        example.addProperty("header_text", sampleAt(0));
        header.add("example", example);
    }

    private static void normalizeBodyExample(final JsonObject components) {
        if (!components.has("body") || !components.get("body").isJsonObject()) {
            return;
        }
        final JsonObject body = components.getAsJsonObject("body");
        final String text = body.has("text") && !body.get("text").isJsonNull() ? body.get("text").getAsString() : "";
        final int count = maxPlaceholderIndex(text);
        if (count <= 0) {
            body.remove("example");
            return;
        }
        final JsonArray bodyText = new JsonArray();
        for (int i = 0; i < count; i++) {
            bodyText.add(sampleAt(i));
        }
        final JsonObject example = new JsonObject();
        example.add("body_text", bodyText);
        body.add("example", example);
    }

    static int maxPlaceholderIndex(final String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        int max = 0;
        final Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max;
    }

    private static String sampleAt(final int zeroBasedIndex) {
        if (zeroBasedIndex >= 0 && zeroBasedIndex < DEFAULT_SAMPLES.length) {
            return DEFAULT_SAMPLES[zeroBasedIndex];
        }
        return "Sample" + (zeroBasedIndex + 1);
    }

    /** Exposed for unit tests. */
    static List<String> bodySamplesFor(final String bodyText) {
        final int count = maxPlaceholderIndex(bodyText);
        final List<String> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            samples.add(sampleAt(i));
        }
        return samples;
    }
}
