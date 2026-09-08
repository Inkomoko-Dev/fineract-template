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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.exception.InvalidLoanStateTransitionException;
import org.apache.fineract.portfolio.loanaccount.exception.LoanDisbursalException;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.exception.TrancheDisbursementAfterMaturityException;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests {@link Loan}.
 */
public class LoanTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    public void init() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 5, 25))));
    }

    @AfterEach
    public void resetMoneyHelper() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    /**
     * Tests {@link Loan#getCharges()} with charges.
     */
    @Test
    public void testGetChargesWithCharges() {
        Loan loan = new Loan();
        ReflectionTestUtils.setField(loan, "charges", Collections.singleton(buildLoanCharge()));

        final Collection<LoanCharge> chargeIds = loan.getCharges();

        assertEquals(1, chargeIds.size());
    }

    /**
     * Tests {@link Loan#getCharges()} with no charges.
     */
    @Test
    public void testGetChargesWithNoCharges() {
        final Loan loan = new Loan();

        final Collection<LoanCharge> chargeIds = loan.getCharges();

        assertEquals(0, chargeIds.size());
    }

    /**
     * Tests {@link Loan#getCharges()} with null to make sure NPE is not thrown.
     */
    @Test
    public void testGetChargesWithNull() {
        final Loan loan = new Loan();
        ReflectionTestUtils.setField(loan, "charges", null);

        final Collection<LoanCharge> chargeIds = loan.getCharges();

        assertEquals(0, chargeIds.size());
    }

    @Test
    public void updateLoanScheduleRejectsDuplicateInstallmentNumbersInCollection() {
        final Loan loan = new Loan();
        final LoanRepaymentScheduleInstallment firstInstallment = new LoanRepaymentScheduleInstallment(null, 1,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, false, null);
        final LoanRepaymentScheduleInstallment duplicateInstallment = new LoanRepaymentScheduleInstallment(null, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, false, null);

        assertThrows(PlatformApiDataValidationException.class,
                () -> loan.updateLoanSchedule(Arrays.asList(firstInstallment, duplicateInstallment)));
    }

    @Test
    public void updateLoanScheduleRejectsDuplicateInstallmentNumbersInModel() {
        final Loan loan = new Loan();
        final LoanScheduleModelPeriod firstPeriod = mock(LoanScheduleModelPeriod.class);
        when(firstPeriod.isRepaymentPeriod()).thenReturn(true);
        when(firstPeriod.periodNumber()).thenReturn(1);
        when(firstPeriod.periodFromDate()).thenReturn(LocalDate.of(2026, 5, 1));
        when(firstPeriod.periodDueDate()).thenReturn(LocalDate.of(2026, 5, 31));
        when(firstPeriod.principalDue()).thenReturn(BigDecimal.TEN);
        when(firstPeriod.interestDue()).thenReturn(BigDecimal.ONE);
        when(firstPeriod.feeChargesDue()).thenReturn(BigDecimal.ZERO);
        when(firstPeriod.penaltyChargesDue()).thenReturn(BigDecimal.ZERO);
        when(firstPeriod.isRecalculatedInterestComponent()).thenReturn(false);

        final LoanScheduleModelPeriod duplicatePeriod = mock(LoanScheduleModelPeriod.class);
        when(duplicatePeriod.isRepaymentPeriod()).thenReturn(true);
        when(duplicatePeriod.periodNumber()).thenReturn(1);
        when(duplicatePeriod.periodFromDate()).thenReturn(LocalDate.of(2026, 6, 1));
        when(duplicatePeriod.periodDueDate()).thenReturn(LocalDate.of(2026, 6, 30));
        when(duplicatePeriod.principalDue()).thenReturn(BigDecimal.TEN);
        when(duplicatePeriod.interestDue()).thenReturn(BigDecimal.ONE);
        when(duplicatePeriod.feeChargesDue()).thenReturn(BigDecimal.ZERO);
        when(duplicatePeriod.penaltyChargesDue()).thenReturn(BigDecimal.ZERO);
        when(duplicatePeriod.isRecalculatedInterestComponent()).thenReturn(false);

        final LoanScheduleModel loanScheduleModel = LoanScheduleModel.from(Arrays.asList(firstPeriod, duplicatePeriod), null, 0, null,
                null, null, null, null, null, null, null);

        assertThrows(PlatformApiDataValidationException.class, () -> loan.updateLoanSchedule(loanScheduleModel));
    }

    @Test
    public void loanReversalTransactionKeepsReversedFlagsFalse() {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(1L);
        final Loan loan = mock(Loan.class);
        when(loan.getNetDisbursalAmount()).thenReturn(BigDecimal.ZERO);
        final LoanTransaction originalTransaction = new LoanTransaction();
        ReflectionTestUtils.setField(originalTransaction, "id", 10L);
        ReflectionTestUtils.setField(originalTransaction, "loan", loan);
        ReflectionTestUtils.setField(originalTransaction, "office", office);
        ReflectionTestUtils.setField(originalTransaction, "typeOf", LoanTransactionType.RECOVERY_REPAYMENT.getValue());
        ReflectionTestUtils.setField(originalTransaction, "dateOf", LocalDate.of(2026, 5, 25));
        ReflectionTestUtils.setField(originalTransaction, "amount", BigDecimal.valueOf(100));

        final LoanTransaction reversalTransaction = LoanTransaction.reversal(originalTransaction, LocalDate.of(2026, 5, 25), null);
        final Map<String, Object> accountingData = reversalTransaction.toMapData(new CurrencyData("KES", "Kenyan Shilling", 2, 1, "KSh",
                "currency.KES"));

        assertTrue(reversalTransaction.isReversalTransaction());
        assertFalse(reversalTransaction.isReversed());
        assertFalse(reversalTransaction.isManuallyAdjustedOrReversed());
        assertFalse(reversalTransaction.isRecoveryRepayment());
        assertTrue(reversalTransaction.isRecoveryRepaymentType());
        assertEquals(Boolean.TRUE, accountingData.get("reversed"));
    }

    @Test
    public void disbursementChargeAdjustmentKeepsAmountPositiveAndStoresDirectionOnFeePortion() {
        final Office office = mock(Office.class);
        final Loan loan = mock(Loan.class);

        final LoanTransaction increaseAdjustment = LoanTransaction.disbursementChargeAdjustment(loan, office,
                Money.of(KES, new BigDecimal("50.00")), LocalDate.of(2026, 5, 25), false);
        final LoanTransaction decreaseAdjustment = LoanTransaction.disbursementChargeAdjustment(loan, office,
                Money.of(KES, new BigDecimal("50.00")), LocalDate.of(2026, 5, 25), true);

        assertEquals(LoanTransactionType.DISBURSEMENT_CHARGE_ADJUSTMENT, increaseAdjustment.getTypeOf());
        assertFalse(increaseAdjustment.isNonMonetaryTransaction());
        assertFalse(increaseAdjustment.isPaymentTransaction());
        assertEquals(0, new BigDecimal("50.00").compareTo(increaseAdjustment.getAmount(KES).getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(increaseAdjustment.getFeeChargesPortion(KES).getAmount()));

        assertEquals(LoanTransactionType.DISBURSEMENT_CHARGE_ADJUSTMENT, decreaseAdjustment.getTypeOf());
        assertFalse(decreaseAdjustment.isNonMonetaryTransaction());
        assertFalse(decreaseAdjustment.isPaymentTransaction());
        assertEquals(0, new BigDecimal("50.00").compareTo(decreaseAdjustment.getAmount(KES).getAmount()));
        assertEquals(0, new BigDecimal("-50.00").compareTo(decreaseAdjustment.getFeeChargesPortion(KES).getAmount()));
    }

    @Test
    public void disbursementChargeAdjustmentMapDataDisplaysAbsoluteFeePortion() {
        final Office office = mock(Office.class);
        final Loan loan = mock(Loan.class);
        when(office.getId()).thenReturn(1L);
        when(loan.getNetDisbursalAmount()).thenReturn(BigDecimal.ZERO);
        final LoanTransaction decreaseAdjustment = LoanTransaction.disbursementChargeAdjustment(loan, office,
                Money.of(KES, new BigDecimal("600.00")), LocalDate.of(2026, 5, 25), true);

        final Map<String, Object> transactionData = decreaseAdjustment.toMapData(new CurrencyData("KES", "Kenyan Shilling", 2, 1, "KSh",
                "currency.KES"));

        assertEquals(0, new BigDecimal("-600.00").compareTo(decreaseAdjustment.getFeeChargesPortion(KES).getAmount()));
        assertEquals(0, new BigDecimal("600.00").compareTo((BigDecimal) transactionData.get("feeChargesPortion")));
    }

    @Test
    public void disbursementChargeAdjustmentDataDisplaysAbsoluteFeePortion() {
        final LoanTransactionData transactionData = new LoanTransactionData(1L,
                LoanEnumerations.transactionType(LoanTransactionType.DISBURSEMENT_CHARGE_ADJUSTMENT),
                LocalDate.of(2026, 5, 25), new BigDecimal("600.00"), null, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-600.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null);

        final BigDecimal feeChargesPortion = (BigDecimal) ReflectionTestUtils.getField(transactionData, "feeChargesPortion");

        assertEquals(0, new BigDecimal("600.00").compareTo(feeChargesPortion));
    }

    @Test
    public void repaymentAtDisbursementCanSeparateChargeFeePaidFromOverpaymentWithoutChangingPaidPool() {
        final Office office = mock(Office.class);
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(office,
                Money.of(KES, new BigDecimal("100.00")), null, LocalDate.of(2026, 5, 25), null);

        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("50.00")),
                Money.zero(KES), Money.of(KES, new BigDecimal("50.00")));

        assertEquals(0, new BigDecimal("100.00").compareTo(repaymentAtDisbursement.getAmount(KES).getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(repaymentAtDisbursement.getFeeChargesPortion(KES).getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(repaymentAtDisbursement.getOverPaymentPortion(KES).getAmount()));
    }

    @Test
    public void repaymentAtDisbursementOverpaymentContributesToLoanTotalOverpayment() {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("100.00")), null, LocalDate.of(2026, 5, 25), null);
        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("50.00")),
                Money.zero(KES), Money.of(KES, new BigDecimal("50.00")));
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "loanTransactions", Collections.singletonList(repaymentAtDisbursement));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.emptyList());

        final Money totalOverpayment = ReflectionTestUtils.invokeMethod(loan, "calculateTotalOverpayment");

        assertEquals(0, new BigDecimal("50.00").compareTo(totalOverpayment.getAmount()));
    }

    @Test
    public void linkedRepaymentAtDisbursementAmountDifferenceDoesNotImplyLoanTotalOverpayment() {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        final LoanCharge disbursementCharge = buildDisbursementLoanCharge(loan, new BigDecimal("1000.00"));
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("2000.00")), null, LocalDate.of(2026, 5, 8), null);
        repaymentAtDisbursement.getLoanChargesPaid().add(new LoanChargePaidBy(repaymentAtDisbursement, disbursementCharge,
                new BigDecimal("1000.00"), null));
        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("1000.00")),
                Money.zero(KES), Money.zero(KES));
        ReflectionTestUtils.setField(repaymentAtDisbursement, "amount", new BigDecimal("2000.00"));
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "loanTransactions", Collections.singletonList(repaymentAtDisbursement));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.emptyList());

        final Money totalOverpayment = ReflectionTestUtils.invokeMethod(loan, "calculateTotalOverpayment");

        assertEquals(0, BigDecimal.ZERO.compareTo(totalOverpayment.getAmount()));
    }

    @Test
    public void linkedRepaymentAtDisbursementRepaymentValueCannotCreateNegativeLoanTotalOverpayment() {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1,
                LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 8), new BigDecimal("300.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installment.payPrincipalComponent(LocalDate.of(2026, 5, 8), Money.of(KES, new BigDecimal("300.00")));
        final LoanCharge disbursementCharge = buildDisbursementLoanCharge(loan, new BigDecimal("500.00"));
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("800.00")), null, LocalDate.of(2026, 5, 8), null);
        repaymentAtDisbursement.getLoanChargesPaid().add(new LoanChargePaidBy(repaymentAtDisbursement, disbursementCharge,
                new BigDecimal("500.00"), null));
        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("500.00")),
                Money.zero(KES));
        repaymentAtDisbursement.updateComponents(Money.of(KES, new BigDecimal("300.00")), Money.zero(KES),
                Money.zero(KES), Money.zero(KES));
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "loanTransactions", Collections.singletonList(repaymentAtDisbursement));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.singletonList(installment));

        final Money totalOverpayment = ReflectionTestUtils.invokeMethod(loan, "calculateTotalOverpayment");

        assertEquals(0, BigDecimal.ZERO.compareTo(totalOverpayment.getAmount()));
    }

    @Test
    public void linkedRepaymentAtDisbursementChargePaidAmountIsExcludedFromLoanTotalOverpayment() {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        final LoanCharge disbursementCharge = buildDisbursementLoanCharge(loan, new BigDecimal("500.00"));
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("800.00")), null, LocalDate.of(2026, 5, 8), null);
        repaymentAtDisbursement.getLoanChargesPaid().add(new LoanChargePaidBy(repaymentAtDisbursement, disbursementCharge,
                new BigDecimal("500.00"), null));
        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("500.00")),
                Money.zero(KES), Money.of(KES, new BigDecimal("300.00")));
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "loanTransactions", Collections.singletonList(repaymentAtDisbursement));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.emptyList());

        final Money totalOverpayment = ReflectionTestUtils.invokeMethod(loan, "calculateTotalOverpayment");

        assertEquals(0, new BigDecimal("300.00").compareTo(totalOverpayment.getAmount()));
    }

    @Test
    public void updateLoanSummaryAndStatusMarksLoanOverpaidAfterRepaymentAtDisbursementOverpayment() {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        when(loanProductRelatedDetail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("100.00")));
        final LocalDate disbursementDate = LocalDate.of(2026, 5, 8);
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, disbursementDate,
                LocalDate.of(2026, 6, 8), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                null);
        installment.payPrincipalComponent(LocalDate.of(2026, 6, 8), Money.of(KES, new BigDecimal("100.00")));
        final LoanTransaction disbursement = LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal("100.00")),
                null, disbursementDate, null);
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("100.00")),
                null, LocalDate.of(2026, 6, 8), null);
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("125.00")), null, disbursementDate, null);
        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("100.00")),
                Money.zero(KES), Money.of(KES, new BigDecimal("25.00")));
        disbursement.updateLoan(loan);
        repayment.updateLoan(loan);
        repaymentAtDisbursement.updateLoan(loan);

        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", disbursementDate);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", disbursementDate);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));
        ReflectionTestUtils.setField(loan, "loanSummaryWrapper", new LoanSummaryWrapper());
        ReflectionTestUtils.setField(loan, "loanLifecycleStateMachine",
                new DefaultLoanLifecycleStateMachine(Arrays.asList(LoanStatus.values())));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.singletonList(installment));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(Collections.singletonList(disbursement)));
        loan.addLoanTransaction(repaymentAtDisbursement);
        loan.addLoanTransaction(repayment);
        ReflectionTestUtils.setField(loan, "charges", Collections.emptySet());

        loan.updateLoanSummarAndStatus();

        assertEquals(LoanStatus.OVERPAID.getValue(), ReflectionTestUtils.getField(loan, "loanStatus"));
        assertEquals(0, new BigDecimal("25.00").compareTo((BigDecimal) ReflectionTestUtils.getField(loan, "totalOverpaid")));
    }

    @Test
    public void calculateTotalOverpaymentTreatsDepositRedrawAsConsumedOverpayment() {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        when(loanProductRelatedDetail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("100.00")));
        final LocalDate disbursementDate = LocalDate.of(2026, 5, 8);
        final LocalDate repaymentDate = LocalDate.of(2026, 6, 8);
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, disbursementDate,
                repaymentDate, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installment.payPrincipalComponent(repaymentDate, Money.of(KES, new BigDecimal("100.00")));
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("125.00")), null,
                repaymentDate, null);
        final LoanTransaction depositRedraw = LoanTransaction.applyRedrawRepayment(mock(Office.class), Money.of(KES, new BigDecimal("25.00")),
                null, repaymentDate, null, loan);
        repayment.updateLoan(loan);
        depositRedraw.updateLoan(loan);

        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.singletonList(installment));
        ReflectionTestUtils.setField(loan, "loanTransactions", Arrays.asList(repayment, depositRedraw));

        final Money totalOverpayment = ReflectionTestUtils.invokeMethod(loan, "calculateTotalOverpayment");

        assertEquals(0, BigDecimal.ZERO.compareTo(totalOverpayment.getAmount()));
    }

    @Test
    public void updateLoanSummaryAndStatusClosesLoanAfterDepositRedrawConsumesOverpayment() {
        final Loan loan = new Loan();
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(KES);
        when(loanProductRelatedDetail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("100.00")));
        final LocalDate disbursementDate = LocalDate.of(2026, 5, 8);
        final LocalDate repaymentDate = LocalDate.of(2026, 6, 8);
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, disbursementDate,
                repaymentDate, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installment.payPrincipalComponent(repaymentDate, Money.of(KES, new BigDecimal("100.00")));
        final LoanTransaction disbursement = LoanTransaction.disbursement(mock(Office.class), Money.of(KES, new BigDecimal("100.00")), null,
                disbursementDate, null);
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("125.00")), null,
                repaymentDate, null);
        final LoanTransaction depositRedraw = LoanTransaction.applyRedrawRepayment(mock(Office.class), Money.of(KES, new BigDecimal("25.00")),
                null, repaymentDate, null, loan);
        disbursement.updateLoan(loan);
        repayment.updateLoan(loan);
        depositRedraw.updateLoan(loan);

        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", disbursementDate);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", disbursementDate);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", loanProductRelatedDetail);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));
        ReflectionTestUtils.setField(loan, "loanSummaryWrapper", new LoanSummaryWrapper());
        ReflectionTestUtils.setField(loan, "loanLifecycleStateMachine",
                new DefaultLoanLifecycleStateMachine(Arrays.asList(LoanStatus.values())));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.singletonList(installment));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(Collections.singletonList(disbursement)));
        loan.addLoanTransaction(repayment);
        loan.addLoanTransaction(depositRedraw);
        ReflectionTestUtils.setField(loan, "charges", Collections.emptySet());

        loan.updateLoanSummarAndStatus();

        assertEquals(LoanStatus.CLOSED_OBLIGATIONS_MET.getValue(), ReflectionTestUtils.getField(loan, "loanStatus"));
        assertEquals(repaymentDate, ReflectionTestUtils.getField(loan, "closedOnDate"));
        assertEquals(null, ReflectionTestUtils.getField(loan, "totalOverpaid"));
    }

    // CGLT-592: the "closed with arrears" defect. A payment that leaves a balance outstanding must not be swept
    // into a deposit redraw, so an active loan can never be flipped to OVERPAID and closed while it still owes.

    @Test
    public void redrawSweepIsBlockedWhileTheLoanStillHasAnOutstandingBalance() {
        final Loan loan = redrawGateLoan(new BigDecimal("40.00"), new BigDecimal("40.00"));
        assertFalse(loan.isGenuineOverpaymentReadyForRedraw());
    }

    @Test
    public void redrawSweepIsAllowedOnlyWhenTheLoanIsFullySettledAndGenuinelyOverpaid() {
        final Loan loan = redrawGateLoan(BigDecimal.ZERO, new BigDecimal("25.00"));
        assertTrue(loan.isGenuineOverpaymentReadyForRedraw());
    }

    @Test
    public void redrawSweepIsBlockedWhenSettledButNotOverpaid() {
        final Loan loan = redrawGateLoan(BigDecimal.ZERO, null);
        assertFalse(loan.isGenuineOverpaymentReadyForRedraw());
    }

    @Test
    public void redrawSweepIsBlockedWhenThereIsNoOverpaymentEvenWithZeroOutstanding() {
        final Loan loan = redrawGateLoan(BigDecimal.ZERO, BigDecimal.ZERO);
        assertFalse(loan.isGenuineOverpaymentReadyForRedraw());
    }

    @Test
    public void falseOverpaymentDoesNotMarkLoanAsOverpaidWhileBalanceIsOutstanding() {
        final Loan loan = new Loan();
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), new BigDecimal("100.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("125.00")),
                null, LocalDate.of(2026, 5, 25), null);
        final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
        ReflectionTestUtils.setField(summary, "totalOutstanding", new BigDecimal("100.00"));
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", mutableScheduleDetail(new BigDecimal("100.00")));
        ReflectionTestUtils.setField(loan, "summary", summary);
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.singletonList(installment));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(Collections.singletonList(repayment)));

        final Money calculatedOverpayment = ReflectionTestUtils.invokeMethod(loan, "calculateTotalOverpayment");

        assertTrue(calculatedOverpayment.isGreaterThanZero());
        final Boolean isOverpaid = ReflectionTestUtils.invokeMethod(loan, "isOverPaid");
        assertFalse(isOverpaid);
    }

    private Loan redrawGateLoan(final BigDecimal totalOutstanding, final BigDecimal totalOverpaid) {
        final Loan loan = new Loan();
        final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
        ReflectionTestUtils.setField(summary, "totalOutstanding", totalOutstanding);
        ReflectionTestUtils.setField(loan, "summary", summary);
        ReflectionTestUtils.setField(loan, "totalOverpaid", totalOverpaid);
        return loan;
    }

    @Test
    public void disbursementChargeAdjustmentRecomputesPaidAndOutstandingAmounts() {
        final LoanCharge loanCharge = buildLoanCharge();

        loanCharge.updateAmountPaidForDisbursementChargeAdjustment(new BigDecimal("100.00"), new BigDecimal("50.00"));

        assertFalse(loanCharge.isPaid());
        assertEquals(0, new BigDecimal("100.00").compareTo(loanCharge.getAmount(KES).getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(loanCharge.getAmountPaid(KES).getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(loanCharge.getAmountOutstanding(KES).getAmount()));

        loanCharge.updateAmountPaidForDisbursementChargeAdjustment(new BigDecimal("50.00"), new BigDecimal("50.00"));

        assertTrue(loanCharge.isPaid());
        assertEquals(0, new BigDecimal("50.00").compareTo(loanCharge.getAmount(KES).getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(loanCharge.getAmountPaid(KES).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(loanCharge.getAmountOutstanding(KES).getAmount()));

        loanCharge.updateAmountPaidForDisbursementChargeAdjustment(BigDecimal.ZERO, BigDecimal.ZERO);

        assertTrue(loanCharge.isPaid());
        assertEquals(0, BigDecimal.ZERO.compareTo(loanCharge.getAmount(KES).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(loanCharge.getAmountPaid(KES).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(loanCharge.getAmountOutstanding(KES).getAmount()));
    }

    @Test
    public void singleDisbursementKeepsGrossDisbursementDetailPrincipalWhenSubmittedDisbursementAmountIsNet() {
        final Loan loan = new Loan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail scheduleDetail = mutableScheduleDetail(new BigDecimal("5000.00"));
        final LoanDisbursementDetails disbursementDetails = new LoanDisbursementDetails(LocalDate.of(2026, 6, 8), null,
                new BigDecimal("5000.00"), new BigDecimal("4200.00"));
        final LoanCharge disbursementCharge = buildDisbursementLoanCharge(loan, new BigDecimal("800.00"));
        final JsonCommand command = jsonCommand("{\"transactionAmount\":4200,\"netDisbursalAmount\":4200,\"locale\":\"en\"}");

        when(loanProduct.isMultiDisburseLoan()).thenReturn(false);

        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", scheduleDetail);
        ReflectionTestUtils.setField(loan, "approvedPrincipal", new BigDecimal("5000.00"));
        ReflectionTestUtils.setField(loan, "charges", Set.of(disbursementCharge));
        ReflectionTestUtils.setField(loan, "disbursementDetails", new ArrayList<>(Collections.singletonList(disbursementDetails)));
        disbursementDetails.updateLoan(loan);

        final Money disburseAmount = loan.adjustDisburseAmount(command, LocalDate.of(2026, 6, 8));

        assertEquals(0, new BigDecimal("5000.00").compareTo(disburseAmount.getAmount()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(disbursementDetails.principal()));
    }

    @Test
    public void multiDisbursementBooksNextTranchePrincipalWhenSubmittedAmountIsNet() {
        final Loan loan = new Loan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail scheduleDetail = mutableScheduleDetail(new BigDecimal("10000000.00"));
        final LoanDisbursementDetails firstTranche = new LoanDisbursementDetails(LocalDate.of(2026, 8, 25), null,
                new BigDecimal("3000000.00"), new BigDecimal("2940000.00"));
        final LoanDisbursementDetails secondTranche = new LoanDisbursementDetails(LocalDate.of(2026, 9, 25), null,
                new BigDecimal("7000000.00"), new BigDecimal("7000000.00"));
        final JsonCommand command = jsonCommand("{\"transactionAmount\":2940000,\"netDisbursalAmount\":2940000,\"locale\":\"en\"}");

        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);

        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", scheduleDetail);
        ReflectionTestUtils.setField(loan, "approvedPrincipal", new BigDecimal("10000000.00"));
        ReflectionTestUtils.setField(loan, "disbursementDetails", new ArrayList<>(List.of(firstTranche, secondTranche)));
        firstTranche.updateLoan(loan);
        secondTranche.updateLoan(loan);

        final Money disburseAmount = loan.adjustDisburseAmount(command, LocalDate.of(2026, 8, 25));

        assertEquals(0, new BigDecimal("3000000.00").compareTo(disburseAmount.getAmount()));
        assertEquals(LocalDate.of(2026, 8, 25), firstTranche.actualDisbursementDate());
        assertNull(secondTranche.actualDisbursementDate());
        assertEquals(0, new BigDecimal("3000000.00").compareTo(firstTranche.principal()));
    }

    @Test
    public void multiDisbursementRejectsFutureTrancheBeforeItsExpectedDate() {
        final Loan loan = new Loan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail scheduleDetail = mutableScheduleDetail(new BigDecimal("7000000.00"));
        final LoanDisbursementDetails futureTranche = new LoanDisbursementDetails(LocalDate.of(2026, 12, 15), null,
                new BigDecimal("7000000.00"), new BigDecimal("7000000.00"));
        final JsonCommand command = jsonCommand("{\"transactionAmount\":7000000,\"locale\":\"en\"}");

        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", scheduleDetail);
        ReflectionTestUtils.setField(loan, "approvedPrincipal", new BigDecimal("7000000.00"));
        ReflectionTestUtils.setField(loan, "disbursementDetails", new ArrayList<>(Collections.singletonList(futureTranche)));
        futureTranche.updateLoan(loan);

        final LoanDisbursalException exception = assertThrows(LoanDisbursalException.class,
                () -> loan.adjustDisburseAmount(command, LocalDate.of(2026, 8, 24)));

        assertEquals("error.msg.loan.actualdisbursementdate.before.expectedtranchedate", exception.getGlobalisationMessageCode());
        assertNull(futureTranche.actualDisbursementDate());
    }

    @Test
    public void multiDisbursementPrepaymentPrincipalIsLimitedToDisbursedExposure() {
        final Loan loan = new Loan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail scheduleDetail = mutableScheduleDetail(new BigDecimal("10000000.00"));
        final LocalDate prepaymentDate = LocalDate.of(2026, 8, 25);
        final LoanDisbursementDetails disbursedTranche = new LoanDisbursementDetails(prepaymentDate, prepaymentDate,
                new BigDecimal("3000000.00"), new BigDecimal("2940000.00"));
        final LoanDisbursementDetails pendingTranche = new LoanDisbursementDetails(LocalDate.of(2026, 12, 1), null,
                new BigDecimal("7000000.00"), new BigDecimal("7000000.00"));
        final LoanRepaymentScheduleInstallment inflatedInstallment = new LoanRepaymentScheduleInstallment(null, 1, prepaymentDate,
                LocalDate.of(2027, 2, 25), new BigDecimal("9500000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);

        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        ReflectionTestUtils.setField(summary, "totalPrincipalRepaid", new BigDecimal("500000.00"));
        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", scheduleDetail);
        ReflectionTestUtils.setField(loan, "summary", summary);
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", new ArrayList<>(List.of(inflatedInstallment)));
        ReflectionTestUtils.setField(loan, "disbursementDetails", new ArrayList<>(List.of(disbursedTranche, pendingTranche)));

        final LoanRepaymentScheduleInstallment prepayment = loan.fetchPrepaymentDetail(null, prepaymentDate);

        assertEquals(0, new BigDecimal("2500000.00").compareTo(prepayment.getPrincipalOutstanding(KES).getAmount()));
    }

    @Test
    public void fullyRepaidMultiDisbursementLoanRemainsActiveWithPendingApprovedTranche() {
        final Loan loan = new Loan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LocalDate disbursementDate = LocalDate.of(2026, 8, 25);
        final LocalDate repaymentDate = LocalDate.of(2026, 9, 25);
        final LoanDisbursementDetails disbursedTranche = new LoanDisbursementDetails(disbursementDate, disbursementDate,
                new BigDecimal("1000000.00"), new BigDecimal("1000000.00"));
        final LoanDisbursementDetails pendingTranche = new LoanDisbursementDetails(LocalDate.of(2026, 12, 1), null,
                new BigDecimal("1000000.00"), new BigDecimal("1000000.00"));
        final LoanProductRelatedDetail scheduleDetail = mock(LoanProductRelatedDetail.class);
        when(scheduleDetail.getCurrency()).thenReturn(KES);
        when(scheduleDetail.getPrincipal()).thenReturn(Money.of(KES, new BigDecimal("1000000.00")));
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, disbursementDate,
                repaymentDate, new BigDecimal("1000000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installment.payPrincipalComponent(repaymentDate, Money.of(KES, new BigDecimal("1000000.00")));
        final LoanTransaction disbursement = LoanTransaction.disbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("1000000.00")), null, disbursementDate, null);
        final LoanTransaction repayment = LoanTransaction.repayment(mock(Office.class), Money.of(KES, new BigDecimal("1000000.00")),
                null, repaymentDate, null);
        disbursement.updateLoan(loan);
        repayment.updateLoan(loan);

        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", disbursementDate);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", disbursementDate);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", scheduleDetail);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));
        ReflectionTestUtils.setField(loan, "loanSummaryWrapper", new LoanSummaryWrapper());
        ReflectionTestUtils.setField(loan, "loanLifecycleStateMachine",
                new DefaultLoanLifecycleStateMachine(Arrays.asList(LoanStatus.values())));
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", Collections.singletonList(installment));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(Arrays.asList(disbursement, repayment)));
        ReflectionTestUtils.setField(loan, "charges", Collections.emptySet());
        ReflectionTestUtils.setField(loan, "disbursementDetails", new ArrayList<>(List.of(disbursedTranche, pendingTranche)));

        loan.updateLoanSummarAndStatus();

        assertEquals(LoanStatus.ACTIVE.getValue(), ReflectionTestUtils.getField(loan, "loanStatus"));
        assertNull(ReflectionTestUtils.getField(loan, "closedOnDate"));
        assertEquals(pendingTranche, loan.getNextUndisbursedDisbursementDetail());
    }

    @Test
    public void trancheDisbursementDateAfterMaturityIsRejected() {
        final Loan loan = new Loan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "expectedMaturityDate", LocalDate.of(2027, 2, 28));

        assertThrows(TrancheDisbursementAfterMaturityException.class,
                () -> loan.validateTrancheDisbursementDateIsNotAfterMaturity(LocalDate.of(2027, 3, 1)));
    }

    @Test
    public void trancheDisbursementDateOnMaturityIsAllowed() {
        final Loan loan = new Loan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "expectedMaturityDate", LocalDate.of(2027, 2, 28));

        loan.validateTrancheDisbursementDateIsNotAfterMaturity(LocalDate.of(2027, 2, 28));
    }

    @Test
    public void icReviewWithReducedAmountKeepsAppliedAmountAndUpdatesApprovedAmount() {
        final Loan loan = newLoanForIcReview(new BigDecimal("5000.00"));
        final LoanProductRelatedDetail scheduleDetail = (LoanProductRelatedDetail) ReflectionTestUtils.getField(loan,
                "loanRepaymentScheduleDetail");
        final JsonCommand command = jsonCommand("{\"icReviewRecommendedAmount\":4000,\"locale\":\"en\"}");

        loan.loanApplicationICReview(null, command);

        // Applied amount (original client request) must never be overwritten by the review.
        assertEquals(0, new BigDecimal("5000.00").compareTo(loan.getProposedPrincipal()));
        // Approved/IC-review/working principal track the latest recommendation.
        assertEquals(0, new BigDecimal("4000").compareTo(loan.getApprovedPrincipal()));
        assertEquals(0, new BigDecimal("4000").compareTo(loan.getApprovedICReview()));
        assertEquals(0, new BigDecimal("4000").compareTo(scheduleDetail.getPrincipal().getAmount()));
    }

    @Test
    public void icReviewWithEqualAmountKeepsAppliedAmountUnchanged() {
        final Loan loan = newLoanForIcReview(new BigDecimal("5000.00"));
        final JsonCommand command = jsonCommand("{\"icReviewRecommendedAmount\":5000,\"locale\":\"en\"}");

        loan.loanApplicationICReview(null, command);

        assertEquals(0, new BigDecimal("5000.00").compareTo(loan.getProposedPrincipal()));
        assertEquals(0, new BigDecimal("5000").compareTo(loan.getApprovedPrincipal()));
    }

    @Test
    public void icReviewRejectsRecommendedAmountGreaterThanAppliedAmount() {
        final Loan loan = newLoanForIcReview(new BigDecimal("5000.00"));
        final JsonCommand command = jsonCommand("{\"icReviewRecommendedAmount\":6000,\"locale\":\"en\"}");

        // The system disallows a recommendation above the applied amount, so applied < approved cannot occur.
        assertThrows(InvalidLoanStateTransitionException.class, () -> loan.loanApplicationICReview(null, command));
        assertEquals(0, new BigDecimal("5000.00").compareTo(loan.getProposedPrincipal()));
    }

    private Loan newLoanForIcReview(final BigDecimal appliedAmount) {
        final Loan loan = new Loan();
        ReflectionTestUtils.setField(loan, "loanProduct", mock(LoanProduct.class));
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", mutableScheduleDetail(appliedAmount));
        ReflectionTestUtils.setField(loan, "proposedPrincipal", appliedAmount);
        ReflectionTestUtils.setField(loan, "approvedPrincipal", appliedAmount);
        ReflectionTestUtils.setField(loan, "approvedICReview", appliedAmount);
        return loan;
    }

    /**
     * Builds a new loan charge.
     *
     * @return the {@link LoanCharge}
     */
    private LoanCharge buildLoanCharge() {
        return new LoanCharge(mock(Loan.class), mock(Charge.class), new BigDecimal(100), new BigDecimal(100),
                ChargeTimeType.TRANCHE_DISBURSEMENT, ChargeCalculationType.FLAT, LocalDate.of(2022, 6, 27), ChargePaymentMode.REGULAR, 1,
                new BigDecimal(100));
    }

    private LoanCharge buildDisbursementLoanCharge(final Loan loan, final BigDecimal amount) {
        return new LoanCharge(loan, mock(Charge.class), amount, amount, ChargeTimeType.DISBURSEMENT, ChargeCalculationType.FLAT,
                LocalDate.of(2026, 6, 8), ChargePaymentMode.REGULAR, 1, amount);
    }

    private LoanProductRelatedDetail mutableScheduleDetail(final BigDecimal initialPrincipal) {
        final AtomicReference<BigDecimal> principal = new AtomicReference<>(initialPrincipal);
        final LoanProductRelatedDetail scheduleDetail = mock(LoanProductRelatedDetail.class);
        when(scheduleDetail.getCurrency()).thenReturn(KES);
        when(scheduleDetail.getPrincipal()).thenAnswer(invocation -> Money.of(KES, principal.get()));
        doAnswer(invocation -> {
            principal.set(invocation.getArgument(0));
            return null;
        }).when(scheduleDetail).setPrincipal(any(BigDecimal.class));
        return scheduleDetail;
    }

    private JsonCommand jsonCommand(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void undoDisbursalResetsActualDisbursementDateOnSingleDisbursementLoanDetails() {
        final LoanProduct loanProduct = mock(LoanProduct.class);
        when(loanProduct.isMultiDisburseLoan()).thenReturn(false);

        final LoanProductRelatedDetail scheduleDetail = mutableScheduleDetail(new BigDecimal("5000000.00"));
        final LoanLifecycleStateMachine stateMachine = mock(LoanLifecycleStateMachine.class);
        when(stateMachine.transition(any(), any())).thenReturn(LoanStatus.APPROVED);

        final LocalDate expectedDate = LocalDate.of(2026, 7, 7);
        final LocalDate actualDate = LocalDate.of(2026, 7, 7);
        final LoanDisbursementDetails detail = new LoanDisbursementDetails(expectedDate, actualDate, new BigDecimal("5000000.00"), null);

        final Loan loan = new Loan();
        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", scheduleDetail);
        ReflectionTestUtils.setField(loan, "loanLifecycleStateMachine", stateMachine);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));

        final List<LoanDisbursementDetails> details = Collections.singletonList(detail);
        ReflectionTestUtils.setField(loan, "disbursementDetails", details);
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        ReflectionTestUtils.setField(loan, "expectedDisbursementDate", expectedDate);
        ReflectionTestUtils.setField(loan, "actualDisbursementDate", actualDate);
        ReflectionTestUtils.setField(loan, "approvedPrincipal", new BigDecimal("5000000.00"));

        final org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO scheduleGeneratorDTO = mock(
                org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO.class);

        loan.undoDisbursal(scheduleGeneratorDTO, new ArrayList<>(), new ArrayList<>());

        assertNull(detail.actualDisbursementDate());
        assertNull(ReflectionTestUtils.getField(loan, "actualDisbursementDate"));
        assertEquals(LoanStatus.APPROVED.getValue(), loan.getLoanStatus());
    }
}
