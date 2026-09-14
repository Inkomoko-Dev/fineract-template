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
package org.apache.fineract.infrastructure.novu.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.fineract.infrastructure.campaigns.sms.service.SmsCampaignReadPlatformService;
import org.apache.fineract.infrastructure.novu.service.NovuCampaignService;
import org.apache.fineract.infrastructure.novu.service.NovuClient;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Path("/novu")
@Component
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NovuApiResource {

    private static final String CAMPAIGN_RESOURCE = "NOVUCAMPAIGN";
    private static final String LOG_RESOURCE = "NOVUNOTIFICATIONLOG";
    private final PlatformSecurityContext securityContext;
    private final NovuCampaignService campaignService;
    private final JdbcTemplate jdbcTemplate;
    private final SmsCampaignReadPlatformService smsCampaignReadPlatformService;
    private final NovuClient novuClient;
    private final Gson gson = new Gson();

    public NovuApiResource(final PlatformSecurityContext securityContext, final NovuCampaignService campaignService,
            final JdbcTemplate jdbcTemplate, final SmsCampaignReadPlatformService smsCampaignReadPlatformService,
            final NovuClient novuClient) {
        this.securityContext = securityContext;
        this.campaignService = campaignService;
        this.jdbcTemplate = jdbcTemplate;
        this.smsCampaignReadPlatformService = smsCampaignReadPlatformService;
        this.novuClient = novuClient;
    }

    @GET
    @Path("campaigns")
    public String campaigns() {
        securityContext.authenticatedUser().validateHasReadPermission(CAMPAIGN_RESOURCE);
        return gson.toJson(campaignService.findAll());
    }

    @GET
    @Path("events")
    public String events() {
        securityContext.authenticatedUser().validateHasReadPermission(CAMPAIGN_RESOURCE);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("loanEvents", NovuCampaignService.LOAN_EVENTS);
        result.put("customEventsSupported", true);
        result.put("customTriggerEndpoint", "/novu/events/{eventType}/trigger");
        result.put("triggerTypes", NovuCampaignService.TRIGGER_TYPES);
        result.put("channels", NovuCampaignService.CHANNELS);
        result.put("chatProviders", NovuCampaignService.CHAT_PROVIDERS);
        return gson.toJson(result);
    }

    @GET
    @Path("campaigns/template")
    public String template() {
        securityContext.authenticatedUser().validateHasReadPermission(CAMPAIGN_RESOURCE);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("triggerTypes", NovuCampaignService.TRIGGER_TYPES);
        result.put("recipientTypes", List.of("CLIENT", "STAFF", "BOTH"));
        result.put("channels", NovuCampaignService.CHANNELS);
        result.put("chatProviders", NovuCampaignService.CHAT_PROVIDERS);
        result.put("loanEvents", NovuCampaignService.LOAN_EVENTS);
        result.put("businessRulesAndScheduleOptions", smsCampaignReadPlatformService.retrieveTemplate("SMS"));
        result.put("templateSyntax", "${variableName}");
        result.put("templateVariables", List.of("clientName", "firstName", "lastName", "loanAccountNumber", "approvedPrincipal",
                "currency", "transactionAmount", "dueDate", "amountDue", "daysUntilDue"));
        result.put("novuWorkflowBindings", Map.of("emailSubject", "{{payload.emailSubject}}", "emailBody", "{{payload.emailBody}}",
                "smsBody", "{{payload.smsBody}}", "inAppBody", "{{payload.inAppBody}}", "chatBody", "{{payload.chatBody}}"));
        return gson.toJson(result);
    }

    @POST
    @Path("campaigns")
    public String create(final String json) {
        securityContext.authenticatedUser().validateHasCreatePermission(CAMPAIGN_RESOURCE);
        return gson.toJson(campaignService.create(json, securityContext.authenticatedUser().getId()));
    }

    @PUT
    @Path("campaigns/{id}")
    public String update(@PathParam("id") final Long id, final String json) {
        securityContext.authenticatedUser().validateHasUpdatePermission(CAMPAIGN_RESOURCE);
        return gson.toJson(campaignService.update(id, json));
    }

    @DELETE
    @Path("campaigns/{id}")
    public String delete(@PathParam("id") final Long id) {
        securityContext.authenticatedUser().validateHasPermissionTo("DELETE_NOVUCAMPAIGN");
        campaignService.delete(id);
        return "{\"resourceId\":" + id + "}";
    }

    @POST
    @Path("campaigns/{id}/trigger")
    public String triggerCampaign(@PathParam("id") final Long id) {
        securityContext.authenticatedUser().validateHasCreatePermission(CAMPAIGN_RESOURCE);
        return gson.toJson(campaignService.triggerCampaign(id));
    }

    @GET
    @Path("logs")
    public String logs(@QueryParam("limit") final Integer requestedLimit, @QueryParam("offset") final Integer requestedOffset) {
        securityContext.authenticatedUser().validateHasReadPermission(LOG_RESOURCE);
        final int limit = Math.max(1, Math.min(requestedLimit == null ? 100 : requestedLimit, 500));
        final int offset = Math.max(0, requestedOffset == null ? 0 : requestedOffset);
        final List<Map<String, Object>> logs = jdbcTemplate.queryForList("SELECT l.id, l.campaign_id campaignId, "
                + "c.campaign_name campaignName, l.event_type eventType, l.workflow_id workflowId, "
                + "l.subscriber_id subscriberId, l.recipient_type recipientType, l.channel, l.status, "
                + "l.transaction_id transactionId, l.response_message responseMessage, l.created_on createdOn "
                + "FROM novu_notification_log l LEFT JOIN novu_campaign c ON c.id = l.campaign_id "
                + "ORDER BY l.created_on DESC LIMIT ? OFFSET ?", limit, offset);
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("pageItems", logs);
        response.put("totalFilteredRecords", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM novu_notification_log", Long.class));
        return gson.toJson(response);
    }

    @POST
    @Path("events/{eventType}/trigger")
    public String triggerCustom(@PathParam("eventType") final String eventType, final String json) {
        securityContext.authenticatedUser().validateHasCreatePermission(CAMPAIGN_RESOURCE);
        final JsonObject body = JsonParser.parseString(json).getAsJsonObject();
        final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        final Map<String, Object> subscriber = gson.fromJson(body.get("subscriber"), mapType);
        final Map<String, Object> payload = body.has("payload") ? gson.fromJson(body.get("payload"), mapType) : Map.of();
        return gson.toJson(campaignService.triggerCustomEvent(eventType, subscriber, payload));
    }

    @POST
    @Path("subscribers/sync")
    public String syncSubscribers() {
        securityContext.authenticatedUser().validateHasCreatePermission(CAMPAIGN_RESOURCE);
        return gson.toJson(campaignService.syncSubscribers());
    }

    @POST
    @Path("subscribers/{subscriberId}/credentials")
    public String subscriberCredentials(@PathParam("subscriberId") final String subscriberId, final String json) {
        securityContext.authenticatedUser().validateHasUpdatePermission(CAMPAIGN_RESOURCE);
        final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        final Map<String, Object> body = gson.fromJson(json, mapType);
        final String providerId = body.get("providerId") == null ? "" : body.get("providerId").toString();
        if (!NovuCampaignService.CHAT_PROVIDERS.containsValue(providerId)) {
            throw new IllegalArgumentException("providerId must be whatsapp-business, telegram or slack");
        }
        final NovuClient.TriggerResult result = novuClient.upsertProviderCredentials(subscriberId, body);
        return gson.toJson(Map.of("successful", result.isSuccessful(), "transactionId", result.getTransactionId(), "message",
                result.getMessage()));
    }
}
