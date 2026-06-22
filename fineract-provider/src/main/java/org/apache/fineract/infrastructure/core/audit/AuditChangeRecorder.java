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
package org.apache.fineract.infrastructure.core.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Records configuration audit changes in a consistent before/after shape for
 * persistence in {@code m_portfolio_command_source.command_as_json}.
 */
public final class AuditChangeRecorder {

    private AuditChangeRecorder() {
    }

    public static void recordChange(final Map<String, Object> changes, final String field, final Object before, final Object after) {
        if (!Objects.equals(before, after)) {
            changes.put(field, beforeAfter(before, after));
        }
    }

    public static void recordNestedChange(final Map<String, Object> changes, final String parent, final String child, final Object before,
            final Object after) {
        if (!Objects.equals(before, after)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) changes.computeIfAbsent(parent, key -> new LinkedHashMap<>());
            nested.put(child, beforeAfter(before, after));
        }
    }

    public static Map<String, Object> beforeAfter(final Object before, final Object after) {
        final Map<String, Object> entry = new LinkedHashMap<>(2);
        entry.put("before", before);
        entry.put("after", after);
        return entry;
    }
}
