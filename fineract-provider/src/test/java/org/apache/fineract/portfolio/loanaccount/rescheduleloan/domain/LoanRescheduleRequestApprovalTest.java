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
package org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;

class LoanRescheduleRequestApprovalTest {

    @Test
    void approvalRecordsWhoApprovedAndOnWhichDate() {
        final LoanRescheduleRequest request = new LoanRescheduleRequest();
        final AppUser approver = mock(AppUser.class);
        final LocalDate approvedOn = LocalDate.of(2026, 4, 10);

        request.approve(approver, approvedOn);

        assertEquals(LoanStatus.APPROVED.getValue(), request.getStatusEnum());
        assertEquals(approvedOn, request.getApprovedOnDate());
        assertSame(approver, request.getApprovedByUser());
    }
}
