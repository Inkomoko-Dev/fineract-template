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
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppBusinessHoursData;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSupportTicketData;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppTicketConversationMessageData;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicketRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppBusinessHoursWritePlatformService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSupportTicketService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSupportTicketWritePlatformService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/whatsapp/interactive")
@Component
@Scope("singleton")
@Tag(name = "WhatsApp Interactive Advisor", description = "Advisor inbox, support tickets, and business hours")
@RequiredArgsConstructor
public class WhatsAppInteractiveTicketApiResource {

    private final PlatformSecurityContext context;
    private final WhatsAppSupportTicketService supportTicketService;
    private final WhatsAppSupportTicketWritePlatformService ticketWritePlatformService;
    private final WhatsAppBusinessHoursWritePlatformService businessHoursWritePlatformService;
    private final WhatsAppSupportTicketRepository ticketRepository;
    private final DefaultToApiJsonSerializer<WhatsAppSupportTicketData> ticketSerializer;
    private final DefaultToApiJsonSerializer<WhatsAppTicketConversationMessageData> conversationSerializer;
    private final DefaultToApiJsonSerializer<WhatsAppBusinessHoursData> businessHoursSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;

    @GET
    @Path("tickets")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveTickets(@QueryParam("status") final String status, @QueryParam("assignedStaffId") final Long assignedStaffId) {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        final WhatsAppTicketStatus ticketStatus = status == null ? null : WhatsAppTicketStatus.valueOf(status);
        final List<WhatsAppSupportTicketData> data = supportTicketService.retrieveTickets(ticketStatus, assignedStaffId);
        return ticketSerializer.serialize(data);
    }

    @GET
    @Path("tickets/{ticketId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveTicket(@PathParam("ticketId") final Long ticketId) {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        return ticketSerializer.serialize(supportTicketService.retrieveTicket(ticketId));
    }

    @GET
    @Path("tickets/{ticketId}/messages")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveTicketMessages(@PathParam("ticketId") final Long ticketId) {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        final String phoneNumber = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp support ticket not found: " + ticketId)).getPhoneNumber();
        final List<WhatsAppTicketConversationMessageData> data = supportTicketService.retrieveConversationHistory(phoneNumber);
        return conversationSerializer.serialize(data);
    }

    @PUT
    @Path("tickets/{ticketId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateTicket(@PathParam("ticketId") final Long ticketId, final String apiRequestBodyAsJson) {
        context.authenticatedUser().validateHasPermissionTo("CONFIGURE_AFRICASTALKING");
        final CommandProcessingResult result = ticketWritePlatformService.updateTicket(ticketId, apiRequestBodyAsJson);
        return commandProcessingResultSerializer.serialize(result);
    }

    @GET
    @Path("business-hours")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveBusinessHours() {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        return businessHoursSerializer.serialize(businessHoursWritePlatformService.retrieveBusinessHours());
    }

    @PUT
    @Path("business-hours/{businessHoursId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateBusinessHours(@PathParam("businessHoursId") final Long businessHoursId, final String apiRequestBodyAsJson) {
        context.authenticatedUser().validateHasPermissionTo("CONFIGURE_AFRICASTALKING");
        final CommandProcessingResult result = businessHoursWritePlatformService.updateBusinessHours(businessHoursId, apiRequestBodyAsJson);
        return commandProcessingResultSerializer.serialize(result);
    }
}
