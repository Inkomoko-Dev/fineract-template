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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
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
}
