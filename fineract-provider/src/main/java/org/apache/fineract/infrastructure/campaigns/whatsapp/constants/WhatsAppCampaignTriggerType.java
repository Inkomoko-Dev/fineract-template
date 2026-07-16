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
package org.apache.fineract.infrastructure.campaigns.whatsapp.constants;

import org.apache.fineract.infrastructure.core.data.EnumOptionData;

public enum WhatsAppCampaignTriggerType {

    INVALID(-1, "triggerType.invalid"), DIRECT(1, "triggerType.direct"), SCHEDULE(2, "triggerType.schedule"), TRIGGERED(3,
            "triggerType.triggered");

    private final Integer value;
    private final String code;

    WhatsAppCampaignTriggerType(Integer value, String code) {
        this.value = value;
        this.code = code;
    }

    public Integer getValue() {
        return value;
    }

    public String getCode() {
        return code;
    }

    public static WhatsAppCampaignTriggerType fromInt(final Integer typeValue) {
        WhatsAppCampaignTriggerType type = null;
        switch (typeValue) {
            case 1:
                type = DIRECT;
            break;
            case 2:
                type = SCHEDULE;
            break;
            case 3:
                type = TRIGGERED;
            break;
        }
        return type;
    }

    public static EnumOptionData triggerType(final Integer triggerTypeId) {
        return triggerType(WhatsAppCampaignTriggerType.fromInt(triggerTypeId));
    }

    public static EnumOptionData triggerType(final WhatsAppCampaignTriggerType triggerType) {
        EnumOptionData optionData = new EnumOptionData(WhatsAppCampaignTriggerType.INVALID.getValue().longValue(),
                WhatsAppCampaignTriggerType.INVALID.getCode(), "Invalid");
        switch (triggerType) {
            case INVALID:
                optionData = new EnumOptionData(WhatsAppCampaignTriggerType.INVALID.getValue().longValue(),
                        WhatsAppCampaignTriggerType.INVALID.getCode(), "Invalid");
            break;
            case DIRECT:
                optionData = new EnumOptionData(WhatsAppCampaignTriggerType.DIRECT.getValue().longValue(),
                        WhatsAppCampaignTriggerType.DIRECT.getCode(), "Direct");
            break;
            case SCHEDULE:
                optionData = new EnumOptionData(WhatsAppCampaignTriggerType.SCHEDULE.getValue().longValue(),
                        WhatsAppCampaignTriggerType.SCHEDULE.getCode(), "Schedule");
            break;
            case TRIGGERED:
                optionData = new EnumOptionData(WhatsAppCampaignTriggerType.TRIGGERED.getValue().longValue(),
                        WhatsAppCampaignTriggerType.TRIGGERED.getCode(), "Triggered");
            break;
        }
        return optionData;
    }

    public boolean isDirect() {
        return this.value.equals(WhatsAppCampaignTriggerType.DIRECT.getValue());
    }

    public boolean isSchedule() {
        return this.value.equals(WhatsAppCampaignTriggerType.SCHEDULE.getValue());
    }

    public boolean isTriggered() {
        return this.value.equals(WhatsAppCampaignTriggerType.TRIGGERED.getValue());
    }
}
