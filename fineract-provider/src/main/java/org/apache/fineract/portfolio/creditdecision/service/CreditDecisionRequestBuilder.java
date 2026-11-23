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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionCatalogue;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ParamData;

/**
 * Chooses which values go into the request for one application, following the profile. Nothing here scores or weighs
 * anything: a switched-on parameter is either sent, sent as "not available", left out, or it holds the request back.
 */
public final class CreditDecisionRequestBuilder {

    private CreditDecisionRequestBuilder() {}

    @Getter
    public static final class Built {

        /** Switched on, no value, and the profile says to hold the request. */
        private final List<String> held = new ArrayList<>();
        /** Sent as null. */
        private final List<String> nulls = new ArrayList<>();
        /** Left out of the request. */
        private final List<String> omitted = new ArrayList<>();
        /** Field name to value, in the order of the profile. */
        private final Map<String, Object> parameters = new LinkedHashMap<>();
    }

    public static Built build(final List<ParamData> params, final Map<String, Object> values) {
        final Built built = new Built();
        for (final ParamData param : params) {
            if (!param.isEnabled()) {
                continue;
            }
            final Object value = values.get(param.getKey());
            if (isMissing(value)) {
                final String policy = param.getMissingPolicy();
                if (CreditDecisionCatalogue.MISSING_BLOCK.equals(policy)) {
                    built.held.add(param.getKey());
                } else if (CreditDecisionCatalogue.MISSING_NULL.equals(policy)) {
                    built.parameters.put(param.getFieldName(), null);
                    built.nulls.add(param.getKey());
                } else {
                    built.omitted.add(param.getKey());
                }
            } else {
                built.parameters.put(param.getFieldName(), value);
            }
        }
        return built;
    }

    /** Zero and false are real values. Only a missing or empty one counts as missing. */
    static boolean isMissing(final Object value) {
        return value == null || (value instanceof String && ((String) value).isBlank());
    }
}
