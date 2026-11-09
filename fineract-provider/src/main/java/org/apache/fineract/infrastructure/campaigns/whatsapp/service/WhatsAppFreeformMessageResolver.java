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
package org.apache.fineract.infrastructure.campaigns.whatsapp.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@code ${columnName}} placeholders in FREEFORM WhatsApp campaign messages from a report row.
 */
public final class WhatsAppFreeformMessageResolver {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppFreeformMessageResolver.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private WhatsAppFreeformMessageResolver() {}

    /**
     * Replaces {@code ${key}} with the string value of {@code row.get(key)}. Missing or blank values become empty
     * string and are logged at warn.
     */
    public static String resolve(final String message, final Map<String, Object> row) {
        if (StringUtils.isBlank(message) || !message.contains("${")) {
            return message;
        }
        final Matcher matcher = PLACEHOLDER.matcher(message);
        final StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            final String key = matcher.group(1);
            final Object raw = row == null ? null : row.get(key);
            final String value = raw == null ? "" : String.valueOf(raw);
            if (StringUtils.isBlank(value)) {
                LOG.warn("FREEFORM WhatsApp placeholder '{}' unresolved or blank; substituting empty string", key);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
