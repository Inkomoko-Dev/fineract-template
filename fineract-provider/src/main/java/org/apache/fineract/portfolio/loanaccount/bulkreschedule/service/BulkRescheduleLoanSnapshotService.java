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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.springframework.stereotype.Service;

/** Shared pre/post field population for bulk reschedule result rows. */
@Service
public class BulkRescheduleLoanSnapshotService {

    public BigDecimal currentNominalInterestRate(final Loan loan) {
        final LoanProductRelatedDetail detail = loan == null ? null : loan.getLoanRepaymentScheduleDetail();
        return detail == null ? null : detail.getNominalInterestRatePerPeriod();
    }

    public void applyPostSuccessSnapshot(final BulkRescheduleResult result, final Loan loan) {
        if (result == null || loan == null) {
            return;
        }
        final LoanProductRelatedDetail detail = loan.getLoanRepaymentScheduleDetail();
        if (detail != null) {
            result.setNewInterestRate(detail.getNominalInterestRatePerPeriod());
        }
        if (loan.getSummary() != null) {
            result.setNewTotalOutstanding(loan.getSummary().getTotalOutstanding());
        }
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null) {
            result.setNewTerm(0);
            result.setNextScheduledInstallment(null);
            return;
        }
        result.setNewTerm((int) installments.stream().filter(installment -> !installment.isObligationsMet()).count());
        final LocalDate nextDue = installments.stream().filter(installment -> !installment.isObligationsMet())
                .map(LoanRepaymentScheduleInstallment::getDueDate).findFirst().orElse(null);
        result.setNextScheduledInstallment(nextDue);
    }
}
