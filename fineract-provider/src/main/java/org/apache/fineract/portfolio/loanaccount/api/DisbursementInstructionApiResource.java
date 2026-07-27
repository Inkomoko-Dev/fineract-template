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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionData;
import org.apache.fineract.portfolio.loanaccount.service.DisbursementInstructionReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("/loans/disbursement-instruction")
@Component
@Tag(name = "Loans", description = "Partner disbursement instruction callback and status")
public class DisbursementInstructionApiResource {

    private final PlatformSecurityContext context;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DisbursementInstructionReadPlatformService readPlatformService;
    private final DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;
    private final DefaultToApiJsonSerializer<DisbursementInstructionData> instructionToApiJsonSerializer;

    @Autowired
    public DisbursementInstructionApiResource(final PlatformSecurityContext context,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            final DisbursementInstructionReadPlatformService readPlatformService,
            final DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer,
            final DefaultToApiJsonSerializer<DisbursementInstructionData> instructionToApiJsonSerializer) {
        this.context = context;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.readPlatformService = readPlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.instructionToApiJsonSerializer = instructionToApiJsonSerializer;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create disbursement instruction",
            description = "Requires Idempotency-Key header. Replays with the same key return HTTP 200 without re-applying side effects.")
    public Response createDisbursementInstruction(
            @HeaderParam(DisbursementInstructionApiConstants.IDEMPOTENCY_KEY_HEADER) final String idempotencyKey,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);
        final String normalizedKey = StringUtils.trimToNull(idempotencyKey);
        if (normalizedKey == null) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.idempotencyKey.required",
                    "Idempotency-Key header is required.",
                    List.of(ApiParameterError.parameterError("validation.msg.disbursementInstruction.idempotencyKey.required",
                            "Idempotency-Key header is required.", DisbursementInstructionApiConstants.IDEMPOTENCY_KEY_HEADER)));
        }
        final String jsonWithKey = injectIdempotencyKey(apiRequestBodyAsJson, normalizedKey);
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createDisbursementInstruction().withJson(jsonWithKey).build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        final Map<String, Object> body = flattenResponse(result);
        final boolean replayed = Boolean.TRUE.equals(body.get(DisbursementInstructionApiConstants.REPLAYED));
        final Response.Status status = replayed ? Response.Status.OK : Response.Status.CREATED;
        return Response.status(status).entity(this.toApiJsonSerializer.serialize(body)).build();
    }

    @GET
    @Path("{instructionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve disbursement instruction by id")
    public String retrieveOne(@PathParam("instructionId") @Parameter(description = "instructionId") final Long instructionId) {
        this.context.authenticatedUser().validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);
        final DisbursementInstructionData data = this.readPlatformService.retrieveOne(instructionId);
        return this.instructionToApiJsonSerializer.serialize(data);
    }

    static String injectIdempotencyKey(final String apiRequestBodyAsJson, final String idempotencyKey) {
        final JsonObject json = StringUtils.isBlank(apiRequestBodyAsJson) ? new JsonObject()
                : JsonParser.parseString(apiRequestBodyAsJson).getAsJsonObject();
        json.addProperty(DisbursementInstructionApiConstants.IDEMPOTENCY_KEY, idempotencyKey);
        return json.toString();
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
