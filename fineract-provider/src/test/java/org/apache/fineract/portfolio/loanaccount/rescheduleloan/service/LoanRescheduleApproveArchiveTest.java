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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanRepaymentScheduleHistory;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestDataValidator;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class LoanRescheduleApproveArchiveTest {

    private static final LocalDate APPROVED_ON = LocalDate.of(2026, 4, 3);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Kigali", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 4, 9))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    void approvalDatesTheArchivedScheduleWithTheApprovalDateBeforeTheScheduleIsRegenerated() {
        final LoanRescheduleRequestWritePlatformServiceImpl service = mock(LoanRescheduleRequestWritePlatformServiceImpl.class,
                CALLS_REAL_METHODS);
        final LoanRescheduleRequestRepository requests = mock(LoanRescheduleRequestRepository.class);
        final LoanScheduleHistoryWritePlatformService archiver = mock(LoanScheduleHistoryWritePlatformService.class);
        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        ReflectionTestUtils.setField(service, "loanRescheduleRequestRepository", requests);
        ReflectionTestUtils.setField(service, "loanScheduleHistoryWritePlatformService", archiver);
        ReflectionTestUtils.setField(service, "loanRescheduleRequestDataValidator", mock(LoanRescheduleRequestDataValidator.class));
        ReflectionTestUtils.setField(service, "platformSecurityContext", context);
        ReflectionTestUtils.setField(service, "loanUtilService", mock(LoanUtilService.class));

        final Loan loan = mock(Loan.class);
        final LoanRescheduleRequest request = mock(LoanRescheduleRequest.class);
        final LoanRepaymentScheduleHistory first = mock(LoanRepaymentScheduleHistory.class);
        final LoanRepaymentScheduleHistory second = mock(LoanRepaymentScheduleHistory.class);
        when(requests.findById(3L)).thenReturn(Optional.of(request));
        when(request.getLoan()).thenReturn(loan);
        when(request.getRescheduleFromDate()).thenReturn(LocalDate.of(2026, 4, 30));
        when(context.authenticatedUser()).thenReturn(mock(AppUser.class));
        when(archiver.createLoanScheduleArchive(any(), any(), any())).thenReturn(List.of(first, second));
        when(loan.constructLoanApplicationTerms(any())).thenThrow(new IllegalStateException("stop after archiving"));

        final JsonObject json = new JsonObject();
        json.addProperty("approvedOnDate", "03 April 2026");
        json.addProperty("dateFormat", "dd MMMM yyyy");
        json.addProperty("locale", "en");
        assertThrows(IllegalStateException.class, () -> service.approve(JsonCommand.fromJsonElement(3L, json, new FromJsonHelper())));

        final InOrder order = inOrder(archiver, first, second, loan);
        order.verify(archiver).createLoanScheduleArchive(any(), any(), any());
        order.verify(first).supersededOn(APPROVED_ON);
        order.verify(second).supersededOn(APPROVED_ON);
        order.verify(loan).constructLoanApplicationTerms(any());
    }
}
