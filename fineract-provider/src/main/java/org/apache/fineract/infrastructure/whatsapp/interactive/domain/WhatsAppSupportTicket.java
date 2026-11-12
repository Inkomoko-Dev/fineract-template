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
package org.apache.fineract.infrastructure.whatsapp.interactive.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketPriority;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketStatus;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;

@Entity
@Table(name = "whatsapp_support_ticket")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppSupportTicket extends AbstractPersistableCustom {

    @Column(name = "ticket_number", length = 30, nullable = false, unique = true)
    private String ticketNumber;

    @Column(name = "phone_number", length = 50, nullable = false)
    private String phoneNumber;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "conversation_session_id")
    private Long conversationSessionId;

    @Column(name = "voice_ivr_session_id")
    private Long voiceIvrSessionId;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private WhatsAppTicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20, nullable = false)
    private WhatsAppTicketPriority priority;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "customer_message")
    private String customerMessage;

    @Column(name = "language_code", length = 15)
    private String languageCode;

    @ManyToOne
    @JoinColumn(name = "assigned_staff_id")
    private Staff assignedStaff;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "lastmodified_date", nullable = false)
    private LocalDateTime lastModifiedDate;
}
