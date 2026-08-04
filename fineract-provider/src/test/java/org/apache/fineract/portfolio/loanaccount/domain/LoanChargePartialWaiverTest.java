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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pins the amount arithmetic that the historical penalty waiver (CGLT-656) depends on.
 *
 * <p>
 * The feature rests on a single ordering: {@link LoanCharge#resetPaidAmount} frees the cash a repayment had allocated
 * to the charge (while preserving {@code amountWaived}), and only then does a waive have anything to act on. Without
 * the reset, {@link LoanCharge#waive} returns zero for a fully paid charge, because it waives the outstanding balance.
 * </p>
 */
public class LoanChargePartialWaiverTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, null);
    private static final BigDecimal CHARGE_AMOUNT = BigDecimal.valueOf(10000);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    @DisplayName("waive() alone waives nothing on a fully paid charge - this is why the reset is required")
    public void waiveWithoutResetWaivesNothingOnAFullyPaidCharge() {
        final LoanCharge charge = penaltyCharge();
        payInFull(charge);

        final Money waived = charge.waive(KES, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(waived.getAmount()), "a fully paid charge has no outstanding balance to waive");
    }

    @Test
    @DisplayName("resetPaidAmount then waive waives the full charge, and a replay reset cannot undo it")
    public void resetThenFullWaiveSurvivesTheReplayReset() {
        final LoanCharge charge = penaltyCharge();
        payInFull(charge);

        charge.resetPaidAmount(KES);
        assertEquals(0, CHARGE_AMOUNT.compareTo(charge.getAmountOutstanding(KES).getAmount()),
                "resetting the paid amount should restore the full outstanding balance");

        final Money waived = charge.waive(KES, null);

        assertEquals(0, CHARGE_AMOUNT.compareTo(waived.getAmount()));
        assertEquals(0, CHARGE_AMOUNT.compareTo(charge.getAmountWaived(KES).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(charge.getAmountOutstanding(KES).getAmount()));
        assertFalse(charge.isPaid());
        assertTrue(charge.isWaived());

        // The transaction replay calls resetPaidAmount on every charge before re-processing repayments. Because it
        // preserves amountWaived, the waived penalty must still have nothing outstanding - otherwise the freed cash
        // would flow straight back onto it and the correction would be a no-op.
        charge.resetPaidAmount(KES);

        assertEquals(0, BigDecimal.ZERO.compareTo(charge.getAmountOutstanding(KES).getAmount()),
                "the replay reset must not resurrect an outstanding balance on a fully waived charge");
        assertEquals(0, CHARGE_AMOUNT.compareTo(charge.getAmountWaived(KES).getAmount()), "the replay reset must preserve amountWaived");
    }

    @Test
    @DisplayName("a partial waiver leaves the remainder payable and does not flag the charge as waived")
    public void partialWaiverLeavesRemainderPayable() {
        final LoanCharge charge = penaltyCharge();
        payInFull(charge);
        charge.resetPaidAmount(KES);

        final Money waived = charge.waivePartially(KES, null, BigDecimal.valueOf(4000));

        assertEquals(0, BigDecimal.valueOf(4000).compareTo(waived.getAmount()));
        assertEquals(0, BigDecimal.valueOf(4000).compareTo(charge.getAmountWaived(KES).getAmount()));
        assertEquals(0, BigDecimal.valueOf(6000).compareTo(charge.getAmountOutstanding(KES).getAmount()));
        assertFalse(charge.isPaid());
        // Deliberately false: a partially waived charge must stay an ordinary payable charge, otherwise
        // isResidualPenaltyWaiver (isWaived() && outstanding > 0) would misread it as a CGLT-624 residual.
        assertFalse(charge.isWaived(), "a partially waived charge must not be flagged as waived");

        charge.resetPaidAmount(KES);

        assertEquals(0, BigDecimal.valueOf(6000).compareTo(charge.getAmountOutstanding(KES).getAmount()),
                "the replay reset must leave exactly the unwaived remainder outstanding");
    }

    @Test
    @DisplayName("a partial waiver that clears the balance does flag the charge as waived")
    public void partialWaiverForTheWholeBalanceFlagsTheChargeAsWaived() {
        final LoanCharge charge = penaltyCharge();
        payInFull(charge);
        charge.resetPaidAmount(KES);

        charge.waivePartially(KES, null, CHARGE_AMOUNT);

        assertEquals(0, BigDecimal.ZERO.compareTo(charge.getAmountOutstanding(KES).getAmount()));
        assertTrue(charge.isWaived());
    }

    @Test
    @DisplayName("successive partial waivers accumulate")
    public void successivePartialWaiversAccumulate() {
        final LoanCharge charge = penaltyCharge();
        payInFull(charge);
        charge.resetPaidAmount(KES);

        charge.waivePartially(KES, null, BigDecimal.valueOf(2500));
        charge.waivePartially(KES, null, BigDecimal.valueOf(1500));

        assertEquals(0, BigDecimal.valueOf(4000).compareTo(charge.getAmountWaived(KES).getAmount()));
        assertEquals(0, BigDecimal.valueOf(6000).compareTo(charge.getAmountOutstanding(KES).getAmount()));
        assertFalse(charge.isWaived());
    }

    @Test
    @DisplayName("a waiver above the outstanding balance is rejected")
    public void waiverAboveOutstandingIsRejected() {
        final LoanCharge charge = penaltyCharge();
        payInFull(charge);
        charge.resetPaidAmount(KES);

        assertThrows(PlatformApiDataValidationException.class, () -> charge.waivePartially(KES, null, BigDecimal.valueOf(10001)));
    }

    @Test
    @DisplayName("a zero or negative waiver amount is rejected")
    public void nonPositiveWaiverAmountIsRejected() {
        final LoanCharge charge = penaltyCharge();
        payInFull(charge);
        charge.resetPaidAmount(KES);

        assertThrows(PlatformApiDataValidationException.class, () -> charge.waivePartially(KES, null, BigDecimal.ZERO));
        assertThrows(PlatformApiDataValidationException.class, () -> charge.waivePartially(KES, null, BigDecimal.valueOf(-1)));
    }

    @Test
    @DisplayName("a partial waiver on an instalment fee is rejected in v1")
    public void partialWaiverOnAnInstalmentFeeIsRejected() {
        final LoanCharge charge = instalmentFeeCharge();

        assertThrows(PlatformApiDataValidationException.class, () -> charge.waivePartially(KES, 1, BigDecimal.valueOf(100)));
    }

    private void payInFull(final LoanCharge charge) {
        charge.updatePaidAmountBy(Money.of(KES, CHARGE_AMOUNT), null, Money.zero(KES));
        assertTrue(charge.isPaid(), "fixture precondition: the charge should be fully paid");
        assertEquals(0, BigDecimal.ZERO.compareTo(charge.getAmountOutstanding(KES).getAmount()));
    }

    private LoanCharge penaltyCharge() {
        return new LoanCharge(mock(Loan.class), penaltyDefinition(ChargeTimeType.SPECIFIED_DUE_DATE), CHARGE_AMOUNT, CHARGE_AMOUNT,
                ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, LocalDate.of(2026, 1, 15), ChargePaymentMode.REGULAR, 1,
                CHARGE_AMOUNT);
    }

    private LoanCharge instalmentFeeCharge() {
        // An instalment-fee charge spreads itself across the schedule at construction time, which reads the loan's
        // currency and installments.
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(KES);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(java.util.Collections.emptyList());
        return new LoanCharge(loan, penaltyDefinition(ChargeTimeType.INSTALMENT_FEE), CHARGE_AMOUNT, CHARGE_AMOUNT,
                ChargeTimeType.INSTALMENT_FEE, ChargeCalculationType.FLAT, LocalDate.of(2026, 1, 15), ChargePaymentMode.REGULAR, 1,
                CHARGE_AMOUNT);
    }

    private Charge penaltyDefinition(final ChargeTimeType chargeTimeType) {
        final Charge charge = mock(Charge.class);
        when(charge.isPenalty()).thenReturn(true);
        when(charge.getChargeTimeType()).thenReturn(chargeTimeType.getValue());
        return charge;
    }
}
