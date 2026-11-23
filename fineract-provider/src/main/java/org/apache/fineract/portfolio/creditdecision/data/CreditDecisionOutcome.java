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
package org.apache.fineract.portfolio.creditdecision.data;

import java.util.List;
import java.util.Locale;

/**
 * What the decision engine can direct the core banking system to do with an application. These are the directives in
 * the Credit Scoring Process Flow (Option A): AUTO_APPROVE bypasses IC review and goes to the contract, IC_REVIEW_REQUIRED
 * goes through the IC levels, INELIGIBLE is rejected.
 */
public final class CreditDecisionOutcome {

    public static final String AUTO_APPROVE = "AUTO_APPROVE";
    public static final String IC_REVIEW_REQUIRED = "IC_REVIEW_REQUIRED";
    public static final String INELIGIBLE = "INELIGIBLE";

    public static final List<String> ALL = List.of(AUTO_APPROVE, IC_REVIEW_REQUIRED, INELIGIBLE);

    private CreditDecisionOutcome() {}

    /**
     * The directive for whatever spelling an engine used, or null when it is not one of them. The first draft of the contract
     * used GO, REVIEW and NO-GO, so those are still understood.
     */
    public static String normalise(final String raw) {
        if (raw == null) {
            return null;
        }
        final String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        switch (value) {
            case "AUTO_APPROVE":
            case "AUTOAPPROVE":
            case "GO":
                return AUTO_APPROVE;
            case "IC_REVIEW_REQUIRED":
            case "ICREVIEWREQUIRED":
            case "REVIEW":
                return IC_REVIEW_REQUIRED;
            case "INELIGIBLE":
            case "NO_GO":
            case "NOGO":
                return INELIGIBLE;
            default:
                return null;
        }
    }
}
