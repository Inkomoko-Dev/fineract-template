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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppLoanSelfService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSessionContextSerializer;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceIvrLoanSelfServiceGateTest {

    @Mock
    private VoiceIvrClientAuthService clientAuthService;
    @Mock
    private WhatsAppLoanSelfService loanSelfService;
    @Mock
    private VoiceIvrSessionService sessionService;
    @Mock
    private WhatsAppInteractiveSettingsProvider settings;

    private VoiceIvrLoanSelfServiceGate loanGate;

    @BeforeEach
    void setUp() {
        final AfricasTalkingProperties properties = new AfricasTalkingProperties();
        loanGate = new VoiceIvrLoanSelfServiceGate(clientAuthService, loanSelfService, sessionService,
                new WhatsAppSessionContextSerializer(), settings, properties);
    }

    @Test
    void delegatesToAuthWhenSessionNotAuthenticated() {
        final VoiceIvrSession session = new VoiceIvrSession();
        when(clientAuthService.isSessionAuthenticated(session)).thenReturn(false);
        when(clientAuthService.beginAuthentication(session, null, "LOAN_BALANCE")).thenReturn("<Response><GetDigits/></Response>");

        final String xml = loanGate.beginLoanService(session, "LOAN_BALANCE");

        assertThat(xml).contains("<GetDigits");
    }

    @Test
    void deliversLoanBalanceForSingleLoan() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setLanguageCode("en");
        final Loan loan = org.mockito.Mockito.mock(Loan.class);
        when(loan.getId()).thenReturn(55L);
        when(loanSelfService.findSelfServiceLoans(10L)).thenReturn(List.of(loan));
        when(loanSelfService.buildLoanServiceResponse(55L, "LOAN_BALANCE", "en")).thenReturn("Loan 0001 outstanding balance: 1000 RWF");
        when(sessionService.save(session)).thenReturn(session);

        final WhatsAppSessionContextSerializer serializer = new WhatsAppSessionContextSerializer();
        final org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext context =
                org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext.empty();
        context.setCandidateClientId(10L);
        session.setSessionContext(serializer.toJson(context));

        final String xml = loanGate.continueAfterAuthentication(session, "LOAN_BALANCE", "en");

        assertThat(xml).contains("outstanding balance");
    }
}
