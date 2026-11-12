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
package org.apache.fineract.portfolio.creditdecision.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ParamData;
import org.junit.jupiter.api.Test;

class CreditDecisionRequestBuilderTest {

    private static ParamData param(final String key, final boolean on, final String field, final String policy) {
        return new ParamData(key, on, field, policy);
    }

    @Test
    void sendsOnlySwitchedOnParametersUnderTheirFieldNames() {
        final Map<String, Object> values = new HashMap<>();
        values.put("amount", 40000);
        values.put("term", 12);
        final CreditDecisionRequestBuilder.Built built = CreditDecisionRequestBuilder
                .build(List.of(param("amount", true, "requestedAmount", "block"), param("term", false, "termMonths", "block")), values);
        assertEquals(Map.of("requestedAmount", 40000), built.getParameters());
        assertTrue(built.getHeld().isEmpty());
    }

    @Test
    void missingValuesFollowThePolicyOfEachParameter() {
        final CreditDecisionRequestBuilder.Built built = CreditDecisionRequestBuilder.build(List.of(param("bscore", true, "bureauScore", "block"),
                param("age", true, "applicantAge", "null"), param("purpose", true, "loanPurpose", "omit")), new HashMap<>());
        assertEquals(List.of("bscore"), built.getHeld());
        assertEquals(List.of("age"), built.getNulls());
        assertEquals(List.of("purpose"), built.getOmitted());
        assertTrue(built.getParameters().containsKey("applicantAge"));
        assertNull(built.getParameters().get("applicantAge"));
        assertFalse(built.getParameters().containsKey("loanPurpose"));
        assertFalse(built.getParameters().containsKey("bureauScore"));
    }

    @Test
    void zeroAndNoAreRealValuesNotMissingOnes() {
        final Map<String, Object> values = new HashMap<>();
        values.put("arrears", 0);
        values.put("wo", "No");
        values.put("purpose", "  ");
        final CreditDecisionRequestBuilder.Built built = CreditDecisionRequestBuilder.build(List.of(param("arrears", true, "currentArrears", "block"),
                param("wo", true, "writtenOff24m", "block"), param("purpose", true, "loanPurpose", "omit")), values);
        assertTrue(built.getHeld().isEmpty());
        assertEquals(0, built.getParameters().get("currentArrears"));
        assertEquals("No", built.getParameters().get("writtenOff24m"));
        assertEquals(List.of("purpose"), built.getOmitted());
    }
}
