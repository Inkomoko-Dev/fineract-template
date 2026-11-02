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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;

/** Flattens Fineract validation exceptions into a single user-facing failure string. */
public final class BulkRescheduleExceptionMessages {

    private BulkRescheduleExceptionMessages() {}

    public static String describe(final Throwable failure) {
        return describe(null, failure);
    }

    public static String describe(final String prefix, final Throwable failure) {
        final String details = details(failure);
        if (details == null || details.isBlank()) {
            final String fallback = failure == null ? "Unknown error" : failure.getClass().getSimpleName();
            return prefix == null || prefix.isBlank() ? fallback : prefix + ": " + fallback;
        }
        if (prefix == null || prefix.isBlank() || details.startsWith(prefix)) {
            return details;
        }
        return prefix + ": " + details;
    }

    private static String details(final Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof PlatformApiDataValidationException validation) {
                final String joined = joinValidationErrors(validation);
                if (!joined.isBlank()) {
                    return joined;
                }
                if (validation.getDefaultUserMessage() != null && !validation.getDefaultUserMessage().isBlank()) {
                    return validation.getDefaultUserMessage();
                }
            } else if (current instanceof AbstractPlatformException platform) {
                if (platform.getDefaultUserMessage() != null && !platform.getDefaultUserMessage().isBlank()) {
                    return platform.getDefaultUserMessage();
                }
            }
        }
        return failure == null ? null : failure.getMessage();
    }

    private static String joinValidationErrors(final PlatformApiDataValidationException validation) {
        if (validation.getErrors() == null || validation.getErrors().isEmpty()) {
            return "";
        }
        final Set<String> unique = new LinkedHashSet<>();
        for (final ApiParameterError error : validation.getErrors()) {
            if (error == null) {
                continue;
            }
            String message = error.getDefaultUserMessage();
            if (message == null || message.isBlank()) {
                message = error.getDeveloperMessage();
            }
            if (message == null || message.isBlank() || "Validation errors exist.".equalsIgnoreCase(message.trim())) {
                continue;
            }
            final String parameterName = error.getParameterName();
            if (parameterName != null && !parameterName.isBlank() && !"id".equals(parameterName)
                    && !message.startsWith(parameterName + ":")) {
                unique.add(parameterName + ": " + message);
            } else {
                unique.add(message);
            }
        }
        return String.join("; ", unique);
    }
}
