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
package org.apache.fineract.infrastructure.dataqueries.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.organisation.office.domain.OfficeAccessScope;

/**
 * Widens the single office restriction reports carry - {@code x.hierarchy like concat('${currentUserHierarchy}', '%')} -
 * into a predicate over every office the running user is allowed to see, so a report needs no edit to respect
 * hierarchical and multi-location access.
 */
public final class OfficeScopeReportSqlRewriter {

    private static final String COLUMN = "([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)";
    private static final String PLACEHOLDER = "\\$\\{currentUserHierarchy\\}";

    private static final Pattern CONCAT_FORM = Pattern
            .compile(COLUMN + "\\s+like\\s+concat\\(\\s*'" + PLACEHOLDER + "'\\s*,\\s*'%'\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern INLINE_FORM = Pattern.compile(COLUMN + "\\s+like\\s+'" + PLACEHOLDER + "%'",
            Pattern.CASE_INSENSITIVE);

    private OfficeScopeReportSqlRewriter() {

    }

    public static String rewrite(final String sql, final OfficeAccessScope scope) {
        if (StringUtils.isBlank(sql)) {
            return sql;
        }
        return replaceAll(replaceAll(sql, CONCAT_FORM, scope), INLINE_FORM, scope);
    }

    private static String replaceAll(final String sql, final Pattern pattern, final OfficeAccessScope scope) {
        final Matcher matcher = pattern.matcher(sql);
        final StringBuilder rewritten = new StringBuilder(sql.length());
        while (matcher.find()) {
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(scope.sqlPredicate(matcher.group(1))));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }
}
