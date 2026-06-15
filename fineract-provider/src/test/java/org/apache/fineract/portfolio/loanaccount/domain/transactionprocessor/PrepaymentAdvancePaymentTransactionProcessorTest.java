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
import java.util.stream.Collectors;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.EarlyPaymentLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.HeavensFamilyLoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class PrepaymentAdvancePaymentTransactionProcessorTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 31);
    private static final LocalDate PREPAYMENT_DATE = LocalDate.of(2026, 1, 16);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, PREPAYMENT_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    static List<AbstractLoanRepaymentScheduleTransactionProcessor> processors() {
        return List.of(new FineractStyleLoanRepaymentScheduleTransactionProcessor(),
                new HeavensFamilyLoanRepaymentScheduleTransactionProcessor(),
                new EarlyPaymentLoanRepaymentScheduleTransactionProcessor());
    }

    @ParameterizedTest
    @MethodSource("processors")
    void advancePrepaymentMapsCurrentAndFuturePrincipalSeparately(final AbstractLoanRepaymentScheduleTransactionProcessor processor) {
        final LoanRepaymentScheduleInstallment currentInstallment = new LoanRepaymentScheduleInstallment(null, 1, FROM_DATE, DUE_DATE,
                new BigDecimal("500.00"), new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        final LoanRepaymentScheduleInstallment futureInstallment = new LoanRepaymentScheduleInstallment(null, 2, DUE_DATE,
                DUE_DATE.plusMonths(1), new BigDecimal("500.00"), new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(List.of(currentInstallment, futureInstallment));

        final LoanTransaction repayment = LoanTransaction.repaymentType(null, mock(Office.class), Money.of(KES, new BigDecimal("1050.00")),
                null, PREPAYMENT_DATE, null);

        processor.handleTransaction(repayment, KES, installments, Set.of());

        final Map<Integer, BigDecimal> principalByInstallment = repayment.getLoanTransactionToRepaymentScheduleMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getLoanRepaymentScheduleInstallment().getInstallmentNumber(),
                        mapping -> defaultZero(mapping.getPrincipalPortion(KES).getAmount()), BigDecimal::add));

        assertAmount("500.00", principalByInstallment.getOrDefault(1, BigDecimal.ZERO));
        assertAmount("500.00", principalByInstallment.getOrDefault(2, BigDecimal.ZERO));
        assertAmount("50.00", repayment.getInterestPortion(KES).getAmount());
        assertAmount("1000.00", repayment.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", currentInstallment.getTotalOutstanding(KES).getAmount());
        assertAmount("0.00", futureInstallment.getTotalOutstanding(KES).getAmount());
    }

    private static BigDecimal defaultZero(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
