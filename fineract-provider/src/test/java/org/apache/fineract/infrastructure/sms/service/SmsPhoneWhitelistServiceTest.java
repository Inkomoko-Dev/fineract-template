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
package org.apache.fineract.infrastructure.sms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.campaigns.sms.data.MessageGatewayConfigurationData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.apache.fineract.infrastructure.sms.data.SmsMessageApiQueueResourceData;
import org.apache.fineract.infrastructure.sms.domain.SmsMessage;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageStatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SmsPhoneWhitelistServiceTest {

    @Mock
    private ExternalServicesPropertiesReadPlatformService propertiesReadPlatformService;
    @Mock
    private SmsMessageRepository smsMessageRepository;

    private SmsPhoneWhitelistService service;

    @BeforeEach
    void setUp() {
        service = new SmsPhoneWhitelistService(propertiesReadPlatformService, smsMessageRepository);
    }

    @Test
    void parseWhitelistSupportsCommaSeparatedNumbers() {
        Set<String> whitelist = service.parseWhitelist("+254702719701, +254711111111");
        assertEquals(Set.of("+254702719701", "+254711111111"), whitelist);
    }

    @Test
    void filterAllowsWhitelistedAndMarksOthersFailed() {
        when(propertiesReadPlatformService.getSMSGateway()).thenReturn(new MessageGatewayConfigurationData(null, null, "host", 8005, "/",
                null, null, false, "key", "+254702719701", true));
        SmsMessage blocked = SmsMessage.pendingSms(null, null, null, null, "hello", "+254700000000", null, false);
        ReflectionTestUtils.setField(blocked, "id", 2L);
        when(smsMessageRepository.findById(2L)).thenReturn(Optional.of(blocked));

        SmsMessageApiQueueResourceData allowed = SmsMessageApiQueueResourceData.instance(1L, null, "+254702719701", "ok", "FINERACT");
        SmsMessageApiQueueResourceData denied = SmsMessageApiQueueResourceData.instance(2L, null, "+254700000000", "no", "FINERACT");

        List<SmsMessageApiQueueResourceData> result = List.copyOf(service.filterAllowedOrMarkBlocked(List.of(allowed, denied)));

        assertEquals(1, result.size());
        assertEquals("+254702719701", result.get(0).getPhoneNumber());
        assertEquals(SmsMessageStatusType.FAILED.getValue(), blocked.getStatusType());
        assertEquals(SmsPhoneWhitelistService.BLOCKED_ERROR_MESSAGE, blocked.getErrorMessage());
        verify(smsMessageRepository).saveAndFlush(blocked);
    }

    @Test
    void filterIsNoOpWhenWhitelistDisabled() {
        when(propertiesReadPlatformService.getSMSGateway()).thenReturn(new MessageGatewayConfigurationData(null, null, "host", 8005, "/",
                null, null, false, "key", "+254702719701", false));
        SmsMessageApiQueueResourceData message = SmsMessageApiQueueResourceData.instance(1L, null, "+254700000000", "ok", "FINERACT");

        assertEquals(1, service.filterAllowedOrMarkBlocked(List.of(message)).size());
        verify(smsMessageRepository, never()).findById(any());
    }

    @Test
    void isAllowedMatchesDigitsIgnoringPlus() {
        when(propertiesReadPlatformService.getSMSGateway()).thenReturn(new MessageGatewayConfigurationData(null, null, "host", 8005, "/",
                null, null, false, "key", "+254702719701", true));
        assertTrue(service.isAllowed("254702719701"));
        assertFalse(service.isAllowed("+254700000000"));
    }
}
