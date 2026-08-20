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

import com.google.gson.Gson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleFilterDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.RescheduleFromDateStrategy;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Dedicated Spring proxy for the atomic unit of one bulk loan operation. */
@Service
@RequiredArgsConstructor
public class BulkRescheduleLoanWorker {

    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();

    private final BulkRescheduleExecutionRepository executionRepository;
    private final BulkRescheduleResultRepository resultRepository;
    private final LoanRepository loanRepository;
    private final BulkLoanRescheduleEngine rescheduleEngine;
    private final BulkRescheduleValidationService validationService;
    private final BulkRescheduleNoteService noteService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeLoan(final Long executionId, final Long resultId, final ReschedulingDetailsDto details) {
        final BulkRescheduleExecution execution = executionRepository.findById(executionId).orElseThrow();
        final BulkRescheduleResult result = resultRepository.findById(resultId).orElseThrow();
        final Loan loan = loanRepository.findById(result.getLoanId()).orElseThrow();
        final BulkRescheduleFilterDto filters = gson.fromJson(execution.getFiltersJson(), BulkRescheduleFilterDto.class);
        details.setRescheduleFromDate(resolveRescheduleFromDate(loan, filters.getRescheduleFromDateStrategy()));
        if (details.getRescheduleFromDate() == null) {
            throw new IllegalArgumentException("Loan has no repayment installment available for the selected strategy");
        }
        final List<String> errors = validationService.validateLoanEligibilityForReschedule(loan);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        validationService.validateRescheduleParameters(details, loan);
        final Long requestId = rescheduleEngine.performReschedule(loan, details);
        rescheduleEngine.approveReschedule(requestId);
        noteService.addRescheduleNoteToLoan(loan, execution);
        result.setStatus(BulkRescheduleResultStatus.SUCCEEDED);
        result.setRescheduleRequestId(requestId);
        result.setOriginalRescheduleRequestId(requestId);
        resultRepository.save(result);
    }

    private java.time.LocalDate resolveRescheduleFromDate(final Loan loan, final RescheduleFromDateStrategy strategy) {
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null || installments.isEmpty()) {
            return null;
        }
        if (strategy == RescheduleFromDateStrategy.NEXT_UNPAID) {
            return installments.stream().filter(installment -> !installment.isObligationsMet())
                    .map(LoanRepaymentScheduleInstallment::getDueDate).findFirst().orElse(null);
        }
        return installments.get(0).getDueDate();
    }

    /** Preserves the existing rollback business behavior, but isolates its result mutation. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackLoan(final Long resultId) {
        final BulkRescheduleResult result = resultRepository.findById(resultId).orElseThrow();
        loanRepository.findById(result.getLoanId()).orElseThrow();
        result.setStatus(BulkRescheduleResultStatus.ROLLED_BACK);
        result.setErrorMessage(null);
        resultRepository.save(result);
    }
}
