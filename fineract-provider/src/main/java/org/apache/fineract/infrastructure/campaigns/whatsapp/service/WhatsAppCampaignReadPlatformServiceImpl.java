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

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.campaigns.constants.CampaignType;
import org.apache.fineract.infrastructure.campaigns.sms.data.SmsBusinessRulesData;
import org.apache.fineract.infrastructure.campaigns.sms.service.SmsCampaignDropdownReadPlatformService;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignEnumerations;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignTriggerType;
import org.apache.fineract.infrastructure.campaigns.whatsapp.data.WhatsAppCampaignData;
import org.apache.fineract.infrastructure.campaigns.whatsapp.data.WhatsAppCampaignTimeLine;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaign;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaignRepository;
import org.apache.fineract.infrastructure.campaigns.whatsapp.exception.WhatsAppCampaignNotFoundException;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.dataqueries.domain.Report;
import org.apache.fineract.portfolio.calendar.service.CalendarDropdownReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppCampaignReadPlatformServiceImpl implements WhatsAppCampaignReadPlatformService {

    private final WhatsAppCampaignRepository whatsAppCampaignRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SmsCampaignDropdownReadPlatformService smsCampaignDropdownReadPlatformService;
    private final CalendarDropdownReadPlatformService calendarDropdownReadPlatformService;
    private final BusinessRuleMapper businessRuleMapper;

    @Autowired
    public WhatsAppCampaignReadPlatformServiceImpl(final WhatsAppCampaignRepository whatsAppCampaignRepository,
            final JdbcTemplate jdbcTemplate, final SmsCampaignDropdownReadPlatformService smsCampaignDropdownReadPlatformService,
            final CalendarDropdownReadPlatformService calendarDropdownReadPlatformService,
            final org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator sqlGenerator) {
        this.whatsAppCampaignRepository = whatsAppCampaignRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.smsCampaignDropdownReadPlatformService = smsCampaignDropdownReadPlatformService;
        this.calendarDropdownReadPlatformService = calendarDropdownReadPlatformService;
        this.businessRuleMapper = new BusinessRuleMapper(sqlGenerator);
    }

    @Override
    public WhatsAppCampaignData retrieveTemplate() {
        // Reuse existing SMS stretchy reports (and any WHATSAPP-typed ones) as audience / business rules.
        String sql = "select " + this.businessRuleMapper.schema() + " where sr.report_type in (?, ?)";
        final Collection<SmsBusinessRulesData> businessRulesOptions = this.jdbcTemplate.query(sql, this.businessRuleMapper,
                CampaignType.SMS.name(), CampaignType.WHATSAPP.name());
        final Collection<EnumOptionData> campaignTypeOptions = Arrays
                .asList(WhatsAppCampaignEnumerations.whatsAppCampaignType(CampaignType.WHATSAPP));
        final Collection<EnumOptionData> campaignTriggerTypeOptions = Arrays.asList(
                WhatsAppCampaignEnumerations.whatsAppCampaignTriggerType(WhatsAppCampaignTriggerType.DIRECT),
                WhatsAppCampaignEnumerations.whatsAppCampaignTriggerType(WhatsAppCampaignTriggerType.SCHEDULE),
                WhatsAppCampaignEnumerations.whatsAppCampaignTriggerType(WhatsAppCampaignTriggerType.TRIGGERED));
        final Collection<EnumOptionData> months = this.smsCampaignDropdownReadPlatformService.retrieveMonths();
        final Collection<EnumOptionData> weekDays = this.smsCampaignDropdownReadPlatformService.retrieveWeeks();
        final Collection<EnumOptionData> frequencyTypeOptions = this.calendarDropdownReadPlatformService
                .retrieveCalendarFrequencyTypeOptions();
        final Collection<EnumOptionData> periodFrequencyOptions = this.smsCampaignDropdownReadPlatformService
                .retrivePeriodFrequencyTypes();
        return WhatsAppCampaignData.template(businessRulesOptions, campaignTypeOptions, campaignTriggerTypeOptions, months, weekDays,
                frequencyTypeOptions, periodFrequencyOptions);
    }

    @Override
    public WhatsAppCampaignData retrieveOne(final Long campaignId) {
        final WhatsAppCampaign campaign = this.whatsAppCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new WhatsAppCampaignNotFoundException(campaignId));
        if (!campaign.isVisible()) {
            throw new WhatsAppCampaignNotFoundException(campaignId);
        }
        return mapToData(campaign);
    }

    @Override
    public Collection<WhatsAppCampaignData> retrieveAll() {
        return this.whatsAppCampaignRepository.findAll().stream().filter(WhatsAppCampaign::isVisible).map(this::mapToData)
                .collect(Collectors.toList());
    }

    private WhatsAppCampaignData mapToData(final WhatsAppCampaign campaign) {
        final Report report = campaign.getBusinessRuleId();
        final String reportName = report != null ? report.getReportName() : null;
        final Long runReportId = report != null ? report.getId() : null;
        final AppUser submittedBy = campaign.getSubmittedBy();
        final AppUser approvedBy = campaign.getApprovedBy();
        final AppUser closedBy = campaign.getClosedBy();
        final WhatsAppCampaignTimeLine timeline = new WhatsAppCampaignTimeLine(campaign.getSubmittedOnDate(),
                submittedBy != null ? submittedBy.getUsername() : null, campaign.getActivationLocalDate(),
                approvedBy != null ? approvedBy.getUsername() : null, campaign.getClosureDate(),
                closedBy != null ? closedBy.getUsername() : null);
        return WhatsAppCampaignData.instance(campaign.getId(), campaign.getCampaignName(),
                WhatsAppCampaignEnumerations.whatsAppCampaignType(CampaignType.fromInt(campaign.getCampaignType())),
                WhatsAppCampaignEnumerations.whatsAppCampaignTriggerType(WhatsAppCampaignTriggerType.fromInt(campaign.getTriggerType())),
                runReportId, reportName, campaign.getParamValue(),
                WhatsAppCampaignEnumerations.whatsAppCampaignStatus(
                        org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignStatus
                                .fromInt(campaign.getStatus())),
                campaign.getMessage(), campaign.getNextTriggerDate(), campaign.getLastTriggerDate(), timeline,
                campaign.getRecurrenceStartDate(), campaign.getRecurrence(), campaign.getAtTemplateName(), campaign.getLanguageCode(),
                campaign.getBodyVariableMapping(), parseBodyVariableMappingList(campaign.getBodyVariableMapping()));
    }

    private List<String> parseBodyVariableMappingList(final String bodyVariableMapping) {
        if (StringUtils.isBlank(bodyVariableMapping)) {
            return List.of();
        }
        final JsonArray array = JsonParser.parseString(bodyVariableMapping).getAsJsonArray();
        final List<String> result = new ArrayList<>();
        for (var element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    private static final class BusinessRuleMapper implements ResultSetExtractor<List<SmsBusinessRulesData>> {

        final String schema;

        private BusinessRuleMapper(final org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator sqlGenerator) {
            final StringBuilder sql = new StringBuilder(300);
            sql.append("sr.id as id, ");
            sql.append("sr.report_name as reportName, ");
            sql.append("sr.report_type as reportType, ");
            sql.append("sr.report_subtype as reportSubType, ");
            sql.append("sr.description as description, ");
            sql.append("sp.parameter_variable as params, ");
            sql.append("sp.").append(sqlGenerator.escape("parameter_FormatType")).append(" as paramType, ");
            sql.append("sp.parameter_label as paramLabel, ");
            sql.append("sp.parameter_name as paramName ");
            sql.append("from stretchy_report sr ");
            sql.append("left join stretchy_report_parameter as srp on srp.report_id = sr.id ");
            sql.append("left join stretchy_parameter as sp on sp.id = srp.parameter_id ");
            this.schema = sql.toString();
        }

        public String schema() {
            return this.schema;
        }

        @Override
        public List<SmsBusinessRulesData> extractData(final ResultSet rs) throws SQLException, DataAccessException {
            final List<SmsBusinessRulesData> smsBusinessRulesDataList = new ArrayList<>();
            final Map<Long, SmsBusinessRulesData> mapOfSameObjects = new HashMap<>();
            while (rs.next()) {
                final Long id = rs.getLong("id");
                SmsBusinessRulesData smsBusinessRulesData = mapOfSameObjects.get(id);
                if (smsBusinessRulesData == null) {
                    final String reportName = rs.getString("reportName");
                    final String reportType = rs.getString("reportType");
                    final String reportSubType = rs.getString("reportSubType");
                    final String paramName = rs.getString("paramName");
                    final String paramLabel = rs.getString("paramLabel");
                    final String description = rs.getString("description");
                    final Map<String, Object> hashMap = new HashMap<>();
                    hashMap.put(paramLabel, paramName);
                    smsBusinessRulesData = SmsBusinessRulesData.instance(id, reportName, reportType, reportSubType, hashMap, description);
                    mapOfSameObjects.put(id, smsBusinessRulesData);
                    smsBusinessRulesDataList.add(smsBusinessRulesData);
                }
                final Map<String, Object> hashMap = new HashMap<>();
                final String paramName = rs.getString("paramName");
                final String paramLabel = rs.getString("paramLabel");
                hashMap.put(paramLabel, paramName);
                smsBusinessRulesData.getReportParamName().putAll(hashMap);
            }
            return smsBusinessRulesDataList;
        }
    }
}
