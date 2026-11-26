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

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Resolves which existing schedule installment number a reschedule should start from
 * when {@code rescheduleFromDate} may not equal any installment due date (CGLT-857).
 */
public final class RescheduleFromInstallmentResolver {

    private RescheduleFromInstallmentResolver() {}

    /**
     * @return installment number of the first installment with {@code dueDate >= rescheduleFromDate},
     *         or empty if the date is null, the schedule is empty, or the date is after every due date
     */
    public static Optional<Integer> resolveInstallmentNumber(
            final Collection<LoanRepaymentScheduleInstallment> installments, final LocalDate rescheduleFromDate) {
        if (rescheduleFromDate == null || installments == null || installments.isEmpty()) {
            return Optional.empty();
        }
        return installments.stream()
                .filter(i -> i.getDueDate() != null && !i.getDueDate().isBefore(rescheduleFromDate))
                .min(Comparator.comparing(LoanRepaymentScheduleInstallment::getDueDate)
                        .thenComparing(LoanRepaymentScheduleInstallment::getInstallmentNumber))
                .map(LoanRepaymentScheduleInstallment::getInstallmentNumber);
    }
}
