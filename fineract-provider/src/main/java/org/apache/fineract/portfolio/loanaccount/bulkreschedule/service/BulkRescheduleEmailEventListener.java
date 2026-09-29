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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleEmailEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Runs on Fineract's {@code SimpleAsyncTaskExecutor} (see {@code SpringConfig}) so SMTP does not
 * block the request thread that published the event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkRescheduleEmailEventListener implements ApplicationListener<BulkRescheduleEmailEvent> {

    private final PlatformEmailService emailService;

    @Override
    public void onApplicationEvent(final BulkRescheduleEmailEvent event) {
        final EmailDetail email = event.getEmail();
        try {
            ThreadLocalContextUtil.init(event.getContext());
            emailService.sendDefinedEmail(email);
            log.info("Bulk reschedule email sent to {} (cc {}) for request {}", email.getAddress(), email.getCc(),
                    email.getSubject());
        } catch (RuntimeException e) {
            log.error("Bulk reschedule email could not be sent to {}. Check SMTP configuration.", email.getAddress(), e);
        } finally {
            ThreadLocalContextUtil.clear();
        }
    }
}
