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
package org.apache.fineract.organisation.externalmapping.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.externalmapping.data.ExternalOrganizationMappingData;
import org.apache.fineract.organisation.externalmapping.service.ExternalOrganizationMappingService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/externalorganizationmappings")
@Component
@Scope("singleton")
@RequiredArgsConstructor
@Tag(name = "External Organization Mapping", description = "Maps Fineract offices onto organizations in an external GL or ERP. Many offices can share one external organization.")
public class ExternalOrganizationMappingApiResource {

    private static final String RESOURCE = "EXTERNALORGANIZATIONMAPPING";

    private final PlatformSecurityContext context;
    private final ExternalOrganizationMappingService mappingService;
    private final DefaultToApiJsonSerializer<ExternalOrganizationMappingData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List office to external organization mappings")
    public String retrieveAll(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE);
        final List<ExternalOrganizationMappingData> mappings = this.mappingService.retrieveAll();
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, mappings);
    }

    @GET
    @Path("{mappingId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve one office to external organization mapping")
    public String retrieveOne(@PathParam("mappingId") final Long mappingId, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE);
        final ExternalOrganizationMappingData mapping = this.mappingService.retrieveOne(mappingId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, mapping);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create an office to external organization mapping")
    public String create(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo("CREATE_EXTERNALORGANIZATIONMAPPING");
        return this.toApiJsonSerializer.serialize(this.mappingService.create(apiRequestBodyAsJson));
    }

    @POST
    @Path("import")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Import office to external organization mappings")
    public String importMappings(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo("CREATE_EXTERNALORGANIZATIONMAPPING");
        return this.toApiJsonSerializer.serialize(this.mappingService.importMappings(apiRequestBodyAsJson));
    }

    @PUT
    @Path("{mappingId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update an office to external organization mapping")
    public String update(@PathParam("mappingId") final Long mappingId, final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo("UPDATE_EXTERNALORGANIZATIONMAPPING");
        return this.toApiJsonSerializer.serialize(this.mappingService.update(mappingId, apiRequestBodyAsJson));
    }

    @DELETE
    @Path("{mappingId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete an office to external organization mapping")
    public String delete(@PathParam("mappingId") final Long mappingId) {
        this.context.authenticatedUser().validateHasPermissionTo("DELETE_EXTERNALORGANIZATIONMAPPING");
        return this.toApiJsonSerializer.serialize(this.mappingService.delete(mappingId));
    }
}
