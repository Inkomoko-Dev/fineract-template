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
package org.apache.fineract.portfolio.loanaccount.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementData;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/loans/{loanId}/disbursements")
@Component
@Scope("singleton")
@Tag(name = "Loan Disbursement Details", description = "")
public class LoanDisbursementDetailApiResource {

    private final Set<String> responseDataParameters = new HashSet<>(
            Arrays.asList("id", "expectedDisbursementDate", "actualDisbursementDate", "principal", "approvedPrincipal"));

    private final String resourceNameForPermissions = "LOAN";

    private final DefaultToApiJsonSerializer<DisbursementData> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final PlatformSecurityContext context;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final LoanReadPlatformService loanReadPlatformService;

    @Autowired
    public LoanDisbursementDetailApiResource(final DefaultToApiJsonSerializer<DisbursementData> toApiJsonSerializer,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService, final PlatformSecurityContext context,
            final ApiRequestParameterHelper apiRequestParameterHelper, final LoanReadPlatformService loanReadPlatformService) {
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.context = context;
        this.loanReadPlatformService = loanReadPlatformService;
    }

    @Operation(summary = "Update Disbursement Date", description = "Update Disbursement Date.")
    @PUT
    @Path("{disbursementId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String updateDisbursementDate(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId,
            @PathParam("disbursementId") @Parameter(description = "disbursementId") final Long disbursementId,
            final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateDisbusementDate(loanId, disbursementId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @Operation(summary = "Add And Delete Disbursement Detail", description = "Add And Delete Disbursement Detail.")
    @PUT
    @Path("editDisbursements")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String addAndDeleteDisbursementDetail(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId,
            final String apiRequestBodyAsJson) {

        CommandWrapper commandRequest = new CommandWrapperBuilder().addAndDeleteDisbursementDetails(loanId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @Operation(summary = "Retrive Detail", description = "Retrive Detail.")
    @GET
    @Path("{disbursementId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retriveDetail(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId,
            @PathParam("disbursementId") @Parameter(description = "disbursementId") final Long disbursementId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final DisbursementData disbursementData = this.loanReadPlatformService.retrieveLoanDisbursementDetail(loanId, disbursementId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, disbursementData, this.responseDataParameters);
    }

}
