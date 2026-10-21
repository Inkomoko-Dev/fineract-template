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
package org.apache.fineract.infrastructure.creditbureau.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.creditbureau.domain.TransUnionCreditReportCsvData;
import org.apache.fineract.infrastructure.creditbureau.service.CreditBureauReadPlatformService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.CRBPostingLoggerData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Path("/crb/posting-logs")
@Component
@Scope("singleton")
@Tag(name = "CRB Posting", description = "Generate crb posting report status")
public class CRBPostingAPIResource {

    private final PlatformSecurityContext context;
    private final ToApiJsonSerializer<CRBPostingLoggerData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final CreditBureauReadPlatformService crbReadPlatformService;

    private final Set<String> responseDataParameters = new HashSet<>(
            Arrays.asList("id", "batchId", "hasPassed", "loanId", "loanAccountNumber", "crbResponseId", "errorLogs", "payload", "date"));

    @Autowired
    public CRBPostingAPIResource(PlatformSecurityContext context, ToApiJsonSerializer<CRBPostingLoggerData> toApiJsonSerializer,
            ApiRequestParameterHelper apiRequestParameterHelper, CreditBureauReadPlatformService crbReadPlatformService) {
        this.context = context;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.crbReadPlatformService = crbReadPlatformService;
    }

    @Operation(summary = "Retrieve All CRBPosting Logs", description = "Retrieve All CRBPosting Logs.")
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveAllCRBPostingLogs(@Context final UriInfo uriInfo,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") final Integer limit) {

        String resourceNameForPermissions = "VIEW_CRB_LOGGER";

        this.context.authenticatedUser().validateHasReadPermission(resourceNameForPermissions);

        final Collection<CRBPostingLoggerData> result = crbReadPlatformService.retrieveCrbPostingLogs(offset, limit);
        log.debug("Retrieved {} CRB posting log row(s)", result.size());

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.toApiJsonSerializer.serialize(settings, result, this.responseDataParameters);
    }

    @Operation(summary = "Mark Log Has Fixed", description = "Mark Log Has Fixed.")
    @POST
    @Path("/{loanId}/mark-fixed")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String markLogHasFixed(@PathParam("loanId") @Parameter(description = "loanId") final Integer loanId,
            @Context final UriInfo uriInfo) {
        String resourceNameForPermissions = "VIEW_CRB_LOGGER";
        this.context.authenticatedUser().validateHasReadPermission(resourceNameForPermissions);
        log.debug("Marking CRB posting log as fixed for loanId {}", loanId);

        crbReadPlatformService.markCRBLogAsFixed(loanId.toString());

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.toApiJsonSerializer.serialize(null);

    }

    @Operation(summary = "Export Logs To CSV", description = "Export Logs To CSV.")
    @GET
    @Path("/export")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public Response exportLogsToCSV(@Context final UriInfo uriInfo) {

        String resourceNameForPermissions = "VIEW_CRB_LOGGER";

        this.context.authenticatedUser().validateHasReadPermission(resourceNameForPermissions);

        log.debug("Exporting CRB posting logs to CSV");

        TransUnionCreditReportCsvData fileData = this.crbReadPlatformService.generateCsvReport(uriInfo.getQueryParameters());

        return Response.ok(fileData.getInputStream(), fileData.getContentType())
                .header("Content-Disposition", "attachment; filename=\"" + fileData.getFileName() + "\"").build();

    }

}
