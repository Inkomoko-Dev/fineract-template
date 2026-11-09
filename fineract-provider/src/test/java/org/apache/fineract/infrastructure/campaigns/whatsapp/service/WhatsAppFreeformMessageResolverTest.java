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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsAppFreeformMessageResolverTest {

    @Test
    void resolvesMultiplePlaceholdersFromRow() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("clientName", "Lucy");
        row.put("loanBalance", "15000");
        row.put("dueDate", "2026-11-15");

        final String resolved = WhatsAppFreeformMessageResolver.resolve(
                "Hi ${clientName}, your Inkomoko loan balance is ${loanBalance}. Please pay by ${dueDate}.", row);

        assertEquals("Hi Lucy, your Inkomoko loan balance is 15000. Please pay by 2026-11-15.", resolved);
    }

    @Test
    void missingColumnBecomesEmptyString() {
        final Map<String, Object> row = Map.of("clientName", "Lucy");

        final String resolved = WhatsAppFreeformMessageResolver.resolve("Hi ${clientName}, balance ${loanBalance}.", row);

        assertEquals("Hi Lucy, balance .", resolved);
    }

    @Test
    void leavesMessageWithoutPlaceholdersUnchanged() {
        final String message = "Hello everyone, this is a broadcast.";
        assertEquals(message, WhatsAppFreeformMessageResolver.resolve(message, Map.of("clientName", "Lucy")));
    }

    @Test
    void nullMessageReturnsNull() {
        assertNull(WhatsAppFreeformMessageResolver.resolve(null, Map.of()));
    }

    @Test
    void escapesRegexSpecialCharactersInValues() {
        final Map<String, Object> row = Map.of("clientName", "Ada $1");
        final String resolved = WhatsAppFreeformMessageResolver.resolve("Hi ${clientName}", row);
        assertEquals("Hi Ada $1", resolved);
    }
}
