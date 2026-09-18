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
package org.apache.fineract.portfolio.loanclassification.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationAuditData;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationData;
import org.apache.fineract.portfolio.loanclassification.service.LoanClassificationReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/loans/{loanId}/classification")
@Component
@Scope("singleton")
@Tag(name = "Loan Classification", description = "Per-loan classification and override")
public class LoanClassificationApiResource {

    private final PlatformSecurityContext context;
    private final LoanClassificationReadPlatformService readPlatformService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final DefaultToApiJsonSerializer<Object> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    public LoanClassificationApiResource(final PlatformSecurityContext context,
            final LoanClassificationReadPlatformService readPlatformService, final ApiRequestParameterHelper apiRequestParameterHelper,
            final DefaultToApiJsonSerializer<Object> toApiJsonSerializer,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieve(@PathParam("loanId") final Long loanId, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_LOANCLASSIFICATION");
        final LoanClassificationData data = this.readPlatformService.retrieveLoanClassification(loanId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, data);
    }

    @GET
    @Path("audit")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveAudit(@PathParam("loanId") final Long loanId, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_LOANCLASSIFICATION");
        final Collection<LoanClassificationAuditData> data = this.readPlatformService.retrieveLoanAudit(loanId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, data);
    }

    @POST
    @Path("override")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String override(@PathParam("loanId") final Long loanId, final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo("OVERRIDE_LOANCLASSIFICATION");
        final CommandWrapper command = new CommandWrapperBuilder().overrideLoanClassification(loanId).withJson(apiRequestBodyAsJson)
                .build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(command);
        return this.toApiJsonSerializer.serialize(result);
    }
}
