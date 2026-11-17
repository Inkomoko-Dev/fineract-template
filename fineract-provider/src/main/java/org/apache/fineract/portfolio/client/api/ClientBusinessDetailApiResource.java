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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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
import org.apache.fineract.portfolio.client.data.ClientBusinessDetailData;
import org.apache.fineract.portfolio.client.service.BusinessDetailReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/clients/{clientId}/businessDetail")
@Component
@Scope("singleton")
@Tag(name = "Client Business Detail", description = "This API is responsible on creating Business Details for Client")
public class ClientBusinessDetailApiResource {

    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final ToApiJsonSerializer<ClientBusinessDetailData> toApiJsonSerializer;
    private final PlatformSecurityContext context;
    private final BusinessDetailReadPlatformService businessDetailReadPlatformService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @Autowired
    public ClientBusinessDetailApiResource(PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            ToApiJsonSerializer<ClientBusinessDetailData> toApiJsonSerializer, PlatformSecurityContext context,
            BusinessDetailReadPlatformService businessDetailReadPlatformService, ApiRequestParameterHelper apiRequestParameterHelper) {
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.context = context;
        this.businessDetailReadPlatformService = businessDetailReadPlatformService;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
    }

    @GET
    @Path("template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Client Business Details Template", description = "This is a convenience resource. It can be useful when building maintenance user interface screens for client business details applications. The template data returned consists of any or all of:\n"
            + "\n" + "Field Defaults\n" + "Allowed Value Lists\n\n" + "Example Request:\n" + "\n" + "clients/template")
    public String retrieveTemplate(@Context final UriInfo uriInfo, @PathParam("clientId") final long clientId) {

        this.context.authenticatedUser().validateHasReadPermission(ClientApiConstants.READ_CLIENTBUSINESSDETAIL);

        ClientBusinessDetailData clientBusinessDetailData = null;
        clientBusinessDetailData = this.businessDetailReadPlatformService.retrieveTemplate(clientId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, clientBusinessDetailData,
                ClientApiConstants.CLIENT_BUSINESS_DETAIL_RESPONSE_REQUEST_DATA_PARAMETERS);
    }

    @Operation(summary = "Add Business Detail", description = "Add Business Detail.")
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String addBusinessDetail(@PathParam("clientId") @Parameter(description = "clientId") final long clientId,
            final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().addBusinessDetail(clientId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("{businessDetailId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Client Business Details by Id", description = "This is a convenience resource. It can be useful when building maintenance user interface screens for client business details applications. The data returned consists of any or all of:\n"
            + "\n" + "Field Defaults\n" + "Allowed Value Lists\n\n" + "Example Request:\n" + "\n" + "clients/template")
    public String retrieveBusinessDetail(@Context final UriInfo uriInfo, @PathParam("clientId") final long clientId,
            @PathParam("businessDetailId") final long businessDetailId) {

        this.context.authenticatedUser().validateHasReadPermission(ClientApiConstants.READ_CLIENTBUSINESSDETAIL);

        ClientBusinessDetailData clientBusinessDetailData = this.businessDetailReadPlatformService.retrieveBusinessDetail(clientId,
                businessDetailId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, clientBusinessDetailData,
                ClientApiConstants.CLIENT_BUSINESS_DETAIL_RESPONSE_REQUEST_DATA_PARAMETERS);
    }

    @DELETE
    @Path("{businessDetailId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a Client Business Detail", description = "Delete a Client Business Detail")
    public String deleteBusinessDetail(@PathParam("businessDetailId") final Long businessDetailId,
            @PathParam("clientId") final Long clientId) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteBusinessDetail(clientId, businessDetailId).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("{businessDetailId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a Client Business Detail", description = "Update a Client Business Detail")
    public String update(@PathParam("clientId") final Long clientId, @PathParam("businessDetailId") final Long businessDetailId,
            final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder() //
                .updateBusinessDetail(clientId, businessDetailId) //
                .withJson(apiRequestBodyAsJson) //
                .build(); //

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

}
