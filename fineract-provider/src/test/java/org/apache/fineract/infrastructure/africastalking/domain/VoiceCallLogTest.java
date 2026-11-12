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
package org.apache.fineract.infrastructure.africastalking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceCallLogTest {

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void ignoresOutOfOrderStatusRewind() {
        final VoiceCallLog callLog = VoiceCallLog.inbound("ATV_1", "+254700000099", "+254711082867", null, null);
        callLog.applyEventUpdate(AfricasTalkingConstants.CALL_STATUS_ANSWERED, null, null, null);
        callLog.applyEventUpdate(AfricasTalkingConstants.CALL_STATUS_COMPLETED, 30, "https://rec.example/1.mp3", null);

        callLog.applyEventUpdate(AfricasTalkingConstants.CALL_STATUS_RINGING, null, null, null);

        assertThat(callLog.getStatus()).isEqualTo(AfricasTalkingConstants.CALL_STATUS_COMPLETED);
        assertThat(callLog.getRecordingUrl()).isEqualTo("https://rec.example/1.mp3");
        assertThat(callLog.getDurationSeconds()).isEqualTo(30);
    }

    @Test
    void allowsForwardStatusTransitions() {
        assertThat(VoiceCallLog.isForwardStatusTransition(AfricasTalkingConstants.CALL_STATUS_INITIATED,
                AfricasTalkingConstants.CALL_STATUS_RINGING)).isTrue();
        assertThat(VoiceCallLog.isForwardStatusTransition(AfricasTalkingConstants.CALL_STATUS_COMPLETED,
                AfricasTalkingConstants.CALL_STATUS_RINGING)).isFalse();
    }
}
