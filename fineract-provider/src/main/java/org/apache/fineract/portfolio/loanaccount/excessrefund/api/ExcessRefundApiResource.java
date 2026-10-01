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

import java.util.Collection;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundData;
import org.apache.fineract.portfolio.loanaccount.excessrefund.service.ExcessRefundReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/excess-refunds")
@Component
@RequiredArgsConstructor
public class ExcessRefundApiResource {

    private final PlatformSecurityContext context;
    private final ExcessRefundReadPlatformService readPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<Object> toApiJsonSerializer;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveAll(@QueryParam("loanId") final Long loanId, @QueryParam("status") final Integer status,
            @QueryParam("batchId") final Long batchId) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_EXCESS_REFUND");
        final Collection<ExcessRefundData> results = this.readPlatformService.retrieveAll(loanId, status, batchId);
        return this.toApiJsonSerializer.serialize(results);
    }

    @GET
    @Path("{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveOne(@PathParam("id") final Long id) {
        return this.toApiJsonSerializer.serialize(this.readPlatformService.retrieveOne(id));
    }

    @GET
    @Path("export")
    @Produces({ "text/csv" })
    public Response export(@QueryParam("status") final Integer status) {
        final String csv = this.readPlatformService.exportCsv(status);
        return Response.ok(csv).header("Content-Disposition", "attachment; filename=excess-refunds.csv").build();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String create(final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createExcessRefund().withJson(apiRequestBodyAsJson).build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String command(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            final String apiRequestBodyAsJson) {
        final CommandWrapperBuilder builder = new CommandWrapperBuilder().withJson(StringUtils.defaultIfBlank(apiRequestBodyAsJson, "{}"));
        final CommandWrapper commandRequest;
        if (is(commandParam, "approve")) {
            commandRequest = builder.approveExcessRefund(id).build();
        } else if (is(commandParam, "reject")) {
            commandRequest = builder.rejectExcessRefund(id).build();
        } else if (is(commandParam, "cancel")) {
            commandRequest = builder.cancelExcessRefund(id).build();
        } else if (is(commandParam, "recordPayment")) {
            commandRequest = builder.recordExcessRefundPayment(id).build();
        } else if (is(commandParam, "sendToPaymentHub")) {
            commandRequest = builder.sendExcessRefundToPaymentHub(id).build();
        } else if (is(commandParam, "post")) {
            commandRequest = builder.postExcessRefund(id).build();
        } else {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.invalid.command",
                    "Unsupported excess refund command: " + commandParam);
        }
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return this.toApiJsonSerializer.serialize(result);
    }

    private boolean is(final String commandParam, final String commandValue) {
        return StringUtils.isNotBlank(commandParam) && commandParam.trim().equalsIgnoreCase(commandValue);
    }
}
