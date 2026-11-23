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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkRescheduleValidationServiceTest {

    @Mock
    private LoanRescheduleRequestRepository loanRescheduleRequestRepository;

    @Mock
    private Loan loan;

    @Mock
    private Client client;

    @Mock
    private LoanProductRelatedDetail repaymentDetail;

    @InjectMocks
    private BulkRescheduleValidationService validationService;

    @Test
    void skipsWhenLoanAlreadyAtTargetRate() {
        when(loan.getLoanRepaymentScheduleDetail()).thenReturn(repaymentDetail);
        when(repaymentDetail.getNominalInterestRatePerPeriod()).thenReturn(new BigDecimal("10.000000"));
        when(repaymentDetail.getInterestMethod()).thenReturn(InterestMethod.FLAT);

        final Optional<String> skip = validationService.findAlreadyAtTargetSkipReason(loan, new BigDecimal("10"), InterestMethod.FLAT);

        assertThat(skip).contains(BulkRescheduleValidationService.SKIP_ALREADY_AT_TARGET_RATE);
    }

    @Test
    void doesNotSkipWhenRateDiffersFromTarget() {
        when(loan.getLoanRepaymentScheduleDetail()).thenReturn(repaymentDetail);
        when(repaymentDetail.getNominalInterestRatePerPeriod()).thenReturn(new BigDecimal("15"));

        final Optional<String> skip = validationService.findAlreadyAtTargetSkipReason(loan, new BigDecimal("10"), InterestMethod.FLAT);

        assertThat(skip).isEmpty();
    }

    @Test
    void doesNotSkipWhenInterestMethodDoesNotMatchFilter() {
        when(loan.getLoanRepaymentScheduleDetail()).thenReturn(repaymentDetail);
        when(repaymentDetail.getNominalInterestRatePerPeriod()).thenReturn(new BigDecimal("10"));
        when(repaymentDetail.getInterestMethod()).thenReturn(InterestMethod.DECLINING_BALANCE);

        final Optional<String> skip = validationService.findAlreadyAtTargetSkipReason(loan, new BigDecimal("10"), InterestMethod.FLAT);

        assertThat(skip).isEmpty();
    }

    @Test
    void allowsLoanEvenIfPriorBulkSucceeded() {
        when(loan.getLoanStatus()).thenReturn(LoanStatus.ACTIVE.getValue());
        when(loan.getClient()).thenReturn(client);
        when(client.isActive()).thenReturn(true);
        when(loan.getId()).thenReturn(42L);
        when(loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(anyLong(), anyInt())).thenReturn(Collections.emptyList());

        final List<String> errors = validationService.validateLoanEligibilityForReschedule(loan);

        assertThat(errors).isEmpty();
    }

    @Test
    void rejectsNonActiveLoan() {
        when(loan.getLoanStatus()).thenReturn(LoanStatus.CLOSED_WRITTEN_OFF.getValue());

        final List<String> errors = validationService.validateLoanEligibilityForReschedule(loan);

        assertThat(errors).anyMatch(message -> message.contains("ACTIVE"));
    }

    @Test
    void rejectsMultiTrancheLoan() {
        when(loan.getLoanStatus()).thenReturn(LoanStatus.ACTIVE.getValue());
        when(loan.getClient()).thenReturn(client);
        when(client.isActive()).thenReturn(true);
        when(loan.isMultiDisburmentLoan()).thenReturn(true);
        when(loan.getId()).thenReturn(7L);
        when(loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(anyLong(), anyInt())).thenReturn(Collections.emptyList());

        final List<String> errors = validationService.validateLoanEligibilityForReschedule(loan);

        assertThat(errors).anyMatch(message -> message.contains("Multi-tranche"));
    }

    @Test
    void detectsCompletedRateChangeWhenOriginalDiffersFromCurrent() {
        when(loan.getLoanRepaymentScheduleDetail()).thenReturn(repaymentDetail);
        when(repaymentDetail.getNominalInterestRatePerPeriod()).thenReturn(new BigDecimal("10"));

        assertThat(validationService.looksLikeCompletedRateChange(new BigDecimal("15"), loan)).isTrue();
        assertThat(validationService.looksLikeCompletedRateChange(new BigDecimal("10"), loan)).isFalse();
    }
}
