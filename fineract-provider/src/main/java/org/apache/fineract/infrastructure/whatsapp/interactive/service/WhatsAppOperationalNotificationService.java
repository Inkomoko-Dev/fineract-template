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
        final StringBuilder content = new StringBuilder("New WhatsApp support ticket ").append(ticket.getTicketNumber()).append(" (")
                .append(ticket.getCategory()).append(")");
        if (ticket.getAssignedStaff() != null) {
            content.append(" assigned to ").append(ticket.getAssignedStaff().displayName());
        }
        notifyRecipients(ticket, WhatsAppOperationalNotificationType.TICKET_CREATED, "CREATED", content.toString());
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
        notifyRecipients(ticket, WhatsAppOperationalNotificationType.SLA_BREACH, "SLA_BREACH",
                "WhatsApp ticket " + ticket.getTicketNumber() + " has breached first-response SLA");
        operationalNotificationRepository.save(
                WhatsAppOperationalNotification.record(ticket.getId(), WhatsAppOperationalNotificationType.SLA_BREACH));
    }

    @Transactional
    public void notifyTicketAssigned(final WhatsAppSupportTicket ticket) {
        if (!settingsProvider.isNotifyOnTicketAssigned()) {
            return;
        }
        final String assignee = ticket.getAssignedStaff() != null ? ticket.getAssignedStaff().displayName() : "unassigned";
        notifyRecipients(ticket, WhatsAppOperationalNotificationType.TICKET_ASSIGNED, "ASSIGNED",
                "WhatsApp ticket " + ticket.getTicketNumber() + " assigned to " + assignee);
    }

    @Transactional
    public void notifyTicketResolved(final WhatsAppSupportTicket ticket) {
        if (!settingsProvider.isNotifyOnTicketResolved()) {
            return;
        }
        notifyRecipients(ticket, WhatsAppOperationalNotificationType.TICKET_RESOLVED, "RESOLVED",
                "WhatsApp ticket " + ticket.getTicketNumber() + " marked " + ticket.getStatus());
    }

    private void notifyRecipients(final WhatsAppSupportTicket ticket, final WhatsAppOperationalNotificationType notificationType,
            final String action, final String content) {
        final List<Long> userIds = resolveRecipientUserIds();
        if (userIds.isEmpty()) {
            return;
        }
        notificationWritePlatformService.notify(userIds, "WHATSAPP_SUPPORT_TICKET", ticket.getId(), action, null, content, true);
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
