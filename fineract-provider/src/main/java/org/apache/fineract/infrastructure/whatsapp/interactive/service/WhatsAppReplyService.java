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
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.infrastructure.notifications.data.NotificationResult;
import org.apache.fineract.infrastructure.notifications.service.NotificationCommandService;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhatsAppReplyService {

    private final NotificationCommandService notificationCommandService;

    public void sendTransactionalReply(final String phoneNumber, final RecipientType recipientType, final Client client,
            final Staff staff, final String message) {
        final NotificationCommand command = NotificationCommand.freeformWhatsApp(NotificationPurpose.TRANSACTIONAL, phoneNumber,
                recipientType, client, staff, message, true);
        final NotificationResult result = notificationCommandService.send(command);
        if (!result.isAccepted()) {
            throw new IllegalStateException("Failed to send WhatsApp session reply: " + result.getRejectionReason());
        }
    }
}
