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

package org.apache.fineract.notification.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.GmailBackedPlatformEmailService;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevelRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionState;
import org.apache.fineract.portfolio.loanaccount.service.DynamicIcReviewLevelHelper;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private GmailBackedPlatformEmailService emailService;

    @Mock
    private BusinessEventNotifierService businessEventNotifierService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private DynamicIcReviewLevelHelper dynamicIcReviewLevelHelper;

    @Mock
    private LoanDecisionLevelRepository loanDecisionLevelRepository;

    @Mock
    private Loan loan;

    @Mock
    private Client client;

    @Mock
    private LoanDecision decision;

    @Mock
    private AppUser approver;

    @Mock
    private AppUser loanOfficerAppUser;

    @Mock
    private Staff loanOfficer;

    @Mock
    private Note note;

    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        emailNotificationService = new EmailNotificationService(emailService, businessEventNotifierService, appUserRepository,
                dynamicIcReviewLevelHelper, loanDecisionLevelRepository);
        ReflectionTestUtils.setField(emailNotificationService, "baseUrl", "https://cbs.example.com");

        when(loan.getLoanOfficer()).thenReturn(loanOfficer);
        when(loan.getId()).thenReturn(1L);
        when(loan.getAccountNumber()).thenReturn("LN-001");
        when(approver.getEmail()).thenReturn("approver@example.com");
        when(approver.getDisplayName()).thenReturn("Approver");
    }

    @Test
    void sendsLoanOfficerAsCcForIcReviewNotification() {
        when(decision.getNextLoanIcReviewDecisionState()).thenReturn(1400);
        when(decision.getIcReviewDecisionLevelOneBy()).thenReturn(approver);
        when(dynamicIcReviewLevelHelper.isIcReviewLevel(1400)).thenReturn(true);
        when(dynamicIcReviewLevelHelper.getIcReviewLevelNumber(1400)).thenReturn(1);
        when(dynamicIcReviewLevelHelper.getLevelDisplayName(1400)).thenReturn("ONE");
        when(loanOfficer.emailAddress()).thenReturn("officer@example.com");
        when(loan.getClient()).thenReturn(client);
        when(client.getDisplayName()).thenReturn("Client");

        emailNotificationService.sendLoanDecisionAcceptedNotification(loan, decision, note);

        ArgumentCaptor<EmailDetail> emailCaptor = ArgumentCaptor.forClass(EmailDetail.class);
        verify(emailService).sendDefinedEmail(emailCaptor.capture());
        assertEquals("approver@example.com", emailCaptor.getValue().getAddress());
        assertEquals("officer@example.com", emailCaptor.getValue().getCc());
    }

    @Test
    void fallsBackToApplicationUserEmailWhenStaffEmailIsUnavailable() {
        when(decision.getNextLoanIcReviewDecisionState()).thenReturn(1400);
        when(decision.getIcReviewDecisionLevelOneBy()).thenReturn(approver);
        when(dynamicIcReviewLevelHelper.isIcReviewLevel(1400)).thenReturn(true);
        when(dynamicIcReviewLevelHelper.getIcReviewLevelNumber(1400)).thenReturn(1);
        when(dynamicIcReviewLevelHelper.getLevelDisplayName(1400)).thenReturn("ONE");
        when(loanOfficer.emailAddress()).thenReturn(null);
        when(loanOfficer.getId()).thenReturn(7L);
        when(appUserRepository.findAppUserByStaffId(7L)).thenReturn(loanOfficerAppUser);
        when(loanOfficerAppUser.getEmail()).thenReturn("fallback-officer@example.com");
        when(loan.getClient()).thenReturn(client);
        when(client.getDisplayName()).thenReturn("Client");

        emailNotificationService.sendLoanDecisionAcceptedNotification(loan, decision, note);

        ArgumentCaptor<EmailDetail> emailCaptor = ArgumentCaptor.forClass(EmailDetail.class);
        verify(emailService).sendDefinedEmail(emailCaptor.capture());
        assertEquals("fallback-officer@example.com", emailCaptor.getValue().getCc());
    }

    @Test
    void doesNotCopyLoanOfficerForPrepareAndSignNotification() {
        when(decision.getNextLoanIcReviewDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(decision.getLoan()).thenReturn(loan);
        when(loanOfficer.getId()).thenReturn(7L);
        when(appUserRepository.findAppUserByStaffId(7L)).thenReturn(approver);
        when(dynamicIcReviewLevelHelper.getLevelDisplayName(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue())).thenReturn("PREPARE");
        when(loan.getClient()).thenReturn(client);
        when(client.getDisplayName()).thenReturn("Client");

        emailNotificationService.sendLoanDecisionAcceptedNotification(loan, decision, note);

        ArgumentCaptor<EmailDetail> emailCaptor = ArgumentCaptor.forClass(EmailDetail.class);
        verify(emailService).sendDefinedEmail(emailCaptor.capture());
        assertNull(emailCaptor.getValue().getCc());
    }
}
