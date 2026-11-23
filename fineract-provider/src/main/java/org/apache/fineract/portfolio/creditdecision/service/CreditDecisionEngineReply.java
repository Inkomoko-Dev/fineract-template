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
import lombok.Getter;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionOutcome;

/**
 * Reads what the decision engine replied. The engine owns the decision: this only picks the recommendation, score, risk
 * tier, maximum exposure, model version and reason codes out of the reply so they can be shown. Field names follow the
 * Credit Scoring Process Flow (recommendation, riskTier, maxAllowedExposure, modelVersion, scoringStatus, reasonCodes,
 * eligibilityResults.failedRules). The first draft's names (decision, outcome, result, reasons, engine) are still read.
 */
@Getter
public final class CreditDecisionEngineReply {

    /** The only directives the screens know how to act on and show. */
    public static final List<String> DECISIONS = CreditDecisionOutcome.ALL;

    private final String decision;
    private final BigDecimal score;
    private final String reasonCodes;
    private final String riskTier;
    private final BigDecimal maxExposure;
    private final String modelVersion;
    /** Why the reply could not be used, when it could not. */
    private final String problem;

    private CreditDecisionEngineReply(final String decision, final BigDecimal score, final String reasonCodes, final String riskTier,
            final BigDecimal maxExposure, final String modelVersion, final String problem) {
        this.decision = decision;
        this.score = score;
        this.reasonCodes = reasonCodes;
        this.riskTier = riskTier;
        this.maxExposure = maxExposure;
        this.modelVersion = modelVersion;
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

        final String status = text(root, "scoringStatus");
        if (status != null && !"COMPLETED".equalsIgnoreCase(status.trim())) {
            return unusable("The engine did not finish scoring (scoring status " + status.trim() + ")");
        }
        final String rawDecision = text(root, "recommendation", "decision", "outcome", "result");
        if (rawDecision == null) {
            return unusable("The engine reply has no recommendation");
        }
        final String decision = CreditDecisionOutcome.normalise(rawDecision);
        if (decision == null) {
            return unusable("The engine returned the recommendation '" + rawDecision
                    + "'. Expected AUTO_APPROVE, IC_REVIEW_REQUIRED or INELIGIBLE");
        }
        final String tier = text(root, "riskTier");
        final String model = text(root, "modelVersion", "engine");
        return new CreditDecisionEngineReply(decision, number(root, "score"), reasons(root), tier == null ? null : cut(tier, 30),
                number(root, "maxAllowedExposure"), model == null ? null : cut(model, 100), null);
    }

    private static CreditDecisionEngineReply unusable(final String problem) {
        return new CreditDecisionEngineReply(null, null, null, null, null, null, problem);
    }

    private static String cut(final String text, final int max) {
        final String trimmed = text.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
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
        // an ineligible reply may list only the knockout rules that failed
        final JsonElement eligibility = root.get("eligibilityResults");
        if (eligibility != null && eligibility.isJsonObject()) {
            final JsonElement failed = eligibility.getAsJsonObject().get("failedRules");
            if (failed != null && failed.isJsonArray()) {
                final List<String> out = new ArrayList<>();
                for (final JsonElement item : failed.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) {
                        out.add(item.getAsString());
                    }
                }
                if (!out.isEmpty()) {
                    final String joined = String.join(", ", out);
                    return joined.length() > 500 ? joined.substring(0, 500) : joined;
                }
            }
        }
        return null;
    }
}
