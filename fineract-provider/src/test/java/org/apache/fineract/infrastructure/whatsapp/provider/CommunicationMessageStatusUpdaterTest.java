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
package org.apache.fineract.infrastructure.whatsapp.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunicationMessageStatusUpdaterTest {

    @Mock
    private CommunicationMessageRepository communicationMessageRepository;

    private CommunicationMessageStatusUpdater statusUpdater;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        statusUpdater = new CommunicationMessageStatusUpdater(communicationMessageRepository);
    }

    @Test
    void mapsMetaDeliveredAndFailedStatuses() {
        final CommunicationMessage delivered = new CommunicationMessage();
        delivered.setExternalId("wamid.1");
        delivered.setStatus(CommunicationMessageStatus.SENT);
        when(communicationMessageRepository.findByExternalId("wamid.1")).thenReturn(Optional.of(delivered));

        assertTrue(statusUpdater.applyProviderStatus("wamid.1", "delivered", null));
        assertEquals(CommunicationMessageStatus.DELIVERED, delivered.getStatus());
        verify(communicationMessageRepository).save(delivered);

        final CommunicationMessage failed = new CommunicationMessage();
        failed.setExternalId("wamid.2");
        failed.setStatus(CommunicationMessageStatus.SENT);
        when(communicationMessageRepository.findByExternalId("wamid.2")).thenReturn(Optional.of(failed));

        assertTrue(statusUpdater.applyProviderStatus("wamid.2", "failed", "Undeliverable"));
        assertEquals(CommunicationMessageStatus.FAILED, failed.getStatus());
        assertEquals("Undeliverable", failed.getStatusDetail());

        when(communicationMessageRepository.findByExternalId("wamid.missing")).thenReturn(Optional.empty());
        assertFalse(statusUpdater.applyProviderStatus("wamid.missing", "delivered", null));
    }
}
