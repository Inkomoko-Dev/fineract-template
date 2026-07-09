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
package org.apache.fineract.infrastructure.africastalking.domain;

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
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;

@Entity
@Table(name = "communication_message")
@Getter
@Setter
@NoArgsConstructor
public class CommunicationMessage extends AbstractPersistableCustom {

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20, nullable = false)
    private CommunicationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20, nullable = false)
    private CommunicationDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", length = 20, nullable = false)
    private RecipientType recipientType;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "phone_number", length = 50, nullable = false)
    private String phoneNumber;

    @Column(name = "message_body", columnDefinition = "TEXT")
    private String messageBody;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private CommunicationMessageStatus status;

    @Column(name = "status_detail", length = 255)
    private String statusDetail;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "delivered_date")
    private LocalDateTime deliveredDate;

    @Column(name = "read_date")
    private LocalDateTime readDate;

    public static CommunicationMessage pendingOutbound(final CommunicationChannel channel, final String phoneNumber,
            final RecipientType recipientType, final Client client, final Staff staff, final String messageBody) {
        final CommunicationMessage message = new CommunicationMessage();
        message.channel = channel;
        message.direction = CommunicationDirection.OUTBOUND;
        message.recipientType = recipientType;
        message.client = client;
        message.staff = staff;
        message.phoneNumber = phoneNumber;
        message.messageBody = messageBody;
        message.status = CommunicationMessageStatus.PENDING;
        message.createdDate = DateUtils.getLocalDateTimeOfTenant();
        return message;
    }

    public static CommunicationMessage inboundWhatsApp(final String phoneNumber, final RecipientType recipientType, final Client client,
            final Staff staff, final String messageBody, final String externalId) {
        final CommunicationMessage message = new CommunicationMessage();
        message.channel = CommunicationChannel.WHATSAPP;
        message.direction = CommunicationDirection.INBOUND;
        message.recipientType = recipientType;
        message.client = client;
        message.staff = staff;
        message.phoneNumber = phoneNumber;
        message.messageBody = messageBody;
        message.externalId = externalId;
        message.status = CommunicationMessageStatus.DELIVERED;
        message.createdDate = DateUtils.getLocalDateTimeOfTenant();
        message.deliveredDate = message.createdDate;
        return message;
    }
}
