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
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverData;
import org.apache.fineract.portfolio.loanaccount.service.HistoricalPenaltyWaiverReadPlatformService;
import org.springframework.stereotype.Component;

/**
 * Approval queue for historical penalty waivers (CGLT-656). Submission lives on the loan charge resource, next to the
 * standard waiver; only the decision endpoints belong here.
 */
@Path("/loans/historicalpenaltywaivers")
@Component
@RequiredArgsConstructor
@Tag(name = "Historical Penalty Waivers", description = "Approve or reject a controlled historical penalty waiver")
public class HistoricalPenaltyWaiverApiResource {

    public static final String READ_PERMISSION = "READ_HISTORICALPENALTYWAIVER";

    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> toApiJsonSerializer;
    private final DefaultToApiJsonSerializer<HistoricalPenaltyWaiverData> waiverSerializer;
    private final HistoricalPenaltyWaiverReadPlatformService readPlatformService;
    private final PlatformSecurityContext context;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List historical penalty waivers awaiting approval")
    public String retrievePendingApprovalQueue() {
        this.context.authenticatedUser().validateHasPermissionTo(READ_PERMISSION);
        return this.waiverSerializer.serialize(this.readPlatformService.retrievePendingApprovalQueue());
    }

    @GET
    @Path("{waiverId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve one historical penalty waiver with the transactions it touched")
    public String retrieveOne(@PathParam("waiverId") @Parameter(description = "waiverId") final Long waiverId) {
        this.context.authenticatedUser().validateHasPermissionTo(READ_PERMISSION);
        return this.waiverSerializer.serialize(this.readPlatformService.retrieveOne(waiverId));
    }

    @GET
    @Path("loans/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Correction history for a loan")
    public String retrieveByLoan(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId) {
        this.context.authenticatedUser().validateHasPermissionTo(READ_PERMISSION);
        return this.waiverSerializer.serialize(this.readPlatformService.retrieveByLoanId(loanId));
    }

    @POST
    @Path("{waiverId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Approve or reject a historical penalty waiver")
    public String decide(@PathParam("waiverId") @Parameter(description = "waiverId") final Long waiverId,
            @QueryParam("command") @Parameter(description = "command") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapperBuilder builder = new CommandWrapperBuilder().withJson(apiRequestBodyAsJson);

        final CommandWrapper commandRequest;
        if (is(commandParam, "approve")) {
            commandRequest = builder.approveHistoricalPenaltyWaiver(waiverId).build();
        } else if (is(commandParam, "reject")) {
            commandRequest = builder.rejectHistoricalPenaltyWaiver(waiverId).build();
        } else {
            throw new UnrecognizedQueryParamException("command", commandParam);
        }

        return this.toApiJsonSerializer.serialize(this.commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }

    private boolean is(final String commandParam, final String commandValue) {
        return commandParam != null && commandParam.trim().equalsIgnoreCase(commandValue);
    }
}
