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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignConstants;
import org.apache.fineract.infrastructure.campaigns.whatsapp.data.WhatsAppPreviewData;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaign;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaignRepository;
import org.apache.fineract.infrastructure.campaigns.whatsapp.exception.WhatsAppCampaignNotFoundException;
import org.apache.fineract.infrastructure.campaigns.whatsapp.serialization.WhatsAppCampaignValidator;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.domain.Report;
import org.apache.fineract.infrastructure.dataqueries.domain.ReportRepository;
import org.apache.fineract.infrastructure.dataqueries.exception.ReportNotFoundException;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.calendar.service.CalendarUtils;
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
    private final GenericDataService genericDataService;

    @Autowired
    public WhatsAppCampaignWritePlatformServiceImpl(final PlatformSecurityContext context,
            final WhatsAppCampaignRepository whatsAppCampaignRepository, final WhatsAppCampaignValidator whatsAppCampaignValidator,
            final ReportRepository reportRepository, final FromJsonHelper fromJsonHelper, final ReadReportingService readReportingService,
            final GenericDataService genericDataService) {
        this.context = context;
        this.whatsAppCampaignRepository = whatsAppCampaignRepository;
        this.whatsAppCampaignValidator = whatsAppCampaignValidator;
        this.reportRepository = reportRepository;
        this.fromJsonHelper = fromJsonHelper;
        this.readReportingService = readReportingService;
        this.genericDataService = genericDataService;
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
        if (campaign.getRecurrenceStartDate() != null
                && campaign.getRecurrenceStartDate().isBefore(DateUtils.getLocalDateTimeOfTenant())) {
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

        // TODO Task 6: enqueue outbound WhatsApp messages for direct/scheduled campaigns

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
        if (campaign.isSchedule()) {
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

        // TODO Task 6: enqueue outbound WhatsApp messages for direct/scheduled campaigns

        return new CommandProcessingResultBuilder().withEntityId(campaign.getId()).build();
    }

    @Override
    public WhatsAppPreviewData preview(final String json) {
        this.context.authenticatedUser();
        this.whatsAppCampaignValidator.validatePreview(json);
        final JsonElement element = this.fromJsonHelper.parse(json);
        final JsonElement paramValueElement = this.fromJsonHelper.extractJsonObjectNamed(WhatsAppCampaignValidator.paramValue, element);
        final String paramValueJson = paramValueElement.toString();
        final JsonElement mappingElement = this.fromJsonHelper.extractJsonObjectNamed(WhatsAppCampaignValidator.bodyVariableMapping,
                element);
        final String bodyVariableMappingJson = mappingElement != null ? this.fromJsonHelper.toJson(mappingElement) : "[]";
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

    private List<HashMap<String, Object>> getRunReportByServiceImpl(final String reportName, final Map<String, String> queryParams)
            throws IOException {
        final String reportType = "report";
        final List<HashMap<String, Object>> resultList = new ArrayList<>();
        final GenericResultsetData results = this.readReportingService.retrieveGenericResultSetForSmsEmailCampaign(reportName, reportType,
                queryParams);
        try {
            final String response = this.genericDataService.generateJsonFromGenericResultsetData(results);
            final List<HashMap<String, Object>> parsed = new ObjectMapper().readValue(response,
                    new TypeReference<List<HashMap<String, Object>>>() {});
            resultList.addAll(parsed);
        } catch (JsonParseException e) {
            LOG.info("Conversion of report query results to JSON failed", e);
            return resultList;
        }
        for (Iterator<HashMap<String, Object>> iter = resultList.iterator(); iter.hasNext();) {
            final HashMap<String, Object> entry = iter.next();
            for (Iterator<Map.Entry<String, Object>> it = entry.entrySet().iterator(); it.hasNext();) {
                final Map.Entry<String, Object> map = it.next();
                final String key = map.getKey();
                final Object ob = map.getValue();
                if (ob instanceof ArrayList<?> arrayList && arrayList.size() == 3) {
                    final String changeArrayDateToStringDate = arrayList.get(2).toString() + "-" + arrayList.get(1).toString() + "-"
                            + arrayList.get(0).toString();
                    entry.put(key, changeArrayDateToStringDate);
                }
            }
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
