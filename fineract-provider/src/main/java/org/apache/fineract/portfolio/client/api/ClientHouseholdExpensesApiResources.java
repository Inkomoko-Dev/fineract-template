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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientHouseholdExpensesData;
import org.apache.fineract.portfolio.client.service.ClientHouseholdExpensesReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/clients/{clientId}/householdExpenses")
@Component
@Tag(name = "Client Household Expenses", description = "")
@RequiredArgsConstructor
public class ClientHouseholdExpensesApiResources {

    private final PlatformSecurityContext context;
    private final ClientHouseholdExpensesReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<ClientHouseholdExpensesData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    private final CodeValueReadPlatformService codeValueReadPlatformService;

    @Operation(summary = "Get Client Household Expenses", description = "Get Client Household Expenses.")
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String getClientHouseholdExpenses(@Context final UriInfo uriInfo,
            @PathParam("clientId") @Parameter(description = "clientId") final long clientId) {

        this.context.authenticatedUser().validateHasReadPermission(ClientApiConstants.CLIENT_HOUSEHOLD_EXPENSES_RESOURCE_NAME);

        final ClientHouseholdExpensesData clientHouseholdExpenses = this.readPlatformService.getClientHouseholdExpenses(clientId);
        final Collection<CodeValueData> codeValues = this.codeValueReadPlatformService
                .retrieveCodeValuesByCode(ClientApiConstants.OTHER_HOUSEHOLD_EXPENSES);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings,
                ClientHouseholdExpensesData.withOtherExpensesTypes(clientHouseholdExpenses, codeValues),
                ClientApiConstants.CLIENT_HOUSEHOLD_EXPENSES_DATA_PARAMETERS);

    }

    @Operation(summary = "Update Client Household Expenses", description = "Update Client Household Expenses.")
    @PUT
    @Path("/{householdExpenseId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String updateClientHouseholdExpenses(
            @PathParam("householdExpenseId") @Parameter(description = "householdExpenseId") final long householdExpenseId,
            final String apiRequestBodyAsJson, @PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateHouseholdExpenses(householdExpenseId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @Operation(summary = "Add Client Household Expenses", description = "Add Client Household Expenses.")
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String addClientHouseholdExpenses(@PathParam("clientId") @Parameter(description = "clientId") final long clientId,
            final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().addHouseholdExpenses(clientId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @Operation(summary = "Delete Client Household Expenses", description = "Delete Client Household Expenses.")
    @DELETE
    @Path("/{householdExpenseId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String deleteClientHouseholdExpenses(
            @PathParam("householdExpenseId") @Parameter(description = "householdExpenseId") final long householdExpenseId,
            final String apiRequestBodyAsJson, @PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteHouseholdExpenses(householdExpenseId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

}
