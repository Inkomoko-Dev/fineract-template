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

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
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
import org.apache.fineract.portfolio.client.data.ClientOtherInfoData;
import org.apache.fineract.portfolio.client.service.ClientOtherInfoReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientOtherInfoWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/clients/{clientId}/otherInfo")
@Component
@Scope("singleton")
@Tag(name = "Client Other Info", description = "")
public class ClientOtherInfoApiResources {

    private final Set<String> responseDataParameters = new HashSet<>(Arrays.asList("id", "clientId", "numberOfChildren",
            "numberOfDependents", "yearArrivedInHostCountry", "co_signors", "guarantor" ,"bankId", "bank"));
    private final String resourceNameForPermissions = "ClientOtherInfo";
    private final PlatformSecurityContext context;
    private final ClientOtherInfoReadPlatformService readPlatformService;
    private final ClientOtherInfoWritePlatformService writePlatformService;
    private final ToApiJsonSerializer<ClientOtherInfoData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    public ClientOtherInfoApiResources(final PlatformSecurityContext context, final ClientOtherInfoReadPlatformService readPlatformService,
            final ToApiJsonSerializer<ClientOtherInfoData> toApiJsonSerializer, final ApiRequestParameterHelper apiRequestParameterHelper,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            final ClientOtherInfoWritePlatformService writePlatformService) {
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
    public String template(@Context final UriInfo uriInfo) {
        ClientOtherInfoData templateData = this.readPlatformService.retrieveTemplate();
        return this.toApiJsonSerializer.serialize(templateData);
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveAllClientOtherInfo(@Context final UriInfo uriInfo, @PathParam("clientId") final long clientId) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final Collection<ClientOtherInfoData> otherInfoData = this.readPlatformService.retrieveAll(clientId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, otherInfoData, this.responseDataParameters);

    }

    @GET
    @Path("/{otherInfoId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveClientOtherInfo(@Context final UriInfo uriInfo, @PathParam("otherInfoId") final Long otherInfoId,
            @PathParam("clientId") final Long clientId) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final ClientOtherInfoData otherInfo = this.readPlatformService.retrieveOne(otherInfoId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, otherInfo, this.responseDataParameters);

    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String createClientOtherInfo(@PathParam("clientId") final long clientId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createClientOtherInfo(clientId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("/{otherInfoId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateClientOtherInfo(@PathParam("otherInfoId") final long otherInfoId, final String apiRequestBodyAsJson,
            @PathParam("clientId") final Long clientId) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateClientOtherInfo(otherInfoId, clientId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("/entity")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveAllClientOtherInfoEntity(@Context final UriInfo uriInfo, @PathParam("clientId") final long clientId) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final Collection<ClientOtherInfoData> otherInfoData = this.readPlatformService.retrieveEntityAll(clientId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, otherInfoData, this.responseDataParameters);

    }

    @GET
    @Path("/entity/{otherInfoId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveClientOtherInfoEntity(@Context final UriInfo uriInfo, @PathParam("otherInfoId") final Long otherInfoId,
            @PathParam("clientId") final Long clientId) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final ClientOtherInfoData otherInfo = this.readPlatformService.retrieveEntityOne(otherInfoId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, otherInfo, this.responseDataParameters);

    }
}
