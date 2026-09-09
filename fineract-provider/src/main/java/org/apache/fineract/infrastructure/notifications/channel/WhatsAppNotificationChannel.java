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
package org.apache.fineract.infrastructure.notifications.channel;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.service.CommunicationMessageDispatchService;
import org.apache.fineract.infrastructure.notifications.constants.NotificationIntent;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.infrastructure.notifications.data.NotificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppNotificationChannel {

    private final CommunicationMessageRepository communicationMessageRepository;
    private final CommunicationMessageDispatchService communicationMessageDispatchService;

    @Transactional
    public NotificationResult send(final NotificationCommand command) {
        final CommunicationMessage message;
        if (command.getIntent() == NotificationIntent.TEMPLATE) {
            message = CommunicationMessage.pendingOutboundTemplate(command.getPhoneNumber(), command.getRecipientType(),
                    command.getClient(), command.getStaff(), command.getTemplateName(), command.getTemplateLanguage(),
                    command.getTemplateBodyValuesJson(), command.getMessageBody(), command.getCampaignId());
        } else {
            message = CommunicationMessage.pendingOutbound(org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel.WHATSAPP,
                    command.getPhoneNumber(), command.getRecipientType(), command.getClient(), command.getStaff(),
                    command.getMessageBody());
            if (StringUtils.isNotBlank(command.getTemplateName())) {
                message.setTemplateName(command.getTemplateName());
            }
        }

        if (command.isSendImmediately()) {
            message.setIdempotencyKey(UUID.randomUUID().toString());
            final CommunicationMessage saved = communicationMessageRepository.saveAndFlush(message);
            communicationMessageDispatchService.dispatchMessage(saved);
            return NotificationResult.accepted(requireId(saved));
        }

        final CommunicationMessage saved = communicationMessageRepository.saveAndFlush(message);
        return NotificationResult.accepted(requireId(saved));
    }

    private static Long requireId(final CommunicationMessage message) {
        final Long id = message.getId();
        if (id == null) {
            throw new IllegalStateException("Communication message was not assigned a database id after save");
        }
        return id;
    }
}
