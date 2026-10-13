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
package org.apache.fineract.commands.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.fineract.commands.data.AuditData;
import org.junit.jupiter.api.Test;

class AuditMapperUserNotesTest {

    private static final String USER_JSON = "{\"username\":\"jdoe\",\"notes\":\"INC-12067 new hire\"}";

    private ResultSet row(final String entityName, final String notes, final String commandAsJson) throws SQLException {
        final ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getString("entityName")).thenReturn(entityName);
        when(rs.getString("actionName")).thenReturn("CREATE");
        when(rs.getString("notes")).thenReturn(notes);
        when(rs.getString("commandAsJson")).thenReturn(commandAsJson);
        when(rs.getObject(anyString())).thenReturn(null);
        return rs;
    }

    @Test
    void hidesUserNotesFromCallersWithoutUpdateUser() throws SQLException {
        final AuditData data = new AuditReadPlatformServiceImpl.AuditMapper(true).mapRow(row("USER", "INC-12067 new hire", USER_JSON), 0);

        assertNull(data.getNotes());
        assertFalse(data.getCommandAsJson().contains("notes"));
        assertTrue(data.getCommandAsJson().contains("jdoe"));
    }

    @Test
    void showsUserNotesToCallersWithUpdateUser() throws SQLException {
        final AuditData data = new AuditReadPlatformServiceImpl.AuditMapper(false).mapRow(row("USER", "INC-12067 new hire", USER_JSON), 0);

        assertEquals("INC-12067 new hire", data.getNotes());
        assertEquals(USER_JSON, data.getCommandAsJson());
    }

    @Test
    void leavesNotesOnOtherEntitiesUntouched() throws SQLException {
        final String json = "{\"notes\":\"report tweak\"}";
        final AuditData data = new AuditReadPlatformServiceImpl.AuditMapper(true).mapRow(row("REPORT", "report tweak", json), 0);

        assertEquals("report tweak", data.getNotes());
        assertEquals(json, data.getCommandAsJson());
    }

    @Test
    void handlesUserRowsWithoutJson() throws SQLException {
        final AuditData data = new AuditReadPlatformServiceImpl.AuditMapper(true).mapRow(row("USER", "reason", null), 0);

        assertNull(data.getNotes());
        assertNull(data.getCommandAsJson());
    }
}
