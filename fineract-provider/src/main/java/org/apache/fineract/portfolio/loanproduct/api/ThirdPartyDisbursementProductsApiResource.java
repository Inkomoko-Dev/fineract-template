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
package org.apache.fineract.portfolio.loanproduct.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductApiConstants;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductData;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementPartnerAccessService;
import org.apache.fineract.portfolio.loanproduct.service.ThirdPartyDisbursementProductReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("/loanproducts/third-party-disbursement")
@Component
@Tag(name = "Loan Products", description = "Third-party disbursement loan product discovery for integration partners")
public class ThirdPartyDisbursementProductsApiResource {

    private final PlatformSecurityContext context;
    private final ThirdPartyDisbursementProductReadPlatformService readPlatformService;
    private final DisbursementPartnerAccessService disbursementPartnerAccessService;
    private final DefaultToApiJsonSerializer<ThirdPartyDisbursementProductData> toApiJsonSerializer;

    @Autowired
    public ThirdPartyDisbursementProductsApiResource(final PlatformSecurityContext context,
            final ThirdPartyDisbursementProductReadPlatformService readPlatformService,
            final DisbursementPartnerAccessService disbursementPartnerAccessService,
            final DefaultToApiJsonSerializer<ThirdPartyDisbursementProductData> toApiJsonSerializer) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.disbursementPartnerAccessService = disbursementPartnerAccessService;
        this.toApiJsonSerializer = toApiJsonSerializer;
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List third-party disbursement loan products",
            description = "Returns loan products configured for third-party disbursement. "
                    + "Provider is taken from m_disbursement_provider_appuser_mapping (query param provider is ignored).")
    public String retrieveAll(@QueryParam(ThirdPartyDisbursementProductApiConstants.PROVIDER) final String provider,
            @QueryParam(ThirdPartyDisbursementProductApiConstants.INCLUDE_INACTIVE) @Parameter(description = "When true, include products past their close date") final Boolean includeInactive,
            @QueryParam(ThirdPartyDisbursementProductApiConstants.OFFSET) final Integer offset,
            @QueryParam(ThirdPartyDisbursementProductApiConstants.LIMIT) final Integer limit) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo(ThirdPartyDisbursementProductApiConstants.PERMISSION_CODE);

        final String boundProvider = resolveBoundProvider(user, provider);
        final Page<ThirdPartyDisbursementProductData> products = this.readPlatformService.retrieveAll(boundProvider, includeInactive,
                offset, limit);
        return this.toApiJsonSerializer.serialize(products);
    }

    @GET
    @Path("{productId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a third-party disbursement loan product",
            description = "Returns a single loan product configured for third-party disbursement, including charges and terms.")
    public String retrieveOne(@PathParam("productId") final Long productId,
            @QueryParam(ThirdPartyDisbursementProductApiConstants.PROVIDER) final String provider) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo(ThirdPartyDisbursementProductApiConstants.PERMISSION_CODE);

        resolveBoundProvider(user, provider);
        final ThirdPartyDisbursementProductData product = this.readPlatformService.retrieveOne(productId);
        return this.toApiJsonSerializer.serialize(product);
    }

    private String resolveBoundProvider(final AppUser user, final String provider) {
        final String boundProvider = this.disbursementPartnerAccessService.resolveProviderCodeForUser(user).orElse(null);
        if (StringUtils.isBlank(boundProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementProduct.partnerBinding.required",
                    "Authenticated user is not bound to a disbursement provider.",
                    List.of(ApiParameterError.generalError("validation.msg.thirdPartyDisbursementProduct.partnerBinding.required",
                            "Authenticated user is not bound to a disbursement provider. "
                                    + "Seed m_disbursement_provider_appuser_mapping for this app user.")));
        }

        final String requestedProvider = ThirdPartyDisbursementProvider.normalize(provider);
        if (requestedProvider != null && !requestedProvider.equals(boundProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.thirdPartyDisbursementProduct.provider.mismatch",
                    "provider query parameter does not match the authenticated partner binding.",
                    List.of(ApiParameterError.parameterError("validation.msg.thirdPartyDisbursementProduct.provider.mismatch",
                            "provider query parameter does not match the authenticated partner binding.",
                            ThirdPartyDisbursementProductApiConstants.PROVIDER, provider)));
        }
        return boundProvider;
    }
}
