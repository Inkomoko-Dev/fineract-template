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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanWritePlatformServiceJpaRepositoryImplTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 6, 8);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 6, 8))));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void refreshDisbursementChargeNetDisbursalAmountRepairsCorruptedSingleDisbursementPrincipal() {
        final LoanDisbursementDetails disbursementDetails = new LoanDisbursementDetails(DISBURSEMENT_DATE, DISBURSEMENT_DATE,
                new BigDecimal("4200.00"), new BigDecimal("4200.00"));
        final Loan loan = singleDisbursementLoan(new BigDecimal("5000.00"), disbursementDetails,
                disbursement(new BigDecimal("5000.00")), repaymentAtDisbursement(new BigDecimal("800.00"), false));

        LoanWritePlatformServiceJpaRepositoryImpl.refreshDisbursementChargeNetDisbursalAmount(loan, null);

        assertAmount("5000.00", disbursementDetails.principal());
        assertAmount("4200.00", disbursementDetails.getNetDisbursalAmount());
        assertAmount("4200.00", loan.getNetDisbursalAmount());
    }

    @Test
    void refreshDisbursementChargeNetDisbursalAmountUsesOnlyActiveRepaymentAtDisbursementTransactions() {
        final LoanDisbursementDetails disbursementDetails = new LoanDisbursementDetails(DISBURSEMENT_DATE, DISBURSEMENT_DATE,
                new BigDecimal("5000.00"), new BigDecimal("4600.00"));
        final Loan loan = singleDisbursementLoan(new BigDecimal("5000.00"), disbursementDetails,
                disbursement(new BigDecimal("5000.00")), repaymentAtDisbursement(new BigDecimal("400.00"), true),
                repaymentAtDisbursement(new BigDecimal("800.00"), false));

        LoanWritePlatformServiceJpaRepositoryImpl.refreshDisbursementChargeNetDisbursalAmount(loan, null);

        assertAmount("5000.00", disbursementDetails.principal());
        assertAmount("4200.00", disbursementDetails.getNetDisbursalAmount());
        assertAmount("4200.00", loan.getNetDisbursalAmount());
    }

    @Test
    void chargePaymentEditRecomputesLoanStatusAfterReprocessingTransactions() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class,
                CALLS_REAL_METHODS);
        final LoanRepositoryWrapper loanRepositoryWrapper = mock(LoanRepositoryWrapper.class);
        final LoanAccountDomainService loanAccountDomainService = mock(LoanAccountDomainService.class);
        ReflectionTestUtils.setField(service, "loanRepositoryWrapper", loanRepositoryWrapper);
        ReflectionTestUtils.setField(service, "loanAccountDomainService", loanAccountDomainService);
        ReflectionTestUtils.setField(service, "loanTransactionRepository", mock(LoanTransactionRepository.class));
        ReflectionTestUtils.setField(service, "accountTransfersWritePlatformService", mock(AccountTransfersWritePlatformService.class));
        final Loan loan = mock(Loan.class);

        ReflectionTestUtils.invokeMethod(service, "recalculateLoanAfterChargePaymentEdit", loan, (LoanCharge) null);

        verify(loan).refreshFeeChargesDueAtDisbursement();
        verify(loan).reprocessTransactions();
        verify(loan).updateLoanSummarAndStatus();
        verify(loanRepositoryWrapper).saveAndFlush(loan);
        verify(loanAccountDomainService).recalculateAccruals(loan);
    }

    @Test
    void chargeAdjustmentAllocationSplitsUpwardRestoreFromFeeReceivableIncrease() {
        final DisbursementChargeAdjustmentAllocation allocation = DisbursementChargeAdjustmentAllocation
                .from(new BigDecimal("500.00"), new BigDecimal("1500.00"), new BigDecimal("1000.00"));

        assertAmount("1000.00", allocation.chargeIncomeIncrease());
        assertAmount("500.00", allocation.customerBalanceIncrease());
        assertAmount("500.00", allocation.feeReceivableIncrease());
        assertAmount("1000.00", allocation.amountAdjustmentTransactionAmount());
    }

    @Test
    void chargeAdjustmentAllocationSplitsDownwardCreditFromFeeReceivableDecrease() {
        final DisbursementChargeAdjustmentAllocation allocation = DisbursementChargeAdjustmentAllocation
                .from(new BigDecimal("1500.00"), new BigDecimal("500.00"), new BigDecimal("1000.00"));

        assertAmount("1000.00", allocation.chargeIncomeDecrease());
        assertAmount("500.00", allocation.customerBalanceDecrease());
        assertAmount("500.00", allocation.feeReceivableDecrease());
        assertAmount("500.00", allocation.amountAdjustmentTransactionAmount());
    }

    @Test
    void chargeAdjustmentAllocationDoesNotCreateCustomerTransactionForUnpaidOnlyDecrease() {
        final DisbursementChargeAdjustmentAllocation allocation = DisbursementChargeAdjustmentAllocation
                .from(new BigDecimal("1500.00"), new BigDecimal("1000.00"), new BigDecimal("500.00"));

        assertAmount("500.00", allocation.chargeIncomeDecrease());
        assertAmount("0.00", allocation.customerBalanceDecrease());
        assertAmount("500.00", allocation.feeReceivableDecrease());
        assertEquals(false, allocation.requiresAmountAdjustmentTransaction());
    }

    @Test
    void validateDisbursementChargeAdjustmentAllowsClosedDisbursedLoanForBalanceCorrection() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class,
                CALLS_REAL_METHODS);
        final Loan loan = mock(Loan.class);
        when(loan.isDisbursed()).thenReturn(true);
        when(loan.isClosed()).thenReturn(true);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "validateLoanCanEditDisbursementChargeAdjustment", loan));
    }

    @Test
    void validateDisbursementChargeAdjustmentRejectsUndisbursedLoan() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class,
                CALLS_REAL_METHODS);
        final Loan loan = mock(Loan.class);
        when(loan.isDisbursed()).thenReturn(false);

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateLoanCanEditDisbursementChargeAdjustment", loan));
    }

    private Loan singleDisbursementLoan(final BigDecimal approvedPrincipal, final LoanDisbursementDetails disbursementDetails,
            final LoanTransaction... transactions) {
        final Loan loan = new TestLoan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail scheduleDetail = mock(LoanProductRelatedDetail.class);
        when(loanProduct.isMultiDisburseLoan()).thenReturn(false);
        when(scheduleDetail.getCurrency()).thenReturn(KES);

        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", scheduleDetail);
        ReflectionTestUtils.setField(loan, "approvedPrincipal", approvedPrincipal);
        ReflectionTestUtils.setField(loan, "netDisbursalAmount", disbursementDetails.getNetDisbursalAmount());
        ReflectionTestUtils.setField(loan, "disbursementDetails", new ArrayList<>(List.of(disbursementDetails)));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(List.of(transactions)));

        disbursementDetails.updateLoan(loan);
        for (final LoanTransaction transaction : transactions) {
            transaction.updateLoan(loan);
        }
        return loan;
    }

    private LoanTransaction disbursement(final BigDecimal amount) {
        return LoanTransaction.disbursement(mock(Office.class), Money.of(KES, amount), null, DISBURSEMENT_DATE, null);
    }

    private LoanTransaction repaymentAtDisbursement(final BigDecimal amount, final boolean reversed) {
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class), Money.of(KES, amount),
                null, DISBURSEMENT_DATE, null);
        if (reversed) {
            repaymentAtDisbursement.reverse();
        }
        return repaymentAtDisbursement;
    }

    private void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static final class TestLoan extends Loan {

        private TestLoan() {}
    }
}
