package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
