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
package org.apache.fineract.infrastructure.whatsapp.provider;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.service.CommunicationLogSanitizer;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationMessageStatusUpdater {

    private final CommunicationMessageRepository communicationMessageRepository;

    /**
     * @return true when a matching communication_message was found and updated
     */
    @Transactional
    public boolean applyProviderStatus(final String externalId, final String status, final String failureReason) {
        if (StringUtils.isAnyBlank(externalId, status)) {
            return false;
        }
        return communicationMessageRepository.findByExternalId(externalId).map(message -> {
            applyStatus(message, status, failureReason);
            return true;
        }).orElseGet(() -> {
            log.debug("No communication message found for external id {}", externalId);
            return false;
        });
    }

    private void applyStatus(final CommunicationMessage message, final String status, final String failureReason) {
        final String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        switch (normalizedStatus) {
            case "SENT", "QUEUED", "ACCEPTED" -> message.setStatus(CommunicationMessageStatus.SENT);
            case "DELIVERED", "SUCCESS" -> {
                message.setStatus(CommunicationMessageStatus.DELIVERED);
                message.setDeliveredDate(DateUtils.getLocalDateTimeOfTenant());
            }
            case "READ" -> {
                message.setStatus(CommunicationMessageStatus.READ);
                message.setReadDate(DateUtils.getLocalDateTimeOfTenant());
            }
            case "FAILED", "REJECTED" -> {
                message.setStatus(CommunicationMessageStatus.FAILED);
                message.setStatusDetail(CommunicationLogSanitizer.truncateDetail(failureReason));
            }
            default -> log.debug("Unhandled WhatsApp status {} for message {}", normalizedStatus, message.getId());
        }
        communicationMessageRepository.save(message);
    }
}
