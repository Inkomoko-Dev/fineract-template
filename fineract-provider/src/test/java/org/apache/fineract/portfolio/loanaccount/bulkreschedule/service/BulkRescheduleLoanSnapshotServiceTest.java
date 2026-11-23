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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkRescheduleLoanSnapshotServiceTest {

    private final BulkRescheduleLoanSnapshotService snapshotService = new BulkRescheduleLoanSnapshotService();

    @Mock
    private Loan loan;

    @Mock
    private LoanProductRelatedDetail detail;

    @Test
    void readsNominalRateFromRepaymentScheduleDetail() {
        when(loan.getLoanRepaymentScheduleDetail()).thenReturn(detail);
        when(detail.getNominalInterestRatePerPeriod()).thenReturn(new BigDecimal("15.00"));

        assertThat(snapshotService.currentNominalInterestRate(loan)).isEqualByComparingTo("15.00");
    }

    @Test
    void appliesPostSuccessSnapshotFromLoan() {
        final BulkRescheduleResult result = new BulkRescheduleResult();
        result.setOriginalInterestRate(new BigDecimal("15"));
        result.setTotalOutstanding(new BigDecimal("1000"));
        when(loan.getLoanRepaymentScheduleDetail()).thenReturn(detail);
        when(detail.getNominalInterestRatePerPeriod()).thenReturn(new BigDecimal("10"));
        when(loan.getSummary()).thenReturn(null);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(Collections.emptyList());

        snapshotService.applyPostSuccessSnapshot(result, loan);

        assertThat(result.getOriginalInterestRate()).isEqualByComparingTo("15");
        assertThat(result.getTotalOutstanding()).isEqualByComparingTo("1000");
        assertThat(result.getNewInterestRate()).isEqualByComparingTo("10");
        assertThat(result.getNewTotalOutstanding()).isNull();
        assertThat(result.getNewTerm()).isEqualTo(0);
        assertThat(result.getNextScheduledInstallment()).isNull();
    }
}
