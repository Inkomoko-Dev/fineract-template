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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.service.TrancheMilestoneEligibilityService;
import org.springframework.stereotype.Component;

@Path("/loans/{loanKey}/tranche-milestones")
@Component
@RequiredArgsConstructor
public class TrancheMilestoneApiResource {

    private final PlatformSecurityContext context;
    private final TrancheMilestoneEligibilityService eligibilityService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> toApiJsonSerializer;

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String submit(@PathParam("loanKey") final String loanKey, final String json) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createTrancheMilestone(loanKey).withJson(body(json)).build();
        return response(this.commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }

    @POST
    @Path("deadline-exception")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String approveDeadlineException(@PathParam("loanKey") final String loanKey, final String json) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().approveTrancheMilestone(loanKey).withJson(body(json)).build();
        return response(this.commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String read(@PathParam("loanKey") final String loanKey) {
        this.context.authenticatedUser().validateHasReadPermission("LOAN");
        return this.eligibilityService.read(loanKey);
    }

    private String response(final CommandProcessingResult result) {
        if (result.getResourceIdentifier() != null) {
            return result.getResourceIdentifier();
        }
        return this.toApiJsonSerializer.serialize(result);
    }

    private static String body(final String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }
}
