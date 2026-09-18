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
package org.apache.fineract.useradministration.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.useradministration.service.PasswordResetWritePlatformService;
import org.springframework.stereotype.Component;

@Path("/passwordreset")
@Component
@Tag(name = "Password Reset", description = "Self-service password reset with silent email verification")
@RequiredArgsConstructor
public class PasswordResetApiResource {

    private final PasswordResetWritePlatformService passwordResetWritePlatformService;
    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Request a password reset verification code", description = "Accepts username only. If the account exists, "
            + "a one-time code is emailed to the address on file. Response does not reveal whether the account exists.")
    public String requestReset(final String apiRequestBodyAsJson) {
        final JsonObject payload = parse(apiRequestBodyAsJson);
        return this.gson.toJson(this.passwordResetWritePlatformService.requestReset(string(payload, "username")));
    }

    @POST
    @Path("confirm")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Confirm password reset with verification code", description = "Validates the emailed one-time code for the "
            + "username and sets a new password.")
    public String confirmReset(final String apiRequestBodyAsJson) {
        final JsonObject payload = parse(apiRequestBodyAsJson);
        return this.gson.toJson(this.passwordResetWritePlatformService.confirmReset(string(payload, "username"), string(payload, "token"),
                string(payload, "password"), string(payload, "repeatPassword")));
    }

    private JsonObject parse(final String apiRequestBodyAsJson) {
        return StringUtils.isBlank(apiRequestBodyAsJson) ? new JsonObject() : this.gson.fromJson(apiRequestBodyAsJson, JsonObject.class);
    }

    private String string(final JsonObject payload, final String key) {
        return payload.has(key) && !payload.get(key).isJsonNull() ? payload.get(key).getAsString() : null;
    }
}
