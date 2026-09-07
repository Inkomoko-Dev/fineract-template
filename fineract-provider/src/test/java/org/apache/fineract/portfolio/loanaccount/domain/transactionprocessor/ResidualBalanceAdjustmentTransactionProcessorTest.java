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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResidualBalanceAdjustmentTransactionProcessorTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 1);
    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 9, 3);
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
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, TRANSACTION_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
        ThreadLocalContextUtil.clear();
    }

    @Test
    void cashBasedResidualWritesOffPrincipalAndWaivesIncomeComponents() {
        final LoanRepaymentScheduleInstallment installment = installment();
        final LoanTransaction transaction = residualTransaction();
        final AbstractLoanRepaymentScheduleTransactionProcessor processor = new FineractStyleLoanRepaymentScheduleTransactionProcessor();

        processor.handleResidualBalanceAdjustment(transaction, KES, List.of(installment), true);

        assertThat(installment.getPrincipalWrittenOff(KES).getAmount()).isEqualByComparingTo("0.10");
        assertThat(installment.getInterestWaived(KES).getAmount()).isEqualByComparingTo("0.20");
        assertThat(installment.getFeeChargesWaived(KES).getAmount()).isEqualByComparingTo("0.30");
        assertThat(installment.getPenaltyChargesWaived(KES).getAmount()).isEqualByComparingTo("0.40");
        assertThat(installment.getInterestWrittenOff(KES).isZero()).isTrue();
        assertThat(installment.getFeeChargesWrittenOff(KES).isZero()).isTrue();
        assertThat(installment.getPenaltyChargesWrittenOff(KES).isZero()).isTrue();
        assertThat(transaction.getAmount(KES).getAmount()).isEqualByComparingTo("1.00");
    }

    @Test
    void accrualResidualWritesOffEveryRecognizedComponent() {
        final LoanRepaymentScheduleInstallment installment = installment();
        final LoanTransaction transaction = residualTransaction();
        final AbstractLoanRepaymentScheduleTransactionProcessor processor = new FineractStyleLoanRepaymentScheduleTransactionProcessor();

        processor.handleResidualBalanceAdjustment(transaction, KES, List.of(installment), false);

        assertThat(installment.getPrincipalWrittenOff(KES).getAmount()).isEqualByComparingTo("0.10");
        assertThat(installment.getInterestWrittenOff(KES).getAmount()).isEqualByComparingTo("0.20");
        assertThat(installment.getFeeChargesWrittenOff(KES).getAmount()).isEqualByComparingTo("0.30");
        assertThat(installment.getPenaltyChargesWrittenOff(KES).getAmount()).isEqualByComparingTo("0.40");
        assertThat(transaction.getAmount(KES).getAmount()).isEqualByComparingTo("1.00");
    }

    private LoanRepaymentScheduleInstallment installment() {
        return new LoanRepaymentScheduleInstallment(null, 1, TRANSACTION_DATE.minusMonths(1), TRANSACTION_DATE,
                new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.30"), new BigDecimal("0.40"), false, null);
    }

    private LoanTransaction residualTransaction() {
        return LoanTransaction.residualBalanceAdjustment(null, mock(Office.class), TRANSACTION_DATE, BigDecimal.ONE, null);
    }
}
