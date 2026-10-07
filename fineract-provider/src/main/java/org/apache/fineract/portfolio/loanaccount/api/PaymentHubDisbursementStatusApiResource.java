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
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.PaymentHubDisbursementStatusData;
import org.apache.fineract.portfolio.loanaccount.service.PaymentHubDisbursementStatusService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/loans/{loanId}/paymenthub-disbursement-status")
@Component
@Scope("singleton")
public class PaymentHubDisbursementStatusApiResource {

    private final PlatformSecurityContext context;
    private final PaymentHubDisbursementStatusService paymentHubDisbursementStatusService;
    private final DefaultToApiJsonSerializer<PaymentHubDisbursementStatusData> toApiJsonSerializer;

    public PaymentHubDisbursementStatusApiResource(final PlatformSecurityContext context,
            final PaymentHubDisbursementStatusService paymentHubDisbursementStatusService,
            final DefaultToApiJsonSerializer<PaymentHubDisbursementStatusData> toApiJsonSerializer) {
        this.context = context;
        this.paymentHubDisbursementStatusService = paymentHubDisbursementStatusService;
        this.toApiJsonSerializer = toApiJsonSerializer;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String queryStatus(@PathParam("loanId") final Long loanId) {
        this.context.authenticatedUser().validateHasPermissionTo("DISBURSE_LOAN");
        return this.toApiJsonSerializer.serialize(this.paymentHubDisbursementStatusService.queryForLoan(loanId));
    }
}
