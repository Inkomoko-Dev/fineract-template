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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.organisation.office.exception.OfficeNotFoundException;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleExecutionDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleResponseDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.TemplateDataDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleLoanPreviewDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionSpecifications;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleAuditRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleExecutionService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleResultExportService;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * REST API Resource for bulk loan reschedule operations. Provides endpoints for template data retrieval,
 * dry run execution, approval workflow, and query operations.
 */
@Slf4j
@Path("/bulk-reschedule")
@Component
@Scope("singleton")
@Tag(name = "Bulk Reschedule", description = "APIs for bulk loan reschedule operations")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class BulkRescheduleApiResource {

    private final DefaultToApiJsonSerializer<Object> apiJsonSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;
    private final PlatformSecurityContext platformSecurityContext;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final BulkRescheduleService bulkRescheduleService;
    private final BulkRescheduleExecutionService bulkRescheduleExecutionService;
    private final BulkRescheduleExecutionRepository executionRepository;
    private final BulkRescheduleResultRepository resultRepository;
    private final OfficeRepository officeRepository;
    private final OfficeHierarchyService officeHierarchyService;
    private final AppUserRepository appUserRepository;
    private final BulkRescheduleAuditRepository auditRepository;
    private final BulkRescheduleResultExportService resultExportService;
    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();

    /**
     * Retrieves template data for bulk reschedule form including filter options, validation rules,
     * and user permissions.
     *
     * @param officeId the office ID to retrieve template data for (optional, defaults to user's office)
     * @param uriInfo URI information
     * @return JSON response containing template data
     */
    @GET
    @Path("template-data")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get template data for bulk reschedule form")
    public String getTemplateData(@QueryParam("officeId") final Long officeId, @Context final UriInfo uriInfo) {
        log.info("Retrieving template data for office: {}", officeId);
        final var user = platformSecurityContext.authenticatedUser();

        Long actualOfficeId = officeId;
        if (actualOfficeId == null) {
            actualOfficeId = user.getOffice().getId();
        }
        if (!officeHierarchyService.validateUserAccessToOffice(user, actualOfficeId)) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.office.access.denied",
                    "User does not have access to office: " + actualOfficeId);
        }

        TemplateDataDto templateData = bulkRescheduleService.getTemplateData(actualOfficeId);
        final ApiRequestJsonSerializationSettings settings = apiRequestParameterHelper
                .process(uriInfo.getQueryParameters());
        return apiJsonSerializer.serialize(settings, templateData);
    }

    /**
     * Submits a bulk reschedule request for dry run processing. Creates a preview of which loans
     * would be affected without making any changes.
     *
     * @param apiRequestBodyAsJson the request body as JSON containing filter and reschedule details
     * @param uriInfo URI information
     * @return JSON response containing execution ID and preview data
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Submit bulk reschedule request for dry run")
    public String submitBulkReschedule(final String apiRequestBodyAsJson, @Context final UriInfo uriInfo) {
        log.info("Submitting bulk reschedule request");
        platformSecurityContext.authenticatedUser();

        try {
            JsonObject payload = StringUtils.isBlank(apiRequestBodyAsJson) ? new JsonObject()
                    : gson.fromJson(apiRequestBodyAsJson, JsonObject.class);
            if (!payload.has("dryRun") || payload.get("dryRun").isJsonNull()) {
                payload.addProperty("dryRun", true);
            }

            final CommandWrapper commandRequest = new CommandWrapperBuilder().createBulkReschedule().withJson(gson.toJson(payload))
                    .build();
            return executeCommand(commandRequest);
        } catch (Exception e) {
            log.error("Error processing bulk reschedule request", e);
            throw e;
        }
    }

    @GET
    @Path("{executionId}/preview")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get paginated bulk reschedule preview")
    public String getPreview(@PathParam("executionId") final Long executionId,
            @QueryParam("page") @DefaultValue("0") final Integer page,
            @QueryParam("size") @DefaultValue("100") final Integer size,
            @QueryParam("status") final String status) {
        final var user = platformSecurityContext.authenticatedUser();
        final BulkRescheduleExecution execution = findAccessibleExecution(executionId, user);
        validateCanViewExecution(user, execution);
        final int requestedPage = page == null ? 0 : Math.max(0, page);
        final int requestedSize = size == null ? 100 : Math.min(500, Math.max(1, size));
        final BulkRescheduleResultStatus resultStatus = StringUtils.isBlank(status) ? null
                : BulkRescheduleResultStatus.valueOf(status.trim().toUpperCase());
        final Pageable resultPageable = PageRequest.of(requestedPage, requestedSize, Sort.by("createdAt").descending());
        final Page<BulkRescheduleResult> resultPage = resultStatus == null
                ? resultRepository.findPageByExecutionId(executionId, resultPageable)
                : resultRepository.findPageByExecutionIdAndStatus(executionId, resultStatus, resultPageable);


        final JsonObject response = new JsonObject();
        response.addProperty("executionId", execution.getId());
        response.addProperty("page", resultPage.getNumber());
        response.addProperty("size", resultPage.getSize());
        response.addProperty("totalElements", resultPage.getTotalElements());
        response.addProperty("totalPages", resultPage.getTotalPages());
        response.addProperty("totalMatched", resultRepository.countByExecutionIdAndStatus(executionId,
                BulkRescheduleResultStatus.PREVIEW_MATCHED));
        response.addProperty("totalSucceeded", resultRepository.countByExecutionIdAndStatus(executionId,
                BulkRescheduleResultStatus.SUCCEEDED));
        response.addProperty("totalExcluded", resultRepository.countByExecutionIdAndStatus(executionId,
                BulkRescheduleResultStatus.EXCLUDED));
        response.addProperty("totalFailed", resultRepository.countByExecutionIdAndStatus(executionId,
                BulkRescheduleResultStatus.FAILED));
        response.add("pageItems", gson.toJsonTree(resultPage.getContent().stream().map(BulkRescheduleLoanPreviewDto::fromResult).toList()));
        return response.toString();
    }

    @GET
    @Path("{executionId}/approvers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List eligible approvers for a bulk reschedule execution")
    public String getEligibleApprovers(@PathParam("executionId") final Long executionId) {
        final var currentUser = platformSecurityContext.authenticatedUser();
        final BulkRescheduleExecution execution = findAccessibleExecution(executionId, currentUser);
        if (!execution.getUser().getId().equals(currentUser.getId()) && !hasPermission(currentUser, "CREATE_RESCHEDULELOAN")) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.approvers.access.denied",
                    "Only the request creator or a user with create permission can choose an approver");
        }
        final var approvers = appUserRepository.findEnabledUsersWithPermission("APPROVE_RESCHEDULELOAN").stream()
                .filter(candidate -> !candidate.getId().equals(currentUser.getId()))
                .filter(candidate -> officeHierarchyService.validateUserAccessToOffice(candidate, execution.getOfficeId()))
                .map(candidate -> Map.of("id", candidate.getId(), "username", candidate.getUsername(), "displayName",
                        StringUtils.defaultIfBlank(candidate.getDisplayName(), candidate.getUsername())))
                .sorted((left, right) -> String.valueOf(left.get("displayName")).compareToIgnoreCase(String.valueOf(right.get("displayName"))))
                .toList();
        return gson.toJson(approvers);
    }

    @GET
    @Path("{executionId}/results.csv")
    @Produces("text/csv")
    @Operation(summary = "Download all bulk reschedule results as CSV")
    public Response downloadResultsCsv(@PathParam("executionId") final Long executionId) {
        final var user = platformSecurityContext.authenticatedUser();
        final BulkRescheduleExecution execution = findAccessibleExecution(executionId, user);
        validateCanViewExecution(user, execution);
        return Response.ok(resultExportService.csv(execution), "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=bulk-reschedule-" + executionId + "-results.csv").build();
    }

    @GET
    @Path("{executionId}/audit")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get bulk reschedule workflow audit history")
    public String getAuditHistory(@PathParam("executionId") final Long executionId) {
        final var user = platformSecurityContext.authenticatedUser();
        user.validateHasPermissionTo("READ_AUDIT");
        final BulkRescheduleExecution execution = findAccessibleExecution(executionId, user);
        final var items = auditRepository.findByExecutionIdOrdered(execution.getId()).stream().map(audit -> {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", audit.getId());
            item.put("executionId", execution.getId());
            item.put("action", audit.getAction() == null ? null : audit.getAction().name());
            item.put("actorId", audit.getActor() == null ? null : audit.getActor().getId());
            item.put("actorUsername", audit.getActor() == null ? null : audit.getActor().getUsername());
            item.put("timestamp", audit.getTimestamp());
            item.put("details", audit.getDetailsJson());
            return item;
        }).toList();
        return gson.toJson(items);
    }

    private boolean hasPermission(final org.apache.fineract.useradministration.domain.AppUser user, final String permission) {
        try {
            user.validateHasPermissionTo(permission);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void validateCanViewExecution(final org.apache.fineract.useradministration.domain.AppUser user,
            final BulkRescheduleExecution execution) {
        if (!execution.getUser().getId().equals(user.getId()) && !hasPermission(user, "READ_RESCHEDULELOAN")
                && !hasPermission(user, "CREATE_RESCHEDULELOAN") && !hasPermission(user, "APPROVE_RESCHEDULELOAN")) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.read.denied",
                    "User does not have permission to view this bulk reschedule execution");
        }
    }

    /**
     * Submits a previewed bulk reschedule request for approval.
     *
     * Transitions the execution from PREVIEW to PENDING_APPROVAL.
     */
    @POST
    @Path("{executionId}/submit-for-approval")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Submit a bulk reschedule execution for approval")
    public String submitForApproval(@PathParam("executionId") final Long executionId,
                                    final String apiRequestBodyAsJson) {

        log.info("Submitting bulk reschedule execution for approval: {}", executionId);
        platformSecurityContext.authenticatedUser();

        try {
            final CommandWrapper commandRequest = new CommandWrapperBuilder()
                    .submitBulkRescheduleForApproval(executionId)
                    .withJson(StringUtils.defaultIfBlank(apiRequestBodyAsJson, "{}"))
                    .build();

            return executeCommand(commandRequest);
        } catch (Exception e) {
            log.error("Error submitting bulk reschedule execution for approval: {}", executionId, e);
            throw e;
        }
    }

    @DELETE
    @Path("{executionId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cancel and delete a bulk reschedule preview")
    public String deletePreview(@PathParam("executionId") final Long executionId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteBulkReschedule(executionId).withJson("{}").build();
        return executeCommand(commandRequest);
    }

    /**
     * Approves a bulk reschedule execution after preview validation and immediately triggers
     * execution in the same workflow.
     *
     * @param executionId the ID of the execution to approve
     * @param apiRequestBodyAsJson the request body as JSON containing optional approval note
     * @param uriInfo URI information
     * @return JSON response containing updated execution details
     */
    @POST
    @Path("{executionId}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Approve a bulk reschedule execution")
    public String approveExecution(@PathParam("executionId") final Long executionId,
                                   final String apiRequestBodyAsJson, 
                                   @Context final UriInfo uriInfo) {
        log.info("Approving bulk reschedule execution: {}", executionId);
        platformSecurityContext.authenticatedUser();

        try {
            final CommandWrapper commandRequest = new CommandWrapperBuilder().approveBulkReschedule(executionId)
                    .withJson(StringUtils.defaultIfBlank(apiRequestBodyAsJson, "{}")).build();
            return executeCommand(commandRequest);
        } catch (Exception e) {
            log.error("Error approving bulk reschedule execution", e);
            throw e;
        }
    }

    /**
     * Rejects a bulk reschedule execution. Moves the execution from PREVIEW status to REJECTED status.
     *
     * @param executionId the ID of the execution to reject
     * @param apiRequestBodyAsJson the request body as JSON containing rejection reason
     * @param uriInfo URI information
     * @return JSON response indicating rejection success
     */
    @POST
    @Path("{executionId}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reject a bulk reschedule execution")
    public String rejectExecution(@PathParam("executionId") final Long executionId,
                                  final String apiRequestBodyAsJson, 
                                  @Context final UriInfo uriInfo) {
        log.info("Rejecting bulk reschedule execution: {}", executionId);
        platformSecurityContext.authenticatedUser();

        try {
            final CommandWrapper commandRequest = new CommandWrapperBuilder().rejectBulkReschedule(executionId)
                    .withJson(StringUtils.defaultIfBlank(apiRequestBodyAsJson, "{}")).build();
            return executeCommand(commandRequest);
        } catch (Exception e) {
            log.error("Error rejecting bulk reschedule execution", e);
            throw e;
        }
    }

    /**
     * Executes a bulk reschedule operation explicitly. This remains available for retries/manual
     * execution when an execution is already in APPROVED status.
     *
     * This endpoint is IDEMPOTENT - calling it multiple times safely processes only new loans.
     * Retries and network failures are safe and won't cause duplicate reschedules.
     *
     * @param executionId the ID of the execution to execute
     * @param uriInfo URI information
     * @return JSON response containing execution results with succeeded/failed/skipped counts
     */
    @POST
    @Path("{executionId}/execute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Execute a bulk reschedule operation")
    public String executeReschedule(@PathParam("executionId") final Long executionId,
                                    @Context final UriInfo uriInfo) {
        log.info("Executing bulk reschedule: {}", executionId);
        platformSecurityContext.authenticatedUser();

        try {
            final BulkRescheduleResponseDto response = bulkRescheduleExecutionService.executeReschedule(executionId);
            final ApiRequestJsonSerializationSettings settings = apiRequestParameterHelper
                    .process(uriInfo.getQueryParameters());
            return apiJsonSerializer.serialize(settings, response);
        } catch (Exception e) {
            log.error("Error executing bulk reschedule", e);
            throw e;
        }
    }

    @POST
    @Path("{executionId}/recover")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Resume an interrupted bulk reschedule execution")
    public String recoverExecution(@PathParam("executionId") final Long executionId) {
        platformSecurityContext.authenticatedUser();
        final CommandWrapper commandRequest = new CommandWrapperBuilder().recoverBulkReschedule(executionId).withJson("{}").build();
        return executeCommand(commandRequest);
    }

    /**
     * Rolls back a previously executed bulk reschedule operation.
     * 
     * Reverses all successfully rescheduled loans back to their original schedules.
     *
     * @param executionId the ID of the execution to rollback
     * @param apiRequestBodyAsJson the request body as JSON containing rollback reason
     * @param uriInfo URI information
     * @return JSON response containing rollback results
     */
    @POST
    @Path("{executionId}/rollback")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rollback a bulk reschedule execution")
    public String rollbackExecution(@PathParam("executionId") final Long executionId,
                                    final String apiRequestBodyAsJson, 
                                    @Context final UriInfo uriInfo) {
        log.info("Rolling back bulk reschedule execution: {}", executionId);
        platformSecurityContext.authenticatedUser();

        try {
            final CommandWrapper commandRequest = new CommandWrapperBuilder().rollbackBulkReschedule(executionId)
                    .withJson(StringUtils.defaultIfBlank(apiRequestBodyAsJson, "{}")).build();
            return executeCommand(commandRequest);
        } catch (Exception e) {
            log.error("Error rolling back bulk reschedule execution", e);
            throw e;
        }
    }


    /**
     * Retrieves details of a specific bulk reschedule execution including current status, results,
     * and audit trail.
     *
     * @param executionId the ID of the execution to retrieve
     * @param uriInfo URI information
     * @return JSON response containing execution details
     */
    @GET
    @Path("{executionId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get details of a specific bulk reschedule execution")
    public String getExecution(@PathParam("executionId") final Long executionId,
                               @Context final UriInfo uriInfo) {
        log.info("Retrieving bulk reschedule execution: {}", executionId);
        final var user = platformSecurityContext.authenticatedUser();
        final BulkRescheduleExecution execution = findAccessibleExecution(executionId, user);
        validateCanViewExecution(user, execution);
        final String officeName = officeRepository.findById(execution.getOfficeId()).map(Office::getName).orElse(null);
        final BulkRescheduleExecutionDto response = BulkRescheduleExecutionDto.toExecutionDto(execution, officeName);
        applyLiveResultCounts(response, executionId);

        final ApiRequestJsonSerializationSettings settings = apiRequestParameterHelper
                .process(uriInfo.getQueryParameters());
        return apiJsonSerializer.serialize(settings, response);
    }

    private void applyLiveResultCounts(final BulkRescheduleExecutionDto response, final Long executionId) {
        final int succeeded = (int) resultRepository.countByExecutionIdAndStatus(executionId,
                BulkRescheduleResultStatus.SUCCEEDED);
        final int failed = (int) resultRepository.countByExecutionIdAndStatus(executionId,
                BulkRescheduleResultStatus.FAILED);
        final int remaining = (int) resultRepository.countByExecutionIdAndStatus(executionId,
                BulkRescheduleResultStatus.PREVIEW_MATCHED);
        response.setTotalSucceeded(succeeded);
        response.setTotalFailed(failed);
        response.setTotalProcessed(succeeded + (response.getTotalExecutionFailed() == null ? 0 : response.getTotalExecutionFailed()));
        response.setTotalRemaining(remaining);
    }


    /**
     * Lists bulk reschedule executions with optional filtering by status, office, and date range.
     * Supports pagination via limit and offset parameters.
     *
     * @param status filter by execution status (optional)
     * @param officeId filter by office ID (optional)
     * @param dateRange date range filter in format "startDate,endDate" (optional)
     * @param limit maximum number of results to return (default: 50)
     * @param offset number of results to skip (default: 0)
     * @param uriInfo URI information
     * @return JSON response containing list of executions
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List bulk reschedule executions with optional filters")
    public String listExecutions(@QueryParam("status") final String status,
                                 @QueryParam("officeId") final Long officeId,
                                 @QueryParam("assignedToMe") @DefaultValue("false") final Boolean assignedToMe,
                                 @QueryParam("dateRange") final String dateRange,
                                 @QueryParam("limit") @DefaultValue("50") final Integer limit,
                                 @QueryParam("offset") @DefaultValue("0") final Integer offset,
                                 @Context final UriInfo uriInfo) {

        log.info(
                "Listing bulk reschedule executions - status: {}, officeId: {}, limit: {}, offset: {}",
                status, officeId, limit, offset);

        final var user = platformSecurityContext.authenticatedUser();
        if (!hasPermission(user, "READ_RESCHEDULELOAN") && !hasPermission(user, "CREATE_RESCHEDULELOAN")
                && !hasPermission(user, "APPROVE_RESCHEDULELOAN")) {
            user.validateHasPermissionTo("READ_RESCHEDULELOAN");
        }

        try {
            /*
             * If no office is supplied, default to the authenticated user's office.
             * The selected office and all descendant offices are included.
             */
            final List<Long> accessibleOfficeIds = officeHierarchyService.getUserAccessibleOffices(user);
            final Long filterOfficeId = officeId != null ? officeId : user.getOffice().getId();
            if (!accessibleOfficeIds.contains(filterOfficeId)) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.office.access.denied",
                        "User does not have access to office: " + filterOfficeId);
            }

            final Office filterOffice = officeRepository.findById(filterOfficeId).orElseThrow(() -> new OfficeNotFoundException(filterOfficeId));

            final String hierarchy = filterOffice.getHierarchy();

            final List<Office> offices = officeRepository.findByHierarchyStartingWith(hierarchy).stream()
                    .filter(office -> accessibleOfficeIds.contains(office.getId()))
                    .toList();

            final List<Long> officeIds = offices.stream()
                    .map(Office::getId)
                    .distinct()
                    .toList();

            final Map<Long, String> officeNames = offices.stream()
                    .collect(Collectors.toMap(
                            Office::getId,
                            Office::getName
                    ));

            log.debug("Resolved bulk reschedule office hierarchy - rootOfficeId: {}, officeIds: {}", filterOfficeId, officeIds);

            /*
             * Status filter.
             */
            final BulkRescheduleExecutionStatus statusEnum =
                    StringUtils.isNotBlank(status) ? BulkRescheduleExecutionStatus.valueOf(status.trim().toUpperCase()) : null;

            /*
             * Date range filter.
             */
            LocalDateTime startDate = null;
            LocalDateTime endDate = null;

            if (StringUtils.isNotBlank(dateRange)) {

                final int commaIndex = dateRange.indexOf(',');

                if (commaIndex > 0 && commaIndex < dateRange.length() - 1) {
                    startDate = parseDateBoundary(dateRange.substring(0, commaIndex).trim(), false);
                    endDate = parseDateBoundary(dateRange.substring(commaIndex + 1).trim(), true);
                }
            }

            /*
             * Build dynamic specification.
             */
            final Specification<BulkRescheduleExecution> specification =
                    Specification
                            .where(BulkRescheduleExecutionSpecifications.officeIdIn(officeIds))
                            .and(BulkRescheduleExecutionSpecifications.hasStatus(statusEnum))
                            .and(BulkRescheduleExecutionSpecifications.assignedTo(Boolean.TRUE.equals(assignedToMe) ? user.getId() : null))
                            .and(BulkRescheduleExecutionSpecifications.createdFrom(startDate))
                            .and(BulkRescheduleExecutionSpecifications.createdUntil(endDate));

            /*
             * Pagination.
             *
             * Existing API uses offset + limit.
             * Convert that to Spring Data page + size.
             */
            final int safeLimit = Math.min(500, Math.max(1, limit == null ? 50 : limit));
            final int safeOffset = Math.max(0, offset == null ? 0 : offset);

            final int pageNumber =
                    safeOffset / safeLimit;

            final Pageable pageable = PageRequest.of(
                            pageNumber, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt")
            );

            final Page<BulkRescheduleExecution> executionPage =
                    executionRepository.findAll(specification, pageable);

            /*
             * Build response.
             */
            final JsonObject response = new JsonObject();

            response.addProperty( "totalCount", executionPage.getTotalElements());
            response.addProperty("offset", safeOffset);
            response.addProperty("limit", safeLimit);
            response.addProperty("page", executionPage.getNumber());
            response.addProperty("totalPages", executionPage.getTotalPages());

            final List<BulkRescheduleExecutionDto> pageItems =
                    executionPage.getContent()
                            .stream()
                            .map(execution ->
                                    BulkRescheduleExecutionDto.toExecutionDto(
                                            execution,
                                            officeNames.get(execution.getOfficeId())))
                            .collect(Collectors.toList());

            response.add(
                    "pageItems",
                    gson.toJsonTree(pageItems));
            return response.toString();

        } catch (Exception e) {
            log.error(
                    "Error listing bulk reschedule executions",
                    e);
            throw e;
        }
    }

    private String executeCommand(final CommandWrapper commandRequest) {
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        if (result.getChanges() != null && result.getChanges().containsKey("response")) {
            return String.valueOf(result.getChanges().get("response"));
        }
        return this.commandProcessingResultSerializer.serialize(result);
    }

    private BulkRescheduleExecution findAccessibleExecution(final Long executionId,
            final org.apache.fineract.useradministration.domain.AppUser user) {
        final BulkRescheduleExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                        "Execution not found with ID: " + executionId));
        if (!officeHierarchyService.validateUserAccessToOffice(user, execution.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.access.denied",
                    "User does not have access to this bulk reschedule execution");
        }
        return execution;
    }

    private LocalDateTime parseDateBoundary(final String value, final boolean endOfDay) {
        if (value.contains("T")) {
            return LocalDateTime.parse(value);
        }
        final LocalDate date = LocalDate.parse(value);
        return endOfDay ? date.plusDays(1).atStartOfDay().minusNanos(1) : date.atStartOfDay();
    }
}
