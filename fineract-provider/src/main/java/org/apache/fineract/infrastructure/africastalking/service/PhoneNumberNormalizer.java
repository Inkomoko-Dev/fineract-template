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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {

    private final AfricasTalkingProperties properties;

    public PhoneNumberNormalizer(final AfricasTalkingProperties properties) {
        this.properties = properties;
    }

    public String normalize(final String rawPhoneNumber) {
        if (StringUtils.isBlank(rawPhoneNumber)) {
            return null;
        }
        String digits = rawPhoneNumber.trim().replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("00")) {
            return "+" + digits.substring(2);
        }
        final String countryCode = properties.getPhone().getDefaultCountryCode();
        if (digits.startsWith(countryCode)) {
            return "+" + digits;
        }
        if (digits.startsWith("0")) {
            return "+" + countryCode + digits.substring(1);
        }
        return "+" + countryCode + digits;
    }

    public List<String> lookupVariants(final String rawPhoneNumber) {
        final Set<String> variants = new LinkedHashSet<>();
        if (StringUtils.isBlank(rawPhoneNumber)) {
            return List.of();
        }
        final String trimmed = rawPhoneNumber.trim();
        variants.add(trimmed);
        final String normalized = normalize(trimmed);
        if (normalized != null) {
            variants.add(normalized);
            if (normalized.startsWith("+")) {
                variants.add(normalized.substring(1));
            }
            final String countryCode = properties.getPhone().getDefaultCountryCode();
            if (normalized.startsWith("+" + countryCode)) {
                variants.add("0" + normalized.substring(countryCode.length() + 1));
            }
        }
        return new ArrayList<>(variants);
    }
}
