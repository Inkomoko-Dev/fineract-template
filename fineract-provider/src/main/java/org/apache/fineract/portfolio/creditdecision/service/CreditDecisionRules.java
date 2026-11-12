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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionCatalogue;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CatalogueItem;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ParamData;

/**
 * The rules a profile version must satisfy. They concern only which data is sent; nothing here scores or decides.
 */
public final class CreditDecisionRules {

    static final Pattern FIELD_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private CreditDecisionRules() {}

    /**
     * Returns one entry per catalogue item, in catalogue order, with the caller's choices applied and any item the
     * caller left out filled with its default (switched off). Unknown keys are reported by {@link #validateParams}.
     */
    public static List<ParamData> complete(final List<ParamData> given) {
        final Map<String, ParamData> byKey = new HashMap<>();
        if (given != null) {
            for (final ParamData p : given) {
                if (p != null && p.getKey() != null) {
                    byKey.put(p.getKey(), p);
                }
            }
        }
        final List<ParamData> out = new ArrayList<>();
        for (final CatalogueItem item : CreditDecisionCatalogue.items().values()) {
            final ParamData chosen = byKey.get(item.getKey());
            if (chosen == null) {
                out.add(new ParamData(item.getKey(), false, item.getDefaultField(), item.getDefaultMissing()));
            } else {
                final String field = chosen.getFieldName() == null || chosen.getFieldName().isBlank() ? item.getDefaultField()
                        : chosen.getFieldName().trim();
                final String missing = chosen.getMissingPolicy() == null || chosen.getMissingPolicy().isBlank() ? item.getDefaultMissing()
                        : chosen.getMissingPolicy().trim().toLowerCase();
                out.add(new ParamData(item.getKey(), chosen.isEnabled(), field, missing));
            }
        }
        return out;
    }

    /** Problems that make even a draft unsaveable: unknown parameters, bad policies. */
    public static List<ApiParameterError> validateParams(final List<ParamData> given) {
        final List<ApiParameterError> errors = new ArrayList<>();
        if (given == null) {
            return errors;
        }
        for (final ParamData p : given) {
            if (p == null || p.getKey() == null || !CreditDecisionCatalogue.isKnown(p.getKey())) {
                final String key = p == null ? "null" : String.valueOf(p.getKey());
                errors.add(ApiParameterError.parameterError("validation.msg.creditdecision.param.unknown",
                        "'" + key + "' is not in the data catalogue", "params", key));
                continue;
            }
            final String policy = p.getMissingPolicy() == null || p.getMissingPolicy().isBlank() ? null
                    : p.getMissingPolicy().trim().toLowerCase();
            if (policy != null && !CreditDecisionCatalogue.MISSING_POLICIES.contains(policy)) {
                errors.add(ApiParameterError.parameterError("validation.msg.creditdecision.param.missingPolicy.invalid",
                        "Choose hold, send as not available, or leave out for " + p.getKey(), p.getKey(), p.getMissingPolicy()));
            }
        }
        return errors;
    }

    /**
     * What must be true before a version can be submitted for approval. Keyed by parameter key (or
     * "products"/"reason").
     */
    public static Map<String, String> submitProblems(final List<ParamData> completed, final List<Long> productIds, final String reason) {
        final Map<String, String> problems = new LinkedHashMap<>();
        final Map<String, String> seen = new HashMap<>();
        int enabled = 0;
        for (final ParamData p : completed) {
            if (!p.isEnabled()) {
                continue;
            }
            enabled++;
            if (p.getFieldName() == null || !FIELD_NAME.matcher(p.getFieldName()).matches()) {
                problems.put(p.getKey(), "Use letters, numbers and underscores, starting with a letter");
            } else if (seen.containsKey(p.getFieldName())) {
                problems.put(p.getKey(), "Already used by another parameter");
                problems.put(seen.get(p.getFieldName()), "Already used by another parameter");
            } else {
                seen.put(p.getFieldName(), p.getKey());
            }
        }
        if (enabled == 0) {
            problems.put("params", "Switch on at least one parameter to submit");
        }
        if (productIds == null || productIds.isEmpty()) {
            problems.put("products", "Choose at least one product to submit");
        }
        if (reason == null || reason.isBlank()) {
            problems.put("reason", "Say why this change is needed");
        }
        return problems;
    }

    public static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    public static void throwIfAny(final Map<String, String> problems) {
        if (problems.isEmpty()) {
            return;
        }
        final List<ApiParameterError> errors = new ArrayList<>();
        problems.forEach((param, message) -> errors
                .add(ApiParameterError.parameterError("validation.msg.creditdecision." + param + ".invalid", message, param)));
        throw new PlatformApiDataValidationException(errors);
    }
}
