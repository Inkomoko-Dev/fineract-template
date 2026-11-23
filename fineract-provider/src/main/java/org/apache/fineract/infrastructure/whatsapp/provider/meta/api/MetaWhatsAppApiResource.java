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
package org.apache.fineract.infrastructure.whatsapp.provider.meta.api;

import com.google.gson.Gson;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppProviderRouter;
import org.apache.fineract.infrastructure.whatsapp.provider.meta.MetaWhatsAppWebhookService;
import org.apache.fineract.infrastructure.whatsapp.provider.meta.MetaWhatsAppWebhookVerifier;
import org.springframework.stereotype.Component;

/**
 * Meta Cloud API WhatsApp endpoints.
 * <p>
 * Register the webhook callback in Meta as:
 * {@code https://<host>/api/v1/meta/whatsapp/webhook?tenantIdentifier=<tenant>}
 * Meta does not send {@code Fineract-Platform-TenantId}; the query param is required.
 */
@Path("/meta/whatsapp")
@Component
@RequiredArgsConstructor
@Slf4j
public class MetaWhatsAppApiResource {

    private static final String RESOURCE_NAME = "COMMUNICATION";
    private static final Gson GSON = new Gson();

    private final PlatformSecurityContext context;
    private final MetaWhatsAppWebhookVerifier webhookVerifier;
    private final MetaWhatsAppWebhookService webhookService;
    private final WhatsAppProviderRouter providerRouter;

    @GET
    @Path("webhook")
    @Produces({ MediaType.TEXT_PLAIN })
    public Response verifyWebhook(@QueryParam("hub.mode") final String mode, @QueryParam("hub.verify_token") final String verifyToken,
            @QueryParam("hub.challenge") final String challenge) {
        if (webhookVerifier.verifyChallenge(mode, verifyToken)) {
            return Response.ok(challenge == null ? "" : challenge).type(MediaType.TEXT_PLAIN).build();
        }
        return Response.status(Response.Status.FORBIDDEN).entity("Verification failed").build();
    }

    @POST
    @Path("webhook")
    @Consumes({ MediaType.WILDCARD })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response receiveWebhook(final String payload, @HeaderParam("X-Hub-Signature-256") final String signature) {
        if (!webhookVerifier.verifySignature(payload == null ? "" : payload, signature)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"invalid signature\"}").build();
        }
        try {
            webhookService.processWebhookPayload(payload);
            return Response.ok("{\"status\":\"ok\"}").build();
        } catch (Exception e) {
            log.error("Failed processing Meta WhatsApp webhook", e);
            // Meta retries on non-2xx; return 200 after logging to avoid poison loops for parse bugs.
            return Response.ok("{\"status\":\"accepted\"}").build();
        }
    }

    @GET
    @Path("connectivity")
    @Produces({ MediaType.APPLICATION_JSON })
    public String connectivity() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        return GSON.toJson(providerRouter.connectivitySnapshot());
    }

    @GET
    @Path("provider")
    @Produces({ MediaType.APPLICATION_JSON })
    public String providerConfig() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        return GSON.toJson(providerRouter.connectivitySnapshot());
    }
}
