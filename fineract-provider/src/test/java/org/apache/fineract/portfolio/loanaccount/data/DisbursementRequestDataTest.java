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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DisbursementRequestDataTest {

    private final Gson gson = new Gson();

    @Test
    void serializesWithoutFxTimestamp() {
        final DisbursementRequestData data = new DisbursementRequestData("cbs_disb_1_1", "000000001", BigDecimal.TEN, "RWF",
                "Equity Bank", 110L, "250700000000", "100245401259", "Equity Bank", "CBS", 110L);

        final String json = assertDoesNotThrow(() -> gson.toJson(data));

        assertFalse(json.contains("fxTimestamp"));
    }

    @Test
    void serializesFxTimestampAsString() {
        final DisbursementRequestData data = new DisbursementRequestData("cbs_disb_1_1", "000000001", BigDecimal.TEN, "RWF",
                "Equity Bank", 110L, "250700000000", "100245401259", "Equity Bank", "CBS", 110L);

        data.setFxTimestamp("2026-05-14T13:42:20");

        final String json = assertDoesNotThrow(() -> gson.toJson(data));

        assertTrue(json.contains("\"fxTimestamp\":\"2026-05-14T13:42:20\""));
    }
}
