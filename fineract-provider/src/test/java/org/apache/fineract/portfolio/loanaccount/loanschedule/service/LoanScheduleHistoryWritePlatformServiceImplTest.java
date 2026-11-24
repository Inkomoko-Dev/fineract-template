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
package org.apache.fineract.portfolio.loanaccount.loanschedule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanRepaymentScheduleHistory;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanRepaymentScheduleHistoryRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LoanScheduleHistoryWritePlatformServiceImplTest {

    private static final LocalDate SUPERSEDED_ON = LocalDate.of(2026, 4, 10);

    private final LoanScheduleHistoryReadPlatformService readService = mock(LoanScheduleHistoryReadPlatformService.class);
    private final LoanRepaymentScheduleHistoryRepository repository = mock(LoanRepaymentScheduleHistoryRepository.class);
    private final LoanScheduleHistoryWritePlatformServiceImpl service = new LoanScheduleHistoryWritePlatformServiceImpl(readService,
            repository);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    @SuppressWarnings("unchecked")
    void supersededScheduleIsArchivedAsTheNextVersionDatedWhenItWasReplaced() {
        final Loan loan = loan(false);
        when(readService.fetchCurrentVersionNumber(7L)).thenReturn(2);

        service.archiveSupersededSchedule(loan, SUPERSEDED_ON);

        final ArgumentCaptor<List<LoanRepaymentScheduleHistory>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertEquals(2, saved.getValue().size());
        for (final LoanRepaymentScheduleHistory history : saved.getValue()) {
            assertEquals(3, history.getVersion());
            assertEquals(SUPERSEDED_ON, history.getSupersededOnDate());
            assertNull(history.getLoanRescheduleRequest());
        }
        assertEquals(0, new BigDecimal("150").compareTo(saved.getValue().get(1).getInterestCharged()));
        assertEquals(LocalDate.of(2026, 5, 31), saved.getValue().get(1).getDueDate());
    }

    @Test
    void originalScheduleProductsKeepTheirHistoryForTheAgingJob() {
        service.archiveSupersededSchedule(loan(true), SUPERSEDED_ON);

        verify(repository, never()).saveAll(any());
    }

    @Test
    void archivesKeptForOtherReasonsHaveNoSupersededDate() {
        final Loan loan = loan(false);
        when(readService.fetchCurrentVersionNumber(7L)).thenReturn(0);

        final List<LoanRepaymentScheduleHistory> archive = service.createLoanScheduleArchive(loan.getRepaymentScheduleInstallments(), loan,
                null);

        archive.forEach(history -> assertNull(history.getSupersededOnDate()));
    }

    private static Loan loan(final boolean arrearsBasedOnOriginalSchedule) {
        final Loan loan = mock(Loan.class);
        final LoanProduct product = mock(LoanProduct.class);
        when(product.isArrearsBasedOnOriginalSchedule()).thenReturn(arrearsBasedOnOriginalSchedule);
        when(loan.getLoanProduct()).thenReturn(product);
        when(loan.getId()).thenReturn(7L);
        when(loan.getCurrency()).thenReturn(new MonetaryCurrency("RWF", 2, 0));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(
                new LoanRepaymentScheduleInstallment(null, 1, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30), new BigDecimal("1000"),
                        new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, false, null),
                new LoanRepaymentScheduleInstallment(null, 2, LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31), new BigDecimal("1000"),
                        new BigDecimal("150"), BigDecimal.ZERO, BigDecimal.ZERO, false, null)));
        return loan;
    }
}
