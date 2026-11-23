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
package org.apache.fineract.portfolio.creditdecision.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.EffectiveRuleData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.PolicyValueData;
import org.junit.jupiter.api.Test;

class CreditDecisionPolicyTest {

    private static PolicyValueData rule(final String scope, final String scopeKey, final String key, final String value) {
        return new PolicyValueData(scope, scopeKey, key, value == null ? null : new BigDecimal(value));
    }

    private static List<PolicyValueData> starterWith(final PolicyValueData... changes) {
        final List<PolicyValueData> rules = new ArrayList<>(CreditDecisionPolicy.starter());
        for (final PolicyValueData change : changes) {
            rules.removeIf(r -> r.getScope().equals(change.getScope()) && r.getScopeKey().equals(change.getScopeKey())
                    && r.getKey().equals(change.getKey()));
            rules.add(change);
        }
        return CreditDecisionPolicy.normalise(rules);
    }

    private static Map<Long, String> kes(final Long... ids) {
        final Map<Long, String> out = new java.util.LinkedHashMap<>();
        for (final Long id : ids) {
            out.put(id, "KES");
        }
        return out;
    }

    @Test
    void theStarterPolicyIsTheOneInTheProcessFlowAndPassesSubmitChecks() {
        final List<PolicyValueData> starter = CreditDecisionPolicy.normalise(CreditDecisionPolicy.starter());

        assertTrue(CreditDecisionPolicy.submitProblems(starter, List.of(1L), kes(1L)).isEmpty());
        final Map<String, BigDecimal> values = CreditDecisionPolicy.effectiveValues(starter, 1L, "KES");
        assertEquals(18, values.get("minAge").intValue());
        assertEquals(30, values.get("maxDaysPastDue").intValue());
        assertEquals(0, values.get("arrearsTolerance").intValue());
        assertEquals(6, values.get("minMonthsInBusiness").intValue());
        assertEquals(750, values.get("lowRiskMinScore").intValue());
        assertEquals(650, values.get("mediumRiskMinScore").intValue());
        assertEquals(100, values.get("weightInternalHistory").add(values.get("weightBureauHistory"))
                .add(values.get("weightBusinessFinancials")).add(values.get("weightDemographics")).intValue());
    }

    @Test
    void weightsThatDoNotAddUpToOneHundredAreRefusedAtSubmit() {
        final List<PolicyValueData> rules = starterWith(rule("GLOBAL", "*", "weightInternalHistory", "41"));

        final Map<String, String> problems = CreditDecisionPolicy.submitProblems(rules, List.of(1L), kes(1L));

        assertTrue(problems.containsKey("weights.GLOBAL:*"), problems.toString());
        assertTrue(problems.get("weights.GLOBAL:*").contains("101%"), problems.toString());
    }

    @Test
    void aScopeThatSetsSomeWeightsMustSetAllFour() {
        final List<PolicyValueData> rules = starterWith(rule("COUNTRY", "rwf", "weightInternalHistory", "100"));

        assertTrue(CreditDecisionPolicy.submitProblems(rules, List.of(1L), kes(1L)).containsKey("weights.COUNTRY:RWF"));
    }

