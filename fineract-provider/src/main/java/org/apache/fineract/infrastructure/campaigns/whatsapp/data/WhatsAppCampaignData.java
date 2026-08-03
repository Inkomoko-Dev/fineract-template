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
package org.apache.fineract.infrastructure.campaigns.whatsapp.data;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.fineract.infrastructure.campaigns.sms.data.SmsBusinessRulesData;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

@SuppressWarnings("unused")
public final class WhatsAppCampaignData {

    private Long id;
    private final String campaignName;
    private final EnumOptionData campaignType;
    private final Long runReportId;
    private final String reportName;
    private final String paramValue;
    private final EnumOptionData campaignStatus;
    private final EnumOptionData triggerType;
    private final String campaignMessage;
    private final LocalDateTime nextTriggerDate;
    private final LocalDateTime lastTriggerDate;
    private final WhatsAppCampaignTimeLine timeline;
    private final LocalDateTime recurrenceStartDate;
    private final String recurrence;
    private final String atTemplateName;
    private final String languageCode;
    private final String bodyVariableMapping;
    private final List<String> bodyVariableMappingList;

    private final Collection<EnumOptionData> campaignTypeOptions;
    private final Collection<EnumOptionData> triggerTypeOptions;
    private final Collection<SmsBusinessRulesData> businessRulesOptions;
    private final Collection<EnumOptionData> months;
    private final Collection<EnumOptionData> weekDays;
    private final Collection<EnumOptionData> frequencyTypeOptions;
    private final Collection<EnumOptionData> periodFrequencyOptions;

    private WhatsAppCampaignData(final Long id, final String campaignName, final EnumOptionData campaignType,
            final EnumOptionData triggerType, final Long runReportId, final String reportName, final String paramValue,
            final EnumOptionData campaignStatus, final String message, final LocalDateTime nextTriggerDate,
            final LocalDateTime lastTriggerDate, final WhatsAppCampaignTimeLine timeline, final LocalDateTime recurrenceStartDate,
            final String recurrence, final String atTemplateName, final String languageCode, final String bodyVariableMapping,
            final List<String> bodyVariableMappingList, final Collection<SmsBusinessRulesData> businessRulesOptions,
            final Collection<EnumOptionData> campaignTypeOptions, final Collection<EnumOptionData> triggerTypeOptions,
            final Collection<EnumOptionData> months, final Collection<EnumOptionData> weekDays,
            final Collection<EnumOptionData> frequencyTypeOptions, final Collection<EnumOptionData> periodFrequencyOptions) {
        this.id = id;
        this.campaignName = campaignName;
        this.campaignType = campaignType;
        this.triggerType = triggerType;
        this.runReportId = runReportId;
        this.reportName = reportName;
        this.paramValue = paramValue;
        this.campaignStatus = campaignStatus;
        this.campaignMessage = message;
        this.nextTriggerDate = nextTriggerDate;
        this.lastTriggerDate = lastTriggerDate;
        this.timeline = timeline;
        this.recurrenceStartDate = recurrenceStartDate;
        this.recurrence = recurrence;
        this.atTemplateName = atTemplateName;
        this.languageCode = languageCode;
        this.bodyVariableMapping = bodyVariableMapping;
        this.bodyVariableMappingList = bodyVariableMappingList;
        this.businessRulesOptions = businessRulesOptions;
        this.campaignTypeOptions = campaignTypeOptions;
        this.triggerTypeOptions = triggerTypeOptions;
        this.months = months;
        this.weekDays = weekDays;
        this.frequencyTypeOptions = frequencyTypeOptions;
        this.periodFrequencyOptions = periodFrequencyOptions;
    }

    public static WhatsAppCampaignData instance(final Long id, final String campaignName, final EnumOptionData campaignType,
            final EnumOptionData triggerType, final Long runReportId, final String reportName, final String paramValue,
            final EnumOptionData campaignStatus, final String message, final LocalDateTime nextTriggerDate,
            final LocalDateTime lastTriggerDate, final WhatsAppCampaignTimeLine timeline, final LocalDateTime recurrenceStartDate,
            final String recurrence, final String atTemplateName, final String languageCode, final String bodyVariableMapping,
            final List<String> bodyVariableMappingList) {
        return new WhatsAppCampaignData(id, campaignName, campaignType, triggerType, runReportId, reportName, paramValue, campaignStatus,
                message, nextTriggerDate, lastTriggerDate, timeline, recurrenceStartDate, recurrence, atTemplateName, languageCode,
                bodyVariableMapping, bodyVariableMappingList, null, null, null, null, null, null, null);
    }

    public static WhatsAppCampaignData template(final Collection<SmsBusinessRulesData> businessRulesOptions,
            final Collection<EnumOptionData> campaignTypeOptions, final Collection<EnumOptionData> triggerTypeOptions,
            final Collection<EnumOptionData> months, final Collection<EnumOptionData> weekDays,
            final Collection<EnumOptionData> frequencyTypeOptions, final Collection<EnumOptionData> periodFrequencyOptions) {
        return new WhatsAppCampaignData(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, businessRulesOptions, campaignTypeOptions, triggerTypeOptions, months, weekDays, frequencyTypeOptions,
                periodFrequencyOptions);
    }

    public Long getId() {
        return id;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public EnumOptionData getCampaignType() {
        return campaignType;
    }

    public Long getRunReportId() {
        return runReportId;
    }

    public String getParamValue() {
        return paramValue;
    }

    public EnumOptionData getCampaignStatus() {
        return campaignStatus;
    }

    public String getMessage() {
        return campaignMessage;
    }

    public LocalDateTime getNextTriggerDate() {
        return nextTriggerDate;
    }

    public LocalDateTime getLastTriggerDate() {
        return lastTriggerDate;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public LocalDateTime getRecurrenceStartDate() {
        return recurrenceStartDate;
    }

    public String getReportName() {
        return reportName;
    }

    public String getAtTemplateName() {
        return atTemplateName;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getBodyVariableMapping() {
        return bodyVariableMapping;
    }

    public List<String> getBodyVariableMappingList() {
        return bodyVariableMappingList;
    }
}
