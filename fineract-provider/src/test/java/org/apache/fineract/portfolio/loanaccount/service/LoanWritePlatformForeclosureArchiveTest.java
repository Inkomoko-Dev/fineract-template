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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanEventApiJsonValidator;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class LoanWritePlatformForeclosureArchiveTest {

    private static final LocalDate FORECLOSED_ON = LocalDate.of(2026, 4, 15);

    @Test
    void foreclosureArchivesTheScheduleItReplacesDatedOnTheForeclosure() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class, CALLS_REAL_METHODS);
        final LoanScheduleHistoryWritePlatformService archiver = mock(LoanScheduleHistoryWritePlatformService.class);
        final LoanAssembler assembler = mock(LoanAssembler.class);
        final LoanAccountDomainService domainService = mock(LoanAccountDomainService.class);
        ReflectionTestUtils.setField(service, "loanScheduleHistoryWritePlatformService", archiver);
        ReflectionTestUtils.setField(service, "loanAssembler", assembler);
        ReflectionTestUtils.setField(service, "loanAccountDomainService", domainService);
        ReflectionTestUtils.setField(service, "fromApiJsonHelper", new FromJsonHelper());
        ReflectionTestUtils.setField(service, "loanEventApiJsonValidator", new LoanEventApiJsonValidator(new FromJsonHelper(), null, null));
        final Loan loan = mock(Loan.class);
        when(assembler.assembleFrom(7L)).thenReturn(loan);
        when(domainService.foreCloseLoan(any(), any(), any(), anyBoolean())).thenReturn(new HashMap<>());
        final JsonCommand command = JsonCommand.from("{\"transactionDate\":\"15 April 2026\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}");

        service.forecloseLoan(7L, command);

        final InOrder order = inOrder(archiver, domainService);
        order.verify(archiver).archiveForeclosedSchedule(loan, FORECLOSED_ON);
        order.verify(domainService).foreCloseLoan(eq(loan), eq(FORECLOSED_ON), any(), eq(false));
        verify(archiver, never()).createAndSaveLoanScheduleArchive(any(), any(), any());
    }
}
