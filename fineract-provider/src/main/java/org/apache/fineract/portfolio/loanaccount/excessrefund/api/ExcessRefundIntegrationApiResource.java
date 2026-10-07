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
package org.apache.fineract.portfolio.loanaccount.excessrefund.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.excessrefund.service.ExcessRefundWritePlatformService;
import org.springframework.stereotype.Component;

@Path("/loans/excess-refunds-integration")
@Component
@RequiredArgsConstructor
public class ExcessRefundIntegrationApiResource {

    private final PlatformSecurityContext context;
    private final ExcessRefundWritePlatformService writePlatformService;
    private final FromJsonHelper fromApiJsonHelper;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> toApiJsonSerializer;

    @POST
    @Path("update")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String update(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasUpdatePermission("LOAN");
        final JsonElement element = this.fromApiJsonHelper.parse(apiRequestBodyAsJson);
        final JsonObject body = element.getAsJsonObject();
        final String requestId = body.get("requestId").getAsString();
        final String resultCode = body.has("resultCode") ? body.get("resultCode").getAsString() : "500";
        final String transactionRef = body.has("transactionRef") && !body.get("transactionRef").isJsonNull()
                ? body.get("transactionRef").getAsString()
                : null;
        final String resultMessage = body.has("resultMessage") && !body.get("resultMessage").isJsonNull()
                ? body.get("resultMessage").getAsString()
                : null;
        final boolean success = "200".equals(resultCode) || "0".equals(resultCode) || "SUCCESS".equalsIgnoreCase(resultCode);
        final CommandProcessingResult result = this.writePlatformService.applyHubCallback(requestId, success, transactionRef, resultMessage);
        return this.toApiJsonSerializer.serialize(result);
    }
}
