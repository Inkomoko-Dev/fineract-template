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
package org.apache.fineract.infrastructure.notifications.data;

import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.notifications.constants.NotificationChannel;
import org.apache.fineract.infrastructure.notifications.constants.NotificationIntent;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;

public final class NotificationCommand {

    private final NotificationChannel channel;
    private final NotificationIntent intent;
    private final NotificationPurpose purpose;
    private final String phoneNumber;
    private final RecipientType recipientType;
    private final Client client;
    private final Staff staff;
    private final String messageBody;
    private final String templateName;
    private final String templateLanguage;
    private final String templateBodyValuesJson;
    private final Long campaignId;
    private final boolean sendImmediately;
    private final String callPurpose;
    private final boolean recordingConsentRequired;

    private NotificationCommand(final NotificationChannel channel, final NotificationIntent intent, final NotificationPurpose purpose,
            final String phoneNumber, final RecipientType recipientType, final Client client, final Staff staff, final String messageBody,
            final String templateName, final String templateLanguage, final String templateBodyValuesJson, final Long campaignId,
            final boolean sendImmediately, final String callPurpose, final boolean recordingConsentRequired) {
        this.channel = channel;
        this.intent = intent;
        this.purpose = purpose;
        this.phoneNumber = phoneNumber;
        this.recipientType = recipientType;
        this.client = client;
        this.staff = staff;
        this.messageBody = messageBody;
        this.templateName = templateName;
        this.templateLanguage = templateLanguage;
        this.templateBodyValuesJson = templateBodyValuesJson;
        this.campaignId = campaignId;
        this.sendImmediately = sendImmediately;
        this.callPurpose = callPurpose;
        this.recordingConsentRequired = recordingConsentRequired;
    }

    public static NotificationCommand freeformWhatsApp(final NotificationPurpose purpose, final String phoneNumber,
            final RecipientType recipientType, final Client client, final Staff staff, final String messageBody,
            final boolean sendImmediately) {
        return new NotificationCommand(NotificationChannel.WHATSAPP, NotificationIntent.FREEFORM, purpose, phoneNumber, recipientType,
                client, staff, messageBody, null, null, null, null, sendImmediately, null, false);
    }

    public static NotificationCommand templateWhatsApp(final NotificationPurpose purpose, final String phoneNumber,
            final RecipientType recipientType, final Client client, final Staff staff, final String templateName,
            final String templateLanguage, final String templateBodyValuesJson, final String auditMessageBody, final Long campaignId) {
        return new NotificationCommand(NotificationChannel.WHATSAPP, NotificationIntent.TEMPLATE, purpose, phoneNumber, recipientType,
                client, staff, auditMessageBody, templateName, templateLanguage, templateBodyValuesJson, campaignId, false, null, false);
    }

    public static NotificationCommand templateWhatsApp(final NotificationPurpose purpose, final String phoneNumber,
            final RecipientType recipientType, final Client client, final Staff staff, final String templateName,
            final String templateLanguage, final String templateBodyValuesJson, final String auditMessageBody, final Long campaignId,
            final boolean sendImmediately) {
        return new NotificationCommand(NotificationChannel.WHATSAPP, NotificationIntent.TEMPLATE, purpose, phoneNumber, recipientType,
                client, staff, auditMessageBody, templateName, templateLanguage, templateBodyValuesJson, campaignId, sendImmediately, null,
                false);
    }

    public static NotificationCommand outboundVoice(final NotificationPurpose purpose, final String phoneNumber,
            final RecipientType recipientType, final Client client, final Staff staff, final String callPurpose,
            final boolean recordingConsentRequired) {
        return new NotificationCommand(NotificationChannel.VOICE, NotificationIntent.FREEFORM, purpose, phoneNumber, recipientType, client,
                staff, null, null, null, null, null, true, callPurpose, recordingConsentRequired);
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationIntent getIntent() {
        return intent;
    }

    public NotificationPurpose getPurpose() {
        return purpose;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public RecipientType getRecipientType() {
        return recipientType;
    }

    public Client getClient() {
        return client;
    }

    public Staff getStaff() {
        return staff;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getTemplateLanguage() {
        return templateLanguage;
    }

    public String getTemplateBodyValuesJson() {
        return templateBodyValuesJson;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public boolean isSendImmediately() {
        return sendImmediately;
    }

    public String getCallPurpose() {
        return callPurpose;
    }

    public boolean isRecordingConsentRequired() {
        return recordingConsentRequired;
    }
}
