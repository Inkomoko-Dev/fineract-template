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
package org.apache.fineract.infrastructure.campaigns.whatsapp.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignConstants;
import org.apache.fineract.infrastructure.campaigns.whatsapp.data.WhatsAppCampaignData;
import org.apache.fineract.infrastructure.campaigns.whatsapp.data.WhatsAppPreviewData;
import org.apache.fineract.infrastructure.campaigns.whatsapp.service.WhatsAppCampaignReadPlatformService;
import org.apache.fineract.infrastructure.campaigns.whatsapp.service.WhatsAppCampaignWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/whatsappcampaigns")
@Component
@Scope("singleton")
public class WhatsAppCampaignApiResource {

    private final PlatformSecurityContext context;
    private final WhatsAppCampaignReadPlatformService readPlatformService;
    private final WhatsAppCampaignWritePlatformService writePlatformService;
    private final DefaultToApiJsonSerializer<WhatsAppCampaignData> campaignSerializer;
    private final DefaultToApiJsonSerializer<WhatsAppPreviewData> previewSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @Autowired
    public WhatsAppCampaignApiResource(final PlatformSecurityContext context,
            final WhatsAppCampaignReadPlatformService readPlatformService,
            final WhatsAppCampaignWritePlatformService writePlatformService,
            final DefaultToApiJsonSerializer<WhatsAppCampaignData> campaignSerializer,
            final DefaultToApiJsonSerializer<WhatsAppPreviewData> previewSerializer,
            final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer,
            final ApiRequestParameterHelper apiRequestParameterHelper) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.writePlatformService = writePlatformService;
        this.campaignSerializer = campaignSerializer;
        this.previewSerializer = previewSerializer;
        this.commandProcessingResultSerializer = commandProcessingResultSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
    }

    @GET
    @Path("template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve WhatsApp Campaign Template")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WhatsAppCampaignData.class)))
    public String template(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(WhatsAppCampaignConstants.RESOURCE_NAME);
        final WhatsAppCampaignData data = this.readPlatformService.retrieveTemplate();
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.campaignSerializer.serialize(settings, data);
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List WhatsApp Campaigns")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WhatsAppCampaignData.class)))
    public String retrieveAll(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(WhatsAppCampaignConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.campaignSerializer.serialize(settings, this.readPlatformService.retrieveAll());
    }

    @GET
    @Path("{resourceId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a WhatsApp Campaign")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WhatsAppCampaignData.class)))
    public String retrieveOne(@PathParam("resourceId") final Long resourceId, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(WhatsAppCampaignConstants.RESOURCE_NAME);
        final WhatsAppCampaignData data = this.readPlatformService.retrieveOne(resourceId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.campaignSerializer.serialize(settings, data);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a WhatsApp Campaign")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public String create(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasCreatePermission(WhatsAppCampaignConstants.RESOURCE_NAME);
        final CommandProcessingResult result = this.writePlatformService.create(apiRequestBodyAsJson);
        return this.commandProcessingResultSerializer.serializeResult(result);
    }

    @PUT
    @Path("{campaignId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a WhatsApp Campaign")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public String update(@PathParam("campaignId") final Long campaignId, @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasUpdatePermission(WhatsAppCampaignConstants.RESOURCE_NAME);
        final CommandProcessingResult result = this.writePlatformService.update(campaignId, apiRequestBodyAsJson);
        return this.commandProcessingResultSerializer.serializeResult(result);
    }

    @POST
    @Path("{campaignId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate | Close | Reactivate a WhatsApp Campaign")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public String handleCommands(@PathParam("campaignId") final Long campaignId, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser();
        CommandProcessingResult result;
        if (is(commandParam, "activate")) {
            this.context.authenticatedUser().validateHasPermissionTo("ACTIVATE_WHATSAPPCAMPAIGN");
            result = this.writePlatformService.activate(campaignId, apiRequestBodyAsJson);
        } else if (is(commandParam, "close")) {
            this.context.authenticatedUser().validateHasPermissionTo("CLOSE_WHATSAPPCAMPAIGN");
            result = this.writePlatformService.close(campaignId, apiRequestBodyAsJson);
        } else if (is(commandParam, "reactivate")) {
            this.context.authenticatedUser().validateHasPermissionTo("REACTIVATE_WHATSAPPCAMPAIGN");
            result = this.writePlatformService.reactivate(campaignId, apiRequestBodyAsJson);
        } else {
            result = CommandProcessingResult.empty();
        }
        return this.commandProcessingResultSerializer.serializeResult(result);
    }

    @POST
    @Path("preview")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Preview a WhatsApp Campaign message")
    public String preview(@Parameter(hidden = true) final String apiRequestBodyAsJson, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(WhatsAppCampaignConstants.RESOURCE_NAME);
        final WhatsAppPreviewData previewData = this.writePlatformService.preview(apiRequestBodyAsJson);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.previewSerializer.serialize(settings, previewData);
    }

    @DELETE
    @Path("{campaignId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a WhatsApp Campaign", description = "Note: Only closed WhatsApp Campaigns can be deleted")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public String delete(@PathParam("campaignId") final Long campaignId) {
        this.context.authenticatedUser().validateHasPermissionTo("DELETE_WHATSAPPCAMPAIGN");
        final CommandProcessingResult result = this.writePlatformService.delete(campaignId);
        return this.commandProcessingResultSerializer.serializeResult(result);
    }

    private boolean is(final String commandParam, final String commandValue) {
        return StringUtils.isNotBlank(commandParam) && commandParam.trim().equalsIgnoreCase(commandValue);
    }
}
