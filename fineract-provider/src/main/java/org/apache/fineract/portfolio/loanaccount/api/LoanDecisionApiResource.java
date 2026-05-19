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

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.ActiveIcReviewLevelData;
import org.apache.fineract.portfolio.loanaccount.data.IcReviewLevelConfigData;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.data.LoanApprovalMatrixData;
import org.apache.fineract.portfolio.loanaccount.data.LoanDecisionData;
import org.apache.fineract.portfolio.loanaccount.service.IcReviewLevelConfigReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanApprovalMatrixReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/loans/decision")
@Component
@Scope("singleton")
@Tag(name = "Loans Decision", description = "The API adds another layer of decision to a loan account life cycle")
public class LoanDecisionApiResource {

    private final String resourceNameForPermissions = "LOAN";
    private final Set<String> loanDataParameters = new HashSet<>(Arrays.asList("cohortOptions", "countryOptions", "programOptions",
            "surveyLocationOptions", "clientId", "clientName", "loanProductName", "loanProductId", "currencyOptions"));

    private final DefaultToApiJsonSerializer<LoanDecisionData> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final PlatformSecurityContext context;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final DefaultToApiJsonSerializer<LoanAccountData> loanApprovalDataToApiJsonSerializer;
    private final DefaultToApiJsonSerializer<LoanApprovalMatrixData> loanApprovalMatrixDataToApiJsonSerializer;
    private final LoanReadPlatformService loanReadPlatformService;
    private final CurrencyReadPlatformService currencyReadPlatformService;
    private final LoanApprovalMatrixReadPlatformService loanApprovalMatrixReadPlatformService;
    private final ConfigurationReadPlatformService configurationReadPlatformService;
    private final IcReviewLevelConfigReadPlatformService icReviewLevelConfigReadPlatformService;

    public LoanDecisionApiResource(final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            final DefaultToApiJsonSerializer<LoanDecisionData> toApiJsonSerializer, final PlatformSecurityContext context,
            final ApiRequestParameterHelper apiRequestParameterHelper,
            final DefaultToApiJsonSerializer<LoanAccountData> loanApprovalDataToApiJsonSerializer,
            final LoanReadPlatformService loanReadPlatformService, final CurrencyReadPlatformService currencyReadPlatformService,
            DefaultToApiJsonSerializer<LoanApprovalMatrixData> loanApprovalMatrixDataToApiJsonSerializer,
            final LoanApprovalMatrixReadPlatformService loanApprovalMatrixReadPlatformService,
            final ConfigurationReadPlatformService configurationReadPlatformService,
            final IcReviewLevelConfigReadPlatformService icReviewLevelConfigReadPlatformService) {

        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.context = context;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.loanApprovalDataToApiJsonSerializer = loanApprovalDataToApiJsonSerializer;
        this.loanReadPlatformService = loanReadPlatformService;
        this.currencyReadPlatformService = currencyReadPlatformService;
        this.loanApprovalMatrixDataToApiJsonSerializer = loanApprovalMatrixDataToApiJsonSerializer;
        this.loanApprovalMatrixReadPlatformService = loanApprovalMatrixReadPlatformService;
        this.configurationReadPlatformService = configurationReadPlatformService;
        this.icReviewLevelConfigReadPlatformService = icReviewLevelConfigReadPlatformService;
    }

