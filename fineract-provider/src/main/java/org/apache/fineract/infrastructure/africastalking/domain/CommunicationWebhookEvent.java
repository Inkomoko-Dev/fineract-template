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

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;

@Entity
@Table(name = "communication_webhook_event")
@Getter
@Setter
@NoArgsConstructor
public class CommunicationWebhookEvent extends AbstractPersistableCustom {

    @Column(name = "event_id", length = 150, nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @Column(name = "payload_hash", length = 64, nullable = false)
    private String payloadHash;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "processed_date")
    private LocalDateTime processedDate;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    public static CommunicationWebhookEvent create(final String eventId, final String eventType, final String payloadHash) {
        final CommunicationWebhookEvent event = new CommunicationWebhookEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.payloadHash = payloadHash;
        event.processed = true;
        event.processedDate = DateUtils.getLocalDateTimeOfTenant();
        event.createdDate = DateUtils.getLocalDateTimeOfTenant();
        return event;
    }
}
