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
package org.apache.fineract.portfolio.supplier.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.supplier.data.SupplierApiConstants;
import org.apache.fineract.portfolio.supplier.data.SupplierData;
import org.apache.fineract.portfolio.supplier.data.SupplierTemplateData;
import org.apache.fineract.portfolio.supplier.service.SupplierReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("/suppliers")
@Component
@Tag(name = "Suppliers", description = "Kifiya supplier registration callback and read-only supplier management")
public class SupplierApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = SupplierApiConstants.ENTITY_NAME;

    private final PlatformSecurityContext context;
    private final SupplierReadPlatformService readPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<SupplierData> toApiJsonSerializer;

    @Autowired
    public SupplierApiResource(final PlatformSecurityContext context, final SupplierReadPlatformService readPlatformService,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            final DefaultToApiJsonSerializer<SupplierData> toApiJsonSerializer) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
    }

    @POST
    @Path("callback")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Supplier registration callback", description = "Upserts a supplier from an external financing partner (e.g. Kifiya)")
    public String callback(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo("CREATE_SUPPLIER");
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createSupplier().withJson(apiRequestBodyAsJson).build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List suppliers")
    public String retrieveAll(@QueryParam("q") final String search, @QueryParam("businessSector") final String businessSector,
            @QueryParam("supplierType") final String supplierType, @QueryParam("country") final String country,
            @QueryParam("syncStatus") final String syncStatus, @QueryParam("offset") final Integer offset,
            @QueryParam("limit") final Integer limit) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);
        final List<SupplierData> suppliers = this.readPlatformService.retrieveAll(search, businessSector, supplierType, country, syncStatus,
                offset, limit);
        return this.toApiJsonSerializer.serialize(suppliers);
    }

    @GET
    @Path("template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Supplier filter template")
    public String retrieveTemplate() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);
        final SupplierTemplateData template = this.readPlatformService.retrieveTemplate();
        return this.toApiJsonSerializer.serialize(template);
    }

    @GET
    @Path("{supplierId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a supplier")
    public String retrieveOne(@PathParam("supplierId") @Parameter(description = "supplierId") final Long supplierId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);
        return this.toApiJsonSerializer.serialize(this.readPlatformService.retrieveOne(supplierId));
    }
}
