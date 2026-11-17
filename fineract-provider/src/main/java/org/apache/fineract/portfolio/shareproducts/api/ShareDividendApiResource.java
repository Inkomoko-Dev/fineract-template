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
package org.apache.fineract.portfolio.shareproducts.api;

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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.shareaccounts.data.ShareAccountDividendData;
import org.apache.fineract.portfolio.shareaccounts.service.ShareAccountDividendReadPlatformService;
import org.apache.fineract.portfolio.shareproducts.data.ShareProductDividendPayOutData;
import org.apache.fineract.portfolio.shareproducts.service.ShareProductDividendReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/shareproduct/{productId}/dividend")
@Component
@Scope("singleton")

@Tag(name = "Self Dividend", description = "")
public class ShareDividendApiResource {

    private final DefaultToApiJsonSerializer<ShareProductDividendPayOutData> toApiJsonSerializer;
    private final DefaultToApiJsonSerializer<ShareAccountDividendData> toApiAccountDetailJsonSerializer;
    private final PlatformSecurityContext platformSecurityContext;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final ShareAccountDividendReadPlatformService shareAccountDividendReadPlatformService;
    private final ShareProductDividendReadPlatformService shareProductDividendReadPlatformService;
    private final String resourceNameForPermissions = "DIVIDEND_SHAREPRODUCT";

    @Autowired
    public ShareDividendApiResource(final DefaultToApiJsonSerializer<ShareProductDividendPayOutData> toApiJsonSerializer,
            final PlatformSecurityContext platformSecurityContext,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            final DefaultToApiJsonSerializer<ShareAccountDividendData> toApiDividendsJsonSerializer,
            final ShareAccountDividendReadPlatformService shareAccountDividendReadPlatformService,
            final ShareProductDividendReadPlatformService shareProductDividendReadPlatformService) {
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.platformSecurityContext = platformSecurityContext;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.toApiAccountDetailJsonSerializer = toApiDividendsJsonSerializer;
        this.shareAccountDividendReadPlatformService = shareAccountDividendReadPlatformService;
        this.shareProductDividendReadPlatformService = shareProductDividendReadPlatformService;
    }

    @Operation(summary = "Retrieve All", description = "Retrieve All.")
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveAll(@PathParam("productId") @Parameter(description = "productId") final Long productId,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") final Integer limit,
            @QueryParam("orderBy") @Parameter(description = "orderBy") final String orderBy,
            @QueryParam("sortOrder") @Parameter(description = "sortOrder") final String sortOrder,
            @QueryParam("status") @Parameter(description = "status") final Integer status) {

        this.platformSecurityContext.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);
        final SearchParameters searchParameters = SearchParameters.forPagination(offset, limit, orderBy, sortOrder);
        Page<ShareProductDividendPayOutData> dividendPayoutDetails = this.shareProductDividendReadPlatformService.retriveAll(productId,
                status, searchParameters);
        return this.toApiJsonSerializer.serialize(dividendPayoutDetails);
    }

    @Operation(summary = "Retrieve Dividend Details", description = "Retrieve Dividend Details.")
    @GET
    @Path("{dividendId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveDividendDetails(@PathParam("dividendId") @Parameter(description = "dividendId") final Long dividendId,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") final Integer limit,
            @QueryParam("orderBy") @Parameter(description = "orderBy") final String orderBy,
            @QueryParam("sortOrder") @Parameter(description = "sortOrder") final String sortOrder,
            @QueryParam("accountNo") @Parameter(description = "accountNo") final String accountNo,
            @PathParam("productId") @Parameter(description = "productId") final Long productId) {

        this.platformSecurityContext.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);
        final SearchParameters searchParameters = SearchParameters.forPaginationAndAccountNumberSearch(offset, limit, orderBy, sortOrder,
                accountNo);
        Page<ShareAccountDividendData> dividendDetails = this.shareAccountDividendReadPlatformService.retriveAll(dividendId,
                searchParameters);
        return this.toApiAccountDetailJsonSerializer.serialize(dividendDetails);
    }

    @Operation(summary = "Create Dividend Detail", description = "Create Dividend Detail.")
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String createDividendDetail(@PathParam("productId") @Parameter(description = "productId") final Long productId,
            final String apiRequestBodyAsJson) {
        this.platformSecurityContext.authenticatedUser();
        CommandWrapper commandWrapper = new CommandWrapperBuilder().createShareProductDividendPayoutCommand(productId)
                .withJson(apiRequestBodyAsJson).build();
        final CommandProcessingResult commandProcessingResult = this.commandsSourceWritePlatformService.logCommandSource(commandWrapper);
        return this.toApiJsonSerializer.serialize(commandProcessingResult);
    }

    @Operation(summary = "Update Dividend Detail", description = "Update Dividend Detail.")
    @PUT
    @Path("{dividendId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String updateDividendDetail(@PathParam("productId") @Parameter(description = "productId") final Long productId,
            @PathParam("dividendId") @Parameter(description = "dividendId") final Long dividendId,
            @QueryParam("command") @Parameter(description = "command") final String commandParam, final String apiRequestBodyAsJson) {
        CommandWrapper commandWrapper = null;
        this.platformSecurityContext.authenticatedUser();
        if (is(commandParam, "approve")) {
            commandWrapper = new CommandWrapperBuilder().approveShareProductDividendPayoutCommand(productId, dividendId)
                    .withJson(apiRequestBodyAsJson).build();
        } else {
            throw new UnrecognizedQueryParamException("command", commandParam);
        }
        final CommandProcessingResult commandProcessingResult = this.commandsSourceWritePlatformService.logCommandSource(commandWrapper);
        return this.toApiJsonSerializer.serialize(commandProcessingResult);
    }

    @Operation(summary = "Delete Dividend Detail", description = "Delete Dividend Detail.")
    @DELETE
    @Path("{dividendId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String deleteDividendDetail(@PathParam("productId") @Parameter(description = "productId") final Long productId,
            @PathParam("dividendId") @Parameter(description = "dividendId") final Long dividendId) {
        this.platformSecurityContext.authenticatedUser();
        final CommandWrapper commandWrapper = new CommandWrapperBuilder().deleteShareProductDividendPayoutCommand(productId, dividendId)
                .build();
        final CommandProcessingResult commandProcessingResult = this.commandsSourceWritePlatformService.logCommandSource(commandWrapper);
        return this.toApiJsonSerializer.serialize(commandProcessingResult);
    }

    private boolean is(final String commandParam, final String commandValue) {
        return StringUtils.isNotBlank(commandParam) && commandParam.trim().equalsIgnoreCase(commandValue);
    }

}
