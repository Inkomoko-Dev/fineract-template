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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationCodes;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationCountryConfigData;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationSummaryRowData;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationThresholdData;
import org.apache.fineract.portfolio.loanclassification.service.LoanClassificationReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/loanclassification")
@Component
@Scope("singleton")
@Tag(name = "Loan Classification", description = "Configurable country-based loan classification")
public class LoanClassificationConfigApiResource {

    private final PlatformSecurityContext context;
    private final LoanClassificationReadPlatformService readPlatformService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final DefaultToApiJsonSerializer<Object> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    public LoanClassificationConfigApiResource(final PlatformSecurityContext context,
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
    @Path("codes")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveCodes(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_LOANCLASSIFICATIONCONFIG");
        final Collection<LoanClassificationThresholdData> codes = Arrays.stream(LoanClassificationCodes.values())
                .map(code -> new LoanClassificationThresholdData(code.getCode(), code.getLabel(), null, null)).collect(Collectors.toList());
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, codes);
    }

    @GET
    @Path("countries/template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveTemplate(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_LOANCLASSIFICATIONCONFIG");
        final LoanClassificationCountryConfigData template = this.readPlatformService.retrieveTemplate();
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, template);
    }

    @GET
    @Path("countries")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveCountries(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_LOANCLASSIFICATIONCONFIG");
        final Collection<LoanClassificationCountryConfigData> configs = this.readPlatformService.retrieveAllCountryConfigs();
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, configs);
    }

    @GET
    @Path("countries/{configId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveCountry(@PathParam("configId") final Long configId, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_LOANCLASSIFICATIONCONFIG");
        final LoanClassificationCountryConfigData config = this.readPlatformService.retrieveCountryConfig(configId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, config);
    }

    @POST
    @Path("countries")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String createCountryConfig(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo("CREATE_LOANCLASSIFICATIONCONFIG");
        final CommandWrapper command = new CommandWrapperBuilder().createLoanClassificationCountryConfig().withJson(apiRequestBodyAsJson)
                .build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(command);
        return this.toApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("countries/{configId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateCountryConfig(@PathParam("configId") final Long configId, final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo("UPDATE_LOANCLASSIFICATIONCONFIG");
        final CommandWrapper command = new CommandWrapperBuilder().updateLoanClassificationCountryConfig(configId)
                .withJson(apiRequestBodyAsJson).build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(command);
        return this.toApiJsonSerializer.serialize(result);
    }

    @DELETE
    @Path("countries/{configId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String deleteCountryConfig(@PathParam("configId") final Long configId) {
        this.context.authenticatedUser().validateHasPermissionTo("DELETE_LOANCLASSIFICATIONCONFIG");
        final CommandWrapper command = new CommandWrapperBuilder().deleteLoanClassificationCountryConfig(configId).build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(command);
        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("summary")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveSummary(@QueryParam("countryId") final Long countryId, @QueryParam("officeId") final Long officeId,
            @QueryParam("loanProductId") final Long loanProductId, @QueryParam("fromDate") final String fromDate,
            @QueryParam("toDate") final String toDate, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasPermissionTo("READ_LOANCLASSIFICATIONCONFIG");
        final Collection<LoanClassificationSummaryRowData> rows = this.readPlatformService.retrieveSummary(countryId, officeId,
                loanProductId, parseIsoDate(fromDate), parseIsoDate(toDate));
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, rows);
    }

    private LocalDate parseIsoDate(final String value) {
        return StringUtils.isBlank(value) ? null : LocalDate.parse(value);
    }
}
