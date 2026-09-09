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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSettingRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppOperationalNotificationRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicket;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppOperationalNotificationServiceTest {

    @Mock
    private WhatsAppInteractiveSettingRepository settingRepository;
    @Mock
    private NotificationWritePlatformService notificationWritePlatformService;
    @Mock
    private WhatsAppOperationalNotificationRepository operationalNotificationRepository;

    private WhatsAppOperationalNotificationService service;

    @BeforeEach
    void setUp() {
        final WhatsAppInteractiveProperties properties = new WhatsAppInteractiveProperties();
        properties.setNotifyOnTicketCreated(true);
        properties.setOperationalNotifyUserIds("9");
        final WhatsAppInteractiveSettingsProvider settingsProvider = new WhatsAppInteractiveSettingsProvider(properties, settingRepository);
        service = new WhatsAppOperationalNotificationService(settingsProvider, notificationWritePlatformService,
                operationalNotificationRepository);
    }

    @Test
    void notifiesConfiguredUsersOnTicketCreated() {
        final WhatsAppSupportTicket ticket = new WhatsAppSupportTicket();
        ticket.setTicketNumber("WA-20260907-0001");
        ticket.setCategory("ADVISOR");

        service.notifyTicketCreated(ticket);

        verify(notificationWritePlatformService).notify(eq(java.util.List.of(9L)), eq("WHATSAPP_SUPPORT_TICKET"), eq(null), eq("CREATED"),
                eq(null), any(String.class), eq(true));
    }

    @Test
    void skipsNotificationWhenDisabled() {
        final WhatsAppInteractiveProperties properties = new WhatsAppInteractiveProperties();
        properties.setNotifyOnTicketCreated(false);
        final WhatsAppInteractiveSettingsProvider settingsProvider = new WhatsAppInteractiveSettingsProvider(properties, settingRepository);
        service = new WhatsAppOperationalNotificationService(settingsProvider, notificationWritePlatformService,
                operationalNotificationRepository);
        final WhatsAppSupportTicket ticket = new WhatsAppSupportTicket();
        ticket.setTicketNumber("WA-20260907-0002");
        ticket.setCategory("OTHER");

        service.notifyTicketCreated(ticket);

        verify(notificationWritePlatformService, never()).notify(any(java.util.Collection.class), any(), any(), any(), any(), any(),
                eq(true));
    }
}
