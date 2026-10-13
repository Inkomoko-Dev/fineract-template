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
package org.apache.fineract.accounting.provisioning.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the company/country a provision GL belongs to.
 *
 * <p>Mapped GL codes are stored as {@code {glCode}-{accountId}-{company}_{category}},
 * for example {@code 2601-3591-Rwanda Capital_Watch/Special Mention-loan loss allowance}.
 * CBS uses the company token in that string so one journal is sent per company
 * (Rwanda Capital, Chad, Kenya Capital, …).</p>
 */
final class ProvisionPostingCompany {

    private static final Pattern MAPPED_GL = Pattern.compile("^(\\d+)-(\\d+)-(.+)$");
    private static final Pattern CATEGORY_SUFFIX = Pattern.compile(
            "(?i)[_ /]+(?:Watch|Loss|Doubtful|Substandard|Collective|Special)\\b.*$");
    private static final String ETHIOPIA = "ethiopia";

    private ProvisionPostingCompany() {}

    static String fromGlAccountCode(final String glAccountCode, final Long officeId) {
        if (glAccountCode != null && !glAccountCode.isBlank()) {
            final Matcher mapped = MAPPED_GL.matcher(glAccountCode.trim());
            if (mapped.matches()) {
                final String company = CATEGORY_SUFFIX.matcher(mapped.group(3).trim()).replaceFirst("").trim();
                if (!company.isEmpty()) {
                    return company;
                }
            }
        }
        return "OFFICE-" + officeId;
    }

    static boolean isExcluded(final String companyKey) {
        return companyKey != null && companyKey.toLowerCase(Locale.ROOT).contains(ETHIOPIA);
    }

    static String groupingKey(final String companyKey, final String currencyCode) {
        return companyKey + "::" + currencyCode;
    }

    static String slug(final String companyKey) {
        if (companyKey == null || companyKey.isBlank()) {
            return "UNKNOWN";
        }
        final String slug = companyKey.replaceAll("[^A-Za-z0-9]+", "");
        return slug.isEmpty() ? "UNKNOWN" : slug;
    }
}
