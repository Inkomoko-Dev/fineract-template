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
package org.apache.fineract.portfolio.client.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.api.PartnerClientApiConstants;
import org.apache.fineract.portfolio.client.data.PartnerClientData;
import org.apache.fineract.portfolio.client.data.PartnerClientHistoryData;
import org.apache.fineract.portfolio.client.service.PartnerClientReadPlatformService;
import org.apache.fineract.portfolio.client.service.PartnerClientWritePlatformService;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementPartnerAccessService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/partner/clients")
@Component
@Scope("singleton")
@Tag(name = "Partner Clients", description = "Partner client discovery for third-party integration partners")
@Slf4j
public class PartnerClientsApiResource {

    private final PlatformSecurityContext context;
    private final PartnerClientReadPlatformService readPlatformService;
    private final PartnerClientWritePlatformService writePlatformService;
    private final DisbursementPartnerAccessService disbursementPartnerAccessService;
    private final DefaultToApiJsonSerializer<PartnerClientData> toApiJsonSerializer;
    private final DefaultToApiJsonSerializer<PartnerClientHistoryData> toApiJsonSerializerHistory;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    public PartnerClientsApiResource(final PlatformSecurityContext context,
            final PartnerClientReadPlatformService readPlatformService,
            final PartnerClientWritePlatformService writePlatformService,
            final DisbursementPartnerAccessService disbursementPartnerAccessService,
            final DefaultToApiJsonSerializer<PartnerClientData> toApiJsonSerializer,
            final DefaultToApiJsonSerializer<PartnerClientHistoryData> toApiJsonSerializerHistory,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.writePlatformService = writePlatformService;
        this.disbursementPartnerAccessService = disbursementPartnerAccessService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.toApiJsonSerializerHistory = toApiJsonSerializerHistory;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List partner clients", description = "Returns clients assigned to the authenticated user's partner. "
            + "Partner is taken from m_disbursement_provider_appuser_mapping (query param partner is ignored). "
            + "Supports filtering by phone, status, office, and date range.")
    public String retrieveAll(@QueryParam(PartnerClientApiConstants.PHONE) final String phone,
            @QueryParam(PartnerClientApiConstants.STATUS) final Integer status,
            @QueryParam(PartnerClientApiConstants.OFFICE_ID) final Long officeId,
            @QueryParam(PartnerClientApiConstants.FROM_DATE) final String fromDate,
            @QueryParam(PartnerClientApiConstants.TO_DATE) final String toDate,
            @QueryParam(PartnerClientApiConstants.OFFSET) @DefaultValue("0") final Integer offset,
            @QueryParam(PartnerClientApiConstants.LIMIT) @DefaultValue("15") final Integer limit) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasReadPermission(PartnerClientApiConstants.PERMISSION_CODE);

        final String boundProvider = this.disbursementPartnerAccessService.resolveProviderCodeForUser(user).orElse(null);
        if (StringUtils.isBlank(boundProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.partnerClient.partnerBinding.required",
                    "Authenticated user is not bound to a disbursement provider.",
                    List.of(ApiParameterError.generalError("validation.msg.partnerClient.partnerBinding.required",
                            "Authenticated user is not bound to a disbursement provider. "
                                    + "Seed m_disbursement_provider_appuser_mapping for this app user.")));
        }

        // If phone is provided, use phone lookup
        if (StringUtils.isNotBlank(phone)) {
            final PartnerClientData client = this.readPlatformService.retrievePartnerClientByPhone(phone, boundProvider);
            if (client == null) {
                return this.toApiJsonSerializer.serialize(new Page<>(Collections.emptyList(), 0));
            }
            return this.toApiJsonSerializer.serialize(new Page<>(Collections.singletonList(client), 1));
        }

        LocalDate parsedFromDate = null;
        LocalDate parsedToDate = null;
        
        try {
            if (fromDate != null && !fromDate.trim().isEmpty()) {
                parsedFromDate = LocalDate.parse(fromDate);
            }
            if (toDate != null && !toDate.trim().isEmpty()) {
                parsedToDate = LocalDate.parse(toDate);
            }
        } catch (Exception e) {
            throw new PlatformApiDataValidationException("validation.msg.invalid.date.format",
                    "Invalid date format. Use YYYY-MM-DD format.",
                    List.of(ApiParameterError.generalError("validation.msg.invalid.date.format",
                            "Invalid date format. Use YYYY-MM-DD format.")));
        }

