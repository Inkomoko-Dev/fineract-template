package org.apache.fineract.portfolio.loanaccount.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.service.TrancheReleaseNotificationService;
import org.springframework.stereotype.Component;

@Path("/loans/{loanId}/tranche-release-report")
@Component
@RequiredArgsConstructor
public class TrancheReleaseReportApiResource {

    private final PlatformSecurityContext context;
    private final TrancheReleaseNotificationService trancheReleaseNotificationService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String report(@PathParam("loanId") final Long loanId) {
        this.context.authenticatedUser().validateHasReadPermission("LOAN");
        return this.trancheReleaseNotificationService.reportJson(loanId);
    }
}
