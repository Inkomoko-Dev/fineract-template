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
package org.apache.fineract.infrastructure.novu.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;

@Entity
@Table(name = "novu_campaign")
@Getter
public class NovuCampaign extends AbstractPersistableCustom {

    @Column(name = "campaign_name", nullable = false, length = 150)
    private String campaignName;
    @Column(name = "workflow_id", nullable = false, length = 150)
    private String workflowId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;
    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType;
    @Column(name = "channels", nullable = false, length = 100)
    private String channels;
    @Column(name = "email_subject", length = 255)
    private String emailSubject;
    @Column(name = "email_body")
    private String emailBody;
    @Column(name = "sms_body", length = 1000)
    private String smsBody;
    @Column(name = "in_app_body", length = 1000)
    private String inAppBody;
    @Column(name = "chat_body", length = 1000)
    private String chatBody;
    @Column(name = "report_name", length = 255)
    private String reportName;
    @Column(name = "param_value")
    private String paramValue;
    @Column(name = "recurrence", length = 255)
    private String recurrence;
    @Column(name = "recurrence_start_date")
    private LocalDateTime recurrenceStartDate;
    @Column(name = "next_trigger_date")
    private LocalDateTime nextTriggerDate;
    @Column(name = "last_trigger_date")
    private LocalDateTime lastTriggerDate;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;
    @Column(name = "last_modified_on")
    private LocalDateTime lastModifiedOn;

    protected NovuCampaign() {}

    public NovuCampaign(final String campaignName, final String workflowId, final String eventType, final String triggerType,
            final String recipientType, final String channels, final String emailSubject, final String emailBody, final String smsBody,
            final String inAppBody, final String chatBody, final String reportName, final String paramValue, final String recurrence,
            final LocalDateTime recurrenceStartDate, final boolean active, final Long createdBy) {
        this.createdOn = DateUtils.getLocalDateTimeOfTenant();
        this.createdBy = createdBy;
        update(campaignName, workflowId, eventType, triggerType, recipientType, channels, emailSubject, emailBody, smsBody, inAppBody,
                chatBody, reportName, paramValue, recurrence, recurrenceStartDate, active);
    }

    public void update(final String campaignName, final String workflowId, final String eventType, final String triggerType,
            final String recipientType, final String channels, final String emailSubject, final String emailBody, final String smsBody,
            final String inAppBody, final String chatBody, final String reportName, final String paramValue, final String recurrence,
            final LocalDateTime recurrenceStartDate, final boolean active) {
        final LocalDateTime previousStartDate = this.recurrenceStartDate;
        this.campaignName = campaignName;
        this.workflowId = workflowId;
        this.eventType = eventType;
        this.triggerType = triggerType;
        this.recipientType = recipientType;
        this.channels = channels;
        this.emailSubject = emailSubject;
        this.emailBody = emailBody;
        this.smsBody = smsBody;
        this.inAppBody = inAppBody;
        this.chatBody = chatBody;
        this.reportName = reportName;
        this.paramValue = paramValue;
        this.recurrence = recurrence;
        this.recurrenceStartDate = recurrenceStartDate;
        if ("SCHEDULED".equals(triggerType)
                && (this.nextTriggerDate == null || !java.util.Objects.equals(recurrenceStartDate, previousStartDate))) {
            this.nextTriggerDate = recurrenceStartDate;
        } else if (!"SCHEDULED".equals(triggerType)) {
            this.nextTriggerDate = null;
        }
        this.active = active;
        this.lastModifiedOn = DateUtils.getLocalDateTimeOfTenant();
    }

    public void recordScheduledRun(final LocalDateTime nextDate) {
        this.lastTriggerDate = this.nextTriggerDate;
        this.nextTriggerDate = nextDate;
        this.lastModifiedOn = DateUtils.getLocalDateTimeOfTenant();
    }

    public void deactivateAfterFinalRun() {
        this.lastTriggerDate = this.nextTriggerDate;
        this.nextTriggerDate = null;
        this.active = false;
        this.lastModifiedOn = DateUtils.getLocalDateTimeOfTenant();
    }
}
