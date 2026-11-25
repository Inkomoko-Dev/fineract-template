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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class LoanWritePlatformTrancheArchiveTest {

    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 6, 8);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DISBURSEMENT_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
        ThreadLocalContextUtil.clear();
    }

    @Test
    void laterTrancheArchivesTheScheduleItReplacesDatedOnTheDisbursement() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class, CALLS_REAL_METHODS);
        final LoanScheduleHistoryWritePlatformService archiver = mock(LoanScheduleHistoryWritePlatformService.class);
        ReflectionTestUtils.setField(service, "loanScheduleHistoryWritePlatformService", archiver);
        final Loan loan = mock(Loan.class);
        when(loan.status()).thenReturn(LoanStatus.ACTIVE);

        service.archiveScheduleBeforeLaterTranche(loan, DISBURSEMENT_DATE);

        verify(archiver).archiveSupersededSchedule(loan, DISBURSEMENT_DATE);
    }

    @Test
    void firstDisbursementArchivesNothingBecauseTheLoanWasNotAgedBefore() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class, CALLS_REAL_METHODS);
        final LoanScheduleHistoryWritePlatformService archiver = mock(LoanScheduleHistoryWritePlatformService.class);
        ReflectionTestUtils.setField(service, "loanScheduleHistoryWritePlatformService", archiver);
        final Loan loan = mock(Loan.class);
        when(loan.status()).thenReturn(LoanStatus.APPROVED);

        service.archiveScheduleBeforeLaterTranche(loan, DISBURSEMENT_DATE);

        verify(archiver, never()).archiveSupersededSchedule(any(), any());
    }

    @Test
    void undoingTheLastTrancheArchivesTheScheduleItReplacesBeforeRegeneratingIt() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class, CALLS_REAL_METHODS);
        final LoanScheduleHistoryWritePlatformService archiver = mock(LoanScheduleHistoryWritePlatformService.class);
        final LoanAssembler assembler = mock(LoanAssembler.class);
        ReflectionTestUtils.setField(service, "loanScheduleHistoryWritePlatformService", archiver);
        ReflectionTestUtils.setField(service, "loanAssembler", assembler);
        ReflectionTestUtils.setField(service, "businessEventNotifierService", mock(BusinessEventNotifierService.class));
        ReflectionTestUtils.setField(service, "applicationCurrencyRepository",
                mock(ApplicationCurrencyRepositoryWrapper.class, RETURNS_DEEP_STUBS));
        ReflectionTestUtils.setField(service, "loanUtilService", mock(LoanUtilService.class));
        ReflectionTestUtils.setField(service, "journalEntryWritePlatformService", mock(JournalEntryWritePlatformService.class));
        final Loan loan = mock(Loan.class);
        when(assembler.assembleFrom(7L)).thenReturn(loan);
        when(loan.isMultiDisburmentLoan()).thenReturn(true);
        when(loan.getDisbursementDetails()).thenReturn(
                List.of(new LoanDisbursementDetails(DISBURSEMENT_DATE, DISBURSEMENT_DATE, new BigDecimal("1000"), new BigDecimal("1000")),
                        new LoanDisbursementDetails(DISBURSEMENT_DATE, DISBURSEMENT_DATE, new BigDecimal("500"), new BigDecimal("500"))));
        when(loan.undoLastDisbursal(any(), any(), any(), any())).thenReturn(new HashMap<>());

        service.undoLastLoanDisbursal(7L, JsonCommand.fromJsonElement(7L, new JsonObject()));

        final InOrder order = inOrder(archiver, loan);
        order.verify(archiver).archiveSupersededSchedule(loan, DISBURSEMENT_DATE);
        order.verify(loan).undoLastDisbursal(any(), any(), any(), any());
    }
}
