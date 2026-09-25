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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementLoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementLoanByClientData;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementLoanData;
import org.apache.fineract.portfolio.loanaccount.data.ThirdPartyDisbursementRepaymentData;
import org.apache.fineract.portfolio.loanaccount.service.ThirdPartyDisbursementLoanReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementPartnerAccessService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("/loans/third-party-disbursement")
@Component
@Tag(name = "Loans", description = "Third-party disbursement loan discovery for integration partners")
public class ThirdPartyDisbursementLoansApiResource {

    private final PlatformSecurityContext context;
    private final ThirdPartyDisbursementLoanReadPlatformService readPlatformService;
    private final DisbursementPartnerAccessService disbursementPartnerAccessService;
    private final DefaultToApiJsonSerializer<ThirdPartyDisbursementLoanData> toApiJsonSerializer;

    @Autowired
    public ThirdPartyDisbursementLoansApiResource(final PlatformSecurityContext context,
            final ThirdPartyDisbursementLoanReadPlatformService readPlatformService,
            final DisbursementPartnerAccessService disbursementPartnerAccessService,
            final DefaultToApiJsonSerializer<ThirdPartyDisbursementLoanData> toApiJsonSerializer) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.disbursementPartnerAccessService = disbursementPartnerAccessService;
        this.toApiJsonSerializer = toApiJsonSerializer;
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List third-party disbursement loans",
            description = "Returns loans on products configured for the authenticated partner's disbursement provider. "
                    + "Provider is taken from m_disbursement_provider_appuser_mapping (query param provider is ignored). "
                    + "Use readyForInstruction=true to find approved loans not yet sent a disbursement instruction.")
    public String retrieveAll(@QueryParam(ThirdPartyDisbursementLoanApiConstants.PROVIDER) final String provider,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.STATUS) final String status,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.READY_FOR_INSTRUCTION) @Parameter(description = "When true, only approved loans with no disbursement instruction sub-status") final Boolean readyForInstruction,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.LOAN_ACCOUNT_NO) final String loanAccountNo,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.EXTERNAL_ID) final String externalId,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.OFFSET) final Integer offset,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.LIMIT) final Integer limit) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo(ThirdPartyDisbursementLoanApiConstants.PERMISSION_CODE);

        final String boundProvider = requireBoundProvider(user);

        final String requestedProvider = ThirdPartyDisbursementProvider.normalize(provider);
        if (requestedProvider != null && !requestedProvider.equals(boundProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.provider.mismatch",
                    "provider query parameter does not match the authenticated partner binding.",
                    List.of(ApiParameterError.parameterError("validation.msg.thirdPartyDisbursementLoan.provider.mismatch",
                            "provider query parameter does not match the authenticated partner binding.",
                            ThirdPartyDisbursementLoanApiConstants.PROVIDER, provider)));
        }

        final Page<ThirdPartyDisbursementLoanData> loans = this.readPlatformService.retrieveAll(boundProvider, status,
                readyForInstruction, loanAccountNo, externalId, offset, limit);
        return this.toApiJsonSerializer.serialize(loans);
    }

    @GET
    @Path("/by-client")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List a partner client's third-party disbursement loans",
            description = "Returns loans and currency-grouped totals for exactly one client lookup key. "
                    + "The authenticated partner binding determines the visible provider.")
    public String retrieveByClient(@QueryParam(ThirdPartyDisbursementLoanApiConstants.STATUS) final String status,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.CLIENT_ID) final Long clientId,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.CLIENT_ACCOUNT_NO) final String clientAccountNo,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.CLIENT_EXTERNAL_ID) final String clientExternalId,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.PHONE) final String phone,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.OFFSET) final Integer offset,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.LIMIT) final Integer limit) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo(ThirdPartyDisbursementLoanApiConstants.PERMISSION_CODE);
        final String boundProvider = requireBoundProvider(user);
        final ThirdPartyDisbursementLoanByClientData loans = this.readPlatformService.retrieveByClient(boundProvider, status, clientId,
                clientAccountNo, clientExternalId, phone, offset, limit);
        return this.toApiJsonSerializer.serialize(loans);
    }

    @GET
    @Path("/{loanId}/transactions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List third-party loan repayments",
            description = "Returns ordinary repayment transactions for a loan owned by the authenticated partner.")
    public String retrieveRepayments(@PathParam("loanId") final Long loanId, @QueryParam("fromDate") final String fromDate,
            @QueryParam("toDate") final String toDate, @QueryParam("includeReversed") final Boolean includeReversed,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.OFFSET) final Integer offset,
            @QueryParam(ThirdPartyDisbursementLoanApiConstants.LIMIT) final Integer limit) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo(ThirdPartyDisbursementLoanApiConstants.PERMISSION_CODE);
        final Page<ThirdPartyDisbursementRepaymentData> transactions = this.readPlatformService.retrieveRepayments(requireBoundProvider(user),
                loanId, parseDate("fromDate", fromDate), parseDate("toDate", toDate), Boolean.TRUE.equals(includeReversed), offset, limit);
        return this.toApiJsonSerializer.serialize(transactions);
    }

    private static LocalDate parseDate(final String parameterName, final String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.date.invalid",
                    parameterName + " must be an ISO date (YYYY-MM-DD).", List.of(ApiParameterError.parameterErrorWithValue(
                            "validation.msg.thirdPartyDisbursementLoan.date.invalid", parameterName + " must be an ISO date (YYYY-MM-DD).",
                            parameterName, value)));
        }
    }

    private String requireBoundProvider(final AppUser user) {
        final String boundProvider = this.disbursementPartnerAccessService.resolveProviderCodeForUser(user).orElse(null);
        if (StringUtils.isBlank(boundProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementLoan.partnerBinding.required",
                    "Authenticated user is not bound to a disbursement provider.",
                    List.of(ApiParameterError.generalError("validation.msg.thirdPartyDisbursementLoan.partnerBinding.required",
                            "Authenticated user is not bound to a disbursement provider. "
                                    + "Seed m_disbursement_provider_appuser_mapping for this app user.")));
        }
        return boundProvider;
    }
}
