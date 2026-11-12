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
package org.apache.fineract.infrastructure.africastalking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipientResolutionServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private StaffRepository staffRepository;

    private RecipientResolutionService recipientResolutionService;

    @BeforeEach
    void setUp() {
        final AfricasTalkingProperties properties = new AfricasTalkingProperties();
        properties.getPhone().setDefaultCountryCode("254");
        recipientResolutionService = new RecipientResolutionService(new PhoneNumberNormalizer(properties), clientRepository,
                staffRepository);
    }

    @Test
    void resolvesClientByPhoneNumber() {
        final Client client = mock(Client.class);
        when(client.getId()).thenReturn(10L);
        when(clientRepository.findByMobileNumbers(anyList())).thenReturn(List.of(client));
        when(staffRepository.findByMobileNumbers(anyList())).thenReturn(List.of());

        final var resolved = recipientResolutionService.resolve("0712345678");

        assertEquals(RecipientType.CLIENT, resolved.getRecipientType());
        assertEquals(10L, resolved.getClientId());
        assertEquals("+254712345678", resolved.getNormalizedPhoneNumber());
    }

    @Test
    void resolvesStaffWhenClientNotFound() {
        final Staff staff = mock(Staff.class);
        when(staff.getId()).thenReturn(20L);
        when(clientRepository.findByMobileNumbers(anyList())).thenReturn(List.of());
        when(staffRepository.findByMobileNumbers(anyList())).thenReturn(List.of(staff));

        final var resolved = recipientResolutionService.resolve("+254700000001");

        assertEquals(RecipientType.STAFF, resolved.getRecipientType());
        assertEquals(20L, resolved.getStaffId());
    }

    @Test
    void returnsUnknownWhenNoMatch() {
        when(clientRepository.findByMobileNumbers(anyList())).thenReturn(List.of());
        when(staffRepository.findByMobileNumbers(anyList())).thenReturn(List.of());

        final var resolved = recipientResolutionService.resolve("+254799999999");

        assertEquals(RecipientType.UNKNOWN, resolved.getRecipientType());
        assertNull(resolved.getClientId());
        assertNull(resolved.getStaffId());
    }
}
