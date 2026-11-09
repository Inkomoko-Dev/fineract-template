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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.CommunicationMessageDispatchService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificationChannelTest {

    @Mock
    private CommunicationMessageRepository communicationMessageRepository;

    @Mock
    private CommunicationMessageDispatchService communicationMessageDispatchService;

    @InjectMocks
    private WhatsAppNotificationChannel channel;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
    }

    @Test
    void freeformPathNeverPersistsTemplateName() {
        when(communicationMessageRepository.saveAndFlush(any(CommunicationMessage.class))).thenAnswer(invocation -> {
            final CommunicationMessage message = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(message, "id", 42L);
            return message;
        });

        final NotificationCommand command = NotificationCommand.freeformWhatsApp(NotificationPurpose.CAMPAIGN, "+254712345678",
                RecipientType.CLIENT, null, null, "Hi Lucy, please pay by Friday.", false);

        channel.send(command);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository).saveAndFlush(captor.capture());
        final CommunicationMessage saved = captor.getValue();
        assertNull(saved.getTemplateName());
        assertNull(saved.getTemplateLanguage());
        assertNull(saved.getTemplateBodyValues());
        assertEquals("Hi Lucy, please pay by Friday.", saved.getMessageBody());
    }
}
