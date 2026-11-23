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
package org.apache.fineract.portfolio.loanaccount.rescheduleloan.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.Test;

class LoanReschedulePreserveTermAndCadenceTest {

    @Test
    void syncCadenceUpdatesLoanDetailFromRequestAndScheduleSize() {
        final LoanRescheduleRequestWritePlatformServiceImpl service = new LoanRescheduleRequestWritePlatformServiceImpl(null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);

        final Loan loan = mock(Loan.class);
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(loan.repaymentScheduleDetail()).thenReturn(detail);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment(loan, 1, LocalDate.of(2026, 10, 30)),
                installment(loan, 2, LocalDate.of(2026, 11, 30))));

        final LoanRescheduleRequest request = mock(LoanRescheduleRequest.class);
        when(request.getRepaymentEvery()).thenReturn(1);
        when(request.getRepaymentFrequencyType()).thenReturn(PeriodFrequencyType.MONTHS.getValue());

        service.syncLoanRepaymentCadenceFromRescheduleRequest(loan, request);

        verify(detail).setRepayEvery(1);
        verify(detail).setRepaymentPeriodFrequencyType(PeriodFrequencyType.MONTHS);
        verify(loan).updateNumberOfRepayments(2);
    }

    private static LoanRepaymentScheduleInstallment installment(final Loan loan, final int number, final LocalDate dueDate) {
        return new LoanRepaymentScheduleInstallment(loan, number, dueDate.minusMonths(1), dueDate, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, false, new HashSet<>());
    }
}
