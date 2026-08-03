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
package org.apache.fineract.infrastructure.africastalking.scheduler;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.service.CommunicationMessageDispatchService;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommunicationMessageScheduledJobService {

    private final CommunicationMessageDispatchService communicationMessageDispatchService;

    @CronTarget(jobName = JobName.SEND_WHATSAPP_MESSAGES)
    public void sendPendingWhatsAppMessages() {
        communicationMessageDispatchService.dispatchPendingMessages();
    }
}
