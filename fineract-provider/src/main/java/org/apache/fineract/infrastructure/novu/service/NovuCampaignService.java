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
package org.apache.fineract.infrastructure.novu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.campaigns.sms.service.SmsCampaignReadPlatformService;
import org.apache.fineract.infrastructure.novu.domain.NovuCampaign;
import org.apache.fineract.infrastructure.novu.domain.NovuCampaignRepository;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.calendar.service.CalendarUtils;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NovuCampaignService {

    public static final List<String> LOAN_EVENTS = List.of("LOAN_CREATED", "LOAN_APPROVED", "LOAN_DISBURSED", "LOAN_REJECTED",
            "LOAN_REPAYMENT", "LOAN_CLOSED");
    public static final List<String> EVENT_TEMPLATE_VARIABLES = List.of("clientName", "firstName", "lastName", "loanAccountNumber",
            "approvedPrincipal", "disbursedAmount", "currency", "transactionAmount", "eventType");
    public static final List<String> REPORT_TEMPLATE_VARIABLES = List.of("clientName", "firstName", "lastName", "loanAccountNumber",
            "dueDate", "amountDue", "daysUntilDue", "mobileNo", "email");
    public static final List<String> TRIGGER_TYPES = List.of("DIRECT", "SCHEDULED", "TRIGGERED");
    public static final List<String> CHANNELS = List.of("IN_APP", "EMAIL", "SMS", "WHATSAPP", "TELEGRAM", "SLACK");
    public static final Map<String, String> CHAT_PROVIDERS = Map.of("WHATSAPP", "whatsapp-business", "TELEGRAM", "telegram", "SLACK",
            "slack");
    public static final List<String> RECIPIENT_TYPES = List.of("CLIENT", "STAFF", "BOTH");
    public static final Map<String, String> WORKFLOW_BINDINGS = Map.of("emailSubject", "{{payload.emailSubject}}", "emailBody",
            "{{payload.emailBody}}", "smsBody", "{{payload.smsBody}}", "inAppBody", "{{payload.inAppBody}}", "chatBody",
            "{{payload.chatBody}}");

    private final NovuCampaignRepository repository;
    private final NovuClient novuClient;
    private final JdbcTemplate jdbcTemplate;
    private final ReadReportingService readReportingService;
    private final SmsCampaignReadPlatformService smsCampaignReadPlatformService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NovuCampaignService(final NovuCampaignRepository repository, final NovuClient novuClient, final JdbcTemplate jdbcTemplate,
            final ReadReportingService readReportingService, final SmsCampaignReadPlatformService smsCampaignReadPlatformService,
            final PhoneNumberNormalizer phoneNumberNormalizer) {
        this.repository = repository;
        this.novuClient = novuClient;
        this.jdbcTemplate = jdbcTemplate;
        this.readReportingService = readReportingService;
        this.smsCampaignReadPlatformService = smsCampaignReadPlatformService;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
    }

    public NovuCampaign getCampaign(final Long id) {
        return repository.findById(id).orElseThrow(() -> new PlatformDataIntegrityException("error.msg.novu.campaign.not.found",
                "Novu campaign with id " + id + " was not found"));
    }

    public List<Map<String, Object>> findAllAsMaps() {
        return repository.findAll().stream().map(this::toApiMap).collect(Collectors.toList());
    }

    public Map<String, Object> catalogue() {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("loanEvents", LOAN_EVENTS);
        result.put("triggerTypes", TRIGGER_TYPES);
        result.put("channels", CHANNELS);
        result.put("chatProviders", CHAT_PROVIDERS);
        result.put("recipientTypes", RECIPIENT_TYPES);
        return result;
    }

    public Map<String, Object> templateOptions() {
        final Map<String, Object> result = new LinkedHashMap<>(catalogue());
        result.put("audienceReports", listAudienceReports());
        result.put("customEventsSupported", true);
        result.put("customTriggerEndpoint", "/novu/events/{eventType}/trigger");
        result.put("templateSyntax", "${variableName}");
        result.put("eventTemplateVariables", EVENT_TEMPLATE_VARIABLES);
        result.put("reportTemplateVariables", REPORT_TEMPLATE_VARIABLES);
        result.put("templateVariables", EVENT_TEMPLATE_VARIABLES);
        result.put("novuWorkflowBindings", WORKFLOW_BINDINGS);
        try {
            result.put("businessRulesAndScheduleOptions", smsCampaignReadPlatformService.retrieveTemplate("SMS"));
        } catch (final RuntimeException ignored) {
            result.put("businessRulesAndScheduleOptions", Map.of());
        }
        return result;
    }

    public Map<String, Object> listLogs(final Integer requestedLimit, final Integer requestedOffset) {
        final int limit = Math.max(1, Math.min(requestedLimit == null ? 100 : requestedLimit, 500));
        final int offset = Math.max(0, requestedOffset == null ? 0 : requestedOffset);
        final List<Map<String, Object>> logs = jdbcTemplate.queryForList("SELECT l.id, l.campaign_id campaignId, "
                + "c.campaign_name campaignName, l.event_type eventType, l.workflow_id workflowId, "
                + "l.subscriber_id subscriberId, l.recipient_type recipientType, l.channel, l.status, "
                + "l.transaction_id transactionId, l.response_message responseMessage, l.created_on createdOn "
                + "FROM novu_notification_log l LEFT JOIN novu_campaign c ON c.id = l.campaign_id "
                + "ORDER BY l.created_on DESC LIMIT ? OFFSET ?", limit, offset);
        for (final Map<String, Object> log : logs) {
            final Object createdOn = log.get("createdOn");
            if (createdOn != null && !(createdOn instanceof String) && !(createdOn instanceof Number)) {
                log.put("createdOn", createdOn.toString());
            }
        }
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("pageItems", logs);
        response.put("totalFilteredRecords", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM novu_notification_log", Long.class));
        return response;
    }

    public Map<String, Object> toApiMap(final NovuCampaign campaign) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", campaign.getId());
        result.put("campaignName", campaign.getCampaignName());
        result.put("workflowId", campaign.getWorkflowId());
        result.put("eventType", campaign.getEventType());
        result.put("triggerType", campaign.getTriggerType());
        result.put("recipientType", campaign.getRecipientType());
        result.put("channels", campaign.getChannels());
        result.put("emailSubject", campaign.getEmailSubject());
        result.put("emailBody", campaign.getEmailBody());
        result.put("smsBody", campaign.getSmsBody());
        result.put("inAppBody", campaign.getInAppBody());
        result.put("chatBody", campaign.getChatBody());
        result.put("reportName", campaign.getReportName());
        result.put("paramValue", campaign.getParamValue());
        result.put("recurrence", campaign.getRecurrence());
        result.put("recurrenceStartDate", formatDateTime(campaign.getRecurrenceStartDate()));
        result.put("nextTriggerDate", formatDateTime(campaign.getNextTriggerDate()));
        result.put("lastTriggerDate", formatDateTime(campaign.getLastTriggerDate()));
        result.put("active", campaign.isActive());
        result.put("createdBy", campaign.getCreatedBy());
        result.put("createdOn", formatDateTime(campaign.getCreatedOn()));
        result.put("lastModifiedOn", formatDateTime(campaign.getLastModifiedOn()));
        return result;
    }

    private String formatDateTime(final LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public List<Map<String, Object>> listAudienceReports() {
        try {
            return jdbcTemplate.query("SELECT sr.id reportId, sr.report_name reportName, sr.report_type reportType, "
                    + "sr.report_subtype reportSubType, sr.description reportDescription FROM stretchy_report sr "
                    + "WHERE UPPER(sr.report_type) = 'SMS' ORDER BY sr.report_name", (rs, rowNum) -> {
                        final Map<String, Object> report = new LinkedHashMap<>();
                        report.put("reportId", rs.getLong("reportId"));
                        report.put("reportName", rs.getString("reportName"));
                        report.put("reportType", rs.getString("reportType"));
                        report.put("reportSubType", rs.getString("reportSubType"));
                        report.put("reportDescription", rs.getString("reportDescription"));
                        return report;
                    });
        } catch (final RuntimeException ignored) {
            return List.of();
        }
    }

    @Transactional
    public NovuCampaign create(final String json, final Long userId) {
        final CampaignValues values = parse(json);
        final NovuCampaign campaign = repository.saveAndFlush(new NovuCampaign(values.name, values.workflowId, values.eventType,
                values.triggerType, values.recipientType, values.channels, values.emailSubject, values.emailBody, values.smsBody,
                values.inAppBody, values.chatBody, values.reportName, values.paramValue, values.recurrence, values.recurrenceStartDate,
                values.active, userId));
        ensureWorkflow(campaign);
        return campaign;
    }

    @Transactional
    public NovuCampaign update(final Long id, final String json) {
        final NovuCampaign campaign = getCampaign(id);
        final CampaignValues values = parse(json);
        campaign.update(values.name, values.workflowId, values.eventType, values.triggerType, values.recipientType, values.channels,
                values.emailSubject, values.emailBody, values.smsBody, values.inAppBody, values.chatBody, values.reportName,
                values.paramValue, values.recurrence, values.recurrenceStartDate, values.active);
        final NovuCampaign saved = repository.saveAndFlush(campaign);
        ensureWorkflow(saved);
        return saved;
    }

    @Transactional
    public void delete(final Long id) {
        final NovuCampaign campaign = getCampaign(id);
        final String workflowId = campaign.getWorkflowId();
        final boolean unusedWorkflow = StringUtils.isNotBlank(workflowId) && repository.countByWorkflowId(workflowId) <= 1;
        repository.delete(campaign);
        if (unusedWorkflow) {
            requireSuccess(novuClient.deleteWorkflow(workflowId), "error.msg.novu.workflow.delete.failed",
                    "Campaign was removed locally but the Novu workflow could not be deleted: ");
        }
    }

    private void ensureWorkflow(final NovuCampaign campaign) {
        requireSuccess(novuClient.ensureWorkflow(campaign.getWorkflowId(), campaign.getCampaignName(), splitChannels(campaign.getChannels())),
                "error.msg.novu.workflow.create.failed", "Campaign was saved but the Novu workflow could not be created: ");
    }

    public void triggerLoanEvent(final String eventType, final Loan loan, final Map<String, Object> additionalPayload) {
        final List<NovuCampaign> campaigns = repository.findByEventTypeAndTriggerTypeAndActiveTrue(eventType, "TRIGGERED");
        for (final NovuCampaign campaign : campaigns) {
            final Map<String, Object> payload = loanPayload(loan, eventType);
            payload.putAll(additionalPayload);
            if (includesRecipient(campaign, "CLIENT") && loan.getClient() != null) {
                trigger(campaign, clientSubscriber(loan.getClient()), payload, "CLIENT");
            }
            if (includesRecipient(campaign, "STAFF") && loan.getLoanOfficer() != null) {
                trigger(campaign, staffSubscriber(loan.getLoanOfficer()), payload, "STAFF");
            }
        }
    }

    public Map<String, Integer> triggerCustomEvent(final String eventType, final Map<String, Object> subscriber,
            final Map<String, Object> payload) {
        int triggered = 0;
        for (final NovuCampaign campaign : repository.findByEventTypeAndTriggerTypeAndActiveTrue(eventType.toUpperCase(Locale.ROOT),
                "TRIGGERED")) {
            trigger(campaign, subscriber, payload, "CUSTOM");
            triggered++;
        }
        return Map.of("triggered", triggered);
    }

    public Map<String, Integer> triggerCampaign(final Long id) {
        final NovuCampaign campaign = getCampaign(id);
        if (!"DIRECT".equals(campaign.getTriggerType()) && !"SCHEDULED".equals(campaign.getTriggerType())) {
            throw new PlatformDataIntegrityException("error.msg.novu.campaign.not.direct",
                    "Only direct or scheduled campaigns can be manually sent");
        }
        return triggerReportAudience(campaign);
    }

    @Transactional
    @CronTarget(jobName = JobName.PROCESS_NOVU_SCHEDULED_CAMPAIGNS)
    public void processScheduledCampaigns() throws JobExecutionException {
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        for (final NovuCampaign campaign : repository.findByTriggerTypeAndActiveTrue("SCHEDULED")) {
            if (campaign.getNextTriggerDate() == null || campaign.getNextTriggerDate().isAfter(now)) {
                continue;
            }
            triggerReportAudience(campaign);
            if (StringUtils.isBlank(campaign.getRecurrence())) {
                campaign.deactivateAfterFinalRun();
            } else {
                LocalDateTime next = CalendarUtils.getNextRecurringDate(campaign.getRecurrence(), campaign.getNextTriggerDate(), now);
                if (!next.isAfter(now)) {
                    next = CalendarUtils.getNextRecurringDate(campaign.getRecurrence(), campaign.getNextTriggerDate(), now.plusSeconds(1));
                }
                campaign.recordScheduledRun(next);
            }
            repository.saveAndFlush(campaign);
        }
    }

    private Map<String, Integer> triggerReportAudience(final NovuCampaign campaign) {
        if (StringUtils.isBlank(campaign.getReportName())) {
            throw new PlatformDataIntegrityException("error.msg.novu.campaign.report.required",
                    "An audience report is required for direct and scheduled campaigns");
        }
        int triggered = 0;
        int skipped = 0;
        for (final Map<String, Object> row : runAudienceReport(campaign)) {
            final Map<String, Object> subscriber = reportSubscriber(campaign, row);
            if (subscriber == null) {
                skipped++;
                continue;
            }
            trigger(campaign, subscriber, row, campaign.getRecipientType());
            triggered++;
        }
        return Map.of("triggered", triggered, "skipped", skipped);
    }

    private List<Map<String, Object>> runAudienceReport(final NovuCampaign campaign) {
        try {
            final Map<String, String> params = StringUtils.isBlank(campaign.getParamValue()) ? new HashMap<>()
                    : objectMapper.readValue(campaign.getParamValue(), new TypeReference<HashMap<String, String>>() {});
            final GenericResultsetData results = readReportingService.retrieveGenericResultSetForSmsEmailCampaign(campaign.getReportName(),
                    "report", params);
            final List<Map<String, Object>> rows = new ArrayList<>();
            if (results.getColumnHeaders() == null || results.getData() == null) {
                return rows;
            }
            for (final ResultsetRowData rowData : results.getData()) {
                final Map<String, Object> row = new LinkedHashMap<>();
                final List<String> values = rowData.getRow();
                for (int i = 0; values != null && i < values.size() && i < results.getColumnHeaders().size(); i++) {
                    final ResultsetColumnHeaderData header = results.getColumnHeaders().get(i);
                    row.put(header.getColumnName(), values.get(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (final IOException | RuntimeException e) {
            throw new PlatformDataIntegrityException("error.msg.novu.campaign.report.failed",
                    "Unable to run Novu audience report: " + e.getMessage());
        }
    }

    private Map<String, Object> reportSubscriber(final NovuCampaign campaign, final Map<String, Object> row) {
        final Object rawId = first(row, "subscriberId", "id", "clientId", "staffId", "userId");
        if (rawId == null) {
            return null;
        }
        if ("BOTH".equals(campaign.getRecipientType()) && !row.containsKey("subscriberId")) {
            return null;
        }
        final String prefix = "STAFF".equals(campaign.getRecipientType()) ? "staff-" : "client-";
        final Map<String, Object> subscriber = new LinkedHashMap<>();
        subscriber.put("subscriberId", row.containsKey("subscriberId") ? rawId.toString() : prefix + rawId);
        putIfPresent(subscriber, "firstName", stringValue(first(row, "firstName", "firstname")));
        putIfPresent(subscriber, "lastName", stringValue(first(row, "lastName", "lastname")));
        putIfPresent(subscriber, "email", stringValue(first(row, "email", "emailAddress", "email_address")));
        putIfPresent(subscriber, "phone", internationalPhone(stringValue(first(row, "phone", "mobileNo", "mobile_no")),
                stringValue(first(row, "mobileCountryCode", "countryCode", "country_code"))));
        return subscriber;
    }

    private Object first(final Map<String, Object> values, final String... keys) {
        for (final String key : keys) {
            if (values.get(key) != null) {
                return values.get(key);
            }
        }
        return null;
    }

    private String stringValue(final Object value) {
        return value == null ? null : value.toString();
    }

    public Map<String, Integer> syncSubscribers() {
        int synced = 0;
        int failed = 0;
        final List<Map<String, Object>> subscribers = jdbcTemplate.query(
                "SELECT CONCAT('client-', id) subscriberId, firstname firstName, lastname lastName, "
                        + "email_address email, mobile_no phone, mobile_country_code countryCode FROM m_client WHERE status_enum = 300 "
                        + "UNION ALL SELECT CONCAT('staff-', id), firstname, lastname, email_address, mobile_no, NULL "
                        + "FROM m_staff WHERE is_active = 1 "
                        + "UNION ALL SELECT CONCAT('user-', u.id), u.firstname, u.lastname, u.email, s.mobile_no, NULL "
                        + "FROM m_appuser u LEFT JOIN m_staff s ON s.id = u.staff_id WHERE u.is_deleted = false",
                (rs, rowNum) -> subscriber(rs.getString("subscriberId"), rs.getString("firstName"), rs.getString("lastName"),
                        rs.getString("email"), internationalPhone(rs.getString("phone"), rs.getString("countryCode"))));
        for (int start = 0; start < subscribers.size(); start += 500) {
            final NovuClient.BulkSubscriberResult result = novuClient.upsertSubscribers(
                    subscribers.subList(start, Math.min(start + 500, subscribers.size())));
            synced += result.getSynced();
            failed += result.getFailed();
        }
        return Map.of("synced", synced, "failed", failed, "total", subscribers.size());
    }

    private void trigger(final NovuCampaign campaign, final Map<String, Object> subscriber, final Map<String, Object> payload,
            final String recipientType) {
        final String transactionId = UUID.randomUUID().toString();
        final Map<String, Object> deliveryPayload = new LinkedHashMap<>(payload);
        deliveryPayload.put("channels", campaign.getChannels());
        putIfPresent(deliveryPayload, "emailSubject", render(campaign.getEmailSubject(), deliveryPayload));
        putIfPresent(deliveryPayload, "emailBody", render(campaign.getEmailBody(), deliveryPayload));
        putIfPresent(deliveryPayload, "smsBody", render(campaign.getSmsBody(), deliveryPayload));
        putIfPresent(deliveryPayload, "inAppBody", render(campaign.getInAppBody(), deliveryPayload));
        putIfPresent(deliveryPayload, "chatBody", render(campaign.getChatBody(), deliveryPayload));
        final NovuClient.TriggerResult result = novuClient.trigger(campaign.getWorkflowId(), subscriber, deliveryPayload, transactionId);
        for (final String channel : splitChannels(campaign.getChannels())) {
            jdbcTemplate.update("INSERT INTO novu_notification_log (campaign_id, event_type, workflow_id, subscriber_id, "
                    + "recipient_type, channel, status, transaction_id, response_message, created_on) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    campaign.getId(), campaign.getEventType(), campaign.getWorkflowId(), subscriber.get("subscriberId"), recipientType,
                    channel, result.isSuccessful() ? "ACCEPTED" : "FAILED", result.getTransactionId(),
                    StringUtils.abbreviate(StringUtils.defaultString(result.getMessage()), 990), DateUtils.getLocalDateTimeOfTenant());
        }
    }

    private String render(final String template, final Map<String, Object> values) {
        String rendered = template;
        if (rendered == null) {
            return null;
        }
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            final String value = entry.getValue() == null ? "" : entry.getValue().toString();
            rendered = rendered.replace("${" + entry.getKey() + "}", value).replace("{{" + entry.getKey() + "}}", value);
        }
        return rendered;
    }

    private Map<String, Object> loanPayload(final Loan loan, final String eventType) {
        final Client client = loan.getClient();
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("loanId", loan.getId());
        payload.put("loanAccountNumber", loan.getAccountNumber());
        payload.put("clientId", client == null ? null : client.getId());
        payload.put("clientName", client == null ? null : client.getDisplayName());
        payload.put("firstName", client == null ? null : client.getFirstname());
        payload.put("lastName", client == null ? null : client.getLastname());
        payload.put("approvedPrincipal", loan.getApprovedPrincipal());
        payload.put("disbursedAmount", loan.getDisbursedAmount());
        payload.put("currency", loan.getCurrencyCode());
        return payload;
    }

    private Map<String, Object> clientSubscriber(final Client client) {
        return subscriber("client-" + client.getId(), client.getFirstname(), client.getLastname(), client.getEmail(),
                internationalPhone(client.mobileNo(), client.getMobileCountryCode()));
    }

    private Map<String, Object> staffSubscriber(final Staff staff) {
        return subscriber("staff-" + staff.getId(), staff.displayName(), null, staff.emailAddress(),
                internationalPhone(staff.mobileNo(), null));
    }

    private String internationalPhone(final String phone, final String countryCode) {
        return phoneNumberNormalizer.normalize(phone, countryCode);
    }

    private Map<String, Object> subscriber(final String subscriberId, final String firstName, final String lastName, final String email,
            final String phone) {
        final Map<String, Object> subscriber = new LinkedHashMap<>();
        subscriber.put("subscriberId", subscriberId);
        putIfPresent(subscriber, "firstName", firstName);
        putIfPresent(subscriber, "lastName", lastName);
        putIfPresent(subscriber, "email", email);
        putIfPresent(subscriber, "phone", phone);
        return subscriber;
    }

    private void putIfPresent(final Map<String, Object> target, final String key, final String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private boolean includesRecipient(final NovuCampaign campaign, final String recipientType) {
        return recipientType.equals(campaign.getRecipientType()) || "BOTH".equals(campaign.getRecipientType());
    }

    private List<String> splitChannels(final String channels) {
        if (StringUtils.isBlank(channels)) {
            return List.of();
        }
        return Arrays.stream(channels.split(",")).map(String::trim).filter(StringUtils::isNotBlank).collect(Collectors.toList());
    }

    private void requireSuccess(final NovuClient.TriggerResult result, final String code, final String message) {
        if (!result.isSuccessful()) {
            throw new PlatformDataIntegrityException(code, message + result.getMessage());
        }
    }

    private CampaignValues parse(final String json) {
        final JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        final String name = required(object, "campaignName");
        final String workflowId = required(object, "workflowId");
        final String eventType = required(object, "eventType").toUpperCase(Locale.ROOT);
        final String triggerType = optional(object, "triggerType") == null ? "TRIGGERED"
                : optional(object, "triggerType").toUpperCase(Locale.ROOT);
        final String recipientType = required(object, "recipientType").toUpperCase(Locale.ROOT);
        final String channels = required(object, "channels").toUpperCase(Locale.ROOT);
        if (!TRIGGER_TYPES.contains(triggerType)) {
            throw new PlatformDataIntegrityException("error.msg.novu.trigger.invalid",
                    "Trigger type must be DIRECT, SCHEDULED or TRIGGERED");
        }
        if (!RECIPIENT_TYPES.contains(recipientType)) {
            throw new PlatformDataIntegrityException("error.msg.novu.recipient.invalid", "Recipient type must be CLIENT, STAFF or BOTH");
        }
        for (final String channel : splitChannels(channels)) {
            if (!CHANNELS.contains(channel)) {
                throw new PlatformDataIntegrityException("error.msg.novu.channel.invalid", "Unsupported Novu channel: " + channel);
            }
        }
        final String reportName = optional(object, "reportName");
        final String paramValue = object.has("paramValue") && !object.get("paramValue").isJsonNull()
                ? object.get("paramValue").toString() : null;
        final String recurrence = optional(object, "recurrence");
        final LocalDateTime recurrenceStartDate = parseDateTime(optional(object, "recurrenceStartDate"));
        if (("DIRECT".equals(triggerType) || "SCHEDULED".equals(triggerType)) && StringUtils.isBlank(reportName)) {
            throw new PlatformDataIntegrityException("error.msg.novu.campaign.report.required",
                    "reportName is required for direct and scheduled campaigns");
        }
        if ("SCHEDULED".equals(triggerType) && recurrenceStartDate == null) {
            throw new PlatformDataIntegrityException("error.msg.novu.campaign.schedule.required",
                    "recurrenceStartDate is required for scheduled campaigns");
        }
        final boolean active = object.has("active") && object.get("active").getAsBoolean();
        return new CampaignValues(name, workflowId, eventType, triggerType, recipientType, channels, optional(object, "emailSubject"),
                optional(object, "emailBody"), optional(object, "smsBody"), optional(object, "inAppBody"), optional(object, "chatBody"),
                reportName, paramValue, recurrence, recurrenceStartDate, active);
    }

    private LocalDateTime parseDateTime(final String value) {
        return StringUtils.isBlank(value) ? null : LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String required(final JsonObject object, final String name) {
        if (!object.has(name) || StringUtils.isBlank(object.get(name).getAsString())) {
            throw new PlatformDataIntegrityException("error.msg.novu.campaign.parameter.required", name + " is required");
        }
        return object.get(name).getAsString().trim();
    }

    private String optional(final JsonObject object, final String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? StringUtils.trimToNull(object.get(name).getAsString()) : null;
    }

    private static final class CampaignValues {

        private final String name;
        private final String workflowId;
        private final String eventType;
        private final String triggerType;
        private final String recipientType;
        private final String channels;
        private final String emailSubject;
        private final String emailBody;
        private final String smsBody;
        private final String inAppBody;
        private final String chatBody;
        private final String reportName;
        private final String paramValue;
        private final String recurrence;
        private final LocalDateTime recurrenceStartDate;
        private final boolean active;

        private CampaignValues(final String name, final String workflowId, final String eventType, final String triggerType,
                final String recipientType, final String channels, final String emailSubject, final String emailBody, final String smsBody,
                final String inAppBody, final String chatBody, final String reportName, final String paramValue, final String recurrence,
                final LocalDateTime recurrenceStartDate, final boolean active) {
            this.name = name;
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
            this.active = active;
        }
    }
}
