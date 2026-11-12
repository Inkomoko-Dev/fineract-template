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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppOperationalNotificationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppOperationalNotification;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppOperationalNotificationRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicket;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppOperationalNotificationService {

    private final WhatsAppInteractiveSettingsProvider settingsProvider;
    private final NotificationWritePlatformService notificationWritePlatformService;
    private final WhatsAppOperationalNotificationRepository operationalNotificationRepository;

    @Transactional
    public void notifyTicketCreated(final WhatsAppSupportTicket ticket) {
        if (!settingsProvider.isNotifyOnTicketCreated()) {
            return;
        }
        notifyRecipients(ticket, WhatsAppOperationalNotificationType.TICKET_CREATED,
                "New WhatsApp support ticket " + ticket.getTicketNumber() + " (" + ticket.getCategory() + ")");
    }

    @Transactional
    public void notifySlaBreach(final WhatsAppSupportTicket ticket) {
        if (!settingsProvider.isNotifyOnSlaBreach()) {
            return;
        }
        if (operationalNotificationRepository.findByTicketIdAndNotificationType(ticket.getId(), WhatsAppOperationalNotificationType.SLA_BREACH)
                .isPresent()) {
            return;
        }
        notifyRecipients(ticket, WhatsAppOperationalNotificationType.SLA_BREACH,
                "WhatsApp ticket " + ticket.getTicketNumber() + " has breached first-response SLA");
        operationalNotificationRepository.save(
                WhatsAppOperationalNotification.record(ticket.getId(), WhatsAppOperationalNotificationType.SLA_BREACH));
    }

    private void notifyRecipients(final WhatsAppSupportTicket ticket, final WhatsAppOperationalNotificationType notificationType,
            final String content) {
        final List<Long> userIds = resolveRecipientUserIds();
        if (userIds.isEmpty()) {
            return;
        }
        if (notificationType == WhatsAppOperationalNotificationType.TICKET_CREATED) {
            notificationWritePlatformService.notify(userIds, "WHATSAPP_SUPPORT_TICKET", ticket.getId(), "CREATED", null, content, true);
            return;
        }
        notificationWritePlatformService.notify(userIds, "WHATSAPP_SUPPORT_TICKET", ticket.getId(), "SLA_BREACH", null, content, true);
    }

    private List<Long> resolveRecipientUserIds() {
        final String configured = settingsProvider.getOperationalNotifyUserIds();
        if (StringUtils.isBlank(configured)) {
            return List.of();
        }
        return Arrays.stream(configured.split(",")).map(String::trim).filter(NumberUtils::isParsable).map(Long::valueOf)
                .collect(Collectors.toList());
    }
}
