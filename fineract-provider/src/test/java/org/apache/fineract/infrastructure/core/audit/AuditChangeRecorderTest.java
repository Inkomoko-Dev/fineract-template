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
package org.apache.fineract.infrastructure.core.audit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class AuditChangeRecorderTest {

    @Test
    public void recordChangeStoresBeforeAndAfterWhenValuesDiffer() {
        final Map<String, Object> changes = new LinkedHashMap<>();
        AuditChangeRecorder.recordChange(changes, "levelSixUnsecuredFirstCycleMaxAmount", new BigDecimal("6160000"),
                new BigDecimal("7160000"));

        assertTrue(changes.containsKey("levelSixUnsecuredFirstCycleMaxAmount"));
        @SuppressWarnings("unchecked")
        final Map<String, Object> entry = (Map<String, Object>) changes.get("levelSixUnsecuredFirstCycleMaxAmount");
        assertEquals(new BigDecimal("6160000"), entry.get("before"));
        assertEquals(new BigDecimal("7160000"), entry.get("after"));
    }

    @Test
    public void recordChangeSkipsUnchangedValues() {
        final Map<String, Object> changes = new LinkedHashMap<>();
        AuditChangeRecorder.recordChange(changes, "numberOfLevels", 8, 8);
        assertFalse(changes.containsKey("numberOfLevels"));
    }

    @Test
    public void recordNestedChangeStoresPermissionToggle() {
        final Map<String, Object> changes = new LinkedHashMap<>();
        AuditChangeRecorder.recordNestedChange(changes, "permissions", "ACCEPT_LOANICREVIEWDECISIONLEVELSIX", false, true);

        @SuppressWarnings("unchecked")
        final Map<String, Object> permissions = (Map<String, Object>) changes.get("permissions");
        @SuppressWarnings("unchecked")
        final Map<String, Object> permissionChange = (Map<String, Object>) permissions.get("ACCEPT_LOANICREVIEWDECISIONLEVELSIX");
        assertEquals(false, permissionChange.get("before"));
        assertEquals(true, permissionChange.get("after"));
    }
}
