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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanRepositoryWrapperRecalculationArchiveTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 4, 10);

    private final LoanRepository repository = mock(LoanRepository.class);
    private final LoanScheduleHistoryWritePlatformService archiver = mock(LoanScheduleHistoryWritePlatformService.class);
    private final LoanRepositoryWrapper wrapper = new LoanRepositoryWrapper(repository, mock(LoanRedrawAccountRepository.class),
            mock(LoanTransactionRepository.class), mock(LoanExcessRefundRepository.class), archiver);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    void savingALoanArchivesTheScheduleItsRecalculationReplacedDatedOnTheBusinessDate() {
        final List<LoanRepaymentScheduleInstallment> superseded = List.of(new LoanRepaymentScheduleInstallment(null, 1,
                BUSINESS_DATE.minusMonths(1), BUSINESS_DATE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, false, null));
        final Loan saved = loanWith(superseded);
        final Loan flushed = loanWith(superseded);

        wrapper.save(saved);
        wrapper.saveAndFlush(flushed);

        verify(archiver).archiveSupersededSchedule(saved, superseded, BUSINESS_DATE);
        verify(archiver).archiveSupersededSchedule(flushed, superseded, BUSINESS_DATE);
    }

    @Test
    void savingALoanWhoseScheduleWasNotReplacedArchivesNothing() {
        final Loan loan = loanWith(List.of());

        wrapper.save(loan);
        wrapper.save(List.of(loan));

        verify(archiver, never()).archiveSupersededSchedule(any(), any(), any());
    }

    private Loan loanWith(final List<LoanRepaymentScheduleInstallment> superseded) {
        final Loan loan = mock(Loan.class);
        when(loan.takeScheduleSupersededByRecalculation()).thenReturn(superseded);
        when(repository.save(loan)).thenReturn(loan);
        when(repository.saveAndFlush(loan)).thenReturn(loan);
        return loan;
    }
}
