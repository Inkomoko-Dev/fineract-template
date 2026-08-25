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
package org.apache.fineract.infrastructure.campaigns.whatsapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignConstants;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignStatus;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignTriggerType;
import org.apache.fineract.infrastructure.campaigns.whatsapp.data.WhatsAppPreviewData;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaign;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaignRepository;
import org.apache.fineract.infrastructure.campaigns.whatsapp.exception.WhatsAppCampaignNotFoundException;
import org.apache.fineract.infrastructure.campaigns.whatsapp.serialization.WhatsAppCampaignValidator;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
import org.apache.fineract.infrastructure.dataqueries.domain.Report;
import org.apache.fineract.infrastructure.dataqueries.domain.ReportRepository;
import org.apache.fineract.infrastructure.dataqueries.exception.ReportNotFoundException;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.calendar.service.CalendarUtils;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppCampaignWritePlatformServiceImpl implements WhatsAppCampaignWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppCampaignWritePlatformServiceImpl.class);

    private final PlatformSecurityContext context;
    private final WhatsAppCampaignRepository whatsAppCampaignRepository;
    private final WhatsAppCampaignValidator whatsAppCampaignValidator;
    private final ReportRepository reportRepository;
    private final FromJsonHelper fromJsonHelper;
    private final ReadReportingService readReportingService;
    private final CommunicationMessageRepository communicationMessageRepository;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final StaffRepositoryWrapper staffRepositoryWrapper;
    private final PhoneNumberNormalizer phoneNumberNormalizer;

    @Autowired
    public WhatsAppCampaignWritePlatformServiceImpl(final PlatformSecurityContext context,
            final WhatsAppCampaignRepository whatsAppCampaignRepository, final WhatsAppCampaignValidator whatsAppCampaignValidator,
            final ReportRepository reportRepository, final FromJsonHelper fromJsonHelper, final ReadReportingService readReportingService,
            final CommunicationMessageRepository communicationMessageRepository, final ClientRepositoryWrapper clientRepositoryWrapper,
            final StaffRepositoryWrapper staffRepositoryWrapper, final PhoneNumberNormalizer phoneNumberNormalizer) {
        this.context = context;
        this.whatsAppCampaignRepository = whatsAppCampaignRepository;
        this.whatsAppCampaignValidator = whatsAppCampaignValidator;
        this.reportRepository = reportRepository;
        this.fromJsonHelper = fromJsonHelper;
        this.readReportingService = readReportingService;
        this.communicationMessageRepository = communicationMessageRepository;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.staffRepositoryWrapper = staffRepositoryWrapper;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
    }

    @Transactional
    @Override
    public CommandProcessingResult create(final JsonCommand command) {
        return create(command.json());
    }

    @Transactional
    @Override
    public CommandProcessingResult create(final String json) {
        final AppUser currentUser = this.context.authenticatedUser();
        final String normalizedJson = ensureMessageDefault(json);
        this.whatsAppCampaignValidator.validateCreate(normalizedJson);
        final JsonCommand command = parseCommand(normalizedJson, null);
        final Long runReportId = command.longValueOfParameterNamed(WhatsAppCampaignValidator.runReportId);
        final Report report = this.reportRepository.findById(runReportId).orElseThrow(() -> new ReportNotFoundException(runReportId));
        final WhatsAppCampaign campaign = WhatsAppCampaign.createNew(currentUser, report, command);
        if (campaign.getRecurrenceStartDate() != null && campaign.getRecurrenceStartDate().isBefore(DateUtils.getLocalDateTimeOfTenant())) {
            throw new GeneralPlatformDomainRuleException("error.msg.campaign.recurrenceStartDate.in.the.past",
                    "Recurrence start date cannot be the past date.", campaign.getRecurrenceStartDate());
        }
        this.whatsAppCampaignRepository.saveAndFlush(campaign);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(campaign.getId()).build();
    }

    @Transactional
    @Override
    public CommandProcessingResult update(final Long resourceId, final String json) {
        try {
            this.context.authenticatedUser();
            final String normalizedJson = ensureMessageDefault(json);
            this.whatsAppCampaignValidator.validateForUpdate(normalizedJson);
            final WhatsAppCampaign campaign = this.whatsAppCampaignRepository.findById(resourceId)
                    .orElseThrow(() -> new WhatsAppCampaignNotFoundException(resourceId));
            if (campaign.isActive()) {
                throw new GeneralPlatformDomainRuleException("error.msg.whatsapp.campaign.cannot.be.updated",
                        "Campaign with identifier " + resourceId + " cannot be updated as it is not in `Closed` state.", resourceId);
            }
            final JsonCommand command = parseCommand(normalizedJson, resourceId);
            final Map<String, Object> changes = campaign.update(command);
            if (changes.containsKey(WhatsAppCampaignValidator.runReportId)) {
                final Long newValue = command.longValueOfParameterNamed(WhatsAppCampaignValidator.runReportId);
                final Report report = this.reportRepository.findById(newValue).orElseThrow(() -> new ReportNotFoundException(newValue));
                campaign.updateBusinessRuleId(report);
            }
            if (!changes.isEmpty()) {
                this.whatsAppCampaignRepository.saveAndFlush(campaign);
            }
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(resourceId).with(changes).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(dve.getMostSpecificCause());
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult delete(final Long resourceId) {
        this.context.authenticatedUser();
        final WhatsAppCampaign campaign = this.whatsAppCampaignRepository.findById(resourceId)
                .orElseThrow(() -> new WhatsAppCampaignNotFoundException(resourceId));
        campaign.delete();
        this.whatsAppCampaignRepository.saveAndFlush(campaign);
        return new CommandProcessingResultBuilder().withEntityId(campaign.getId()).build();
    }

    @Transactional
    @Override
    public CommandProcessingResult activate(final Long campaignId, final String json) {
        final AppUser currentUser = this.context.authenticatedUser();
        this.whatsAppCampaignValidator.validateActivation(json);
        final WhatsAppCampaign campaign = this.whatsAppCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new WhatsAppCampaignNotFoundException(campaignId));
        final JsonCommand command = parseCommand(json, campaignId);
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
        final LocalDate activationDate = command.localDateValueOfParameterNamed(WhatsAppCampaignValidator.activationDateParamName);
        campaign.activate(currentUser, fmt, activationDate);
        this.whatsAppCampaignRepository.saveAndFlush(campaign);

        if (campaign.isSchedule()) {
            LocalDateTime nextTriggerDate;
            if (campaign.getRecurrenceStartDateTime().isBefore(DateUtils.getLocalDateTimeOfTenant())) {
                nextTriggerDate = CalendarUtils.getNextRecurringDate(campaign.getRecurrence(), campaign.getRecurrenceStartDate(),
                        DateUtils.getLocalDateTimeOfTenant());
            } else {
                nextTriggerDate = campaign.getRecurrenceStartDate();
            }
            campaign.setNextTriggerDate(nextTriggerDate);
            this.whatsAppCampaignRepository.saveAndFlush(campaign);
        }

        if (campaign.isDirect()) {
            insertDirectCampaignIntoOutboundTable(campaign);
        }

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(campaign.getId()).build();
    }

    @Transactional
    @Override
    public CommandProcessingResult close(final Long campaignId, final String json) {
        final AppUser currentUser = this.context.authenticatedUser();
        this.whatsAppCampaignValidator.validateClosedDate(json);
        final WhatsAppCampaign campaign = this.whatsAppCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new WhatsAppCampaignNotFoundException(campaignId));
        final JsonCommand command = parseCommand(json, campaignId);
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
        final LocalDate closureDate = command.localDateValueOfParameterNamed(WhatsAppCampaignValidator.closureDateParamName);
        campaign.close(currentUser, fmt, closureDate);
        this.whatsAppCampaignRepository.saveAndFlush(campaign);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(campaign.getId()).build();
    }

    @Transactional
    @Override
    public CommandProcessingResult reactivate(final Long campaignId, final String json) {
        this.whatsAppCampaignValidator.validateActivation(json);
        final AppUser currentUser = this.context.authenticatedUser();
        final WhatsAppCampaign campaign = this.whatsAppCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new WhatsAppCampaignNotFoundException(campaignId));
        final JsonCommand command = parseCommand(json, campaignId);
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
        final LocalDate reactivationDate = command.localDateValueOfParameterNamed(WhatsAppCampaignValidator.activationDateParamName);
        campaign.reactivate(currentUser, fmt, reactivationDate);
        if (campaign.isDirect()) {
            insertDirectCampaignIntoOutboundTable(campaign);
        } else if (campaign.isSchedule()) {
            LocalDateTime nextTriggerDate;
            if (campaign.getRecurrenceStartDateTime().isBefore(DateUtils.getLocalDateTimeOfTenant())) {
                nextTriggerDate = CalendarUtils.getNextRecurringDate(campaign.getRecurrence(), campaign.getRecurrenceStartDate(),
                        DateUtils.getLocalDateTimeOfTenant());
            } else {
                nextTriggerDate = campaign.getRecurrenceStartDate();
            }
            campaign.setNextTriggerDate(nextTriggerDate);
        }
        this.whatsAppCampaignRepository.saveAndFlush(campaign);

        return new CommandProcessingResultBuilder().withEntityId(campaign.getId()).build();
    }

    @Override
    public WhatsAppPreviewData preview(final String json) {
        this.context.authenticatedUser();
        this.whatsAppCampaignValidator.validatePreview(json);
        final JsonElement element = this.fromJsonHelper.parse(json);
        final JsonElement paramValueElement = this.fromJsonHelper.extractJsonObjectNamed(WhatsAppCampaignValidator.paramValue, element);
        final String paramValueJson = paramValueElement.toString();
        final JsonArray mappingArray = this.fromJsonHelper.extractJsonArrayNamed(WhatsAppCampaignValidator.bodyVariableMapping, element);
        final String bodyVariableMappingJson = mappingArray != null ? mappingArray.toString() : "[]";
        final String atTemplateNameValue = this.fromJsonHelper.extractStringNamed(WhatsAppCampaignValidator.atTemplateName, element);
        final String languageCodeValue = this.fromJsonHelper.extractStringNamed(WhatsAppCampaignValidator.languageCode, element);

        try {
            final HashMap<String, String> campaignParams = new ObjectMapper().readValue(paramValueJson,
                    new TypeReference<HashMap<String, String>>() {});
            final HashMap<String, String> queryParamForRunReport = new ObjectMapper().readValue(paramValueJson,
                    new TypeReference<HashMap<String, String>>() {});
            final List<HashMap<String, Object>> runReportObject = getRunReportByServiceImpl(campaignParams.get("reportName"),
                    queryParamForRunReport);
            if (runReportObject != null && !runReportObject.isEmpty()) {
                final HashMap<String, Object> entry = runReportObject.get(0);
                final List<String> bodyValues = WhatsAppTemplateVariableMapper.toBodyValues(bodyVariableMappingJson, entry);
                final String previewMessage = buildPreviewMessage(atTemplateNameValue, languageCodeValue, bodyValues);
                return new WhatsAppPreviewData(bodyValues, previewMessage);
            }
            final List<String> emptyValues = WhatsAppTemplateVariableMapper.toBodyValues(bodyVariableMappingJson, Map.of());
            return new WhatsAppPreviewData(emptyValues,
                    "Report preview requires valid parameters. Template: " + atTemplateNameValue + " (" + languageCodeValue + ")");
        } catch (final IOException e) {
            LOG.error("Error generating WhatsApp campaign preview.", e);
            final List<String> emptyValues = WhatsAppTemplateVariableMapper.toBodyValues(bodyVariableMappingJson, Map.of());
            return new WhatsAppPreviewData(emptyValues, "Report preview requires valid parameters.");
        }
    }

    private String buildPreviewMessage(final String atTemplateName, final String languageCode, final List<String> bodyValues) {
        return "Template: " + atTemplateName + " (" + languageCode + ") body=" + bodyValues;
    }

    @Override
    @CronTarget(jobName = JobName.UPDATE_WHATSAPP_OUTBOUND_WITH_CAMPAIGN_MESSAGE)
    public void storeTemplateMessageIntoWhatsAppOutboundTable() throws JobExecutionException {
        final Collection<WhatsAppCampaign> campaigns = this.whatsAppCampaignRepository
                .findByTriggerTypeAndStatus(WhatsAppCampaignTriggerType.SCHEDULE.getValue(), WhatsAppCampaignStatus.ACTIVE.getValue());
        if (campaigns == null) {
            return;
        }
        for (final WhatsAppCampaign campaign : campaigns) {
            final LocalDateTime tenantDateNow = tenantDateTime();
            final LocalDateTime nextTriggerDate = campaign.getNextTriggerDate();
            if (nextTriggerDate == null) {
                continue;
            }
            LOG.info("WhatsApp campaign schedule check tenant={} trigger={} job={}", tenantDateNow, nextTriggerDate,
                    JobName.UPDATE_WHATSAPP_OUTBOUND_WITH_CAMPAIGN_MESSAGE.name());
            if (nextTriggerDate.isBefore(tenantDateNow)) {
                insertDirectCampaignIntoOutboundTable(campaign);
                updateTriggerDates(campaign.getId());
            }
        }
    }

    private void updateTriggerDates(final Long campaignId) {
        final WhatsAppCampaign campaign = this.whatsAppCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new WhatsAppCampaignNotFoundException(campaignId));
        final LocalDateTime previousNext = campaign.getNextTriggerDate();
        campaign.setLastTriggerDate(previousNext);
        LocalDateTime nextRuntime = CalendarUtils.getNextRecurringDate(campaign.getRecurrence(), campaign.getNextTriggerDate(),
                previousNext);
        if (nextRuntime.isBefore(DateUtils.getLocalDateTimeOfTenant())) {
            nextRuntime = CalendarUtils.getNextRecurringDate(campaign.getRecurrence(), campaign.getNextTriggerDate(),
                    DateUtils.getLocalDateTimeOfTenant());
        }
        campaign.setNextTriggerDate(nextRuntime);
        this.whatsAppCampaignRepository.saveAndFlush(campaign);
    }

    private LocalDateTime tenantDateTime() {
        LocalDateTime today = LocalDateTime.now(DateUtils.getDateTimeZoneOfTenant());
        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        if (tenant != null) {
            final ZoneId zone = ZoneId.of(tenant.getTimezoneId());
            today = LocalDateTime.now(zone);
        }
        return today;
    }

    void insertDirectCampaignIntoOutboundTable(final WhatsAppCampaign campaign) {
        final List<HashMap<String, Object>> runReportObject;
        try {
            final HashMap<String, String> campaignParams = new ObjectMapper().readValue(campaign.getParamValue(),
                    new TypeReference<HashMap<String, String>>() {});

            final HashMap<String, String> queryParamForRunReport = new ObjectMapper().readValue(campaign.getParamValue(),
                    new TypeReference<HashMap<String, String>>() {});

            runReportObject = getRunReportByServiceImpl(campaignParams.get("reportName"), queryParamForRunReport);
        } catch (final IOException | RuntimeException e) {
            LOG.error("Error running audience report for WhatsApp campaign {}.", campaign.getId(), e);
            return;
        }

        if (runReportObject == null) {
            return;
        }

        int enqueued = 0;
        int skipped = 0;
        for (final HashMap<String, Object> entry : runReportObject) {
            try {
                if (enqueueCampaignRow(campaign, entry)) {
                    enqueued++;
                } else {
                    skipped++;
                }
            } catch (final IOException | RuntimeException e) {
                skipped++;
                LOG.error("Skipping recipient for WhatsApp campaign {}; the remaining recipients are unaffected.", campaign.getId(), e);
            }
        }
        LOG.info("WhatsApp campaign {} enqueued {} message(s), skipped {}.", campaign.getId(), enqueued, skipped);
    }

    private boolean enqueueCampaignRow(final WhatsAppCampaign campaign, final HashMap<String, Object> entry) throws IOException {
        final Object mobileNo = entry.get("mobileNo");
        final String phoneNumber = mobileNo == null ? null : this.phoneNumberNormalizer.normalize(mobileNo.toString());
        if (StringUtils.isBlank(phoneNumber)) {
            LOG.warn("Skipping recipient with a missing or unusable mobile number for WhatsApp campaign {}.", campaign.getId());
            return false;
        }

        final WhatsAppTemplateVariableMapper.MappingResult mapping = WhatsAppTemplateVariableMapper
                .toBodyValuesStrict(campaign.getBodyVariableMapping(), entry);
        if (!mapping.isComplete()) {
            LOG.warn("Skipping recipient for WhatsApp campaign {}: template {} has unresolved variable(s) {}.", campaign.getId(),
                    campaign.getAtTemplateName(), mapping.getUnresolvedKeys());
            return false;
        }

        final List<String> bodyValues = mapping.getBodyValues();
        final String templateBodyValuesJson = new ObjectMapper().writeValueAsString(bodyValues);
        final String auditMessageBody = bodyValues.isEmpty() ? campaign.getMessage() : String.join("|", bodyValues);

        Client client = null;
        Staff staff = null;
        final Long recipientId = extractRecipientId(entry.get("id"));
        if (recipientId != null) {
            if (campaign.isStaffCampaign()) {
                staff = this.staffRepositoryWrapper.findOneWithNotFoundDetection(recipientId);
            } else {
                client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(recipientId);
            }
        }

        final CommunicationMessage message = CommunicationMessage.pendingOutboundTemplate(phoneNumber, campaign.getRecipientType(), client,
                staff, campaign.getAtTemplateName(), campaign.getLanguageCode(), templateBodyValuesJson, auditMessageBody,
                campaign.getId());
        this.communicationMessageRepository.save(message);
        return true;
    }

    private static Long extractRecipientId(final Object rawId) {
        if (rawId == null || rawId.toString().isBlank()) {
            return null;
        }
        if (rawId instanceof Number) {
            return ((Number) rawId).longValue();
        }
        return Long.parseLong(rawId.toString().trim());
    }

    private List<HashMap<String, Object>> getRunReportByServiceImpl(final String reportName, final Map<String, String> queryParams) {
        final String reportType = "report";
        final GenericResultsetData results = this.readReportingService.retrieveGenericResultSetForSmsEmailCampaign(reportName, reportType,
                queryParams);
        // Map rows directly — avoid generateJsonFromGenericResultsetData + Jackson parse, which breaks on
        // unescaped control characters (tabs/newlines) common in stretchy-report string columns.
        final List<ResultsetColumnHeaderData> columnHeaders = results.getColumnHeaders();
        final List<HashMap<String, Object>> resultList = new ArrayList<>();
        if (columnHeaders == null || results.getData() == null) {
            return resultList;
        }
        for (final ResultsetRowData rowData : results.getData()) {
            final List<String> row = rowData.getRow();
            if (row == null) {
                continue;
            }
            final HashMap<String, Object> entry = new HashMap<>();
            for (int j = 0; j < columnHeaders.size() && j < row.size(); j++) {
                entry.put(columnHeaders.get(j).getColumnName(), row.get(j));
            }
            resultList.add(entry);
        }
        return resultList;
    }

    private JsonCommand parseCommand(final String json, final Long resourceId) {
        final JsonElement element = this.fromJsonHelper.parse(json);
        return JsonCommand.from(json, element, this.fromJsonHelper, WhatsAppCampaignConstants.RESOURCE_NAME, resourceId, null, null, null,
                null, null, null, null, null, null, null);
    }

    private String ensureMessageDefault(final String json) {
        final JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        if (!jsonObject.has(WhatsAppCampaignValidator.message) || jsonObject.get(WhatsAppCampaignValidator.message).isJsonNull()) {
            jsonObject.addProperty(WhatsAppCampaignValidator.message, "");
        }
        return jsonObject.toString();
    }

    private void handleDataIntegrityIssues(final Throwable realCause) {
        throw new PlatformDataIntegrityException("error.msg.whatsapp.campaign.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource: " + realCause.getMessage());
    }
}
