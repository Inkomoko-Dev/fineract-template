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
package org.apache.fineract.portfolio.loanproduct.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

class LoanProductThirdPartyDisbursementMappingTest {

    @Test
    void enablingThirdPartyDisbursementCreatesMappingRow() {
        final LoanProduct product = new LoanProduct();
        final JsonCommand command = jsonCommand("{\"enableThirdPartyDisbursement\":true,\"thirdPartyDisbursementProvider\":\"kifiya\"}");

        product.applyThirdPartyDisbursementSettings(command);

        assertTrue(product.isEnableThirdPartyDisbursement());
        assertEquals("KIFIYA", product.getThirdPartyDisbursementProvider());
    }

    @Test
    void disablingThirdPartyDisbursementRemovesMappingRow() {
        final LoanProduct product = new LoanProduct();
        product.applyThirdPartyDisbursementSettings(
                jsonCommand("{\"enableThirdPartyDisbursement\":true,\"thirdPartyDisbursementProvider\":\"KIFIYA\"}"));

        product.applyThirdPartyDisbursementSettings(jsonCommand("{\"enableThirdPartyDisbursement\":false}"));

        assertFalse(product.isEnableThirdPartyDisbursement());
        assertNull(product.getThirdPartyDisbursementProvider());
    }

    private static JsonCommand jsonCommand(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
