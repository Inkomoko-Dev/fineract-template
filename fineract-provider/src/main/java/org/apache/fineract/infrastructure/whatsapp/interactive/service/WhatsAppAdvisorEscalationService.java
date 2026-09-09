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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicket;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppAdvisorEscalationService {

    private final WhatsAppSupportTicketService supportTicketService;
    private final WhatsAppBusinessHoursService businessHoursService;
    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppSessionContextSerializer contextSerializer;

    private final WhatsAppOperationalNotificationService operationalNotificationService;

    @Transactional
    public String escalate(final WhatsAppConversationSession session, final Client client, final String category,
            final String customerMessage, final String languageCode) {
        final WhatsAppSupportTicket ticket = supportTicketService.createFromEscalation(session, client, category, customerMessage,
                languageCode);
        operationalNotificationService.notifyTicketCreated(ticket);
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        context.setTicketId(ticket.getId());
        context.setPendingAction(null);
        context.setCapturedInput(null);
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.ESCALATED);
        sessionService.save(session);
        final boolean withinBusinessHours = businessHoursService.isWithinBusinessHours(ticket.getCreatedDate());
        return WhatsAppInteractiveMessages.advisorHandoff(languageCode, ticket.getTicketNumber(), withinBusinessHours,
                businessHoursService.describeBusinessHours());
    }
}
