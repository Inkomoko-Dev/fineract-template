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
package org.apache.fineract.infrastructure.jobs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SchedulerJobListenerTriggerTest {

    @Mock
    private SchedularWritePlatformService schedularService;
    @Mock
    private AppUserRepositoryWrapper userRepository;
    @Mock
    private BusinessDateReadPlatformService businessDateReadPlatformService;

    @InjectMocks
    private SchedulerJobListener listener;

    @BeforeEach
    void setUp() {
        given(userRepository.fetchSystemUser()).willReturn(mock(AppUser.class));
        given(businessDateReadPlatformService.getBusinessDates()).willReturn(new HashMap<>());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void manualRunExposesTheUserWhoTriggeredIt() {
        JobDataMap data = new JobDataMap();
        data.put(SchedulerServiceConstants.TRIGGER_TYPE_REFERENCE, SchedulerServiceConstants.TRIGGER_TYPE_APPLICATION);
        data.put(SchedulerServiceConstants.TRIGGERED_BY_USER_ID, 42L);

        listener.jobToBeExecuted(context(data));

        assertThat(ThreadLocalContextUtil.getJobTriggerType()).isEqualTo(SchedulerServiceConstants.TRIGGER_TYPE_APPLICATION);
        assertThat(ThreadLocalContextUtil.getJobTriggeredByUserId()).isEqualTo(42L);
    }

    @Test
    void cronRunOnAReusedThreadDoesNotInheritAnEarlierManualRunner() {
        ThreadLocalContextUtil.setJobTrigger(SchedulerServiceConstants.TRIGGER_TYPE_APPLICATION, 42L);

        listener.jobToBeExecuted(context(new JobDataMap()));

        assertThat(ThreadLocalContextUtil.getJobTriggerType()).isEqualTo(SchedulerServiceConstants.TRIGGER_TYPE_CRON);
        assertThat(ThreadLocalContextUtil.getJobTriggeredByUserId()).isNull();
    }

    private static JobExecutionContext context(JobDataMap data) {
        JobExecutionContext context = mock(JobExecutionContext.class);
        given(context.getMergedJobDataMap()).willReturn(data);
        return context;
    }
}