    @POST
    @Path("reviewApplication/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptLoanApplicationReview(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().acceptLoanApplicationReview(loanId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("reviewApplication/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectLoanApplicationReview(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectLoanApplicationReview(loanId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("dueDiligence/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String applyDueDiligence(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().applyDueDiligence(loanId).withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("dueDiligence/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectDueDiligence(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectDueDiligence(loanId).withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("template/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String template(@PathParam("loanId") final long loanId, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        LoanAccountData loanAccountData = this.loanReadPlatformService.retrieveLoanDecisionDetailsTemplate(loanId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.loanApprovalDataToApiJsonSerializer.serialize(settings, loanAccountData, this.loanDataParameters);

    }

    @POST
    @Path("collateralReview/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptLoanCollateralReview(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectLoanCollateralReview(loanId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("collateralReview/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectLoanCollateralReview(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectLoanCollateralReview(loanId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("approvalMatrix/createApprovalMatrix")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String createLoanApprovalMatrix(final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createLoanApprovalMatrix().withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @DELETE
    @Path("approvalMatrix/{matrixId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String deleteLoanApprovalMatrix(@PathParam("matrixId") final long matrixId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteLoanApprovalMatrix(matrixId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("template/approvalMatrix")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String approvalMatrixTemplate(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        final GlobalConfigurationPropertyData extendLoanLifeCycleConfig = this.configurationReadPlatformService
                .retrieveGlobalConfiguration("Add-More-Stages-To-A-Loan-Life-Cycle");

        final Boolean isExtendLoanLifeCycleConfig = extendLoanLifeCycleConfig.isEnabled();

        final Collection<CurrencyData> currencyOptions = this.currencyReadPlatformService.retrieveAllowedCurrencies();

        // Retrieve active IC review levels from the configuration table
        final List<IcReviewLevelConfigData> activeLevelConfigs = this.icReviewLevelConfigReadPlatformService.retrieveAllActiveLevels();
        final List<ActiveIcReviewLevelData> activeIcReviewLevels = activeLevelConfigs.stream()
                .map(ActiveIcReviewLevelData::fromConfigData)
                .collect(Collectors.toList());

        LoanApprovalMatrixData loanApprovalMatrixData = new LoanApprovalMatrixData();
        loanApprovalMatrixData.setCurrencyOptions(currencyOptions);
        loanApprovalMatrixData.setIsExtendLoanLifeCycleConfig(isExtendLoanLifeCycleConfig);
        loanApprovalMatrixData.setActiveIcReviewLevels(activeIcReviewLevels);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.loanApprovalMatrixDataToApiJsonSerializer.serialize(settings, loanApprovalMatrixData, this.loanDataParameters);

    }

    @GET
    @Path("getAllApprovalMatrix")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String getAllApprovalMatrix(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        List<LoanApprovalMatrixData> loanApprovalMatrixData = this.loanApprovalMatrixReadPlatformService.findAll();

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.loanApprovalMatrixDataToApiJsonSerializer.serialize(settings, loanApprovalMatrixData, this.loanDataParameters);

    }

    @GET
    @Path("getApprovalMatrixDetails/{approvalMatrixId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String getApprovalMatrixDetails(@PathParam("approvalMatrixId") final long approvalMatrixId, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        LoanApprovalMatrixData loanApprovalMatrixData = this.loanApprovalMatrixReadPlatformService
                .getApprovalMatrixDetails(approvalMatrixId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.loanApprovalMatrixDataToApiJsonSerializer.serialize(settings, loanApprovalMatrixData, this.loanDataParameters);

    }

    @PUT
    @Path("updateApprovalMatrix/{matrixId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateApprovalMatrix(@PathParam("matrixId") final long matrixId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateLoanApprovalMatrix(matrixId).withJson(apiRequestBodyAsJson)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelOne/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptIcReviewDecisionLevelOne(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().acceptIcReviewDecisionLevelOne(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelOne/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectIcReviewDecisionLevelOne(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectIcReviewDecisionLevelOne(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelTwo/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptIcReviewDecisionLevelTwo(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().acceptIcReviewDecisionLevelTwo(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelTwo/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectIcReviewDecisionLevelTwo(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectIcReviewDecisionLevelTwo(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelThree/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptIcReviewDecisionLevelThree(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().acceptIcReviewDecisionLevelThree(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelThree/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectIcReviewDecisionLevelThree(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectIcReviewDecisionLevelThree(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelFour/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptIcReviewDecisionLevelFour(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().acceptIcReviewDecisionLevelFour(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelFour/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectIcReviewDecisionLevelFour(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectIcReviewDecisionLevelFour(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelFive/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptIcReviewDecisionLevelFive(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().acceptIcReviewDecisionLevelFive(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("icReviewDecisionLevelFive/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectIcReviewDecisionLevelFive(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectIcReviewDecisionLevelFive(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("prepareAndSignContract/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptPrepareAndSignContract(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().acceptPrepareAndSignContract(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("prepareAndSignContract/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectPrepareAndSignContract(@PathParam("loanId") final long loanId, final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectPrepareAndSignContract(loanId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    /**
     * Dynamic IC Review Decision endpoint - supports any level number
     * This endpoint can handle IC review decisions for levels 1, 2, 3, 4, 5, 6, 7, ...
     *
     * @param loanId the loan ID
     * @param levelNumber the IC review level number (1, 2, 3, 4, 5, 6, ...)
     * @param apiRequestBodyAsJson the request body
     * @return the result
     */
    @POST
    @Path("icReviewDecision/level/{levelNumber}/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String acceptIcReviewDecisionDynamic(@PathParam("loanId") final long loanId,
                                                 @PathParam("levelNumber") final int levelNumber,
                                                 final String apiRequestBodyAsJson) {

        // Build command based on level number
        final CommandWrapper commandRequest = buildIcReviewCommand(loanId, levelNumber, false, apiRequestBodyAsJson);

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    /**
     * Dynamic IC Review Decision reject endpoint - supports any level number
     * This endpoint can handle IC review decision rejections for levels 1, 2, 3, 4, 5, 6, 7, ...
     *
     * @param loanId the loan ID
     * @param levelNumber the IC review level number (1, 2, 3, 4, 5, 6, ...)
     * @param apiRequestBodyAsJson the request body
     * @return the result
     */
    @POST
    @Path("icReviewDecision/level/{levelNumber}/reject/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String rejectIcReviewDecisionDynamic(@PathParam("loanId") final long loanId,
                                                 @PathParam("levelNumber") final int levelNumber,
                                                 final String apiRequestBodyAsJson) {

        // Build command based on level number
        final CommandWrapper commandRequest = buildIcReviewCommand(loanId, levelNumber, true, apiRequestBodyAsJson);

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    /**
     * Helper method to build IC review command based on level number
     * For levels 1-5, uses the existing command builders for backward compatibility
     * For levels 6+, uses the dynamic command builder
     */
    private CommandWrapper buildIcReviewCommand(long loanId, int levelNumber, boolean isReject, String json) {
        CommandWrapperBuilder builder = new CommandWrapperBuilder();

        // For levels 1-5, use existing command builders for backward compatibility
        if (!isReject) {
            switch (levelNumber) {
                case 1: return builder.acceptIcReviewDecisionLevelOne(loanId).withJson(json).build();
                case 2: return builder.acceptIcReviewDecisionLevelTwo(loanId).withJson(json).build();
                case 3: return builder.acceptIcReviewDecisionLevelThree(loanId).withJson(json).build();
                case 4: return builder.acceptIcReviewDecisionLevelFour(loanId).withJson(json).build();
                case 5: return builder.acceptIcReviewDecisionLevelFive(loanId).withJson(json).build();
                default:
                    // For levels 6+, use the dynamic command builder
                    return builder.acceptIcReviewDecisionDynamic(loanId, levelNumber).withJson(json).build();
            }
        } else {
            switch (levelNumber) {
                case 1: return builder.rejectIcReviewDecisionLevelOne(loanId).withJson(json).build();
                case 2: return builder.rejectIcReviewDecisionLevelTwo(loanId).withJson(json).build();
                case 3: return builder.rejectIcReviewDecisionLevelThree(loanId).withJson(json).build();
                case 4: return builder.rejectIcReviewDecisionLevelFour(loanId).withJson(json).build();
                case 5: return builder.rejectIcReviewDecisionLevelFive(loanId).withJson(json).build();
                default:
                    // For levels 6+, use the dynamic command builder
                    return builder.rejectIcReviewDecisionDynamic(loanId, levelNumber).withJson(json).build();
            }
        }
    }

    @GET
    @Path("getAllLoansPendingDecisionEngine/{nextLoanDecisionState}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String getAllLoansPendingDecisionEngine(@PathParam("nextLoanDecisionState") final Integer nextLoanDecisionState,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermissions);

        Collection<LoanAccountData> loanAccountData = this.loanReadPlatformService.getAllLoansPendingDecisionEngine(nextLoanDecisionState);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.loanApprovalDataToApiJsonSerializer.serialize(settings, loanAccountData, this.loanDataParameters);

    }
}
