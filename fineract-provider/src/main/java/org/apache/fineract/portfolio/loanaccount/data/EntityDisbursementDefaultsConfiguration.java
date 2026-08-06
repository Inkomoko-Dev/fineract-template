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
package org.apache.fineract.portfolio.loanaccount.data;

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Configuration for entity-specific disbursement defaults.
 * Each entity (e.g., Kenya Capital, Rwanda Capital) can have its own configuration.
 */
@Data
public class EntityDisbursementDefaultsConfiguration {

    private String entityName;
    private List<String> officeNames;
    private String defaultDepartmentName;
    private String budgetCodeName;
    private String budgetLocationPrefix;

    /**
     * Parse office names from comma-separated string.
     */
    public static List<String> parseOfficeNames(final String officeNamesString) {
        if (officeNamesString == null || officeNamesString.trim().isEmpty()) {
            return new ArrayList<>();
        }
        final List<String> names = new ArrayList<>();
        for (final String part : Splitter.on(',').trimResults().omitEmptyStrings().split(officeNamesString)) {
            names.add(part);
        }
        return names;
    }

    /**
     * Check if the given office name matches any of the configured office names for this entity.
     */
    public boolean matchesOfficeName(final String officeName) {
        if (officeNames == null || officeNames.isEmpty() || officeName == null) {
            return false;
        }
        final String normalizedOffice = officeName.trim();
        for (final String candidate : officeNames) {
            // Exact match, or office name contains the full configured entity office name.
            // Do NOT match the reverse (e.g. "Inkomoko Kenya" must not match "Inkomoko Kenya Capital").
            if (candidate.equalsIgnoreCase(normalizedOffice) || 
                (normalizedOffice.length() >= candidate.length() && normalizedOffice.toLowerCase().contains(candidate.toLowerCase()))) {
                return true;
            }
        }
        return false;
    }
}