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

import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.campaigns.constants.CampaignType;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;

public final class WhatsAppCampaignEnumerations {

    private WhatsAppCampaignEnumerations() {

    }

    public static EnumOptionData whatsAppCampaignTriggerType(final WhatsAppCampaignTriggerType type) {
        EnumOptionData optionData = new EnumOptionData(WhatsAppCampaignTriggerType.INVALID.getValue().longValue(),
                WhatsAppCampaignTriggerType.INVALID.getCode(), "Invalid");
        switch (type) {
            case INVALID:
            break;
            case DIRECT:
                optionData = new EnumOptionData(WhatsAppCampaignTriggerType.DIRECT.getValue().longValue(),
                        WhatsAppCampaignTriggerType.DIRECT.getCode(), "Direct");
            break;
            case SCHEDULE:
                optionData = new EnumOptionData(WhatsAppCampaignTriggerType.SCHEDULE.getValue().longValue(),
                        WhatsAppCampaignTriggerType.SCHEDULE.getCode(), "Scheduled");
            break;
            case TRIGGERED:
                optionData = new EnumOptionData(WhatsAppCampaignTriggerType.TRIGGERED.getValue().longValue(),
                        WhatsAppCampaignTriggerType.TRIGGERED.getCode(), "Triggered");
            break;
        }
        return optionData;
    }

    public static EnumOptionData whatsAppCampaignStatus(final WhatsAppCampaignStatus status) {
        EnumOptionData optionData = new EnumOptionData(WhatsAppCampaignStatus.INVALID.getValue().longValue(),
                WhatsAppCampaignStatus.INVALID.getCode(), "Invalid");
        switch (status) {
            case INVALID:
                optionData = new EnumOptionData(WhatsAppCampaignStatus.INVALID.getValue().longValue(),
                        WhatsAppCampaignStatus.INVALID.getCode(), "Invalid");
            break;
            case PENDING:
                optionData = new EnumOptionData(WhatsAppCampaignStatus.PENDING.getValue().longValue(),
                        WhatsAppCampaignStatus.PENDING.getCode(), "Pending");
            break;
            case ACTIVE:
                optionData = new EnumOptionData(WhatsAppCampaignStatus.ACTIVE.getValue().longValue(),
                        WhatsAppCampaignStatus.ACTIVE.getCode(), "active");
            break;
            case CLOSED:
                optionData = new EnumOptionData(WhatsAppCampaignStatus.CLOSED.getValue().longValue(),
                        WhatsAppCampaignStatus.CLOSED.getCode(), "closed");
            break;
        }
        return optionData;
    }

    public static EnumOptionData whatsAppCampaignType(final CampaignType type) {
        EnumOptionData optionData = new EnumOptionData(CampaignType.INVALID.getValue().longValue(), CampaignType.INVALID.getCode(),
                "Invalid");
        switch (type) {
            case INVALID:
            break;
            case SMS:
                optionData = new EnumOptionData(CampaignType.SMS.getValue().longValue(), CampaignType.SMS.getCode(), "SMS");
            break;
            case NOTIFICATION:
                optionData = new EnumOptionData(CampaignType.NOTIFICATION.getValue().longValue(), CampaignType.NOTIFICATION.getCode(),
                        "NOTIFICATION");
            break;
            case WHATSAPP:
                optionData = new EnumOptionData(CampaignType.WHATSAPP.getValue().longValue(), CampaignType.WHATSAPP.getCode(), "WHATSAPP");
            break;
        }
        return optionData;
    }

    public static EnumOptionData calendarMonthType(final Month entityType) {
        final EnumOptionData optionData = new EnumOptionData(Long.valueOf(entityType.getValue()), entityType.name(), entityType.name());
        return optionData;
    }

    public static List<EnumOptionData> calendarMonthType() {
        final List<EnumOptionData> optionDatas = new ArrayList<>();
        for (final Month monthType : Month.values()) {
            if (Month.DECEMBER.compareTo(monthType) != 0) {
                optionDatas.add(calendarMonthType(monthType));
            }
        }
        return optionDatas;
    }

    public static List<EnumOptionData> calendarPeriodFrequencyTypes(final PeriodFrequencyType[] periodFrequencyTypes) {
        final List<EnumOptionData> optionDatas = new ArrayList<>();
        for (final PeriodFrequencyType periodFrequencyType : periodFrequencyTypes) {
            if (!periodFrequencyType.getValue().equals(PeriodFrequencyType.INVALID.getValue())) {
                final EnumOptionData optionData = new EnumOptionData(periodFrequencyType.getValue().longValue(),
                        periodFrequencyType.getCode(), periodFrequencyType.toString());
                optionDatas.add(optionData);
            }
        }
        return optionDatas;
    }
}
