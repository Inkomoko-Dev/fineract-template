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
package org.apache.fineract.organisation.provisioning.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.provisioning.constants.ProvisioningCriteriaConstants;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;

class ProvisioningCriteriaAuditChangesTest {

    @Test
    void updateRecordsCriteriaNameAsBeforeAfter() {
        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        final ProvisioningCriteria criteria = new ProvisioningCriteria("Old Name", user, LocalDateTime.of(2026, 1, 1, 0, 0), user,
                LocalDateTime.of(2026, 1, 1, 0, 0));

        final String json = "{\"criteriaName\":\"New Name\"}";
        final JsonCommand command = JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null,
                null, null, null, null, null, null, null);

        final Map<String, Object> changes = criteria.update(command, List.of());

        assertTrue(changes.containsKey(ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM));
        @SuppressWarnings("unchecked")
        final Map<String, Object> nameChange = (Map<String, Object>) changes.get(ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM);
        assertEquals("Old Name", nameChange.get("before"));
        assertEquals("New Name", nameChange.get("after"));
        assertEquals("New Name", criteria.getCriteriaName());
    }

    @Test
    void auditSnapshotIncludesNameProductsAndDefinitions() {
        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        final ProvisioningCriteria criteria = new ProvisioningCriteria("SSD", user, LocalDateTime.of(2026, 1, 1, 0, 0), user,
                LocalDateTime.of(2026, 1, 1, 0, 0));

        final Map<String, Object> snapshot = criteria.auditSnapshot();
        assertEquals("SSD", snapshot.get(ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM));
        assertTrue(snapshot.containsKey(ProvisioningCriteriaConstants.JSON_LOANPRODUCTS_PARAM));
        assertTrue(snapshot.containsKey(ProvisioningCriteriaConstants.JSON_PROVISIONING_DEFINITIONS_PARAM));
    }
}
