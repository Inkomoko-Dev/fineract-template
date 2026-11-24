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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanRepaymentScheduleHistory;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanRepaymentScheduleHistoryRepository;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class LoanRescheduleUndoArchiveTest {

    private static final LocalDate UNDONE_ON = LocalDate.of(2026, 4, 10);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Kigali", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, UNDONE_ON)));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    void undoingAnApprovedRescheduleArchivesTheRescheduledScheduleOnTheUndoDateBeforeRestoringTheOriginal() {
        final LoanRescheduleRequestWritePlatformServiceImpl service = mock(LoanRescheduleRequestWritePlatformServiceImpl.class,
                CALLS_REAL_METHODS);
        final LoanRescheduleRequestRepository requests = mock(LoanRescheduleRequestRepository.class);
        final LoanRepaymentScheduleHistoryRepository histories = mock(LoanRepaymentScheduleHistoryRepository.class);
        final LoanScheduleHistoryWritePlatformService archiver = mock(LoanScheduleHistoryWritePlatformService.class);
        final LoanAssembler assembler = mock(LoanAssembler.class);
        final LoanRepositoryWrapper loans = mock(LoanRepositoryWrapper.class);
        ReflectionTestUtils.setField(service, "loanRescheduleRequestRepository", requests);
        ReflectionTestUtils.setField(service, "loanRepaymentScheduleHistoryRepository", histories);
        ReflectionTestUtils.setField(service, "loanScheduleHistoryWritePlatformService", archiver);
        ReflectionTestUtils.setField(service, "loanAssembler", assembler);
        ReflectionTestUtils.setField(service, "loanRepositoryWrapper", loans);
        ReflectionTestUtils.setField(service, "applicationCurrencyRepository",
                mock(ApplicationCurrencyRepositoryWrapper.class, RETURNS_DEEP_STUBS));
        ReflectionTestUtils.setField(service, "journalEntryWritePlatformService", mock(JournalEntryWritePlatformService.class));
        ReflectionTestUtils.setField(service, "loanAccountDomainService", mock(LoanAccountDomainService.class));

        final Loan loan = mock(Loan.class);
        final LoanRescheduleRequest request = mock(LoanRescheduleRequest.class);
        when(request.getStatusEnum()).thenReturn(LoanStatus.APPROVED.getValue());
        when(request.getLoan()).thenReturn(loan);
        when(loan.getId()).thenReturn(7L);
        when(requests.findById(3L)).thenReturn(Optional.of(request));
        when(assembler.assembleFrom(7L)).thenReturn(loan);
        when(loans.saveAndFlush(loan)).thenReturn(loan);
        when(loan.deriveAccountingBridgeData(any(), any(), any(), anyBoolean())).thenReturn(Map.of());
        final LoanRepaymentScheduleHistory original = mock(LoanRepaymentScheduleHistory.class);
        when(original.getInstallmentNumber()).thenReturn(1);
        when(original.getDueDate()).thenReturn(LocalDate.of(2026, 2, 28));
        when(original.getPrincipal()).thenReturn(new BigDecimal("1000"));
        when(original.getInterestCharged()).thenReturn(new BigDecimal("100"));
        when(original.getFeeChargesCharged()).thenReturn(BigDecimal.ZERO);
        when(original.getPenaltyCharges()).thenReturn(BigDecimal.ZERO);
        when(histories.findByRescheduleRequestId(3L)).thenReturn(List.of(original));

        service.undoApproved(3L, null, null);

        final InOrder order = inOrder(archiver, loan, request);
        order.verify(archiver).archiveSupersededSchedule(loan, UNDONE_ON);
        order.verify(loan).updateLoanSchedule(anyCollection());
        order.verify(request).undo();
    }
}
