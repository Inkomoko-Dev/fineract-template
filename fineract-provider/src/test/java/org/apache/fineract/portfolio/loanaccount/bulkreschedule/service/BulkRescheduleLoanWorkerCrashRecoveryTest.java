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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRescheduleRequestToTermVariationMapping;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTermVariationType;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTermVariations;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BulkRescheduleLoanWorkerCrashRecoveryTest {

    @Mock
    private BulkRescheduleExecutionRepository executionRepository;
    @Mock
    private BulkRescheduleResultRepository resultRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private BulkLoanRescheduleEngine rescheduleEngine;
    @Mock
    private BulkRescheduleValidationService validationService;
    @Mock
    private BulkRescheduleNoteService noteService;
    @Mock
    private BulkRescheduleLoanSnapshotService snapshotService;
    @Mock
    private LoanRescheduleRequestRepository loanRescheduleRequestRepository;

    @InjectMocks
    private BulkRescheduleLoanWorker worker;

    @Test
    void rejectsHigherIdApprovedRequestWithUnrelatedInterestRate() {
        final BulkRescheduleResult result = resultWithId(11L);
        final BulkRescheduleExecution execution = executionStartedAt(LocalDateTime.of(2024, 11, 1, 10, 0));
        final LoanRescheduleRequest unrelatedMaxId = approvedRequest(99L, new BigDecimal("12"), LocalDate.of(2024, 11, 2));
        final LoanRescheduleRequest attributable = approvedRequest(50L, new BigDecimal("10"), LocalDate.of(2024, 11, 2));
        when(loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(anyLong(), anyInt()))
                .thenReturn(List.of(unrelatedMaxId, attributable));
        when(resultRepository.findByRescheduleRequestId(50L)).thenReturn(Optional.empty());

        final BulkRescheduleLoanWorker.CrashRecoveryResolution resolution = worker.resolveCrashRecoveryRequestId(407618L, result,
                execution, new BigDecimal("10"));

        assertThat(resolution.getStatus()).isEqualTo(BulkRescheduleResultStatus.SUCCEEDED);
        assertThat(resolution.getRequestId()).isEqualTo(50L);
    }

    @Test
    void acceptsExactlyOneAttributableTargetRateRequest() {
        final BulkRescheduleResult result = resultWithId(22L);
        final BulkRescheduleExecution execution = executionStartedAt(LocalDateTime.of(2024, 11, 1, 10, 0));
        final LoanRescheduleRequest match = approvedRequest(77L, new BigDecimal("10"), LocalDate.of(2024, 11, 1));
        when(loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(1L, LoanStatus.APPROVED.getValue())).thenReturn(List.of(match));
        when(resultRepository.findByRescheduleRequestId(77L)).thenReturn(Optional.empty());

        final BulkRescheduleLoanWorker.CrashRecoveryResolution resolution = worker.resolveCrashRecoveryRequestId(1L, result, execution,
                new BigDecimal("10"));

        assertThat(resolution.getStatus()).isEqualTo(BulkRescheduleResultStatus.SUCCEEDED);
        assertThat(resolution.getRequestId()).isEqualTo(77L);
    }

    @Test
    void zeroMatchesYieldsSkippedNotSucceeded() {
        final BulkRescheduleResult result = resultWithId(33L);
        final BulkRescheduleExecution execution = executionStartedAt(LocalDateTime.of(2024, 11, 1, 10, 0));
        when(loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(anyLong(), anyInt())).thenReturn(List.of());

        final BulkRescheduleLoanWorker.CrashRecoveryResolution resolution = worker.resolveCrashRecoveryRequestId(1L, result, execution,
                new BigDecimal("10"));

        assertThat(resolution.getStatus()).isEqualTo(BulkRescheduleResultStatus.SKIPPED);
        assertThat(resolution.getRequestId()).isNull();
        assertThat(resolution.getReason()).contains("no attributable");
    }

    @Test
    void multipleMatchesYieldsFailedNotSucceeded() {
        final BulkRescheduleResult result = resultWithId(44L);
        final BulkRescheduleExecution execution = executionStartedAt(LocalDateTime.of(2024, 11, 1, 10, 0));
        final LoanRescheduleRequest first = approvedRequest(10L, new BigDecimal("10"), LocalDate.of(2024, 11, 1));
        final LoanRescheduleRequest second = approvedRequest(11L, new BigDecimal("10"), LocalDate.of(2024, 11, 1));
        when(loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(anyLong(), anyInt())).thenReturn(List.of(first, second));
        when(resultRepository.findByRescheduleRequestId(10L)).thenReturn(Optional.empty());
        when(resultRepository.findByRescheduleRequestId(11L)).thenReturn(Optional.empty());

        final BulkRescheduleLoanWorker.CrashRecoveryResolution resolution = worker.resolveCrashRecoveryRequestId(1L, result, execution,
                new BigDecimal("10"));

        assertThat(resolution.getStatus()).isEqualTo(BulkRescheduleResultStatus.FAILED);
        assertThat(resolution.getRequestId()).isNull();
        assertThat(resolution.getReason()).contains("multiple");
    }

    @Test
    void claimedByAnotherResultIsNotAttributed() {
        final BulkRescheduleResult result = resultWithId(55L);
        final BulkRescheduleExecution execution = executionStartedAt(LocalDateTime.of(2024, 11, 1, 10, 0));
        final LoanRescheduleRequest match = approvedRequest(88L, new BigDecimal("10"), LocalDate.of(2024, 11, 1));
        final BulkRescheduleResult otherOwner = resultWithId(999L);
        when(loanRescheduleRequestRepository.findByLoanIdAndStatusEnum(anyLong(), anyInt())).thenReturn(List.of(match));
        when(resultRepository.findByRescheduleRequestId(88L)).thenReturn(Optional.of(otherOwner));

        final BulkRescheduleLoanWorker.CrashRecoveryResolution resolution = worker.resolveCrashRecoveryRequestId(1L, result, execution,
                new BigDecimal("10"));

        assertThat(resolution.getStatus()).isEqualTo(BulkRescheduleResultStatus.SKIPPED);
        assertThat(resolution.getRequestId()).isNull();
    }

    private static BulkRescheduleResult resultWithId(final Long id) {
        final BulkRescheduleResult result = new BulkRescheduleResult();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private static BulkRescheduleExecution executionStartedAt(final LocalDateTime startedAt) {
        final BulkRescheduleExecution execution = new BulkRescheduleExecution();
        execution.setExecutionStartedAt(startedAt);
        return execution;
    }

    private static LoanRescheduleRequest approvedRequest(final Long id, final BigDecimal rate, final LocalDate submittedOn) {
        final LoanRescheduleRequest request = mock(LoanRescheduleRequest.class);
        when(request.getId()).thenReturn(id);
        lenient().when(request.getSubmittedOnDate()).thenReturn(submittedOn);
        lenient().when(request.getApprovedOnDate()).thenReturn(null);

        final LoanTermVariations variation = mock(LoanTermVariations.class);
        when(variation.getTermType()).thenReturn(LoanTermVariationType.INTEREST_RATE_FROM_INSTALLMENT);
        when(variation.getTermValue()).thenReturn(rate);

        final LoanRescheduleRequestToTermVariationMapping mapping = mock(LoanRescheduleRequestToTermVariationMapping.class);
        when(mapping.getLoanTermVariations()).thenReturn(variation);
        when(request.getLoanRescheduleRequestToTermVariationMappings()).thenReturn(Set.of(mapping));
        return request;
    }
}
