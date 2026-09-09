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
package org.apache.fineract.infrastructure.whatsapp.interactive.api;

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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.WhatsAppInteractiveConstants;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppMenuDefinitionData;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppMenuOptionData;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveMenuReadPlatformService;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveMenuWritePlatformService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/whatsapp/interactive/menus")
@Component
@Scope("singleton")
@Tag(name = "WhatsApp Interactive Menus", description = "Configurable WhatsApp self-service menus")
@RequiredArgsConstructor
public class WhatsAppInteractiveMenuApiResource {

    private final PlatformSecurityContext context;
    private final WhatsAppInteractiveMenuReadPlatformService readPlatformService;
    private final WhatsAppInteractiveMenuWritePlatformService writePlatformService;
    private final DefaultToApiJsonSerializer<WhatsAppMenuDefinitionData> menuDefinitionSerializer;
    private final DefaultToApiJsonSerializer<WhatsAppMenuOptionData> menuOptionSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;

    @GET
    @Path("definitions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveMenuDefinitions(@QueryParam("menuKey") final String menuKey) {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        final List<WhatsAppMenuDefinitionData> data = readPlatformService.retrieveMenuDefinitions(menuKey);
        return menuDefinitionSerializer.serialize(data);
    }

    @GET
    @Path("options")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveMenuOptions(@QueryParam("menuKey") final String menuKey, @QueryParam("languageCode") final String languageCode) {
        context.authenticatedUser().validateHasReadPermission(WhatsAppInteractiveConstants.RESOURCE_NAME);
        final List<WhatsAppMenuOptionData> data = readPlatformService.retrieveMenuOptions(menuKey, languageCode);
        return menuOptionSerializer.serialize(data);
    }

    @POST
    @Path("options")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String createMenuOption(final String apiRequestBodyAsJson) {
        context.authenticatedUser().validateHasPermissionTo("CONFIGURE_AFRICASTALKING");
        final CommandProcessingResult result = writePlatformService.createMenuOption(apiRequestBodyAsJson);
        return commandProcessingResultSerializer.serialize(result);
    }

    @PUT
    @Path("options/{optionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateMenuOption(@PathParam("optionId") final Long optionId, final String apiRequestBodyAsJson) {
        context.authenticatedUser().validateHasPermissionTo("CONFIGURE_AFRICASTALKING");
        final CommandProcessingResult result = writePlatformService.updateMenuOption(optionId, apiRequestBodyAsJson);
        return commandProcessingResultSerializer.serialize(result);
    }

    @DELETE
    @Path("options/{optionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String deleteMenuOption(@PathParam("optionId") final Long optionId) {
        context.authenticatedUser().validateHasPermissionTo("CONFIGURE_AFRICASTALKING");
        final CommandProcessingResult result = writePlatformService.deleteMenuOption(optionId);
        return commandProcessingResultSerializer.serialize(result);
    }

    @PUT
    @Path("definitions/{menuKey}/{languageCode}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String updateMenuDefinition(@PathParam("menuKey") final String menuKey, @PathParam("languageCode") final String languageCode,
            final String apiRequestBodyAsJson) {
        context.authenticatedUser().validateHasPermissionTo("CONFIGURE_AFRICASTALKING");
        final CommandProcessingResult result = writePlatformService.updateMenuDefinition(menuKey, languageCode, apiRequestBodyAsJson);
        return commandProcessingResultSerializer.serialize(result);
    }
}
