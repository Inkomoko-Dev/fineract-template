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
package org.apache.fineract.infrastructure.campaigns.whatsapp.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.campaigns.constants.CampaignType;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignStatus;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignTriggerType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.dataqueries.domain.Report;
import org.apache.fineract.portfolio.calendar.domain.CalendarFrequencyType;
import org.apache.fineract.portfolio.calendar.domain.CalendarWeekDaysType;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "whatsapp_campaign", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "campaign_name" }, name = "campaign_name_UNIQUE") })
public class WhatsAppCampaign extends AbstractPersistableCustom {

    private static final String CAMPAIGN_NAME = "campaignName";
    private static final String CAMPAIGN_TYPE = "campaignType";
    private static final String TRIGGER_TYPE = "triggerType";
    private static final String RUN_REPORT_ID = "runReportId";
    private static final String PARAM_VALUE = "paramValue";
    private static final String MESSAGE = "message";
    private static final String AT_TEMPLATE_NAME = "atTemplateName";
    private static final String LANGUAGE_CODE = "languageCode";
    private static final String BODY_VARIABLE_MAPPING = "bodyVariableMapping";
    private static final String ACTIVATION_DATE = "activationDate";
    private static final String RECURRENCE_START_DATE = "recurrenceStartDate";
    private static final String DATE_TIME_FORMAT = "dateTimeFormat";
    private static final String SUBMITTED_ON_DATE = "submittedOnDate";
    private static final String CLOSURE_DATE = "closureDate";
    private static final String RECURRENCE = "recurrence";
    private static final String STATUS = "status";
    private static final String LOCALE = "locale";
    private static final String DATE_FORMAT = "dateFormat";
    private static final String FREQUENCY = "frequency";
    private static final String INTERVAL = "interval";
    private static final String REPEATS_ON_DAY = "repeatsOnDay";

    @Column(name = "campaign_name", nullable = false)
    private String campaignName;

    @Column(name = "campaign_type", nullable = false)
    private Integer campaignType;

    @Column(name = "campaign_trigger_type", nullable = false)
    private Integer triggerType;

    @ManyToOne
    @JoinColumn(name = "report_id", nullable = false)
    private Report businessRuleId;

    @Column(name = "param_value")
    private String paramValue;