        final Page<PartnerClientData> clients = this.readPlatformService.retrieveAllPartnerClients(boundProvider, status,
                officeId, parsedFromDate, parsedToDate, offset, limit);
        return this.toApiJsonSerializer.serialize(clients);
    }

    @GET
    @Path("{clientId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a partner client", description = "Returns specific client details. Admin users can retrieve any client, partner-bound users only their assigned clients")
    public String retrieveOne(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {
        final AppUser user = this.context.authenticatedUser();
        
        final boolean isAdmin = user.hasAnyPermission("ALL_FUNCTIONS", "ADMIN_READ_PARTNERCLIENT", "CREATE_PARTNERCLIENT",
                "UPDATE_PARTNERCLIENT", PartnerClientApiConstants.WRITE_PARTNERCLIENT_CLIENT);
        
        if (isAdmin) {
            // Admin users can retrieve any partner client without partner binding
            user.validateHasReadPermission(PartnerClientApiConstants.PERMISSION_CODE);
            
            final PartnerClientData client = this.readPlatformService.retrievePartnerClientForAdmin(clientId);
            if (client == null) {
                throw new PlatformApiDataValidationException("validation.msg.partnerClient.client.notFound",
                        "Client not found or not assigned to any partner.",
                        List.of(ApiParameterError.parameterError("validation.msg.partnerClient.client.notFound",
                                "Client not found or not assigned to any partner.", "clientId", clientId)));
            }
            return this.toApiJsonSerializer.serialize(client);
        } else {
            // Partner-bound users can only retrieve clients assigned to their partner
            user.validateHasReadPermission(PartnerClientApiConstants.PERMISSION_CODE);

            final String boundProvider = this.disbursementPartnerAccessService.resolveProviderCodeForUser(user).orElse(null);
            if (StringUtils.isBlank(boundProvider)) {
                throw new PlatformApiDataValidationException("validation.msg.partnerClient.partnerBinding.required",
                        "Authenticated user is not bound to a disbursement provider.",
                        List.of(ApiParameterError.generalError("validation.msg.partnerClient.partnerBinding.required",
                                "Authenticated user is not bound to a disbursement provider.")));
            }

            final PartnerClientData client = this.readPlatformService.retrievePartnerClient(clientId, boundProvider);
            if (client == null) {
                throw new PlatformApiDataValidationException("validation.msg.partnerClient.client.notFound",
                        "Client not found or not assigned to partner.",
                        List.of(ApiParameterError.parameterError("validation.msg.partnerClient.client.notFound",
                                "Client not found or not assigned to partner.", "clientId", clientId)));
            }

            return this.toApiJsonSerializer.serialize(client);
        }
    }

    @GET
    @Path("{clientId}/assignment")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve current partner assignment", description = "Staff read of the client's active partner mapping. Returns an empty object when unassigned.")
    public String retrieveAssignment(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {
        final AppUser user = this.context.authenticatedUser();
        if (!user.hasAnyPermission("ALL_FUNCTIONS", PartnerClientApiConstants.READ_PARTNERCLIENT_CLIENT, "CREATE_PARTNERCLIENT",
                "UPDATE_PARTNERCLIENT")) {
            user.validateHasReadPermission(PartnerClientApiConstants.PERMISSION_CODE);
        }

        final PartnerClientData client = this.readPlatformService.retrievePartnerClientForAdmin(clientId);
        if (client == null) {
            return "{}";
        }
        return this.toApiJsonSerializer.serialize(client);
    }

    @GET
    @Path("{clientId}/history")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve client mapping history", description = "Returns ownership history for a client. Admin users can access any client history.")
    public String retrieveHistory(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasReadPermission(PartnerClientApiConstants.PERMISSION_CODE);

        final List<PartnerClientHistoryData> history = this.readPlatformService.retrieveClientMappingHistory(clientId);
        return this.toApiJsonSerializerHistory.serialize(history);
    }

    @POST
    @Path("{clientId}/assign")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Assign client to partner", description = "Assigns a client to a partner (admin only)")
    public String assignClient(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @QueryParam(PartnerClientApiConstants.PARTNER_CODE) final String partnerCode,
            @QueryParam(PartnerClientApiConstants.REASON) final String reason) {
        this.context.authenticatedUser().validateHasCreatePermission(PartnerClientApiConstants.RESOURCE_NAME);

        final String normalizedPartnerCode = ThirdPartyDisbursementProvider.normalize(partnerCode);
        if (StringUtils.isBlank(normalizedPartnerCode)) {
            throw new PlatformApiDataValidationException("validation.msg.partnerClient.partnerCode.required",
                    "Partner code is required.",
                    List.of(ApiParameterError.parameterError("validation.msg.partnerClient.partnerCode.required",
                            "Partner code is required.", PartnerClientApiConstants.PARTNER_CODE, partnerCode)));
        }

        final CommandProcessingResult result = this.writePlatformService.assignClientToPartner(clientId,
                normalizedPartnerCode, reason);

        return this.toApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("{clientId}/reassign")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Reassign client to different partner", description = "Reassigns a client to a different partner (admin only)")
    public String reassignClient(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @QueryParam(PartnerClientApiConstants.PARTNER_CODE) final String partnerCode,
            @QueryParam(PartnerClientApiConstants.REASON) final String reason) {
        this.context.authenticatedUser().validateHasUpdatePermission(PartnerClientApiConstants.RESOURCE_NAME);

        final String normalizedPartnerCode = ThirdPartyDisbursementProvider.normalize(partnerCode);
        if (StringUtils.isBlank(normalizedPartnerCode)) {
            throw new PlatformApiDataValidationException("validation.msg.partnerClient.partnerCode.required",
                    "Partner code is required.",
                    List.of(ApiParameterError.parameterError("validation.msg.partnerClient.partnerCode.required",
                            "Partner code is required.", PartnerClientApiConstants.PARTNER_CODE, partnerCode)));
        }

        final CommandProcessingResult result = this.writePlatformService.reassignClient(clientId, normalizedPartnerCode,
                reason);

        return this.toApiJsonSerializer.serialize(result);
    }

    @DELETE
    @Path("{clientId}/mapping")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Remove partner assignment", description = "Removes partner assignment for a client (admin only)")
    public String deactivateMapping(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @QueryParam(PartnerClientApiConstants.REASON) final String reason) {
        this.context.authenticatedUser().validateHasUpdatePermission(PartnerClientApiConstants.RESOURCE_NAME);

        final CommandProcessingResult result = this.writePlatformService.deactivateClientMapping(clientId, reason);

        return this.toApiJsonSerializer.serialize(result);
    }
}
