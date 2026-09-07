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
package org.apache.fineract.infrastructure.whatsapp.interactive.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.WhatsAppInteractiveConstants;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppConversationSearchResultData;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppInteractiveConfigAreaData;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppInteractiveDashboardData;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppConversationSearchService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveConfigReadPlatformService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveConfigWritePlatformService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveDashboardService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/whatsapp/interactive")
@Component
@Scope("singleton")
@Tag(name = "WhatsApp Interactive Operations", description = "Conversation search, dashboards, and configuration")
@RequiredArgsConstructor
public class WhatsAppInteractiveOperationsApiResource {

    private final PlatformSecurityContext context;
    private final WhatsAppConversationSearchService conversationSearchService;
    private final WhatsAppInteractiveDashboardService dashboardService;
    private final WhatsAppInteractiveConfigReadPlatformService configReadPlatformService;
    private final WhatsAppInteractiveConfigWritePlatformService configWritePlatformService;
    private final DefaultToApiJsonSerializer<WhatsAppConversationSearchResultData> searchSerializer;
    private final DefaultToApiJsonSerializer<WhatsAppInteractiveDashboardData> dashboardSerializer;
    private final DefaultToApiJsonSerializer<WhatsAppInteractiveConfigAreaData> configSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;

    @GET
    @Path("conversations/search")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String searchConversations(@QueryParam("phoneNumber") final String phoneNumber, @QueryParam("clientId") final Long clientId,
            @QueryParam("text") final String text, @QueryParam("fromDate") final String fromDate,
            @QueryParam("toDate") final String toDate, @QueryParam("recipientType") final String recipientType,
            @QueryParam("limit") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        final LocalDate parsedFromDate = fromDate == null ? null : LocalDate.parse(fromDate);
        final LocalDate parsedToDate = toDate == null ? null : LocalDate.parse(toDate);
        final List<WhatsAppConversationSearchResultData> data = conversationSearchService.search(phoneNumber, clientId, text,
                parsedFromDate, parsedToDate, recipientType, limit);
        return searchSerializer.serialize(data);
    }

    @GET
    @Path("dashboard")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveDashboard() {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        return dashboardSerializer.serialize(dashboardService.retrieveDashboard());
    }

    @GET
    @Path("config")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveConfig() {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        return configSerializer.serialize(configReadPlatformService.retrieveConfigAreas());
    }

    @PUT
    @Path("config/{areaCode}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateConfig(@PathParam("areaCode") final String areaCode, final String apiRequestBodyAsJson) {
        context.authenticatedUser().validateHasPermissionTo("CONFIGURE_AFRICASTALKING");
        final CommandProcessingResult result = configWritePlatformService.updateConfigArea(areaCode, apiRequestBodyAsJson);
        return commandProcessingResultSerializer.serialize(result);
    }
}
