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
package org.apache.fineract.infrastructure.sms.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.apache.fineract.infrastructure.campaigns.helper.SmsConfigUtils;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.gcm.service.NotificationSenderService;
import org.apache.fineract.infrastructure.sms.data.SmsMessageApiQueueResourceData;
import org.apache.fineract.infrastructure.sms.domain.SmsMessage;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageStatusType;
import org.apache.fineract.infrastructure.sms.service.SmsPhoneWhitelistService;
import org.apache.fineract.infrastructure.sms.service.SmsReadPlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SmsMessageScheduledJobServiceImplTest {

    @Mock
    private SmsMessageRepository smsMessageRepository;
    @Mock
    private SmsReadPlatformService smsReadPlatformService;
    @Mock
    private SmsConfigUtils smsConfigUtils;
    @Mock
    private NotificationSenderService notificationSenderService;
    @Mock
    private SmsPhoneWhitelistService smsPhoneWhitelistService;
    @Mock
    private ExecutorService triggeredExecutorService;
    @Mock
    private ExecutorService genericExecutorService;

    private SmsMessageScheduledJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsMessageScheduledJobServiceImpl(smsMessageRepository, smsReadPlatformService, smsConfigUtils,
                notificationSenderService, smsPhoneWhitelistService);
        ReflectionTestUtils.setField(service, "triggeredExecutorService", triggeredExecutorService);
        ReflectionTestUtils.setField(service, "genericExecutorService", genericExecutorService);
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now(ZoneId.systemDefault()));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearDataSourceContext();
        ThreadLocalContextUtil.clearTenant();
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now(ZoneId.systemDefault()));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @Test
    void sendTriggeredMessagePersistsSmsBeforeQueueingGatewayPayload() {
        SmsMessage smsMessage = SmsMessage.pendingSms(null, null, null, null, "hello", "+250702719701", null, false);
        when(smsMessageRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<SmsMessage> savedMessages = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedMessages.get(0), "id", 42L);
            return savedMessages;
        });

        service.sendTriggeredMessage(List.of(smsMessage), 7L);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(triggeredExecutorService).execute(taskCaptor.capture());
        @SuppressWarnings("unchecked")
        Collection<SmsMessageApiQueueResourceData> queuedMessages = (Collection<SmsMessageApiQueueResourceData>) ReflectionTestUtils
                .getField(taskCaptor.getValue(), "apiQueueResourceDatas");

        assertEquals(SmsMessageStatusType.WAITING_FOR_DELIVERY_REPORT.getValue(), smsMessage.getStatusType());
        assertEquals(42L, queuedMessages.iterator().next().getInternalId());
    }

    @Test
    void processSmsGatewayResponseHandlesNumericInternalIds() {
        SmsMessage smsMessage = SmsMessage.pendingSms(null, null, null, null, "hello", "+250702719701", null, false);
        ReflectionTestUtils.setField(smsMessage, "id", 123L);
        when(smsMessageRepository.findById(123L)).thenReturn(Optional.of(smsMessage));

        ReflectionTestUtils.invokeMethod(service, "processSmsGatewayResponse",
                "[{\"id\":123,\"status\":\"FAILED\",\"error\":\"provider failure\"}]");

        assertEquals(SmsMessageStatusType.FAILED.getValue(), smsMessage.getStatusType());
        assertEquals("provider failure", smsMessage.getErrorMessage());
        verify(smsMessageRepository).saveAndFlush(smsMessage);
    }
}
