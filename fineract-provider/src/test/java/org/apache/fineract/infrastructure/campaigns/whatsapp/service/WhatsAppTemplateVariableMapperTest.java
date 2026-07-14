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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsAppTemplateVariableMapperTest {

    @Test
    void mapsOrderedKeysFromRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("clientName", "Ada");
        row.put("amount", "1000");
        row.put("dueDate", "2026-07-14");
        row.put("mobileNo", "+254700000000");

        List<String> values = WhatsAppTemplateVariableMapper.toBodyValues(
                "[\"clientName\",\"amount\",\"dueDate\"]", row);

        assertEquals(List.of("Ada", "1000", "2026-07-14"), values);
    }

    @Test
    void missingKeyBecomesEmptyString() {
        Map<String, Object> row = Map.of("clientName", "Ada");
        List<String> values = WhatsAppTemplateVariableMapper.toBodyValues(
                "[\"clientName\",\"amount\"]", row);
        assertEquals(List.of("Ada", ""), values);
    }

    @Test
    void supportsArbitraryFutureTemplateVariableOrders() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("loanId", "99");
        row.put("officeName", "Kigali");
        List<String> values = WhatsAppTemplateVariableMapper.toBodyValues(
                "[\"officeName\",\"loanId\"]", row);
        assertEquals(List.of("Kigali", "99"), values);
    }
}
