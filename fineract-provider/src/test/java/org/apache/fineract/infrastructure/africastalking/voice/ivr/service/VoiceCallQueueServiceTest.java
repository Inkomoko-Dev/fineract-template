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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceCallQueueStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallQueueEntry;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceCallQueueEntryRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceCallQueueServiceTest {

    @Mock
    private VoiceCallQueueEntryRepository queueEntryRepository;

    private VoiceCallQueueService queueService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        queueService = new VoiceCallQueueService(queueEntryRepository);
    }

    @Test
    void enqueuesCallerAtNextPosition() {
        when(queueEntryRepository.countByDepartmentCodeAndStatus("SUPPORT", VoiceCallQueueStatus.WAITING)).thenReturn(2L);
        when(queueEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final VoiceCallQueueEntry entry = queueService.enqueue(buildSession(), "SUPPORT", 99L);

        assertThat(entry.getQueuePosition()).isEqualTo(3);
        assertThat(entry.getStatus()).isEqualTo(VoiceCallQueueStatus.WAITING);
    }

    private VoiceIvrSession buildSession() {
        final VoiceIvrSession session = new VoiceIvrSession();
        session.setExternalSessionId("ATV_1");
        session.setCallerNumber("+254700000099");
        return session;
    }
}
