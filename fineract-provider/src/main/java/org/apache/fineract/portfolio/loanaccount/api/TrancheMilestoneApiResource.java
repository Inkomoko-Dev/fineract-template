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
package org.apache.fineract.portfolio.loanaccount.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.service.TrancheMilestoneEligibilityService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;

@Path("/loans/{loanKey}/tranche-milestones")
@Component
@RequiredArgsConstructor
public class TrancheMilestoneApiResource {

    private final PlatformSecurityContext context;
    private final TrancheMilestoneEligibilityService eligibilityService;

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String submit(@PathParam("loanKey") final String loanKey, final String json) {
        this.context.authenticatedUser().validateHasCreatePermission("TRANCHEMILESTONE");
        return this.eligibilityService.ingest(loanKey, json);
    }

    @POST
    @Path("deadline-exception")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String approveDeadlineException(@PathParam("loanKey") final String loanKey, final String json) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("APPROVE_TRANCHEMILESTONE");
        final String approvedBy = user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getUsername()
                : user.getDisplayName();
        return this.eligibilityService.approveDeadlineException(loanKey, json, approvedBy);
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String read(@PathParam("loanKey") final String loanKey) {
        this.context.authenticatedUser().validateHasReadPermission("LOAN");
        return this.eligibilityService.read(loanKey);
    }
}
