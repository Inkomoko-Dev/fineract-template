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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhoneNumberNormalizerTest {

    private PhoneNumberNormalizer normalizer;

    @BeforeEach
    void setUp() {
        final AfricasTalkingProperties properties = new AfricasTalkingProperties();
        properties.getPhone().setDefaultCountryCode("254");
        normalizer = new PhoneNumberNormalizer(properties);
    }

    @Test
    void normalizesLocalKenyanNumber() {
        assertEquals("+254712345678", normalizer.normalize("0712345678"));
    }

    @Test
    void normalizesInternationalNumber() {
        assertEquals("+254712345678", normalizer.normalize("+254712345678"));
    }

    @Test
    void lookupVariantsIncludeLocalAndInternationalForms() {
        final var variants = normalizer.lookupVariants("0712345678");
        assertTrue(variants.contains("0712345678"));
        assertTrue(variants.contains("+254712345678"));
    }
}
