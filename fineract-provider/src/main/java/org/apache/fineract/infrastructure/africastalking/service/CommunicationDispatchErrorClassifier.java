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

import org.apache.commons.lang3.StringUtils;

public final class CommunicationDispatchErrorClassifier {

    public enum ErrorCategory {

        INVALID_NUMBER, TEMPLATE_MISMATCH, AUTH_CONFIG, TRANSIENT, UNKNOWN
    }

    public record Classification(ErrorCategory category, boolean retryable, String code) {}

    private CommunicationDispatchErrorClassifier() {}

    public static Classification classify(final int statusCode, final String responseBody) {
        final String body = StringUtils.defaultString(responseBody).toLowerCase();

        if (statusCode == 401 || statusCode == 403 || body.contains("unauthorized") || body.contains("apikey")
                || body.contains("api key") || body.contains("authentication")) {
            return new Classification(ErrorCategory.AUTH_CONFIG, false, "AUTH_CONFIG");
        }

        if (body.contains("invalid phone") || body.contains("invalidphonenumber") || body.contains("invalid number")
                || body.contains("phone number is invalid") || body.contains("not a valid phone")) {
            return new Classification(ErrorCategory.INVALID_NUMBER, false, "INVALID_NUMBER");
        }

        if (body.contains("template") && (body.contains("mismatch") || body.contains("invalid") || body.contains("not found")
                || body.contains("bodyvalues") || body.contains("language"))) {
            return new Classification(ErrorCategory.TEMPLATE_MISMATCH, false, "TEMPLATE_MISMATCH");
        }

        if (statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504 || statusCode >= 500
                || body.contains("timeout") || body.contains("temporarily")) {
            return new Classification(ErrorCategory.TRANSIENT, true, "TRANSIENT");
        }

        if (statusCode >= 400) {
            return new Classification(ErrorCategory.UNKNOWN, false, "HTTP_" + statusCode);
        }

        return new Classification(ErrorCategory.UNKNOWN, false, "UNKNOWN");
    }

    public static Classification classifyException(final Exception exception) {
        if (exception instanceof java.io.IOException) {
            return new Classification(ErrorCategory.TRANSIENT, true, "IO_ERROR");
        }
        return new Classification(ErrorCategory.UNKNOWN, false, "EXCEPTION");
    }
}
