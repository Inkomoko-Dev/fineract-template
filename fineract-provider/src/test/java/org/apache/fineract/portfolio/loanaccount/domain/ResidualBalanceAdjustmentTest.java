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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResidualBalanceAdjustmentTest {

    @Test
    void transactionTypeUsesDedicatedNonConflictingId() {
        assertThat(LoanTransactionType.fromInt(32)).isEqualTo(LoanTransactionType.RESIDUAL_BALANCE_ADJUSTMENT);
        assertThat(LoanTransactionType.fromInt(32).isResidualBalanceAdjustment()).isTrue();
        assertThat(LoanTransactionType.fromInt(31)).isEqualTo(LoanTransactionType.PARTIAL_WRITEOFF);
    }

    @Test
    void transactionEnumerationIsVisibleToApiClients() {
        final LoanTransactionEnumData data = LoanEnumerations
                .transactionType(LoanTransactionType.RESIDUAL_BALANCE_ADJUSTMENT);

        assertThat(data.getValue()).isEqualTo("Residual Balance Adjustment");
        assertThat(data.isResidualBalanceAdjustment()).isTrue();
    }

    @Test
    void negativeOverpaymentIsNotEligible() {
        final Loan loan = eligibleLoanProductConfiguration(new BigDecimal("0.50"));
        final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
        summary.updateTotalOutstanding(BigDecimal.ZERO);
        ReflectionTestUtils.setField(loan, "summary", summary);
        ReflectionTestUtils.setField(loan, "totalOverpaid", new BigDecimal("0.25"));

        assertThat(loan.applyResidualBalanceAdjustment(LocalDate.of(2026, 9, 1))).isNull();
    }

    @Test
    void thresholdComesFromLoanProduct() {
        final Loan loan = eligibleLoanProductConfiguration(new BigDecimal("0.50"));
        final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
        summary.updateTotalOutstanding(new BigDecimal("0.51"));
        ReflectionTestUtils.setField(loan, "summary", summary);

        assertThat(loan.applyResidualBalanceAdjustment(LocalDate.of(2026, 9, 1))).isNull();
    }

    private Loan eligibleLoanProductConfiguration(final BigDecimal threshold) {
        final Loan loan = new Loan();
        final LoanProduct product = mock(LoanProduct.class);
        when(product.isResidualAutoCloseEnabled()).thenReturn(true);
        when(product.getResidualClosureThreshold()).thenReturn(threshold);
        ReflectionTestUtils.setField(loan, "loanProduct", product);
        ReflectionTestUtils.setField(loan, "loanStatus", LoanStatus.ACTIVE.getValue());
        return loan;
    }
}
