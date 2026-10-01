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
package org.apache.fineract.portfolio.loanaccount.excessrefund.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.springframework.stereotype.Component;

@Path("/excess-refund-batches")
@Component
@RequiredArgsConstructor
public class ExcessRefundBatchApiResource {

    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> toApiJsonSerializer;

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String create(final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createExcessRefundBatch().withJson(apiRequestBodyAsJson).build();
        return this.toApiJsonSerializer.serialize(this.commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }

    @POST
    @Path("{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String command(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            final String apiRequestBodyAsJson) {
        final CommandWrapperBuilder builder = new CommandWrapperBuilder().withJson(StringUtils.defaultIfBlank(apiRequestBodyAsJson, "{}"));
        final CommandWrapper commandRequest;
        if ("approve".equalsIgnoreCase(commandParam)) {
            commandRequest = builder.approveExcessRefundBatch(id).build();
        } else if ("reject".equalsIgnoreCase(commandParam)) {
            commandRequest = builder.rejectExcessRefundBatch(id).build();
        } else {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.invalid.command",
                    "Unsupported excess refund batch command: " + commandParam);
        }
        return this.toApiJsonSerializer.serialize(this.commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }
}
