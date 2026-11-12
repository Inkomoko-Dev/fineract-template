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
package org.apache.fineract.portfolio.creditdecision.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.springframework.stereotype.Service;

/** The scheduled job that sends failed credit decision requests again once their retry time has come. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditDecisionRetryJobService {

    private final CreditDecisionLoanService service;

    @CronTarget(jobName = JobName.RETRY_FAILED_CREDIT_DECISIONS)
    public void retryFailedCreditDecisions() {
        final int sent = service.retryDue();
        if (sent > 0) {
            log.info("Sent {} failed credit decision request(s) again", sent);
        }
    }
}
