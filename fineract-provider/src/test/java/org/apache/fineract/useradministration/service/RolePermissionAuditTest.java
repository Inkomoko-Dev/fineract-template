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
package org.apache.fineract.useradministration.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.core.audit.AuditChangeRecorder;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.Test;

public class RolePermissionAuditTest {

    @Test
    public void permissionToggleProducesBeforeAfterStructure() {
        final Role role = new Role("IC Approver", "IC approver role");
        final Permission permission = new Permission("portfolio", "LOANICREVIEWDECISIONLEVELSIX", "ACCEPT");
        final String permissionCode = "ACCEPT_LOANICREVIEWDECISIONLEVELSIX";

        final Map<String, Object> changedPermissions = new LinkedHashMap<>();
        final boolean wasSelected = role.hasPermissionTo(permissionCode);
        assertFalse(wasSelected);

        final boolean isSelected = true;
        final boolean changed = role.updatePermission(permission, isSelected);
        assertTrue(changed);

        changedPermissions.put(permissionCode, AuditChangeRecorder.beforeAfter(wasSelected, isSelected));

        @SuppressWarnings("unchecked")
        final Map<String, Object> entry = (Map<String, Object>) changedPermissions.get(permissionCode);
        assertEquals(false, entry.get("before"));
        assertEquals(true, entry.get("after"));
    }
}
