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
package org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.CreocoreLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.RBILoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A Waive-Interest transaction must only ever reduce interest, never principal. When such a transaction falls in advance
 * of a still-open installment (e.g. replayed during a reverse-and-replay reprocess), the fork's advance-payment handler
 * (handleAdvancePaymentWithAccruedInterest) previously allocated it to principal, corrupting the loan balance. These
 * processors must route waivers through the interest-only waiver path regardless of advance/late/on-time timing.
 */
class WaiverAdvanceAllocationTransactionProcessorTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    // Before the installment due date -> classified as an "in advance" transaction.
    private static final LocalDate WAIVER_DATE = LocalDate.of(2026, 1, 16);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, WAIVER_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    static List<AbstractLoanRepaymentScheduleTransactionProcessor> processors() {
        return List.of(new PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(),
                new InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(),
                new CreocoreLoanRepaymentScheduleTransactionProcessor(),
                new FineractStyleLoanRepaymentScheduleTransactionProcessor(),
                new RBILoanRepaymentScheduleTransactionProcessor());
    }

    @ParameterizedTest
    @MethodSource("processors")
    void interestWaiverInAdvanceOfInstallmentWaivesInterestAndNeverTouchesPrincipal(
            final AbstractLoanRepaymentScheduleTransactionProcessor processor) {
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(null, 1, FROM_DATE, DUE_DATE,
                new BigDecimal("500.00"), new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(List.of(installment));

        final Money waiveAmount = Money.of(KES, new BigDecimal("50.00"));
        final LoanTransaction waiver = LoanTransaction.waiver(mock(Office.class), null, waiveAmount, WAIVER_DATE, waiveAmount,
                Money.zero(KES), null);

        processor.handleTransaction(waiver, KES, installments, Set.of());

        // The whole waiver must land on interest, none on principal.
        assertAmount("50.00", waiver.getInterestPortion(KES).getAmount());
        assertAmount("0.00", waiver.getPrincipalPortion(KES).getAmount());
        // Installment interest waived to zero; principal outstanding must be untouched.
        assertAmount("0.00", installment.getInterestOutstanding(KES).getAmount());
        assertAmount("50.00", installment.getInterestWaived(KES).getAmount());
        assertAmount("500.00", installment.getPrincipalOutstanding(KES).getAmount());
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
