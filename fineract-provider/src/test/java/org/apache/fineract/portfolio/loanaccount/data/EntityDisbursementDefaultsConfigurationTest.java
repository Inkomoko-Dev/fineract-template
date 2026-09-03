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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EntityDisbursementDefaultsConfigurationTest {

    @Test
    void parseOfficeNamesHandlesCommaSeparatedList() {
        final String input = "Inkomoko - Capital Kenya Limited, Kenya Capital Branch, Nairobi HQ";
        final List<String> result = EntityDisbursementDefaultsConfiguration.parseOfficeNames(input);
        
        assertEquals(3, result.size());
        assertTrue(result.contains("Inkomoko - Capital Kenya Limited"));
        assertTrue(result.contains("Kenya Capital Branch"));
        assertTrue(result.contains("Nairobi HQ"));
    }

    @Test
    void parseOfficeNamesHandlesWhitespace() {
        final String input = " Inkomoko - Capital Kenya Limited , Kenya Capital Branch ";
        final List<String> result = EntityDisbursementDefaultsConfiguration.parseOfficeNames(input);
        
        assertEquals(2, result.size());
        assertEquals("Inkomoko - Capital Kenya Limited", result.get(0));
        assertEquals("Kenya Capital Branch", result.get(1));
    }

    @Test
    void parseOfficeNamesHandlesEmptyString() {
        final List<String> result = EntityDisbursementDefaultsConfiguration.parseOfficeNames("");
        assertTrue(result.isEmpty());
    }

    @Test
    void parseOfficeNamesHandlesNull() {
        final List<String> result = EntityDisbursementDefaultsConfiguration.parseOfficeNames(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void matchesOfficeNameExactMatch() {
        final EntityDisbursementDefaultsConfiguration config = new EntityDisbursementDefaultsConfiguration();
        config.setOfficeNames(List.of("Inkomoko - Capital Kenya Limited"));
        
        assertTrue(config.matchesOfficeName("Inkomoko - Capital Kenya Limited"));
    }

    @Test
    void matchesOfficeNameCaseInsensitive() {
        final EntityDisbursementDefaultsConfiguration config = new EntityDisbursementDefaultsConfiguration();
        config.setOfficeNames(List.of("Inkomoko - Capital Kenya Limited"));
        
        assertTrue(config.matchesOfficeName("inkomoko kenya capital"));
        assertTrue(config.matchesOfficeName("INKOMOKO KENYA CAPITAL"));
    }

    @Test
    void matchesOfficeNamePartialMatch() {
        final EntityDisbursementDefaultsConfiguration config = new EntityDisbursementDefaultsConfiguration();
        config.setOfficeNames(List.of("Inkomoko - Capital Kenya Limited"));
        
        assertTrue(config.matchesOfficeName("Inkomoko - Capital Kenya Limited - Branch"));
    }

    @Test
    void matchesOfficeNameDoesNotMatchReverse() {
        final EntityDisbursementDefaultsConfiguration config = new EntityDisbursementDefaultsConfiguration();
        config.setOfficeNames(List.of("Inkomoko - Capital Kenya Limited"));
        
        assertFalse(config.matchesOfficeName("Inkomoko Kenya"));
    }

    @Test
    void matchesOfficeNameMultipleOfficeNames() {
        final EntityDisbursementDefaultsConfiguration config = new EntityDisbursementDefaultsConfiguration();
        config.setOfficeNames(List.of("Inkomoko - Capital Kenya Limited", "Kenya Capital Branch", "Nairobi HQ"));
        
        assertTrue(config.matchesOfficeName("Inkomoko - Capital Kenya Limited"));
        assertTrue(config.matchesOfficeName("Kenya Capital Branch"));
        assertTrue(config.matchesOfficeName("Nairobi HQ"));
        assertFalse(config.matchesOfficeName("Mombasa Branch"));
    }

    @Test
    void matchesOfficeNameHandlesNullOfficeName() {
        final EntityDisbursementDefaultsConfiguration config = new EntityDisbursementDefaultsConfiguration();
        config.setOfficeNames(List.of("Inkomoko - Capital Kenya Limited"));
        
        assertFalse(config.matchesOfficeName(null));
    }

    @Test
    void matchesOfficeNameHandlesEmptyOfficeNames() {
        final EntityDisbursementDefaultsConfiguration config = new EntityDisbursementDefaultsConfiguration();
        config.setOfficeNames(List.of());
        
        assertFalse(config.matchesOfficeName("Inkomoko - Capital Kenya Limited"));
    }
}