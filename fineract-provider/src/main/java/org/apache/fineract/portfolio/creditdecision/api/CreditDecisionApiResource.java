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
package org.apache.fineract.portfolio.creditdecision.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionCatalogue;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CommandRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ConnectionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CreateProfileRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogEntryData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogFilter;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ModeRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.NewDraftRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.SaveDraftRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.FlagRequest;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionPolicy;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.VerifyRequest;
import org.apache.fineract.portfolio.creditdecision.service.CreditDecisionLoanService;
import org.apache.fineract.portfolio.creditdecision.service.CreditDecisionLogReader;
import org.apache.fineract.portfolio.creditdecision.service.CreditDecisionService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Admin API for the credit decision profiles. A profile chooses which data is sent to the decision engine for which
 * loan products. It never decides anything: the engine replies with the decision and the log only records it.
 */
@Path("/creditdecision")
@Component
@Scope("singleton")
@RequiredArgsConstructor
@Tag(name = "Credit Decision", description = "Chooses which data is sent to the external decision engine for each loan product, with maker-checker approval. The engine, not Fineract, returns the decision.")
public class CreditDecisionApiResource {

    private static final String RESOURCE = "CREDITDECISION";
    private static final String LOAN_RESOURCE = "LOAN";

    private final PlatformSecurityContext context;
    private final CreditDecisionService service;
    private final CreditDecisionLogReader logReader;
    private final CreditDecisionLoanService loanService;
    private final DefaultToApiJsonSerializer<Object> toApiJsonSerializer;
    private final FromJsonHelper fromJsonHelper;

    @GET
    @Path("catalogue")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Everything that can be sent to the decision engine")
    public String catalogue() {
        read();
        return toApiJsonSerializer.serialize(CreditDecisionCatalogue.groups());
    }

    @GET
    @Path("policy")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The policy settings a version can hold (limits, knockout rules, risk cutoffs, scorecard weights) and the countries they can be set for")
    public String policy() {
        read();
        final List<Map<String, String>> countries = new ArrayList<>();
        CreditDecisionPolicy.countries().forEach((code, name) -> {
            final Map<String, String> country = new LinkedHashMap<>();
            country.put("currency", code);
            country.put("name", name);
            countries.add(country);
        });
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("rules", CreditDecisionPolicy.rules());
        out.put("countries", countries);
        out.put("maxScore", CreditDecisionPolicy.MAX_SCORE);
        return toApiJsonSerializer.serialize(out);
    }

    @GET
    @Path("products")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Loan products and the profile that already uses each one")
    public String products() {
        read();
        return toApiJsonSerializer.serialize(service.products());
    }

    @GET
    @Path("profiles")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List decision profiles with their versions")
    public String profiles() {
        read();
        return toApiJsonSerializer.serialize(service.profiles());
    }

    @GET
    @Path("profiles/{profileId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve one decision profile with its versions")
    public String profile(@PathParam("profileId") final Long profileId) {
        read();
        return toApiJsonSerializer.serialize(service.profile(profileId));
    }