    @Test
    void negativeFractionalOrUnknownValuesAreStructuralProblems() {
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("COUNTRY", "KES", "maxLoanAmount", "-1"))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("GLOBAL", "*", "arrearsTolerance", "-0.5"))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("GLOBAL", "*", "maxDaysPastDue", "30.5"))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("GLOBAL", "*", "lowRiskMinScore", "1001"))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("GLOBAL", "*", "weightDemographics", "101"))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("COUNTRY", "UGX", "maxLoanAmount", "5"))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("GLOBAL", "*", "nothing", "5"))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("PRODUCT", "abc", "maxLoanAmount", "5"))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(starterWith(rule("GLOBAL", "*", "maxLoanAmount", null))).size());
        assertEquals(1, CreditDecisionPolicy.structuralProblems(
                CreditDecisionPolicy.normalise(List.of(rule("GLOBAL", "*", "minAge", "18"), rule("GLOBAL", "*", "minAge", "19")))).size());
        // an amount may have decimals and zero is a fine amount
        assertTrue(CreditDecisionPolicy.structuralProblems(starterWith(rule("COUNTRY", "KES", "maxLoanAmount", "1500.50"),
                rule("COUNTRY", "ETB", "minLoanAmount", "0"))).isEmpty());
    }

    @Test
    void everyCountryInTheRequirementCanHaveACapAndAProductCanOverrideIt() {
        assertEquals(List.of("RWF", "KES", "ETB", "SSP", "XAF"), List.copyOf(CreditDecisionPolicy.countries().keySet()));

        final List<PolicyValueData> rules = starterWith(rule("GLOBAL", "*", "maxLoanAmount", "1000"),
                rule("COUNTRY", "RWF", "maxLoanAmount", "2000"), rule("COUNTRY", "KES", "maxLoanAmount", "3000"),
                rule("COUNTRY", "ETB", "maxLoanAmount", "4000"), rule("COUNTRY", "SSP", "maxLoanAmount", "5000"),
                rule("COUNTRY", "XAF", "maxLoanAmount", "6000"), rule("PRODUCT", "7", "maxLoanAmount", "7000"));

        assertTrue(CreditDecisionPolicy.submitProblems(rules, List.of(7L, 8L), Map.of(7L, "KES", 8L, "XAF")).isEmpty());
        assertEquals(0, new BigDecimal("2000").compareTo(CreditDecisionPolicy.effectiveValues(rules, 9L, "rwf").get("maxLoanAmount")));
        assertEquals(0, new BigDecimal("6000").compareTo(CreditDecisionPolicy.effectiveValues(rules, 8L, "XAF").get("maxLoanAmount")));
        assertEquals(0, new BigDecimal("7000").compareTo(CreditDecisionPolicy.effectiveValues(rules, 7L, "KES").get("maxLoanAmount")));
        assertEquals(0, new BigDecimal("1000").compareTo(CreditDecisionPolicy.effectiveValues(rules, 9L, "UGX").get("maxLoanAmount")));
        assertNull(CreditDecisionPolicy.effectiveValues(starterWith(), 9L, "KES").get("maxLoanAmount"));
    }

    @Test
    void theEffectiveValueSaysWhereItCameFrom() {
        final List<PolicyValueData> rules = starterWith(rule("COUNTRY", "KES", "maxDaysPastDue", "45"),
                rule("PRODUCT", "7", "maxDaysPastDue", "60"));

        final EffectiveRuleData product = CreditDecisionPolicy.effective(rules, 7L, "KES").stream()
                .filter(e -> e.getKey().equals("maxDaysPastDue")).findFirst().orElseThrow();
        final EffectiveRuleData country = CreditDecisionPolicy.effective(rules, 8L, "KES").stream()
                .filter(e -> e.getKey().equals("maxDaysPastDue")).findFirst().orElseThrow();
        final EffectiveRuleData everyone = CreditDecisionPolicy.effective(rules, 9L, "RWF").stream()
                .filter(e -> e.getKey().equals("maxDaysPastDue")).findFirst().orElseThrow();

        assertEquals("PRODUCT", product.getScope());
        assertEquals("COUNTRY", country.getScope());
        assertEquals("KES", country.getScopeKey());
        assertEquals("GLOBAL", everyone.getScope());
        assertEquals(30, everyone.getValue().intValue());
    }

    @Test
    void aSmallestLoanAboveTheLargestAndAMediumCutoffAtOrAboveTheLowOneAreRefused() {
        assertTrue(CreditDecisionPolicy.submitProblems(
                starterWith(rule("GLOBAL", "*", "minLoanAmount", "500"), rule("GLOBAL", "*", "maxLoanAmount", "400")), List.of(1L), kes(1L))
                .containsKey("order.GLOBAL:*"));
        assertTrue(CreditDecisionPolicy.submitProblems(starterWith(rule("GLOBAL", "*", "mediumRiskMinScore", "750")), List.of(1L), kes(1L))
                .containsKey("order.GLOBAL:*"));
        // each is fine alone but clashes once a product is given its country's cap
        assertTrue(CreditDecisionPolicy.submitProblems(
                starterWith(rule("GLOBAL", "*", "minLoanAmount", "500"), rule("COUNTRY", "KES", "maxLoanAmount", "400")), List.of(1L),
                kes(1L)).containsKey("effective.1"));
    }

    @Test
    void theGlobalKnockoutsAndWeightsMustBeSetForEveryone() {
        final List<PolicyValueData> rules = new ArrayList<>(CreditDecisionPolicy.starter());
        rules.removeIf(r -> r.getKey().equals("maxDaysPastDue"));

        assertTrue(CreditDecisionPolicy.submitProblems(CreditDecisionPolicy.normalise(rules), List.of(1L), kes(1L))
                .containsKey("rule.GLOBAL.*.maxDaysPastDue"));
    }

    @Test
    void aProductSettingMustBelongToAProductTheVersionCovers() {
        final Map<String, String> problems = CreditDecisionPolicy
                .submitProblems(starterWith(rule("PRODUCT", "99", "maxLoanAmount", "10")), List.of(1L), kes(1L));

        assertTrue(problems.containsKey("rule.PRODUCT.99.maxLoanAmount"), problems.toString());
    }
}
