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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceIvrCallerIdentificationServiceTest {

    private final VoiceIvrCallerIdentificationService service = new VoiceIvrCallerIdentificationService();

    @Test
    void greetsIdentifiedClientByName() {
        final VoiceIvrSession session = new VoiceIvrSession();
        final Client client = org.mockito.Mockito.mock(Client.class);
        when(client.getDisplayName()).thenReturn("Jane Client");
        session.setClient(client);

        assertThat(service.buildGreeting(session, "en")).isEqualTo("Welcome back, Jane Client.");
    }

    @Test
    void greetsIdentifiedStaffByName() {
        final VoiceIvrSession session = new VoiceIvrSession();
        final Staff staff = org.mockito.Mockito.mock(Staff.class);
        when(staff.displayName()).thenReturn("John Staff");
        session.setStaff(staff);

        assertThat(service.buildGreeting(session, "rw")).isEqualTo("Murakaza neza, John Staff.");
    }

    @Test
    void returnsNullForUnknownCaller() {
        assertThat(service.buildGreeting(new VoiceIvrSession(), "en")).isNull();
    }
}
