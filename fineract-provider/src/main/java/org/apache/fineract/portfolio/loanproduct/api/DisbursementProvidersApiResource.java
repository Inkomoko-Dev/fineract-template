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
package org.apache.fineract.portfolio.loanproduct.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanproduct.data.DisbursementProviderData;
import org.apache.fineract.portfolio.loanproduct.domain.DisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/disbursement-providers")
@Component
@Scope("singleton")
@Tag(name = "Disbursement Providers", description = "Active third-party disbursement partners")
public class DisbursementProvidersApiResource {

    private final PlatformSecurityContext context;
    private final DisbursementProviderReadPlatformService readPlatformService;
    private final DefaultToApiJsonSerializer<DisbursementProviderData> toApiJsonSerializer;

    @Autowired
    public DisbursementProvidersApiResource(final PlatformSecurityContext context,
            final DisbursementProviderReadPlatformService readPlatformService,
            final DefaultToApiJsonSerializer<DisbursementProviderData> toApiJsonSerializer) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List active disbursement partners")
    public String retrieveAll() {
        this.context.authenticatedUser();
        final List<DisbursementProviderData> providers = new ArrayList<>();
        for (final DisbursementProvider provider : this.readPlatformService.retrieveActiveProviders()) {
            providers.add(new DisbursementProviderData(provider.getCode(), provider.getName()));
        }
        return this.toApiJsonSerializer.serialize(providers);
    }
}
