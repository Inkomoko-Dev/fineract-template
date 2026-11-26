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
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.PathParam;
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
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingConnectivityService;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingClient;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingVoiceService;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWebhookAuthenticationException;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWebhookService;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWebhookService.WebhookProcessingResult;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWhatsAppService;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWhatsAppTemplateCreateOutcome;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWhatsAppTemplatePayloadNormalizer;
import org.apache.fineract.infrastructure.africastalking.service.CommunicationLogSanitizer;
import org.apache.fineract.infrastructure.africastalking.service.CommunicationMessageReadPlatformService;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignConstants;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppAtTemplate;
import org.apache.fineract.infrastructure.campaigns.whatsapp.service.WhatsAppAtTemplateCatalogService;
import org.apache.fineract.infrastructure.core.config.TomcatConnectorCustomizer;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/africastalking")
@Component
@Scope("singleton")
@Tag(name = "AfricasTalking", description = "AfricasTalking WhatsApp and Voice integration")
public class AfricasTalkingApiResource {

    private static final Logger LOG = LoggerFactory.getLogger(AfricasTalkingApiResource.class);

    private final PlatformSecurityContext context;
    private final AfricasTalkingConnectivityService connectivityService;
    private final AfricasTalkingClient africastalkingClient;
    private final AfricasTalkingWebhookService webhookService;
    private final AfricasTalkingWhatsAppService whatsAppService;
    private final AfricasTalkingVoiceService voiceService;
    private final CommunicationMessageReadPlatformService communicationMessageReadPlatformService;
    private final AfricasTalkingProperties properties;
    private final DefaultToApiJsonSerializer<ConnectivityTestResultData> connectivitySerializer;
    private final DefaultToApiJsonSerializer<CommunicationMessageData> messageSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final WhatsAppAtTemplateCatalogService whatsAppAtTemplateCatalogService;

    public AfricasTalkingApiResource(final PlatformSecurityContext context,
            final AfricasTalkingConnectivityService connectivityService, final AfricasTalkingClient africastalkingClient,
            final AfricasTalkingWebhookService webhookService,
            final AfricasTalkingWhatsAppService whatsAppService, final AfricasTalkingVoiceService voiceService,
            final CommunicationMessageReadPlatformService communicationMessageReadPlatformService,
            final AfricasTalkingProperties properties,
            final DefaultToApiJsonSerializer<ConnectivityTestResultData> connectivitySerializer,
            final DefaultToApiJsonSerializer<CommunicationMessageData> messageSerializer,
            final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer,
            final ApiRequestParameterHelper apiRequestParameterHelper,
            final WhatsAppAtTemplateCatalogService whatsAppAtTemplateCatalogService) {
        this.context = context;
        this.connectivityService = connectivityService;
        this.africastalkingClient = africastalkingClient;
        this.webhookService = webhookService;
        this.whatsAppService = whatsAppService;
        this.voiceService = voiceService;
        this.communicationMessageReadPlatformService = communicationMessageReadPlatformService;
        this.properties = properties;
        this.connectivitySerializer = connectivitySerializer;
        this.messageSerializer = messageSerializer;
        this.commandProcessingResultSerializer = commandProcessingResultSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.whatsAppAtTemplateCatalogService = whatsAppAtTemplateCatalogService;
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
    public String retrieveMessages(@Context final UriInfo uriInfo, @QueryParam("status") final String statusParam) {
        this.context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final CommunicationMessageStatus status = parseMessageStatus(statusParam);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.messageSerializer.serialize(settings, this.communicationMessageReadPlatformService.retrieveWhatsAppMessages(status));
    }

    static CommunicationMessageStatus parseMessageStatus(final String statusParam) {
        if (StringUtils.isBlank(statusParam)) {
            return null;
        }
        try {
            return CommunicationMessageStatus.valueOf(statusParam.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(
                    "error.msg.africastalking.whatsapp.status.invalid", "Invalid WhatsApp message status", "status", statusParam)), exception);
        }
    }

    @GET
    @Path("messages/{messageId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveMessage(@PathParam("messageId") final Long messageId, @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(AfricasTalkingConstants.RESOURCE_NAME);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.messageSerializer.serialize(settings, this.communicationMessageReadPlatformService.retrieveOne(messageId));
    }

    @POST
    @Path("messages")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String sendMessage(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasCreatePermission(AfricasTalkingConstants.RESOURCE_NAME);
        final CommandProcessingResult result = this.whatsAppService.queueOutboundMessage(apiRequestBodyAsJson);
        return this.commandProcessingResultSerializer.serializeResult(result);
    }

