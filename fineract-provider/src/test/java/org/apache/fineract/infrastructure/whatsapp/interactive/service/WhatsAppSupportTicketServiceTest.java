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
package org.apache.fineract.infrastructure.whatsapp.interactive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicket;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicketRepository;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppSupportTicketServiceTest {

    @Mock
    private WhatsAppSupportTicketRepository ticketRepository;
    @Mock
    private WhatsAppBusinessHoursService businessHoursService;
    @Mock
    private CommunicationMessageRepository communicationMessageRepository;
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;

    private WhatsAppSupportTicketService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppSupportTicketService(ticketRepository, businessHoursService, communicationMessageRepository,
                staffRepositoryWrapper);
    }

    @Test
    void createsTicketFromEscalation() {
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.setPhoneNumber("+254712345678");
        final LocalDateTime slaDueAt = LocalDateTime.of(2026, 9, 7, 14, 0);
        when(businessHoursService.calculateSlaDueAt(any())).thenReturn(slaDueAt);
        when(ticketRepository.count()).thenReturn(0L);
        when(ticketRepository.save(any(WhatsAppSupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final WhatsAppSupportTicket ticket = service.createFromEscalation(session, null, "OTHER", "Need help", "en");

        assertThat(ticket.getTicketNumber()).startsWith("WA-");
        assertThat(ticket.getStatus()).isEqualTo(WhatsAppTicketStatus.OPEN);
        assertThat(ticket.getCategory()).isEqualTo("OTHER");
        assertThat(ticket.getCustomerMessage()).isEqualTo("Need help");
        assertThat(ticket.getSlaDueAt()).isEqualTo(slaDueAt);
        final ArgumentCaptor<WhatsAppSupportTicket> captor = ArgumentCaptor.forClass(WhatsAppSupportTicket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isEqualTo("+254712345678");
    }
}
