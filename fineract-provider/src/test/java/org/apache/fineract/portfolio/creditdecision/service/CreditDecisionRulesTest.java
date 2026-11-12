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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionCatalogue;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ParamData;
import org.junit.jupiter.api.Test;

class CreditDecisionRulesTest {

    @Test
    void completeFillsEveryCatalogueItemAndKeepsTheCallersChoices() {
        final List<ParamData> out = CreditDecisionRules.complete(List.of(new ParamData("amount", true, " loanAmount ", "NULL")));

        assertEquals(CreditDecisionCatalogue.items().size(), out.size());
        final ParamData amount = out.stream().filter(p -> p.getKey().equals("amount")).findFirst().orElseThrow();
        assertTrue(amount.isEnabled());
        assertEquals("loanAmount", amount.getFieldName());
        assertEquals("null", amount.getMissingPolicy());
        final ParamData term = out.stream().filter(p -> p.getKey().equals("term")).findFirst().orElseThrow();
        assertFalse(term.isEnabled());
        assertEquals("termMonths", term.getFieldName());
        assertEquals("block", term.getMissingPolicy());
    }

    @Test
    void starterParamsSwitchOnTheShortSetOnly() {
        final List<ParamData> starter = CreditDecisionCatalogue.starterParams();
        final List<String> on = starter.stream().filter(ParamData::isEnabled).map(ParamData::getKey).toList();

        assertEquals(CreditDecisionCatalogue.STARTER_SET, on);
    }

    @Test
    void unknownParameterAndBadPolicyAreRejected() {
        final List<ParamData> given = List.of(new ParamData("nonsense", true, "x", "block"), new ParamData("amount", true, "a", "guess"));

        assertEquals(2, CreditDecisionRules.validateParams(given).size());
        assertThrows(PlatformApiDataValidationException.class,
                () -> CreditDecisionRules.throwIfAny(CreditDecisionRules.validateParams(given)));
    }

    @Test
    void submitNeedsAParameterAProductAndAReason() {
        final List<ParamData> none = CreditDecisionCatalogue.params(Map.of());

        final Map<String, String> problems = CreditDecisionRules.submitProblems(none, List.of(), " ");

        assertTrue(problems.containsKey("params"));
        assertTrue(problems.containsKey("products"));
        assertTrue(problems.containsKey("reason"));
    }

    @Test
    void submitRejectsBadAndDuplicateFieldNamesOnSwitchedOnParametersOnly() {
        final List<ParamData> params = CreditDecisionRules
                .complete(List.of(new ParamData("amount", true, "same", "block"), new ParamData("term", true, "same", "block"),
                        new ParamData("currency", true, "9bad", "block"), new ParamData("purpose", false, "also bad!", "omit")));

        final Map<String, String> problems = CreditDecisionRules.submitProblems(params, List.of(1L), "Because");

        assertEquals("Already used by another parameter", problems.get("amount"));
        assertEquals("Already used by another parameter", problems.get("term"));
        assertTrue(problems.containsKey("currency"));
        assertFalse(problems.containsKey("purpose"));
        assertFalse(problems.containsKey("products"));
    }

    @Test
    void aWellFormedVersionHasNoProblems() {
        final List<ParamData> params = CreditDecisionCatalogue.starterParams();

        assertTrue(CreditDecisionRules.submitProblems(params, List.of(7L), "Aligned with the policy sheet").isEmpty());
    }
}
