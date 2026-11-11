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

package org.apache.fineract.portfolio.client.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientRecruitmentSurveyData;
import org.apache.fineract.portfolio.client.service.ClientRecruitmentSurveyReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientRecruitmentSurveyWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/clients/{clientId}/recruitmentSurvey")
@Component
@Scope("singleton")
@Tag(name = "Client Recruitment Survey", description = "")
public class ClientRecruitmentSurveyApiResources {

    private final String resourceNameForPermissions = "ClientRecruitmentSurvey";
    private final PlatformSecurityContext context;
    private final ClientRecruitmentSurveyReadPlatformService readPlatformService;
    private final ClientRecruitmentSurveyWritePlatformService writePlatformService;
    private final ToApiJsonSerializer<ClientRecruitmentSurveyData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    public ClientRecruitmentSurveyApiResources(final PlatformSecurityContext context,
            final ClientRecruitmentSurveyReadPlatformService readPlatformService,
            final ToApiJsonSerializer<ClientRecruitmentSurveyData> toApiJsonSerializer,
            final ApiRequestParameterHelper apiRequestParameterHelper,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            final ClientRecruitmentSurveyWritePlatformService writePlatformService) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.writePlatformService = writePlatformService;

    }

    @GET
    @Path("template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Client Recruitment Survey Template", description = "This is a convenience resource. It can be useful when building maintenance user interface screens for client recruitment survey applications. The template data returned consists of any or all of:\n"
            + "\n" + "Field Defaults\n" + "Allowed Value Lists\n\n" + "Example Request:\n" + "\n"
            + "clients/{clientId}/recruitmentSurvey/template")
    public String template(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        ClientRecruitmentSurveyData templateData = this.readPlatformService.retrieveTemplate();
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, templateData,
                ClientApiConstants.CLIENT_RECRUITMENT_SURVEY_RESPONSE_REQUEST_PARAMETER);
    }

    @Operation(summary = "Retrieve All Client Recruitment Survey", description = "Retrieve All Client Recruitment Survey.")
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveAllClientRecruitmentSurvey(@Context final UriInfo uriInfo,
            @PathParam("clientId") @Parameter(description = "clientId") final long clientId) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final Collection<ClientRecruitmentSurveyData> surveyData = this.readPlatformService.retrieveAll(clientId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, surveyData,
                ClientApiConstants.CLIENT_RECRUITMENT_SURVEY_RESPONSE_REQUEST_PARAMETER);

    }

    @Operation(summary = "Retrieve Client Recruitment Survey", description = "Retrieve Client Recruitment Survey.")
    @GET
    @Path("/{surveyId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveClientRecruitmentSurvey(@Context final UriInfo uriInfo,
            @PathParam("surveyId") @Parameter(description = "surveyId") final Long surveyId,
            @PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        ClientRecruitmentSurveyData surveyData = this.readPlatformService.retrieveOne(surveyId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        if (settings.isTemplate()) {
            ClientRecruitmentSurveyData templateData = this.readPlatformService.retrieveTemplate();
            surveyData = ClientRecruitmentSurveyData.templateWithData(surveyData, templateData);
        }

        return this.toApiJsonSerializer.serialize(settings, surveyData,
                ClientApiConstants.CLIENT_RECRUITMENT_SURVEY_RESPONSE_REQUEST_PARAMETER);

    }

    @Operation(summary = "Create Client Recruitment Survey", description = "Create Client Recruitment Survey.")
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String createClientRecruitmentSurvey(@PathParam("clientId") @Parameter(description = "clientId") final long clientId,
            final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createClientRecruitmentSurvey(clientId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @Operation(summary = "Update Client Other Info", description = "Update Client Other Info.")
    @PUT
    @Path("/{surveyId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String updateClientOtherInfo(@PathParam("surveyId") @Parameter(description = "surveyId") final long surveyId,
            final String apiRequestBodyAsJson, @PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateClientRecruitmentSurvey(surveyId, clientId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }
}
