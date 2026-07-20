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
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionApiConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("/loans/disbursement-instruction")
@Component
@Tag(name = "Loans", description = "Kifiya disbursement instruction callback")
public class DisbursementInstructionApiResource {

    private final PlatformSecurityContext context;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;

    @Autowired
    public DisbursementInstructionApiResource(final PlatformSecurityContext context,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            final DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer) {
        this.context = context;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create Kifiya disbursement instruction",
            description = "Validates supplier payment details and creates a disbursement request in CBS for the specified loan")
    public Response createDisbursementInstruction(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createDisbursementInstruction().withJson(apiRequestBodyAsJson)
                .build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return Response.status(Response.Status.CREATED).entity(this.toApiJsonSerializer.serialize(flattenResponse(result))).build();
    }

    private static Map<String, Object> flattenResponse(final CommandProcessingResult result) {
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("resourceId", result.resourceId());
        response.put("loanId", result.getLoanId());
        final Map<String, Object> changes = result.getChanges();
        if (changes != null) {
            response.putAll(changes);
        }
        return response;
    }
}
