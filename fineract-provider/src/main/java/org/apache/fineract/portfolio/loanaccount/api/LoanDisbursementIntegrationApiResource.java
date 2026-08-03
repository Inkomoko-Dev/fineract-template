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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.swagger.v3.oas.annotations.Parameter;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Path("/loans/{accountNo}/disbursements-integration")
@Component
@Scope("singleton")
public class LoanDisbursementIntegrationApiResource {

    private static final Logger LOG = LoggerFactory.getLogger(LoanDisbursementIntegrationApiResource.class);

    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    private final DefaultToApiJsonSerializer<LoanAccountData> toApiJsonSerializer;

    private final PlatformSecurityContext context;

    private final String resourceNameForPermissions = "LOAN";

    private final FromJsonHelper fromApiJsonHelper;

    private final LoanReadPlatformService loanReadPlatformService;

    public LoanDisbursementIntegrationApiResource(PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            DefaultToApiJsonSerializer<LoanAccountData> toApiJsonSerializer, PlatformSecurityContext context,
            FromJsonHelper fromApiJsonHelper, LoanReadPlatformService loanReadPlatformService) {
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.context = context;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.loanReadPlatformService = loanReadPlatformService;
    }

    @POST
    @Path("update")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String stateTransitions(@PathParam("accountNo") @Parameter(description = "accountNo") final String accountNo,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasUpdatePermission(this.resourceNameForPermissions);
        LoanAccountData loanAccountData = this.loanReadPlatformService.retrieveLoanByLoanAccount(accountNo);
        Long loanId = loanAccountData.getId();
        BigDecimal loanPrinciple = loanAccountData.getPrincipal();
        CommandWrapperBuilder resourceDetails = new CommandWrapperBuilder();
        resourceDetails.withLoanId(loanId).withEntityName("LOANNOTE");
        JsonObject newJsonObject = new JsonObject();
        newJsonObject.addProperty("note", apiRequestBodyAsJson);
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createNote(resourceDetails.build(), "loans", loanId)
                .withJson(newJsonObject.toString()).build();
        this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        final JsonElement allElement = this.fromApiJsonHelper.parse(apiRequestBodyAsJson);
        final String resultCode = allElement.getAsJsonObject().get("resultCode").getAsString();

        LOG.info("Update Disbursement In API: " + loanId + " with Result Code: " + resultCode);

        CommandProcessingResult result = null;
        final String extractedJson = extractJson(allElement, loanPrinciple);
        final CommandWrapperBuilder updateBuilder = new CommandWrapperBuilder().withJson(extractedJson);

        final CommandWrapper updateWrapper = updateBuilder.updateDisbursement(loanId).build();
        result = this.commandsSourceWritePlatformService.logCommandSource(updateWrapper);

        if (Integer.valueOf(resultCode) == 200) {
            final CommandWrapper disburseWrapper = updateBuilder.disburseLoanApplication(loanId).build();
            result = this.commandsSourceWritePlatformService.logCommandSource(disburseWrapper);
        }

        return this.toApiJsonSerializer.serialize(result);
    }

    private String extractJson(JsonElement element, BigDecimal loanPrinciple ) {
        JsonObject originalJsonObject = element.getAsJsonObject();
        JsonObject newJsonObject = new JsonObject();
        newJsonObject.add("paymentTypeId", originalJsonObject.get("paymentTypeId"));
        newJsonObject.add("transactionAmount", new JsonPrimitive(loanPrinciple)); //
        newJsonObject.add("actualDisbursementDate", originalJsonObject.get("actualDisbursementDate"));
        newJsonObject.add("locale", originalJsonObject.get("locale"));
        newJsonObject.add("resultCode", originalJsonObject.get("resultCode"));
        newJsonObject.add("dateFormat", originalJsonObject.get("dateFormat"));
        newJsonObject.add("receiptNumber", originalJsonObject.get("transactionRef"));
        return newJsonObject.toString();
    }

}
