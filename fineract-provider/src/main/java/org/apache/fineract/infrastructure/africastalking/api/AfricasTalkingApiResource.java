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
package org.apache.fineract.infrastructure.africastalking.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.data.CommunicationMessageData;
import org.apache.fineract.infrastructure.africastalking.data.ConnectivityTestResultData;
import org.apache.fineract.infrastructure.africastalking.data.VoiceCallLogData;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingConnectivityService;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingVoiceService;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWebhookAuthenticationException;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWebhookService;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWebhookService.WebhookProcessingResult;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWhatsAppService;
import org.apache.fineract.infrastructure.africastalking.service.CommunicationMessageReadPlatformService;
import org.apache.fineract.infrastructure.africastalking.service.VoiceCallLogReadPlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/africastalking")
@Component
@Scope("singleton")
@Tag(name = "AfricasTalking", description = "AfricasTalking WhatsApp and Voice integration")
public class AfricasTalkingApiResource {

    private final PlatformSecurityContext context;
    private final AfricasTalkingConnectivityService connectivityService;
    private final AfricasTalkingWebhookService webhookService;
    private final AfricasTalkingWhatsAppService whatsAppService;
    private final AfricasTalkingVoiceService voiceService;
    private final CommunicationMessageReadPlatformService communicationMessageReadPlatformService;
    private final VoiceCallLogReadPlatformService voiceCallLogReadPlatformService;
    private final AfricasTalkingProperties properties;
    private final DefaultToApiJsonSerializer<ConnectivityTestResultData> connectivitySerializer;
    private final DefaultToApiJsonSerializer<CommunicationMessageData> messageSerializer;
    private final DefaultToApiJsonSerializer<VoiceCallLogData> voiceCallLogSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    public AfricasTalkingApiResource(final PlatformSecurityContext context,
            final AfricasTalkingConnectivityService connectivityService, final AfricasTalkingWebhookService webhookService,
            final AfricasTalkingWhatsAppService whatsAppService, final AfricasTalkingVoiceService voiceService,
            final CommunicationMessageReadPlatformService communicationMessageReadPlatformService,
            final VoiceCallLogReadPlatformService voiceCallLogReadPlatformService, final AfricasTalkingProperties properties,
            final DefaultToApiJsonSerializer<ConnectivityTestResultData> connectivitySerializer,
            final DefaultToApiJsonSerializer<CommunicationMessageData> messageSerializer,
            final DefaultToApiJsonSerializer<VoiceCallLogData> voiceCallLogSerializer,
            final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer,
            final ApiRequestParameterHelper apiRequestParameterHelper) {
        this.context = context;
        this.connectivityService = connectivityService;
        this.webhookService = webhookService;
        this.whatsAppService = whatsAppService;
        this.voiceService = voiceService;
        this.communicationMessageReadPlatformService = communicationMessageReadPlatformService;
        this.voiceCallLogReadPlatformService = voiceCallLogReadPlatformService;
        this.properties = properties;
        this.connectivitySerializer = connectivitySerializer;
        this.messageSerializer = messageSerializer;
        this.voiceCallLogSerializer = voiceCallLogSerializer;
        this.commandProcessingResultSerializer = commandProcessingResultSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
    }

    @GET
    @Path("connectivity")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String testConnectivity(@Context final UriInfo uriInfo, @QueryParam("channel") final String channel) {
        this.context.authenticatedUser().validateHasPermissionTo("CONFIGURE_AFRICASTALKING");
        final ConnectivityTestResultData result = this.connectivityService.testConnectivity(channel);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.connectivitySerializer.serialize(settings, result);
    }

    @GET
    @Path("messages")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveMessages(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.messageSerializer.serialize(settings, this.communicationMessageReadPlatformService.retrieveWhatsAppMessages());
    }

