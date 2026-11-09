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
package org.apache.fineract.portfolio.creditdecision.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Getter;

/**
 * Reads what the decision engine replied. The engine owns the decision: this only picks the decision, score and reason
 * codes out of the reply so they can be shown. Field names are accepted in a few common spellings until the provider's
 * contract is fixed: decision/outcome/result, score, reasonCodes/reasons.
 */
@Getter
public final class CreditDecisionEngineReply {

    /** The only decisions the screens know how to show. */
    public static final List<String> DECISIONS = List.of("GO", "REVIEW", "NO-GO");

    private final String decision;
    private final BigDecimal score;
    private final String reasonCodes;
    /** Why the reply could not be used, when it could not. */
    private final String problem;

    private CreditDecisionEngineReply(final String decision, final BigDecimal score, final String reasonCodes, final String problem) {
        this.decision = decision;
        this.score = score;
        this.reasonCodes = reasonCodes;
        this.problem = problem;
    }

    public boolean usable() {
        return decision != null;
    }

    public static CreditDecisionEngineReply parse(final String body) {
        final JsonObject root;
        try {
            final JsonElement element = JsonParser.parseString(body == null ? "" : body);
            if (!element.isJsonObject()) {
                return unusable("The engine reply is not a JSON object");
            }
            root = element.getAsJsonObject();
        } catch (final RuntimeException e) {
            return unusable("The engine reply is not valid JSON");
        }

        final String rawDecision = text(root, "decision", "outcome", "result");
        if (rawDecision == null) {
            return unusable("The engine reply has no decision");
        }
        final String decision = normalise(rawDecision);
        if (!DECISIONS.contains(decision)) {
            return unusable("The engine returned the decision '" + rawDecision + "'. Expected GO, REVIEW or NO-GO");
        }
        return new CreditDecisionEngineReply(decision, number(root, "score"), reasons(root), null);
    }

    static String normalise(final String raw) {
        final String value = raw.trim().toUpperCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        return "NOGO".equals(value) ? "NO-GO" : value;
    }

    private static CreditDecisionEngineReply unusable(final String problem) {
        return new CreditDecisionEngineReply(null, null, null, problem);
    }

    private static String text(final JsonObject root, final String... names) {
        for (final String name : names) {
            final JsonElement e = root.get(name);
            if (e != null && e.isJsonPrimitive() && !e.getAsString().isBlank()) {
                return e.getAsString();
            }
        }
        return null;
    }

    private static BigDecimal number(final JsonObject root, final String name) {
        final JsonElement e = root.get(name);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        try {
            return new BigDecimal(e.getAsString().trim());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static String reasons(final JsonObject root) {
        for (final String name : new String[] { "reasonCodes", "reasons" }) {
            final JsonElement e = root.get(name);
            if (e == null || e.isJsonNull()) {
                continue;
            }
            final List<String> out = new ArrayList<>();
            if (e.isJsonArray()) {
                final JsonArray array = (JsonArray) e;
                for (final JsonElement item : array) {
                    if (item.isJsonPrimitive()) {
                        out.add(item.getAsString());
                    }
                }
            } else if (e.isJsonPrimitive()) {
                out.add(e.getAsString());
            }
            if (!out.isEmpty()) {
                final String joined = String.join(", ", out);
                return joined.length() > 500 ? joined.substring(0, 500) : joined;
            }
        }
        return null;
    }
}
