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
package org.apache.fineract.infrastructure.whatsapp.interactive.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppOptOutEventType;

@Entity
@Table(name = "whatsapp_opt_out_record")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppOptOutRecord extends AbstractPersistableCustom {

    @Column(name = "phone_number", length = 50, nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 20, nullable = false)
    private WhatsAppOptOutEventType eventType;

    @Column(name = "keyword", length = 50)
    private String keyword;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    public static WhatsAppOptOutRecord event(final String phoneNumber, final WhatsAppOptOutEventType eventType, final String keyword) {
        final WhatsAppOptOutRecord record = new WhatsAppOptOutRecord();
        record.phoneNumber = phoneNumber;
        record.eventType = eventType;
        record.keyword = keyword;
        record.createdDate = DateUtils.getLocalDateTimeOfTenant();
        return record;
    }
}