    @Column(name = "status_enum", nullable = false)
    private Integer status;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "at_template_name", nullable = false, length = 100)
    private String atTemplateName;

    @Column(name = "language_code", nullable = false, length = 15)
    private String languageCode;

    @Column(name = "body_variable_mapping", nullable = false, columnDefinition = "TEXT")
    private String bodyVariableMapping;

    @Column(name = "closedon_date", nullable = true)
    private LocalDate closureDate;

    @ManyToOne(optional = true)
    @JoinColumn(name = "closedon_userid", nullable = true)
    private AppUser closedBy;

    @Column(name = "submittedon_date", nullable = true)
    private LocalDate submittedOnDate;

    @ManyToOne(optional = true)
    @JoinColumn(name = "submittedon_userid", nullable = true)
    private AppUser submittedBy;

    @Column(name = "approvedon_date", nullable = true)
    private LocalDate approvedOnDate;

    @ManyToOne(optional = true)
    @JoinColumn(name = "approvedon_userid", nullable = true)
    private AppUser approvedBy;

    @Column(name = "recurrence", nullable = true)
    private String recurrence;

    @Column(name = "next_trigger_date", nullable = true)
    private LocalDateTime nextTriggerDate;

    @Column(name = "last_trigger_date", nullable = true)
    private LocalDateTime lastTriggerDate;

    @Column(name = "recurrence_start_date", nullable = true)
    private LocalDateTime recurrenceStartDate;

    @Column(name = "is_visible", nullable = true)
    private boolean isVisible;

    public WhatsAppCampaign() {}

    private WhatsAppCampaign(final String campaignName, final Integer campaignType, final Integer triggerType, final Report businessRuleId,
            final String paramValue, final String message, final String atTemplateName, final String languageCode,
            final String bodyVariableMapping, final LocalDate submittedOnDate, final AppUser submittedBy, final String recurrence,
            final LocalDateTime localDateTime) {
        this.campaignName = campaignName;
        this.campaignType = campaignType;
        this.triggerType = WhatsAppCampaignTriggerType.fromInt(triggerType).getValue();
        this.businessRuleId = businessRuleId;
        this.paramValue = paramValue;
        this.status = WhatsAppCampaignStatus.PENDING.getValue();
        this.message = message;
        this.atTemplateName = atTemplateName;
        this.languageCode = languageCode;
        this.bodyVariableMapping = bodyVariableMapping;
        this.submittedOnDate = submittedOnDate;
        this.submittedBy = submittedBy;
        this.recurrence = recurrence;
        final LocalDateTime recurrenceStartDate = LocalDateTime.now(DateUtils.getDateTimeZoneOfTenant());
        this.isVisible = true;
        if (localDateTime != null) {
            this.recurrenceStartDate = localDateTime;
        } else {
            this.recurrenceStartDate = recurrenceStartDate;
        }
    }

    public static WhatsAppCampaign createNew(final AppUser submittedBy, final Report report, final JsonCommand command) {

        final String campaignName = command.stringValueOfParameterNamed(CAMPAIGN_NAME);
        final Long campaignType = command.longValueOfParameterNamed(CAMPAIGN_TYPE);
        final Long triggerType = command.longValueOfParameterNamed(TRIGGER_TYPE);
        final String paramValue = command.jsonFragment(PARAM_VALUE);
        final String message = command.stringValueOfParameterNamed(MESSAGE);
        final String atTemplateName = command.stringValueOfParameterNamed(AT_TEMPLATE_NAME);
        final String languageCode = command.stringValueOfParameterNamed(LANGUAGE_CODE);
        final String bodyVariableMapping = command.jsonFragment(BODY_VARIABLE_MAPPING);

        LocalDate submittedOnDate = DateUtils.getBusinessLocalDate();
        if (command.hasParameter(SUBMITTED_ON_DATE)) {
            submittedOnDate = command.localDateValueOfParameterNamed(SUBMITTED_ON_DATE);
        }
        String recurrence = null;

        LocalDateTime recurrenceStartDate = LocalDateTime.now(DateUtils.getDateTimeZoneOfTenant());
        if (WhatsAppCampaignTriggerType.fromInt(triggerType.intValue()).isSchedule()) {
            final Locale locale = command.extractLocale();
            String dateTimeFormat = null;
            if (command.hasParameter(DATE_TIME_FORMAT)) {
                dateTimeFormat = command.stringValueOfParameterNamed(DATE_TIME_FORMAT);
                final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(dateTimeFormat).withLocale(locale);
                if (command.hasParameter(RECURRENCE_START_DATE)) {
                    recurrenceStartDate = LocalDateTime.parse(command.stringValueOfParameterNamed(RECURRENCE_START_DATE), fmt);
                }
                recurrence = constructRecurrence(command);
            }
        } else {
            recurrenceStartDate = null;
        }

        return new WhatsAppCampaign(campaignName, campaignType.intValue(), triggerType.intValue(), report, paramValue, message,
                atTemplateName, languageCode, bodyVariableMapping, submittedOnDate, submittedBy, recurrence, recurrenceStartDate);
    }

    public Map<String, Object> update(final JsonCommand command) {

        final Map<String, Object> actualChanges = new LinkedHashMap<>(5);

        if (command.isChangeInStringParameterNamed(CAMPAIGN_NAME, this.campaignName)) {
            final String newValue = command.stringValueOfParameterNamed(CAMPAIGN_NAME);
            actualChanges.put(CAMPAIGN_NAME, newValue);
            this.campaignName = StringUtils.defaultIfEmpty(newValue, null);
        }
        if (command.isChangeInStringParameterNamed(MESSAGE, this.message)) {
            final String newValue = command.stringValueOfParameterNamed(MESSAGE);
            actualChanges.put(MESSAGE, newValue);
            this.message = StringUtils.defaultIfEmpty(newValue, null);
        }
        if (command.isChangeInStringParameterNamed(PARAM_VALUE, this.paramValue)) {
            final String newValue = command.jsonFragment(PARAM_VALUE);
            actualChanges.put(PARAM_VALUE, newValue);
            this.paramValue = StringUtils.defaultIfEmpty(newValue, null);
        }
        if (command.isChangeInStringParameterNamed(AT_TEMPLATE_NAME, this.atTemplateName)) {
            final String newValue = command.stringValueOfParameterNamed(AT_TEMPLATE_NAME);
            actualChanges.put(AT_TEMPLATE_NAME, newValue);
            this.atTemplateName = StringUtils.defaultIfEmpty(newValue, null);
        }
        if (command.isChangeInStringParameterNamed(LANGUAGE_CODE, this.languageCode)) {
            final String newValue = command.stringValueOfParameterNamed(LANGUAGE_CODE);
            actualChanges.put(LANGUAGE_CODE, newValue);
            this.languageCode = StringUtils.defaultIfEmpty(newValue, null);
        }
        if (command.isChangeInStringParameterNamed(BODY_VARIABLE_MAPPING, this.bodyVariableMapping)) {
            final String newValue = command.jsonFragment(BODY_VARIABLE_MAPPING);
            actualChanges.put(BODY_VARIABLE_MAPPING, newValue);
            this.bodyVariableMapping = StringUtils.defaultIfEmpty(newValue, null);
        }
        if (command.isChangeInIntegerParameterNamed(CAMPAIGN_TYPE, this.campaignType)) {
            final Integer newValue = command.integerValueOfParameterNamed(CAMPAIGN_TYPE);
            actualChanges.put(CAMPAIGN_TYPE, CampaignType.fromInt(newValue));
            this.campaignType = CampaignType.fromInt(newValue).getValue();
        }

        if (command.isChangeInIntegerParameterNamed(TRIGGER_TYPE, this.triggerType)) {
            final Integer newValue = command.integerValueOfParameterNamed(TRIGGER_TYPE);
            actualChanges.put(TRIGGER_TYPE, WhatsAppCampaignTriggerType.fromInt(newValue));
            this.triggerType = WhatsAppCampaignTriggerType.fromInt(newValue).getValue();
        }

        if (command.isChangeInLongParameterNamed(RUN_REPORT_ID, this.businessRuleId != null ? this.businessRuleId.getId() : null)) {
            final String newValue = command.stringValueOfParameterNamed(RUN_REPORT_ID);
            actualChanges.put(RUN_REPORT_ID, newValue);
        }
        if (command.isChangeInStringParameterNamed(RECURRENCE, this.recurrence)) {
            final String newValue = command.stringValueOfParameterNamed(RECURRENCE);
            actualChanges.put(RECURRENCE, newValue);
            this.recurrence = StringUtils.defaultIfEmpty(newValue, null);
        }

        if (WhatsAppCampaignTriggerType.fromInt(this.triggerType).isSchedule()) {
            final String dateFormatAsInput = command.dateFormat();
            final String dateTimeFormatAsInput = command.stringValueOfParameterNamed(DATE_TIME_FORMAT);
            final String localeAsInput = command.locale();
            final Locale locale = command.extractLocale();
            final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(dateTimeFormatAsInput).withLocale(locale);
            final String valueAsInput = command.stringValueOfParameterNamed(RECURRENCE_START_DATE);
            actualChanges.put(RECURRENCE_START_DATE, valueAsInput);
            actualChanges.put(DATE_FORMAT, dateFormatAsInput);
            actualChanges.put(DATE_TIME_FORMAT, dateTimeFormatAsInput);
            actualChanges.put(LOCALE, localeAsInput);

            this.recurrenceStartDate = LocalDateTime.parse(valueAsInput, fmt);
        }

        return actualChanges;
    }

    public void activate(final AppUser currentUser, final DateTimeFormatter formatter, final LocalDate activationLocalDate) {

        if (isActive()) {
            final String defaultUserMessage = "Cannot activate campaign. Campaign is already active.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.already.active", defaultUserMessage,
                    ACTIVATION_DATE, activationLocalDate.format(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        this.approvedOnDate = activationLocalDate;
        this.approvedBy = currentUser;
        this.status = WhatsAppCampaignStatus.ACTIVE.getValue();

        validate();
    }

    public void close(final AppUser currentUser, final DateTimeFormatter dateTimeFormatter, final LocalDate closureLocalDate) {
        if (isClosed()) {
            final String defaultUserMessage = "Cannot close campaign. Campaign already in closed state.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.already.closed", defaultUserMessage,
                    STATUS, WhatsAppCampaignStatus.fromInt(this.status).getCode());

            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        if (this.triggerType.intValue() == WhatsAppCampaignTriggerType.SCHEDULE.getValue()) {
            this.nextTriggerDate = null;
            this.lastTriggerDate = null;
        }
        this.closedBy = currentUser;
        this.closureDate = closureLocalDate;
        this.status = WhatsAppCampaignStatus.CLOSED.getValue();
        validateClosureDate();
    }

    public void reactivate(final AppUser currentUser, final DateTimeFormatter dateTimeFormat, final LocalDate reactivateLocalDate) {

        if (!isClosed()) {
            final String defaultUserMessage = "Cannot reactivate campaign. Campaign must be in closed state.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.must.be.closed", defaultUserMessage,
                    STATUS, WhatsAppCampaignStatus.fromInt(this.status).getCode());

            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        this.approvedOnDate = reactivateLocalDate;
        this.status = WhatsAppCampaignStatus.ACTIVE.getValue();
        this.approvedBy = currentUser;
        this.closureDate = null;
        this.isVisible = true;
        this.closedBy = null;

        validateReactivate();
    }

    public void delete() {
        if (!isClosed()) {
            final String defaultUserMessage = "Cannot delete campaign. Campaign must be in closed state.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.must.be.closed", defaultUserMessage,
                    STATUS, WhatsAppCampaignStatus.fromInt(this.status).getCode());

            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        this.isVisible = false;
    }

    public boolean isActive() {
        return WhatsAppCampaignStatus.fromInt(this.status).isActive();
    }

    public boolean isPending() {
        return WhatsAppCampaignStatus.fromInt(this.status).isPending();
    }

    public boolean isClosed() {
        return WhatsAppCampaignStatus.fromInt(this.status).isClosed();
    }

    public boolean isDirect() {
        return WhatsAppCampaignTriggerType.fromInt(this.triggerType).isDirect();
    }

    public boolean isSchedule() {
        return WhatsAppCampaignTriggerType.fromInt(this.triggerType).isSchedule();
    }

    public boolean isTriggered() {
        return WhatsAppCampaignTriggerType.fromInt(this.triggerType).isTriggered();
    }

    private void validate() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        validateActivationDate(dataValidationErrors);
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    private void validateReactivate() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        validateReactivationDate(dataValidationErrors);
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    private void validateClosureDate() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        validateClosureDate(dataValidationErrors);
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    private void validateActivationDate(final List<ApiParameterError> dataValidationErrors) {

        if (getSubmittedOnDate() != null && isDateInTheFuture(getSubmittedOnDate())) {

            final String defaultUserMessage = "submitted date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.submittedOnDate.in.the.future",
                    defaultUserMessage, SUBMITTED_ON_DATE, this.submittedOnDate);

            dataValidationErrors.add(error);
        }

        if (getActivationLocalDate() != null && getSubmittedOnDate() != null && getSubmittedOnDate().isAfter(getActivationLocalDate())) {

            final String defaultUserMessage = "submitted date cannot be after the activation date";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.submittedOnDate.after.activation.date",
                    defaultUserMessage, SUBMITTED_ON_DATE, this.submittedOnDate);

            dataValidationErrors.add(error);
        }

        if (getActivationLocalDate() != null && isDateInTheFuture(getActivationLocalDate())) {

            final String defaultUserMessage = "Activation date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.activationDate.in.the.future",
                    defaultUserMessage, ACTIVATION_DATE, getActivationLocalDate());

            dataValidationErrors.add(error);
        }

    }

    private void validateReactivationDate(final List<ApiParameterError> dataValidationErrors) {
        if (getActivationLocalDate() != null && isDateInTheFuture(getActivationLocalDate())) {

            final String defaultUserMessage = "Activation date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.activationDate.in.the.future",
                    defaultUserMessage, ACTIVATION_DATE, getActivationLocalDate());

            dataValidationErrors.add(error);
        }
        if (getActivationLocalDate() != null && getSubmittedOnDate() != null && getSubmittedOnDate().isAfter(getActivationLocalDate())) {

            final String defaultUserMessage = "submitted date cannot be after the activation date";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.submittedOnDate.after.activation.date",
                    defaultUserMessage, SUBMITTED_ON_DATE, this.submittedOnDate);

            dataValidationErrors.add(error);
        }
        if (getSubmittedOnDate() != null && isDateInTheFuture(getSubmittedOnDate())) {

            final String defaultUserMessage = "submitted date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.submittedOnDate.in.the.future",
                    defaultUserMessage, SUBMITTED_ON_DATE, this.submittedOnDate);

            dataValidationErrors.add(error);
        }

    }

    private void validateClosureDate(final List<ApiParameterError> dataValidationErrors) {
        if (getClosureDate() != null && isDateInTheFuture(getClosureDate())) {
            final String defaultUserMessage = "closure date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.campaign.closureDate.in.the.future",
                    defaultUserMessage, CLOSURE_DATE, this.closureDate);

            dataValidationErrors.add(error);
        }
    }

    public LocalDate getSubmittedOnDate() {
        return this.submittedOnDate;
    }

    public LocalDate getClosureDate() {
        return this.closureDate;
    }

    public LocalDate getActivationLocalDate() {
        return this.approvedOnDate;
    }

    private boolean isDateInTheFuture(final LocalDate localDate) {
        return localDate.isAfter(DateUtils.getBusinessLocalDate());
    }

    public Report getBusinessRuleId() {
        return this.businessRuleId;
    }

    public String getCampaignName() {
        return this.campaignName;
    }

    public Integer getCampaignType() {
        return this.campaignType;
    }

    public Integer getTriggerType() {
        return this.triggerType;
    }

    public Integer getStatus() {
        return this.status;
    }

    public String getMessage() {
        return this.message;
    }

    public String getParamValue() {
        return this.paramValue;
    }

    public String getAtTemplateName() {
        return this.atTemplateName;
    }

    public String getLanguageCode() {
        return this.languageCode;
    }

    public String getBodyVariableMapping() {
        return this.bodyVariableMapping;
    }

    public String getRecurrence() {
        return this.recurrence;
    }

    public LocalDateTime getRecurrenceStartDate() {
        return this.recurrenceStartDate;
    }

    public LocalDateTime getRecurrenceStartDateTime() {
        return this.recurrenceStartDate;
    }

    public void setLastTriggerDate(final LocalDateTime lastTriggerDate) {
        this.lastTriggerDate = lastTriggerDate;
    }

    public void setNextTriggerDate(final LocalDateTime nextTriggerDate) {
        this.nextTriggerDate = nextTriggerDate;
    }

    public LocalDateTime getNextTriggerDate() {
        return this.nextTriggerDate;
    }

    public LocalDateTime getLastTriggerDate() {
        return this.lastTriggerDate;
    }

    public boolean isVisible() {
        return this.isVisible;
    }

    public void updateIsVisible(final boolean isVisible) {
        this.isVisible = isVisible;
    }

    public void updateBusinessRuleId(final Report report) {
        this.businessRuleId = report;
    }

    public AppUser getSubmittedBy() {
        return this.submittedBy;
    }

    public AppUser getApprovedBy() {
        return this.approvedBy;
    }

    public AppUser getClosedBy() {
        return this.closedBy;
    }

    private static String constructRecurrence(final JsonCommand command) {
        final Integer frequency = command.integerValueOfParameterNamed(FREQUENCY);
        final CalendarFrequencyType frequencyType = CalendarFrequencyType.fromInt(frequency);
        final Integer interval = command.integerValueOfParameterNamed(INTERVAL);
        Integer repeatsOnDay = null;
        if (frequencyType.isWeekly()) {
            repeatsOnDay = command.integerValueOfParameterNamed(REPEATS_ON_DAY);
        }
        return constructRecurrence(frequencyType, interval, repeatsOnDay);
    }

    private static String constructRecurrence(final CalendarFrequencyType frequencyType, final Integer interval,
            final Integer repeatsOnDay) {
        final StringBuilder recurrenceBuilder = new StringBuilder(200);

        recurrenceBuilder.append("FREQ=");
        recurrenceBuilder.append(frequencyType.toString().toUpperCase());
        if (interval > 1) {
            recurrenceBuilder.append(";INTERVAL=");
            recurrenceBuilder.append(interval);
        }
        if (frequencyType.isWeekly()) {
            if (repeatsOnDay != null) {
                final CalendarWeekDaysType weekDays = CalendarWeekDaysType.fromInt(repeatsOnDay);
                if (!weekDays.isInvalid()) {
                    recurrenceBuilder.append(";BYDAY=");
                    recurrenceBuilder.append(weekDays.toString().toUpperCase());
                }
            }
        }
        return recurrenceBuilder.toString();
    }
}