    @GET
    @Path("profiles/{profileId}/history")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "What was done to a profile, by whom and when, with each field before and after, newest first")
    public String history(@PathParam("profileId") final Long profileId) {
        read();
        return toApiJsonSerializer.serialize(service.history(profileId));
    }

    @GET
    @Path("active")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The live version that applies to a loan product, for the service that builds the engine request")
    public String active(@QueryParam("productId") final Long productId) {
        read();
        final Object found = service.activeForProduct(productId);
        return toApiJsonSerializer.serialize(found == null ? new LinkedHashMap<String, Object>() : found);
    }

    @POST
    @Path("profiles")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a profile with a first draft version")
    public String createProfile(final String body) {
        change();
        return toApiJsonSerializer.serialize(service.createProfile(parse(body, CreateProfileRequest.class)));
    }

    @PUT
    @Path("profiles/{profileId}/versions/{versionNo}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Save the data and products of a draft version")
    public String saveDraft(@PathParam("profileId") final Long profileId, @PathParam("versionNo") final int versionNo, final String body) {
        change();
        return toApiJsonSerializer.serialize(service.saveDraft(profileId, versionNo, parse(body, SaveDraftRequest.class)));
    }

    @POST
    @Path("profiles/{profileId}/versions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Start a new draft from the live version, or from an earlier one")
    public String newDraft(@PathParam("profileId") final Long profileId, final String body) {
        change();
        final NewDraftRequest request = parse(body, NewDraftRequest.class);
        return toApiJsonSerializer.serialize(service.newDraft(profileId, request.getFromVersion()));
    }

    @POST
    @Path("profiles/{profileId}/versions/{versionNo}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Move a version along: command=submit, withdraw, approve or reject")
    public String command(@PathParam("profileId") final Long profileId, @PathParam("versionNo") final int versionNo,
            @QueryParam("command") @DefaultValue("") final String command, final String body) {
        final CommandRequest request = parse(body, CommandRequest.class);
        switch (command) {
            case "submit":
                change();
                return toApiJsonSerializer.serialize(service.submit(profileId, versionNo, request.getReason()));
            case "withdraw":
                change();
                return toApiJsonSerializer.serialize(service.withdraw(profileId, versionNo));
            case "approve":
                approve();
                return toApiJsonSerializer.serialize(service.approve(profileId, versionNo));
            case "reject":
                approve();
                return toApiJsonSerializer.serialize(service.reject(profileId, versionNo, request.getReason()));
            default:
                throw new UnrecognizedQueryParamException("command", command, "submit", "withdraw", "approve", "reject");
        }
    }

    @PUT
    @Path("profiles/{profileId}/mode")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Switch a profile Off or to Shadow (recorded only) at once, or ask for Live, which a second authorised person must approve")
    public String mode(@PathParam("profileId") final Long profileId, final String body) {
        change();
        return toApiJsonSerializer.serialize(service.setMode(profileId, parse(body, ModeRequest.class).getMode()));
    }

    @POST
    @Path("profiles/{profileId}/mode")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Answer a request to go Live: command=approve (a second person) or cancel")
    public String modeCommand(@PathParam("profileId") final Long profileId, @QueryParam("command") @DefaultValue("") final String command) {
        switch (command) {
            case "approve":
                approve();
                return toApiJsonSerializer.serialize(service.approveMode(profileId));
            case "cancel":
                approve();
                return toApiJsonSerializer.serialize(service.cancelMode(profileId));
            default:
                throw new UnrecognizedQueryParamException("command", command, "approve", "cancel");
        }
    }

    @GET
    @Path("products/{productId}/profile")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The decision profile a loan product belongs to, so the new loan form can offer the credit decision flag")
    public String productProfile(@PathParam("productId") final Long productId) {
        context.authenticatedUser().validateHasReadPermission(LOAN_RESOURCE);
        final Object found = service.profileForProduct(productId);
        return toApiJsonSerializer.serialize(found == null ? new LinkedHashMap<String, Object>() : found);
    }

    @GET
    @Path("loans/{loanId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Whether a loan application needs an automated credit decision, its verification and the engine outcome")
    public String loanDecision(@PathParam("loanId") final Long loanId) {
        context.authenticatedUser().validateHasReadPermission(LOAN_RESOURCE);
        return toApiJsonSerializer.serialize(loanService.status(loanId));
    }

    @GET
    @Path("exceptions")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Applications whose scoring failed, is held for missing details, or got stuck, oldest first")
    public String exceptions() {
        context.authenticatedUser().validateHasReadPermission(LOAN_RESOURCE);
        return toApiJsonSerializer.serialize(loanService.exceptions());
    }

    @GET
    @Path("loans/{loanId}/history")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "What was done to a loan application's automated credit decision, by whom and when, newest first")
    public String loanHistory(@PathParam("loanId") final Long loanId) {
        context.authenticatedUser().validateHasReadPermission(LOAN_RESOURCE);
        return toApiJsonSerializer.serialize(loanService.history(loanId));
    }

    @PUT
    @Path("loans/{loanId}/flag")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Say whether a loan application needs an automated credit decision")
    public String flagLoan(@PathParam("loanId") final Long loanId, final String body) {
        context.authenticatedUser().validateHasPermissionTo("UPDATE_CREDITDECISIONLOAN");
        return toApiJsonSerializer.serialize(loanService.setRequired(loanId, parse(body, FlagRequest.class).isRequired()));
    }

    @POST
    @Path("loans/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "command=verify or unverify the application details, or send the request to the decision engine")
    public String loanCommand(@PathParam("loanId") final Long loanId, @QueryParam("command") @DefaultValue("") final String command,
            final String body) {
        switch (command) {
            case "verify":
                context.authenticatedUser().validateHasPermissionTo("VERIFY_CREDITDECISIONLOAN");
                return toApiJsonSerializer.serialize(loanService.verify(loanId, parse(body, VerifyRequest.class).getNote()));
            case "unverify":
                context.authenticatedUser().validateHasPermissionTo("VERIFY_CREDITDECISIONLOAN");
                return toApiJsonSerializer.serialize(loanService.unverify(loanId));
            case "send":
                context.authenticatedUser().validateHasPermissionTo("SEND_CREDITDECISIONLOAN");
                return toApiJsonSerializer.serialize(loanService.send(loanId));
            default:
                throw new UnrecognizedQueryParamException("command", command, "verify", "unverify", "send");
        }
    }

    @GET
    @Path("connection")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Which provider and endpoint requests are sent to")
    public String connection() {
        read();
        return toApiJsonSerializer.serialize(service.connection());
    }

    @PUT
    @Path("connection")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Change the provider, endpoint, timeout and what happens when the engine does not reply")
    public String updateConnection(final String body) {
        context.authenticatedUser().validateHasPermissionTo("UPDATE_CREDITDECISIONCONNECTION");
        return toApiJsonSerializer.serialize(service.updateConnection(parse(body, ConnectionData.class)));
    }

    @GET
    @Path("log")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Requests sent to the decision engine and the replies it gave, one page at a time")
    public String log(@QueryParam("decision") final String decision, @QueryParam("status") final String status,
            @QueryParam("profileId") final Long profileId, @QueryParam("mode") final String mode,
            @QueryParam("search") final String search, @QueryParam("reason") final String reason,
            @QueryParam("riskTier") final String riskTier, @QueryParam("modelVersion") final String modelVersion,
            @QueryParam("from") final String from,
            @QueryParam("to") final String to, @QueryParam("sort") final String sort,
            @QueryParam("page") @DefaultValue("1") final int page,
            @QueryParam("pageSize") @DefaultValue("25") final int pageSize) {
        read();
        final LogFilter filter = new LogFilter();
        filter.setDecision(decision);
        filter.setStatus(status);
        filter.setProfileId(profileId);
        filter.setMode(mode);
        filter.setSearch(search);
        filter.setReason(reason);
        filter.setRiskTier(riskTier);
        filter.setModelVersion(modelVersion);
        filter.setFrom(from);
        filter.setTo(to);
        filter.setSort(sort);
        filter.setPage(page);
        filter.setPageSize(pageSize);
        return toApiJsonSerializer.serialize(logReader.page(filter));
    }

    @GET
    @Path("log/{logId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "One log entry in full, with the request and the reply")
    public String logEntry(@PathParam("logId") final Long logId) {
        read();
        return toApiJsonSerializer.serialize(logReader.entry(logId));
    }

    @POST
    @Path("log")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Record a request and the reply, called by the integration service")
    public String recordLog(final String body) {
        context.authenticatedUser().validateHasPermissionTo("CREATE_CREDITDECISIONLOG");
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("resourceId", service.recordLog(parse(body, LogEntryData.class)));
        return toApiJsonSerializer.serialize(out);
    }

    private void read() {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
    }

    private void change() {
        context.authenticatedUser().validateHasPermissionTo("CREATE_CREDITDECISION");
    }

    private void approve() {
        context.authenticatedUser().validateHasPermissionTo("APPROVE_CREDITDECISION");
    }

    private <T> T parse(final String body, final Class<T> type) {
        final String json = body == null || body.isBlank() ? "{}" : body;
        return fromJsonHelper.fromJson(json, type);
    }
}
