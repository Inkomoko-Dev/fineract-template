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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsAppFreeformMessageResolverTest {

    @Test
    void resolvesMultiplePlaceholdersFromRow() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("clientName", "Lucy");
        row.put("loanBalance", "15000");
        row.put("dueDate", "2026-11-15");

        final var result = WhatsAppFreeformMessageResolver.resolveStrict(
                "Hi ${clientName}, your Inkomoko loan balance is ${loanBalance}. Please pay by ${dueDate}.", row);

        assertTrue(result.isComplete());
        assertEquals("Hi Lucy, your Inkomoko loan balance is 15000. Please pay by 2026-11-15.", result.getResolvedMessage());
    }

    @Test
    void missingColumnIsIncomplete() {
        final Map<String, Object> row = Map.of("clientName", "Lucy");

        final var result = WhatsAppFreeformMessageResolver.resolveStrict("Hi ${clientName}, balance ${loanBalance}.", row);

        assertFalse(result.isComplete());
        assertEquals(List.of("loanBalance"), result.getUnresolvedKeys());
        assertEquals("Hi Lucy, balance .", result.getResolvedMessage());
    }

    @Test
    void leavesMessageWithoutPlaceholdersUnchanged() {
        final String message = "Hello everyone, this is a broadcast.";
        final var result = WhatsAppFreeformMessageResolver.resolveStrict(message, Map.of("clientName", "Lucy"));
        assertTrue(result.isComplete());
        assertEquals(message, result.getResolvedMessage());
    }

    @Test
    void nullMessageReturnsNull() {
        assertNull(WhatsAppFreeformMessageResolver.resolveStrict(null, Map.of()).getResolvedMessage());
    }

    @Test
    void escapesRegexSpecialCharactersInValues() {
        final Map<String, Object> row = Map.of("clientName", "Ada $1");
        final var result = WhatsAppFreeformMessageResolver.resolveStrict("Hi ${clientName}", row);
        assertTrue(result.isComplete());
        assertEquals("Hi Ada $1", result.getResolvedMessage());
    }
}
