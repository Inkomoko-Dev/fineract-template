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

import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Registers @CronTarget handlers for jobs that exist in the UAT job_name table but whose full
 * implementations (CGLT residual closures / WhatsApp SLA) are not yet on this branch. Without these
 * methods JobRegisterServiceImpl fails to schedule the DB rows at startup.
 */
@Service
public class UatPendingScheduledJobService {

    private static final Logger LOG = LoggerFactory.getLogger(UatPendingScheduledJobService.class);

    @CronTarget(jobName = JobName.PROCESS_RESIDUAL_LOAN_CLOSURES)
    public void processResidualLoanClosures() {
        LOG.warn("PROCESS_RESIDUAL_LOAN_CLOSURES scheduled but residual-closure domain logic is not yet ported to this branch; no-op");
    }

    @CronTarget(jobName = JobName.MONITOR_WHATSAPP_SUPPORT_TICKET_SLA)
    public void monitorWhatsAppSupportTicketSla() {
        LOG.warn("MONITOR_WHATSAPP_SUPPORT_TICKET_SLA scheduled but WhatsApp SLA monitor is not yet ported to this branch; no-op");
    }
}
