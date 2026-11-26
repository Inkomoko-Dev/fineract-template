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

import com.google.common.base.Splitter;
import java.util.Locale;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData;

/** Stable reason tags for the exception queue (CGLT-849 FR-01). */
public final class CreditDecisionReasonCodes {

    public static final String ENGINE_TIMEOUT = "ENGINE_TIMEOUT";
    public static final String ENGINE_UNAVAILABLE = "ENGINE_UNAVAILABLE";
    public static final String PAYLOAD_VALIDATION = "PAYLOAD_VALIDATION";
    public static final String MISSING_KYC = "MISSING_KYC";
    public static final String BUREAU_MISSING = "BUREAU_MISSING";
    public static final String POLICY_HOLD = "POLICY_HOLD";
    public static final String SEND_STUCK = "SEND_STUCK";
    public static final String KILL_SWITCH = "KILL_SWITCH";
    public static final String EXCEPTION_OVERRIDE_MANUAL_IC = "EXCEPTION_OVERRIDE_MANUAL_IC";
    public static final String UNKNOWN = "UNKNOWN";

    private CreditDecisionReasonCodes() {}

    public static String forException(final String kind, final String message) {
        if (CreditDecisionLoanData.EXCEPTION_STUCK.equals(kind)) {
            return SEND_STUCK;
        }
        final String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (text.contains("timeout") || text.contains("timed out")) {
            return ENGINE_TIMEOUT;
        }
        if (text.contains("circuit") || text.contains("could not be reached") || text.contains("not connected")
                || text.contains("unavailable") || text.contains("connection refused") || text.contains("http 5")) {
            return ENGINE_UNAVAILABLE;
        }
        if (text.contains("validation") || text.contains("invalid") || text.contains("refused the request")
                || text.contains("http 4")) {
            return PAYLOAD_VALIDATION;
        }
        if (text.contains("kyc") || text.contains("identity") || text.contains("national id")) {
            return MISSING_KYC;
        }
        if (text.contains("bureau") || text.contains("crb") || text.contains("credit report")) {
            return BUREAU_MISSING;
        }
        if (text.contains("missing") || text.contains("held") || text.contains("verify") || text.contains("not sent")) {
            return POLICY_HOLD;
        }
        if (text.contains("kill switch")) {
            return KILL_SWITCH;
        }
        return UNKNOWN;
    }

    /** True when {@code codes} contains the tag as a comma-separated token. */
    public static boolean contains(final String codes, final String tag) {
        if (codes == null || tag == null || tag.isBlank()) {
            return false;
        }
        for (final String part : Splitter.on(',').trimResults().omitEmptyStrings().split(codes)) {
            if (tag.equalsIgnoreCase(part)) {
                return true;
            }
        }
        return false;
    }
}
