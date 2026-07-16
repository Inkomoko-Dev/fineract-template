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
package org.apache.fineract.infrastructure.africastalking.data;

import java.time.LocalDateTime;
import lombok.Getter;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;

@Getter
public class CommunicationMessageData {

    private final Long id;
    private final String externalId;
    private final CommunicationChannel channel;
    private final CommunicationDirection direction;
    private final RecipientType recipientType;
    private final Long clientId;
    private final Long staffId;
    private final String phoneNumber;
    private final String messageBody;
    private final String templateName;
    private final CommunicationMessageStatus status;
    private final String statusDetail;
    private final LocalDateTime createdDate;
    private final LocalDateTime deliveredDate;
    private final LocalDateTime readDate;

    private CommunicationMessageData(final Long id, final String externalId, final CommunicationChannel channel,
            final CommunicationDirection direction, final RecipientType recipientType, final Long clientId, final Long staffId,
            final String phoneNumber, final String messageBody, final String templateName, final CommunicationMessageStatus status,
            final String statusDetail, final LocalDateTime createdDate, final LocalDateTime deliveredDate, final LocalDateTime readDate) {
        this.id = id;
        this.externalId = externalId;
        this.channel = channel;
        this.direction = direction;
        this.recipientType = recipientType;
        this.clientId = clientId;
        this.staffId = staffId;
        this.phoneNumber = phoneNumber;
        this.messageBody = messageBody;
        this.templateName = templateName;
        this.status = status;
        this.statusDetail = statusDetail;
        this.createdDate = createdDate;
        this.deliveredDate = deliveredDate;
        this.readDate = readDate;
    }

    public static CommunicationMessageData instance(final Long id, final String externalId, final CommunicationChannel channel,
            final CommunicationDirection direction, final RecipientType recipientType, final Long clientId, final Long staffId,
            final String phoneNumber, final String messageBody, final String templateName, final CommunicationMessageStatus status,
            final String statusDetail, final LocalDateTime createdDate, final LocalDateTime deliveredDate, final LocalDateTime readDate) {
        return new CommunicationMessageData(id, externalId, channel, direction, recipientType, clientId, staffId, phoneNumber, messageBody,
                templateName, status, statusDetail, createdDate, deliveredDate, readDate);
    }

    public static CommunicationMessageData fromEntity(final CommunicationMessage message) {
        return instance(message.getId(), message.getExternalId(), message.getChannel(), message.getDirection(), message.getRecipientType(),
                message.getClient() != null ? message.getClient().getId() : null,
                message.getStaff() != null ? message.getStaff().getId() : null, message.getPhoneNumber(), message.getMessageBody(),
                message.getTemplateName(), message.getStatus(), message.getStatusDetail(), message.getCreatedDate(),
                message.getDeliveredDate(), message.getReadDate());
    }
}
