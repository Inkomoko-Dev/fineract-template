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
package org.apache.fineract.infrastructure.gcm.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import java.util.HashMap;
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
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.gcm.domain.DeviceRegistration;
import org.apache.fineract.infrastructure.gcm.domain.DeviceRegistrationData;
import org.apache.fineract.infrastructure.gcm.service.DeviceRegistrationReadPlatformService;
import org.apache.fineract.infrastructure.gcm.service.DeviceRegistrationWritePlatformService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/self/device/registration")
@Component
@Scope("singleton")
@Tag(name = "Device Registration", description = "")
public class DeviceRegistrationApiResource {

    private final PlatformSecurityContext context;
    private final DeviceRegistrationWritePlatformService deviceRegistrationWritePlatformService;
    private final DefaultToApiJsonSerializer<DeviceRegistrationData> toApiJsonSerializer;
    private final DeviceRegistrationReadPlatformService deviceRegistrationReadPlatformService;

    @Autowired
    public DeviceRegistrationApiResource(PlatformSecurityContext context,
            final DefaultToApiJsonSerializer<DeviceRegistrationData> toApiJsonSerializer,
            final DeviceRegistrationReadPlatformService deviceRegistrationReadPlatformService,
            final DeviceRegistrationWritePlatformService deviceRegistrationWritePlatformService) {
        this.context = context;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.deviceRegistrationReadPlatformService = deviceRegistrationReadPlatformService;
        this.deviceRegistrationWritePlatformService = deviceRegistrationWritePlatformService;
    }

    @Operation(summary = "Register Device", description = "Register Device.")
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String registerDevice(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser();
        Gson gson = new Gson();
        JsonObject json = new Gson().fromJson(apiRequestBodyAsJson, JsonObject.class);
        Long clientId = json.get(DeviceRegistrationApiConstants.clientIdParamName).getAsLong();
        String registrationId = json.get(DeviceRegistrationApiConstants.registrationIdParamName).getAsString();
        DeviceRegistration deviceRegistration = this.deviceRegistrationWritePlatformService.registerDevice(clientId, registrationId);
        String response = gson.toJson(deviceRegistration.getId());
        return response;
    }

    @Operation(summary = "Retrieve All Device Registrations", description = "Retrieve All Device Registrations.")
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveAllDeviceRegistrations(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser();

        Collection<DeviceRegistrationData> deviceRegistrationDataList = this.deviceRegistrationReadPlatformService
                .retrieveAllDeviceRegiistrations();

        return this.toApiJsonSerializer.serialize(deviceRegistrationDataList);
    }

    @Operation(summary = "Retrieve Device Registration By Client Id", description = "Retrieve Device Registration By Client Id.")
    @GET
    @Path("client/{clientId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveDeviceRegistrationByClientId(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser();

        DeviceRegistrationData deviceRegistrationData = this.deviceRegistrationReadPlatformService
                .retrieveDeviceRegiistrationByClientId(clientId);

        return this.toApiJsonSerializer.serialize(deviceRegistrationData);
    }

    @Operation(summary = "Retrieve Device Regiistration", description = "Retrieve Device Regiistration.")
    @GET
    @Path("{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveDeviceRegiistration(@PathParam("id") @Parameter(description = "id") final Long id,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser();

        DeviceRegistrationData deviceRegistrationData = this.deviceRegistrationReadPlatformService.retrieveDeviceRegiistration(id);

        return this.toApiJsonSerializer.serialize(deviceRegistrationData);
    }

    @Operation(summary = "Update Device Registration", description = "Update Device Registration.")
    @PUT
    @Path("{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String updateDeviceRegistration(@PathParam("id") @Parameter(description = "id") final Long id,
            final String apiRequestBodyAsJson) {

        this.context.authenticatedUser();

        Gson gson = new Gson();
        JsonObject json = new Gson().fromJson(apiRequestBodyAsJson, JsonObject.class);
        Long clientId = json.get(DeviceRegistrationApiConstants.clientIdParamName).getAsLong();
        String registrationId = json.get(DeviceRegistrationApiConstants.registrationIdParamName).getAsString();
        DeviceRegistration deviceRegistration = this.deviceRegistrationWritePlatformService.updateDeviceRegistration(id, clientId,
                registrationId);
        String response = gson.toJson(deviceRegistration.getId());
        return response;
    }

    @Operation(summary = "Delete", description = "Delete.")
    @DELETE
    @Path("{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String delete(@PathParam("id") @Parameter(description = "id") final Long id) {

        this.context.authenticatedUser();
        this.deviceRegistrationWritePlatformService.deleteDeviceRegistration(id);
        return responseMap(id);

    }

    public String responseMap(Long id) {
        HashMap<String, Object> responseMap = new HashMap<>();
        responseMap.put("resource", id);
        return new Gson().toJson(responseMap);
    }

}