    @POST
    @Path("template/send")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String createTemplate(final String apiRequestBodyAsJson) throws IOException {
        // Align with campaign UI Create Template button (CREATE_WHATSAPPCAMPAIGN).
        this.context.authenticatedUser().validateHasCreatePermission(WhatsAppCampaignConstants.RESOURCE_NAME);
        final String stripped = this.whatsAppAtTemplateCatalogService.stripCatalogOnlyFields(apiRequestBodyAsJson);
        // AT docs require example.body_text length == number of {{n}} placeholders.
        final String atPayload = AfricasTalkingWhatsAppTemplatePayloadNormalizer.normalize(stripped);
        var response = this.africastalkingClient.createWhatsAppTemplateFromJson(atPayload);
        // AT may return HTTP 5xx + status=Failure while still allocating templateId with Pending.
        if (!AfricasTalkingWhatsAppTemplateCreateOutcome.isAccepted(response.statusCode(), response.body())) {
            final String safeDetail = CommunicationLogSanitizer
                    .truncateDetail(StringUtils.defaultIfBlank(response.body(), "HTTP " + response.statusCode()));
            throw new GeneralPlatformDomainRuleException("error.msg.africastalking.whatsapp.template.create.failed",
                    "Africa's Talking rejected the WhatsApp template (HTTP " + response.statusCode() + "): " + safeDetail);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOG.info(
                    "Africa's Talking template create returned HTTP {} but templateId={} templateStatus={} — catalogued as submitted.",
                    response.statusCode(),
                    CommunicationLogSanitizer.truncateDetail(AfricasTalkingWhatsAppTemplateCreateOutcome.templateId(response.body())),
                    AfricasTalkingWhatsAppTemplateCreateOutcome.templateStatus(response.body()));
        }
        // Catalog from original JSON so markAsApproved is available; never persist on AT failure.
        final WhatsAppAtTemplate catalogued = this.whatsAppAtTemplateCatalogService.upsertFromCreateRequest(apiRequestBodyAsJson);
        final CommandProcessingResult result = new CommandProcessingResult(catalogued.getId());
        return this.commandProcessingResultSerializer.serializeResult(result);
    }

    @POST
    @Path("webhooks/ping")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.TEXT_PLAIN })
    public Response pingWebhook(final String payload, @Context final HttpHeaders headers) {
        return handleWebhook("PING", payload, headers, null);
    }

    @POST
    @Path("webhooks/whatsapp/inbound")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.TEXT_PLAIN })
    public Response whatsappInbound(final String payload, @Context final HttpHeaders headers,
            @Context final HttpServletRequest request) {
        return handleWebhook("WHATSAPP_INBOUND", payload, headers, peerAddress(request));
    }

    @POST
    @Path("webhooks/whatsapp/status")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.TEXT_PLAIN })
    public Response whatsappStatus(final String payload, @Context final HttpHeaders headers,
            @Context final HttpServletRequest request) {
        return handleWebhook("WHATSAPP_STATUS", payload, headers, peerAddress(request));
    }

    @POST
    @Path("webhooks/voice/callback")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.APPLICATION_XML })
    public Response voiceCallback(final String payload, @Context final HttpHeaders headers,
            @Context final HttpServletRequest request) {
        try {
            final String signature = headers.getHeaderString(properties.getWebhook().getSignatureHeader());
            final String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);
            final WebhookProcessingResult result = webhookService.processWebhook("VOICE_CALLBACK", payload == null ? "" : payload, signature,
                    userAgent, peerAddress(request));
            if (result.duplicate()) {
                // Do not re-enter IVR (would re-issue OTP / create duplicate callbacks).
                // Replay last XML so AT retries keep the caller on the same prompt.
                return Response.ok(voiceService.resolveDuplicateIvrResponse(payload), MediaType.APPLICATION_XML).build();
            }
            voiceService.processInboundCallback(payload);
            return Response.ok(voiceService.buildIvrResponse(payload), MediaType.APPLICATION_XML).build();
        } catch (AfricasTalkingWebhookAuthenticationException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("UNAUTHORIZED").type(MediaType.TEXT_PLAIN).build();
        }
    }

    @POST
    @Path("webhooks/voice/events")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.TEXT_PLAIN })
    public Response voiceEvents(final String payload, @Context final HttpHeaders headers,
            @Context final HttpServletRequest request) {
        return handleWebhook("VOICE_EVENT", payload, headers, peerAddress(request));
    }

    private Response handleWebhook(final String eventType, final String payload, final HttpHeaders headers, final String peerAddress) {
        try {
            final String signature = headers.getHeaderString(properties.getWebhook().getSignatureHeader());
            final String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);
            final WebhookProcessingResult result = webhookService.processWebhook(eventType, payload == null ? "" : payload, signature,
                    userAgent, peerAddress);
            if (!result.duplicate()) {
                processBusinessEvent(eventType, payload);
            }
            return Response.ok(result.duplicate() ? "DUPLICATE" : "OK", MediaType.TEXT_PLAIN).build();
        } catch (AfricasTalkingWebhookAuthenticationException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("UNAUTHORIZED").type(MediaType.TEXT_PLAIN).build();
        }
    }

    private static String peerAddress(final HttpServletRequest request) {
        return (String) request.getAttribute(TomcatConnectorCustomizer.TCP_PEER_ADDRESS_ATTRIBUTE);
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
