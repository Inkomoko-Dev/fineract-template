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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.junit.jupiter.api.Test;

class VoiceIvrDialTargetResolverTest {

    @Test
    void resolvesDepartmentCodesFromVoiceProperties() {
        final AfricasTalkingProperties properties = new AfricasTalkingProperties();
        properties.getVoice().setLoansDepartmentNumber("+254700000001");
        properties.getVoice().setSupportDepartmentNumber("+254700000002");
        properties.getVoice().setInternalDepartmentNumber("+254700000003");
        final PhoneNumberNormalizer normalizer = mock(PhoneNumberNormalizer.class);
        final VoiceIvrDialTargetResolver resolver = new VoiceIvrDialTargetResolver(properties, normalizer);

        assertThat(resolver.resolve("LOANS")).isEqualTo("+254700000001");
        assertThat(resolver.resolve("SUPPORT")).isEqualTo("+254700000002");
        assertThat(resolver.resolve("STAFF")).isEqualTo("+254700000003");
        assertThat(resolver.resolve("+254711111111")).isEqualTo("+254711111111");
    }

    @Test
    void prefersAssignedOfficerThenDepartmentQueue() {
        final AfricasTalkingProperties properties = new AfricasTalkingProperties();
        properties.getVoice().setLoansDepartmentNumber("+254700000001");
        final PhoneNumberNormalizer normalizer = mock(PhoneNumberNormalizer.class);
        when(normalizer.normalize("+254711100000")).thenReturn("+254711100000");
        final VoiceIvrDialTargetResolver resolver = new VoiceIvrDialTargetResolver(properties, normalizer);
        final Staff officer = mock(Staff.class);
        when(officer.mobileNo()).thenReturn("+254711100000");
        final Client client = mock(Client.class);
        when(client.getStaff()).thenReturn(officer);
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setClient(client);

        assertThat(resolver.resolve("LOANS", session)).isEqualTo("+254711100000,+254700000001");
    }
}
