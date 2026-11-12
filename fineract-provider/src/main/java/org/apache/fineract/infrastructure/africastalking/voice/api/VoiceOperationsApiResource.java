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
package org.apache.fineract.infrastructure.africastalking.voice.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceCallQueueEntryData;
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceCallbackRequestData;
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceOperationsDashboardData;
import org.apache.fineract.infrastructure.africastalking.voice.data.VoiceVoicemailData;
import org.apache.fineract.infrastructure.africastalking.voice.service.VoiceCallQueueReadPlatformService;
import org.apache.fineract.infrastructure.africastalking.voice.service.VoiceCallbackDispatchService;
import org.apache.fineract.infrastructure.africastalking.voice.service.VoiceCallbackRequestReadPlatformService;
import org.apache.fineract.infrastructure.africastalking.voice.service.VoiceOperationsDashboardService;
import org.apache.fineract.infrastructure.africastalking.voice.service.VoiceVoicemailReadPlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/africastalking/voice")
@Component
@Scope("singleton")
@Tag(name = "AfricasTalking Voice Operations", description = "Voice dashboards, callbacks, voicemails, and queues")
@RequiredArgsConstructor
public class VoiceOperationsApiResource {

    private final PlatformSecurityContext context;
    private final VoiceOperationsDashboardService dashboardService;
    private final VoiceCallbackRequestReadPlatformService callbackReadPlatformService;
    private final VoiceVoicemailReadPlatformService voicemailReadPlatformService;
    private final VoiceCallQueueReadPlatformService queueReadPlatformService;
    private final VoiceCallbackDispatchService callbackDispatchService;
    private final DefaultToApiJsonSerializer<VoiceOperationsDashboardData> dashboardSerializer;
    private final DefaultToApiJsonSerializer<VoiceCallbackRequestData> callbackSerializer;
    private final DefaultToApiJsonSerializer<VoiceVoicemailData> voicemailSerializer;
    private final DefaultToApiJsonSerializer<VoiceCallQueueEntryData> queueSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @GET
    @Path("dashboard")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveDashboard(@Context final UriInfo uriInfo) {
        context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return dashboardSerializer.serialize(settings, dashboardService.retrieveDashboard());
    }

    @GET
    @Path("callbacks")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveCallbacks(@Context final UriInfo uriInfo) {
        context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return callbackSerializer.serialize(settings, callbackReadPlatformService.retrieveCallbacks());
    }

    @GET
    @Path("callbacks/{callbackId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveCallback(@PathParam("callbackId") final Long callbackId, @Context final UriInfo uriInfo) {
        context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return callbackSerializer.serialize(settings, callbackReadPlatformService.retrieveOne(callbackId));
    }

    @POST
    @Path("callbacks/{callbackId}/dispatch")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String dispatchCallback(@PathParam("callbackId") final Long callbackId) {
        context.authenticatedUser().validateHasCreatePermission(AfricasTalkingConstants.RESOURCE_NAME);
        return commandProcessingResultSerializer.serializeResult(callbackDispatchService.dispatchCallback(callbackId));
    }

    @GET
    @Path("voicemails")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveVoicemails(@Context final UriInfo uriInfo) {
        context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return voicemailSerializer.serialize(settings, voicemailReadPlatformService.retrieveVoicemails());
    }

    @GET
    @Path("queue")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveQueue(@Context final UriInfo uriInfo) {
        context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return queueSerializer.serialize(settings, queueReadPlatformService.retrieveQueueEntries());
    }
}
