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
package org.apache.fineract.infrastructure.campaigns.email.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Collection;
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
import org.apache.fineract.accounting.journalentry.api.DateParam;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.campaigns.email.data.EmailData;
import org.apache.fineract.infrastructure.campaigns.email.service.EmailReadPlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/email")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Component
@Scope("singleton")
@Tag(name = "Email", description = "Email operations.")
public class EmailApiResource {

    private final String resourceNameForPermissions = "Email";
    private final PlatformSecurityContext context;
    private final EmailReadPlatformService readPlatformService;
    private final DefaultToApiJsonSerializer<EmailData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    public EmailApiResource(final PlatformSecurityContext context, final EmailReadPlatformService readPlatformService,
            final DefaultToApiJsonSerializer<EmailData> toApiJsonSerializer, final ApiRequestParameterHelper apiRequestParameterHelper,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
    }

    @Operation(summary = "Retrieve All Emails", description = "Retrieve All Emails.")
    @GET
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveAllEmails(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);
        final Collection<EmailData> emailMessages = this.readPlatformService.retrieveAll();
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, emailMessages);
    }

    @Operation(summary = "Retrieve Pending Email", description = "Retrieve Pending Email.")
    @GET
    @Path("pendingEmail")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrievePendingEmail(@QueryParam("sqlSearch") @Parameter(description = "sqlSearch") final String sqlSearch,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") final Integer limit,
            @QueryParam("orderBy") @Parameter(description = "orderBy") final String orderBy,
            @QueryParam("sortOrder") @Parameter(description = "sortOrder") final String sortOrder, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);
        final SearchParameters searchParameters = SearchParameters.forEmailCampaign(sqlSearch, offset, limit, orderBy, sortOrder);
        Collection<EmailData> emailMessages = this.readPlatformService.retrieveAllPending(searchParameters);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, emailMessages);
    }

    @Operation(summary = "Retrieve Sent Email", description = "Retrieve Sent Email.")
    @GET
    @Path("sentEmail")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveSentEmail(@QueryParam("sqlSearch") @Parameter(description = "sqlSearch") final String sqlSearch,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") final Integer limit,
            @QueryParam("orderBy") @Parameter(description = "orderBy") final String orderBy,
            @QueryParam("sortOrder") @Parameter(description = "sortOrder") final String sortOrder, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final SearchParameters searchParameters = SearchParameters.forEmailCampaign(sqlSearch, offset, limit, orderBy, sortOrder);
        Collection<EmailData> emailMessages = this.readPlatformService.retrieveAllSent(searchParameters);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, emailMessages);
    }

    @Operation(summary = "Retrieve All Email By Status", description = "Retrieve All Email By Status.")
    @GET
    @Path("messageByStatus")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveAllEmailByStatus(@QueryParam("sqlSearch") @Parameter(description = "sqlSearch") final String sqlSearch,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") final Integer limit,
            @QueryParam("status") @Parameter(description = "status") final Integer status,
            @QueryParam("orderBy") @Parameter(description = "orderBy") final String orderBy,
            @QueryParam("sortOrder") @Parameter(description = "sortOrder") final String sortOrder,
            @QueryParam("fromDate") @Parameter(description = "fromDate") final DateParam fromDateParam,
            @QueryParam("toDate") @Parameter(description = "toDate") final DateParam toDateParam,
            @QueryParam("locale") @Parameter(description = "locale") final String locale,
            @QueryParam("dateFormat") @Parameter(description = "dateFormat") final String dateFormat, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);
        LocalDate fromDate = null;
        if (fromDateParam != null) {
            fromDate = fromDateParam.getDate("fromDate", dateFormat, locale);
        }
        LocalDate toDate = null;
        if (toDateParam != null) {
            toDate = toDateParam.getDate("toDate", dateFormat, locale);
        }
        Page<EmailData> emailMessages = this.readPlatformService.retrieveEmailByStatus(limit, status, fromDate, toDate);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, emailMessages);
    }

    @Operation(summary = "Retrieve Failed Email", description = "Retrieve Failed Email.")
    @GET
    @Path("failedEmail")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveFailedEmail(@QueryParam("sqlSearch") @Parameter(description = "sqlSearch") final String sqlSearch,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") final Integer limit,
            @QueryParam("orderBy") @Parameter(description = "orderBy") final String orderBy,
            @QueryParam("sortOrder") @Parameter(description = "sortOrder") final String sortOrder, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final SearchParameters searchParameters = SearchParameters.forEmailCampaign(sqlSearch, offset, limit, orderBy, sortOrder);
        Collection<EmailData> emailMessages = this.readPlatformService.retrieveAllFailed(searchParameters);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, emailMessages);
    }

    @Operation(summary = "Create", description = "Create.")
    @POST
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String create(final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createEmail().withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @Operation(summary = "Retrieve One", description = "Retrieve One.")
    @GET
    @Path("{resourceId}")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveOne(@PathParam("resourceId") @Parameter(description = "resourceId") final Long resourceId,
            @Context final UriInfo uriInfo) {

        final EmailData emailMessage = this.readPlatformService.retrieveOne(resourceId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, emailMessage);
    }

    @Operation(summary = "Update", description = "Update.")
    @PUT
    @Path("{resourceId}")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String update(@PathParam("resourceId") @Parameter(description = "resourceId") final Long resourceId,
            final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateEmail(resourceId).withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @Operation(summary = "Delete", description = "Delete.")
    @DELETE
    @Path("{resourceId}")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String delete(@PathParam("resourceId") @Parameter(description = "resourceId") final Long resourceId) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteEmail(resourceId).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }
}
