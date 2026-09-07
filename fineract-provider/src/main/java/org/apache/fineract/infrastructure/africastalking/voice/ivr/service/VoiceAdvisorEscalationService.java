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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicket;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppOperationalNotificationService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSupportTicketService;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceAdvisorEscalationService {

    private final WhatsAppSupportTicketService supportTicketService;
    private final WhatsAppOperationalNotificationService operationalNotificationService;

    @Transactional
    public WhatsAppSupportTicket escalate(final VoiceIvrSession session, final String departmentCode, final String languageCode) {
        final Client client = session.getClient();
        final WhatsAppSupportTicket ticket = supportTicketService.createFromVoiceEscalation(session.getCallerNumber(), session.getId(),
                client, departmentCode, languageCode);
        operationalNotificationService.notifyTicketCreated(ticket);
        return ticket;
    }
}