    @POST
    @Path("messages")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String sendMessage(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasCreatePermission(AfricasTalkingConstants.RESOURCE_NAME);
        final CommandProcessingResult result = this.whatsAppService.queueOutboundMessage(apiRequestBodyAsJson);
        return this.commandProcessingResultSerializer.serialize(result);
    }

    @POST
    @Path("voice/calls")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String initiateVoiceCall(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasCreatePermission(AfricasTalkingConstants.RESOURCE_NAME);
        final CommandProcessingResult result = this.voiceService.initiateOutboundCall(apiRequestBodyAsJson);
        return this.commandProcessingResultSerializer.serialize(result);
    }

    @GET
    @Path("voice/calls")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveVoiceCalls(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.voiceCallLogSerializer.serialize(settings, this.voiceCallLogReadPlatformService.retrieveCallLogs());
    }

    @POST
    @Path("webhooks/ping")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.TEXT_PLAIN })
    public Response pingWebhook(final String payload, @Context final HttpHeaders headers) {
        return handleWebhook("PING", payload, headers, MediaType.TEXT_PLAIN);
    }

    @POST
    @Path("webhooks/whatsapp/inbound")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.TEXT_PLAIN })
    public Response whatsappInbound(final String payload, @Context final HttpHeaders headers) {
        return handleWebhook("WHATSAPP_INBOUND", payload, headers, MediaType.TEXT_PLAIN);
    }

    @POST
    @Path("webhooks/whatsapp/status")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.TEXT_PLAIN })
    public Response whatsappStatus(final String payload, @Context final HttpHeaders headers) {
        return handleWebhook("WHATSAPP_STATUS", payload, headers, MediaType.TEXT_PLAIN);
    }

    @POST
    @Path("webhooks/voice/callback")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.APPLICATION_XML })
    public Response voiceCallback(final String payload, @Context final HttpHeaders headers) {
        try {
            final String signature = headers.getHeaderString(properties.getWebhook().getSignatureHeader());
            final String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);
            final WebhookProcessingResult result = webhookService.processWebhook("VOICE_CALLBACK", payload == null ? "" : payload, signature,
                    userAgent);
            if (!result.duplicate()) {
                voiceService.processInboundCallback(payload);
            }
            return Response.ok(voiceService.buildIvrResponse(payload), MediaType.APPLICATION_XML).build();
        } catch (AfricasTalkingWebhookAuthenticationException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("UNAUTHORIZED").type(MediaType.TEXT_PLAIN).build();
        }
    }

    @POST
    @Path("webhooks/voice/events")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.TEXT_PLAIN })
    public Response voiceEvents(final String payload, @Context final HttpHeaders headers) {
        return handleWebhook("VOICE_EVENT", payload, headers, MediaType.TEXT_PLAIN);
    }

    private Response handleWebhook(final String eventType, final String payload, final HttpHeaders headers, final String responseType) {
        try {
            final String signature = headers.getHeaderString(properties.getWebhook().getSignatureHeader());
            final String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);
            final WebhookProcessingResult result = webhookService.processWebhook(eventType, payload == null ? "" : payload, signature,
                    userAgent);
            if (!result.duplicate()) {
                processBusinessEvent(eventType, payload);
            }
            if (MediaType.APPLICATION_XML.equals(responseType)) {
                return Response.ok("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>", responseType).build();
            }
            return Response.ok(result.duplicate() ? "DUPLICATE" : "OK", responseType).build();
        } catch (AfricasTalkingWebhookAuthenticationException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("UNAUTHORIZED").type(MediaType.TEXT_PLAIN).build();
        }
    }

    private void processBusinessEvent(final String eventType, final String payload) {
        switch (eventType) {
            case "WHATSAPP_INBOUND" -> whatsAppService.processInboundMessage(payload);
            case "WHATSAPP_STATUS" -> whatsAppService.processStatusUpdate(payload);
            case "VOICE_EVENT" -> voiceService.processCallEvent(payload);
            default -> {
                // Additional handlers can be added here.
            }
        }
    }
}
