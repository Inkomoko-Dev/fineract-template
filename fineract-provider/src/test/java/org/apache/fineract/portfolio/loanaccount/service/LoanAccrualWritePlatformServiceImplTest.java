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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.data.LoanScheduleAccrualData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class LoanAccrualWritePlatformServiceImplTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 1, 14);
    private static final CurrencyData SSP = new CurrencyData("SSP", 2, 0);
    private static final EnumOptionData SPECIFIED_DUE_DATE = new EnumOptionData(
            ChargeTimeType.SPECIFIED_DUE_DATE.getValue().longValue(), ChargeTimeType.SPECIFIED_DUE_DATE.getCode(),
            "Specified due date");

    @Test
    void fullPenaltyWaiverDoesNotAddApplicableChargeWhenChargeWasAlreadyAccrued() {
        final LoanChargeData penaltyCharge = penaltyCharge(new BigDecimal("300000000.00"), new BigDecimal("300000000.00"),
                new BigDecimal("299462600.00"));
        final LoanScheduleAccrualData accrualData = accrualData(new BigDecimal("299462600.00"));

        updateCharges(penaltyCharge, accrualData);

        assertEquals(0, new BigDecimal("299462600.00").compareTo(accrualData.getDueDatePenaltyIncome()));
        assertTrue(accrualData.getApplicableCharges().isEmpty());
    }

    @Test
    void partialPenaltyWaiverAccruesOnlyTheUnwaivedResidualAmount() {
        final LoanChargeData penaltyCharge = penaltyCharge(new BigDecimal("300000000.00"), new BigDecimal("299462600.00"), null);
        final LoanScheduleAccrualData accrualData = accrualData(null);

        updateCharges(penaltyCharge, accrualData);

        assertEquals(0, new BigDecimal("537400.00").compareTo(accrualData.getDueDatePenaltyIncome()));
        assertEquals(0, new BigDecimal("537400.00").compareTo(accrualData.getApplicableCharges().get(penaltyCharge)));
    }

    @Test
    void fullPenaltyWaiverBeforeAccrualLeavesNoPenaltyIncome() {
        final LoanChargeData penaltyCharge = penaltyCharge(new BigDecimal("300000000.00"), new BigDecimal("300000000.00"), null);
        final LoanScheduleAccrualData accrualData = accrualData(null);

        updateCharges(penaltyCharge, accrualData);

        assertNull(accrualData.getDueDatePenaltyIncome());
        assertTrue(accrualData.getApplicableCharges().isEmpty());
    }

    @Test
    void skipsAccrualInsertWhenNonReversedAccrualAlreadyExistsForLoanAndDate() throws Exception {
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        final LoanTransactionRepository loanTransactionRepository = mock(LoanTransactionRepository.class);
        final DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        final JournalEntryWritePlatformService journalEntryWritePlatformService = mock(JournalEntryWritePlatformService.class);
        final LoanAccrualWritePlatformServiceImpl service = new LoanAccrualWritePlatformServiceImpl(jdbcTemplate, null,
                journalEntryWritePlatformService, null, null, null, null, sqlGenerator, loanTransactionRepository);
        final LoanScheduleAccrualData accrualData = accrualData(null);
        accrualData.updateAccruableIncome(new BigDecimal("100.00"));
        accrualData.updateChargeDetails(java.util.Collections.emptyMap(), null, null);

        when(loanTransactionRepository.existsNonReversedAccrualForLoanAndDate(1L, LoanTransactionType.ACCRUAL, DUE_DATE)).thenReturn(true);

        service.addAccrualAccounting(accrualData);

        verify(loanTransactionRepository).existsNonReversedAccrualForLoanAndDate(1L, LoanTransactionType.ACCRUAL, DUE_DATE);
        verify(jdbcTemplate, never()).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO m_loan_transaction"), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
        verify(journalEntryWritePlatformService, never()).createJournalEntriesForLoan(any());
        // CGLT-672: still sync schedule + accrued_till so partial prior writes cannot stick the loan
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.startsWith("UPDATE m_loan_repayment_schedule SET accrual_interest_derived"),
                eq(new BigDecimal("100.00")), any(), any(), eq(1L));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.startsWith("UPDATE m_loan  SET accrued_till"), eq(DUE_DATE), eq(1L));
    }

    @Test
    void insertsAccrualWhenNoExistingNonReversedAccrualForLoanAndDate() throws Exception {
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        final LoanTransactionRepository loanTransactionRepository = mock(LoanTransactionRepository.class);
        final DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        final JournalEntryWritePlatformService journalEntryWritePlatformService = mock(JournalEntryWritePlatformService.class);
        final LoanAccrualWritePlatformServiceImpl service = new LoanAccrualWritePlatformServiceImpl(jdbcTemplate, null,
                journalEntryWritePlatformService, null, null, null, null, sqlGenerator, loanTransactionRepository);
        final LoanScheduleAccrualData accrualData = accrualData(null);
        accrualData.updateAccruableIncome(new BigDecimal("100.00"));
        accrualData.updateChargeDetails(java.util.Collections.emptyMap(), null, null);

        when(loanTransactionRepository.existsNonReversedAccrualForLoanAndDate(1L, LoanTransactionType.ACCRUAL, DUE_DATE)).thenReturn(false);
        when(sqlGenerator.lastInsertId()).thenReturn("LAST_INSERT_ID()");
        when(jdbcTemplate.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Long.class))).thenReturn(99L);

        service.addAccrualAccounting(accrualData);

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO m_loan_transaction"), eq(1L), eq(1L),
                eq(LoanTransactionType.ACCRUAL.getValue()), eq(DUE_DATE), any(BigDecimal.class), any(BigDecimal.class), any(), any(),
                any(LocalDate.class));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.startsWith("UPDATE m_loan_repayment_schedule SET accrual_interest_derived"),
                eq(new BigDecimal("100.00")), any(), any(), eq(1L));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.startsWith("UPDATE m_loan  SET accrued_till"), eq(DUE_DATE), eq(1L));
        verify(journalEntryWritePlatformService).createJournalEntriesForLoan(any());
    }

    private void updateCharges(final LoanChargeData penaltyCharge, final LoanScheduleAccrualData accrualData) {
        final LoanAccrualWritePlatformServiceImpl service = new LoanAccrualWritePlatformServiceImpl(null, null, null, null, null, null,
                null, null, null);

        ReflectionTestUtils.invokeMethod(service, "updateCharges", List.of(penaltyCharge), accrualData, START_DATE, DUE_DATE);
    }

    private LoanChargeData penaltyCharge(final BigDecimal amount, final BigDecimal amountWaived, final BigDecimal amountAccrued) {
        return new LoanChargeData(1L, 1L, DUE_DATE, SPECIFIED_DUE_DATE, amount, amountAccrued, amountWaived, true);
    }

    private LoanScheduleAccrualData accrualData(final BigDecimal accruedPenaltyIncome) {
        return new LoanScheduleAccrualData(1L, 1L, 1, DUE_DATE, PeriodFrequencyType.MONTHS, 1, DUE_DATE, START_DATE, 1L, 1L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, accruedPenaltyIncome, SSP, null, null);
    }
}
