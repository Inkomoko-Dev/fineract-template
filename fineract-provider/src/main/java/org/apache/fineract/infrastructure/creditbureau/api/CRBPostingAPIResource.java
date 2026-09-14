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

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.PaginationParameters;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.creditbureau.domain.TransUnionCreditReportCsvData;
import org.apache.fineract.infrastructure.creditbureau.service.CreditBureauReadPlatformService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.CRBPostingLoggerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;


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
    private static final Logger LOG = LoggerFactory.getLogger(CRBPostingAPIResource.class);

    private final Set<String> responseDataParameters = new HashSet<>(Arrays.asList(
            "id",
            "batchId",
            "hasPassed",
            "loanId",
            "loanAccountNumber",
            "crbResponseId",
            "errorLogs",
            "payload",
            "date")
    );


    @Autowired
    public CRBPostingAPIResource(PlatformSecurityContext context, ToApiJsonSerializer<CRBPostingLoggerData> toApiJsonSerializer, ApiRequestParameterHelper apiRequestParameterHelper, CreditBureauReadPlatformService crbReadPlatformService) {
        this.context = context;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.crbReadPlatformService = crbReadPlatformService;
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveAllCRBPostingLogs(@Context final UriInfo uriInfo, @QueryParam("paged") final Boolean paged,
            @QueryParam("offset") final Integer offset, @QueryParam("limit") final Integer limit, @QueryParam("status") final String status,
            @QueryParam("fromDate") final String fromDate, @QueryParam("toDate") final String toDate,
            @QueryParam("search") final String search) {

        this.context.authenticatedUser().validateHasReadPermission("VIEW_CRB_LOGGER");

        Integer safeLimit = limit;
        if (safeLimit == null || safeLimit <= 0) {
            safeLimit = 15;
        } else if (safeLimit > 200) {
            safeLimit = 200;
        }

        final PaginationParameters parameters = PaginationParameters.instance(paged == null ? Boolean.TRUE : paged, offset, safeLimit, null,
                null);
        final Page<CRBPostingLoggerData> result = crbReadPlatformService.retrieveCrbPostingLogs(parameters, parsePostedStatus(status),
                fromDate, toDate, search);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, result, this.responseDataParameters);
    }

    @GET
    @Path("/{logId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveCRBPostingLog(@PathParam("logId") final Long logId, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission("VIEW_CRB_LOGGER");
        final CRBPostingLoggerData result = crbReadPlatformService.retrieveCrbPostingLog(logId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, result, this.responseDataParameters);
    }

    private Boolean parsePostedStatus(final String status) {
        if (StringUtils.isBlank(status) || "all".equalsIgnoreCase(status)) {
            return null;
        }
        return Boolean.valueOf(status);
    }

    @POST
    @Path("/{loanId}/mark-fixed")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String markLogHasFixed(@PathParam("loanId") final Integer loanId, @Context final UriInfo uriInfo){
        String resourceNameForPermissions = "VIEW_CRB_LOGGER";
        this.context.authenticatedUser().validateHasReadPermission(resourceNameForPermissions);
        log.info("Marking CRB posting log as fixed for loanId {}",loanId);

        crbReadPlatformService.markCRBLogAsFixed(loanId.toString());

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.toApiJsonSerializer.serialize(null);

    }

    @GET
    @Path("/export")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response exportLogsToCSV(@Context final UriInfo uriInfo){

        String resourceNameForPermissions = "VIEW_CRB_LOGGER";

        this.context.authenticatedUser().validateHasReadPermission(resourceNameForPermissions);

        log.info("Exporting CRB posting logs to CSV with query parameters {}", uriInfo.getQueryParameters());

        TransUnionCreditReportCsvData fileData = this.crbReadPlatformService.generateCsvReport(uriInfo.getQueryParameters());

        return Response.ok(fileData.getInputStream(), fileData.getContentType())
                .header("Content-Disposition",
                        "attachment; filename=\"" + fileData.getFileName() + "\"")
                .build();

    }


}
