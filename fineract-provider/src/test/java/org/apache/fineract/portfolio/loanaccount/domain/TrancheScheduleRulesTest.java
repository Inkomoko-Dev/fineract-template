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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrancheScheduleRulesTest {

    @Test
    void sevenMonthLoanKeepsGraceThenTheConfiguredFirstRepaymentAndFiveEqualInstallments() {
        final List<BigDecimal> principals = TrancheScheduleRules.installmentPrincipals(new BigDecimal("250"), new BigDecimal("50"), 1, 7,
                2);

        assertEquals(new BigDecimal("0.00"), principals.get(0));
        assertEquals(new BigDecimal("50.00"), principals.get(1));
        assertEquals(new BigDecimal("40.00"), principals.get(2));
        assertEquals(new BigDecimal("40.00"), principals.get(3));
        assertEquals(new BigDecimal("40.00"), principals.get(4));
        assertEquals(new BigDecimal("40.00"), principals.get(5));
        assertEquals(new BigDecimal("40.00"), principals.get(6));
        assertEquals(new BigDecimal("250.00"), sum(principals));
    }

    @Test
    void largerDisbursedPrincipalRaisesOnlyTheEqualTail() {
        final List<BigDecimal> threeHundredAndFifty = TrancheScheduleRules.installmentPrincipals(new BigDecimal("350"),
                new BigDecimal("50"), 1, 7, 2);
        final List<BigDecimal> fiveHundred = TrancheScheduleRules.installmentPrincipals(new BigDecimal("500"), new BigDecimal("50"), 1, 7,
                2);

        assertEquals(new BigDecimal("60.00"), threeHundredAndFifty.get(2));
        assertEquals(new BigDecimal("90.00"), fiveHundred.get(2));
        assertEquals(new BigDecimal("350.00"), sum(threeHundredAndFifty));
        assertEquals(new BigDecimal("500.00"), sum(fiveHundred));
    }

    @Test
    void firstRepaymentPercentageUsesTheApprovedAmount() {
        assertEquals(new BigDecimal("50.00"),
                TrancheScheduleRules.firstRepaymentPrincipal(new BigDecimal("20"), true, new BigDecimal("250"), 2));
        assertEquals(new BigDecimal("50"),
                TrancheScheduleRules.firstRepaymentPrincipal(new BigDecimal("50"), false, new BigDecimal("250"), 2));
    }

    @Test
    void beforeDisbursementTheScheduleUsesTheFirstTranche() {
        final BigDecimal principal = TrancheScheduleRules.schedulePrincipal(BigDecimal.ZERO, new BigDecimal("150"));
        final List<BigDecimal> principals = TrancheScheduleRules.installmentPrincipals(principal, new BigDecimal("50"), 1, 7, 2);

        assertEquals(new BigDecimal("150"), principal);
        assertEquals(new BigDecimal("0.00"), principals.get(0));
        assertEquals(new BigDecimal("50.00"), principals.get(1));
        assertEquals(new BigDecimal("20.00"), principals.get(2));
        assertEquals(new BigDecimal("150.00"), sum(principals));
    }

    @Test
    void afterDisbursementTheScheduleUsesOnlyWhatHasBeenDisbursed() {
        assertEquals(new BigDecimal("250"), TrancheScheduleRules.schedulePrincipal(new BigDecimal("250"), new BigDecimal("150")));
    }

    @Test
    void firstTrancheAloneUsesOnlyTheAmountDisbursed() {
        final List<BigDecimal> principals = TrancheScheduleRules.installmentPrincipals(new BigDecimal("150"), new BigDecimal("50"), 1, 7,
                2);

        assertEquals(new BigDecimal("50.00"), principals.get(1));
        assertEquals(new BigDecimal("20.00"), principals.get(6));
        assertEquals(new BigDecimal("150.00"), sum(principals));
    }

    @Test
    void lastInstallmentAbsorbsTheRoundingRemainder() {
        final List<BigDecimal> principals = TrancheScheduleRules.installmentPrincipals(new BigDecimal("100.01"), new BigDecimal("10"), 1, 4,
                2);

        assertEquals(new BigDecimal("0.00"), principals.get(0));
        assertEquals(new BigDecimal("10.00"), principals.get(1));
        assertEquals(new BigDecimal("45.01"), principals.get(2));
        assertEquals(new BigDecimal("45.00"), principals.get(3));
        assertEquals(new BigDecimal("100.01"), sum(principals));
    }

    @Test
    void firstRepaymentCannotExceedTheDisbursedPrincipal() {
        final List<BigDecimal> principals = TrancheScheduleRules.installmentPrincipals(new BigDecimal("40"), new BigDecimal("50"), 1, 7, 2);

        assertEquals(new BigDecimal("40.00"), principals.get(1));
        assertEquals(new BigDecimal("0.00"), principals.get(2));
        assertEquals(new BigDecimal("40.00"), sum(principals));
    }

    @Test
    void installmentCountComesFromTheProductRatherThanAFixedFive() {
        final List<BigDecimal> principals = TrancheScheduleRules.installmentPrincipals(new BigDecimal("100"), new BigDecimal("10"), 1, 4,
                2);

        assertEquals(4, principals.size());
        assertEquals(new BigDecimal("0.00"), principals.get(0));
        assertEquals(new BigDecimal("10.00"), principals.get(1));
        assertEquals(new BigDecimal("45.00"), principals.get(2));
        assertEquals(new BigDecimal("45.00"), principals.get(3));
    }

    @Test
    void rescheduleStaysClosedForOtherMultiDisburseProducts() {
        assertFalse(TrancheScheduleRules.rescheduleAllowed(true, false, null));
        assertTrue(TrancheScheduleRules.rescheduleAllowed(true, true, null));
        assertTrue(TrancheScheduleRules.rescheduleAllowed(false, false, null));
    }

    @Test
    void rescheduleIsAllowedWhenTheProductSetsAFirstInstallment() {
        assertTrue(TrancheScheduleRules.rescheduleAllowed(true, false, new BigDecimal("50")));
    }

    private static BigDecimal sum(final List<BigDecimal> amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (final BigDecimal amount : amounts) {
            total = total.add(amount);
        }
        return total;
    }
}
