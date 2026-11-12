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
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConfigArea;

@Entity
@Table(name = "whatsapp_interactive_setting_audit")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppInteractiveSettingAudit extends AbstractPersistableCustom {

    @Column(name = "setting_key", length = 100, nullable = false)
    private String settingKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "config_area", length = 50, nullable = false)
    private WhatsAppConfigArea configArea;

    @Column(name = "previous_value")
    private String previousValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static WhatsAppInteractiveSettingAudit record(final String settingKey, final WhatsAppConfigArea area, final String previousValue,
            final String newValue, final Long changedByUserId) {
        final WhatsAppInteractiveSettingAudit audit = new WhatsAppInteractiveSettingAudit();
        audit.setSettingKey(settingKey);
        audit.setConfigArea(area);
        audit.setPreviousValue(previousValue);
        audit.setNewValue(newValue);
        audit.setChangedByUserId(changedByUserId);
        audit.setChangedAt(DateUtils.getLocalDateTimeOfTenant());
        return audit;
    }
}
