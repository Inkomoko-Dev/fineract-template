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
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RepaymentAtDisbursementOverpaymentTransactionProcessorTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 5, 8);

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
    void repaymentAtDisbursementOverpaymentPaysOutstandingInstallmentsBeforeRemainingOverpaid() {
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(null, 1, DISBURSEMENT_DATE,
                DISBURSEMENT_DATE, new BigDecimal("8.33"), new BigDecimal("41.67"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("800.00")), null, DISBURSEMENT_DATE, null);
        repaymentAtDisbursement.updateRepaymentAtDisbursementComponents(Money.of(KES, new BigDecimal("500.00")),
                Money.zero(KES), Money.of(KES, new BigDecimal("300.00")));

        new PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor().handleTransaction(DISBURSEMENT_DATE,
                new ArrayList<>(List.of(repaymentAtDisbursement)), KES, new ArrayList<>(List.of(installment)), Set.of());

        assertAmount("800.00", repaymentAtDisbursement.getAmount(KES).getAmount());
        assertAmount("500.00", repaymentAtDisbursement.getFeeChargesPortion(KES).getAmount());
        assertAmount("8.33", repaymentAtDisbursement.getPrincipalPortion(KES).getAmount());
        assertAmount("41.67", repaymentAtDisbursement.getInterestPortion(KES).getAmount());
        assertAmount("250.00", repaymentAtDisbursement.getOverPaymentPortion(KES).getAmount());
        assertAmount("0.00", installment.getTotalOutstanding(KES).getAmount());
    }

    private void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
