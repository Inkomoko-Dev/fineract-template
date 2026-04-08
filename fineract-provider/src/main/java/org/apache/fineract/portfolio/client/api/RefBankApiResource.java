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
package org.apache.fineract.portfolio.client.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.RefBankData;
import org.apache.fineract.portfolio.client.service.RefBankReadPlatformService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/banks")
@Component
@Tag(name = "Banks", description = "Reference bank list for client bank name dropdown")
@RequiredArgsConstructor
public class RefBankApiResource {

    private final PlatformSecurityContext context;
    private final RefBankReadPlatformService refBankReadPlatformService;
    private final DefaultToApiJsonSerializer<RefBankData> toApiJsonSerializer;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve all active banks",
               description = "Returns all active banks ordered by country and bank name. " +
                             "Optionally filter by query string across bankName, bankCode and country.")
    public String retrieveAllBanks(
            @QueryParam("q") @Parameter(description = "Search query across bankName, bankCode and country")
            final String query) {

        this.context.authenticatedUser();

        final List<RefBankData> banks = (query != null && !query.trim().isEmpty())
                ? refBankReadPlatformService.searchBanks(query)
                : refBankReadPlatformService.retrieveAllBanks();

        return toApiJsonSerializer.serialize(banks);
    }

    @GET
    @Path("{bankId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a single bank by ID")
    public String retrieveBank(
            @PathParam("bankId") @Parameter(description = "bankId") final Long bankId) {

        this.context.authenticatedUser();

        final RefBankData bank = refBankReadPlatformService.retrieveBank(bankId);
        return toApiJsonSerializer.serialize(bank);
    }
}