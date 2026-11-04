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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrancheReleaseRulesTest {

    @Test
    void principalsUseTheLoanPrincipalAndPutRoundingOnTheLastTranche() {
        final List<BigDecimal> amounts = TrancheReleaseRules.principals(new BigDecimal("1000"),
                TrancheReleaseRules.parsePercentages("60, 40"), 0);

        assertEquals(new BigDecimal("600"), amounts.get(0));
        assertEquals(new BigDecimal("400"), amounts.get(1));
        assertEquals(new BigDecimal("1000"), amounts.get(0).add(amounts.get(1)));
    }

    @Test
    void lastTrancheAbsorbsARoundingRemainder() {
        final List<BigDecimal> amounts = TrancheReleaseRules.principals(new BigDecimal("100"),
                TrancheReleaseRules.parsePercentages("33.33,33.33,33.34"), 2);

        assertEquals(new BigDecimal("33.33"), amounts.get(0));
        assertEquals(new BigDecimal("33.33"), amounts.get(1));
        assertEquals(new BigDecimal("33.34"), amounts.get(2));
    }

    @Test
    void percentagesMustAddToOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> TrancheReleaseRules.parsePercentages("60,30"));
    }

    @Test
    void requiredRepaymentIsThePercentageOfThePreviousTranche() {
        assertEquals(new BigDecimal("75"),
                TrancheReleaseRules.requiredPrincipal(new BigDecimal("150"), new BigDecimal("50"), 0));
    }

    @Test
    void aLaterTrancheCanRequireAPercentageBelow100OrAnAmount() {
        assertEquals(new BigDecimal("60.00"),
                TrancheReleaseRules.requiredFromPrevious(new BigDecimal("150"), new BigDecimal("40"), true, 2));
        assertEquals(new BigDecimal("40.00"),
                TrancheReleaseRules.requiredFromPrevious(new BigDecimal("150"), new BigDecimal("40"), false, 2));
        assertTrue(TrancheReleaseRules.acceptablePercentage(new BigDecimal("99.99")));
        assertFalse(TrancheReleaseRules.acceptablePercentage(new BigDecimal("100")));
        assertFalse(TrancheReleaseRules.acceptablePercentage(new BigDecimal("0")));
        assertTrue(TrancheReleaseRules.acceptablePrincipalPercentage(new BigDecimal("100")));
        assertEquals(new BigDecimal("150.00"),
                TrancheReleaseRules.requiredPrincipal(new BigDecimal("250"), new BigDecimal("60"), 2));
        assertFalse(TrancheReleaseRules.acceptablePrincipalPercentage(new BigDecimal("100.01")));
    }

    @Test
    void trancheCountMustStayInsideTheProductMinimumAndMaximum() {
        TrancheReleaseRules.requireTrancheCount(2, 2, 5);
        assertThrows(RuntimeException.class, () -> TrancheReleaseRules.requireTrancheCount(1, 2, 5));
        assertThrows(RuntimeException.class, () -> TrancheReleaseRules.requireTrancheCount(6, 2, 5));
        TrancheReleaseRules.requireTrancheCount(1, null, 5);
    }

    @Test
    void laterTranchesUseThePrincipalStillLeft() {
        assertEquals(new BigDecimal("45.00"),
                TrancheReleaseRules.percentageOf(new BigDecimal("150"), new BigDecimal("30"), 2));
        assertEquals(new BigDecimal("150.00"),
                TrancheReleaseRules.nextTrancheAmount(new BigDecimal("250"), true, new BigDecimal("60"), 2));
        assertEquals(new BigDecimal("100.00"),
                TrancheReleaseRules.nextTrancheAmount(new BigDecimal("100"), true, new BigDecimal("100"), 2));
        assertEquals(new BigDecimal("40.00"),
                TrancheReleaseRules.nextTrancheAmount(new BigDecimal("100"), false, new BigDecimal("40"), 2));
        assertEquals(new BigDecimal("100.00"),
                TrancheReleaseRules.nextTrancheAmount(new BigDecimal("100"), false, new BigDecimal("150"), 2));
    }

    @Test
    void aLoanValueReplacesTheProductValueAndABlankLoanValueKeepsIt() {
        assertEquals(new BigDecimal("40"), TrancheReleaseRules.effectiveAmount(new BigDecimal("40"), new BigDecimal("50")));
        assertEquals(new BigDecimal("50"), TrancheReleaseRules.effectiveAmount(null, new BigDecimal("50")));
        assertEquals("70,30", TrancheReleaseRules.effectiveText("70,30", "60,40"));
        assertEquals("60,40", TrancheReleaseRules.effectiveText("  ", "60,40"));
    }
}
