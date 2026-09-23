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
package org.apache.fineract.commands.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonElement;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandSourceTest {

    private FromJsonHelper fromJsonHelper;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        fromJsonHelper = new FromJsonHelper();
    }

    private CommandSource commandSourceFor(final String json) {
        final JsonElement parsedJson = json == null ? null : fromJsonHelper.parse(json);
        final CommandWrapper wrapper = new CommandWrapperBuilder().deleteUser(1L).withJson(json).build();
        final JsonCommand command = JsonCommand.from(json, parsedJson, fromJsonHelper, "USER", 1L, null, null, null, null, null, null, null,
                null, null, null);
        return CommandSource.fullEntryFrom(wrapper, command, null);
    }

    @Test
    @DisplayName("notes in the command payload are captured on the audit entry")
    void notesArePersistedOnTheCommandSource() {
        final CommandSource commandSource = commandSourceFor("{\"notes\": \"CGLT-564 access revoked\"}");

        assertEquals("CGLT-564 access revoked", commandSource.getNotes());
    }

    @Test
    @DisplayName("notes longer than the column length are truncated")
    void oversizedNotesAreTruncated() {
        final String oversized = "n".repeat(CommandSource.NOTES_MAX_LENGTH + 50);

        final CommandSource commandSource = commandSourceFor("{\"notes\": \"" + oversized + "\"}");

        assertEquals(CommandSource.NOTES_MAX_LENGTH, commandSource.getNotes().length());
    }

    @Test
    @DisplayName("commands without a notes parameter record no notes")
    void payloadWithoutNotesRecordsNothing() {
        assertNull(commandSourceFor("{\"username\": \"testuser\"}").getNotes());
    }

    @Test
    @DisplayName("commands without a payload record no notes")
    void payloadlessCommandRecordsNothing() {
        assertNull(commandSourceFor(null).getNotes());
    }

    @Test
    @DisplayName("a non textual notes parameter is ignored rather than failing the command")
    void nonPrimitiveNotesAreIgnored() {
        assertNull(commandSourceFor("{\"notes\": {\"ticket\": \"CGLT-564\"}}").getNotes());
    }
}
