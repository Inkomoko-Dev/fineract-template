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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleFilterDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleLoansApiConstants;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleProgressService.ClaimResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkReschedulePreviewService {

    private final PlatformSecurityContext platformSecurityContext;
    private final LoanRepository loanRepository;
    private final BulkRescheduleExecutionRepository executionRepository;
    private final BulkRescheduleResultRepository resultRepository;
    private final OfficeHierarchyService officeHierarchyService;
    private final BulkReschedulePreviewWorker previewWorker;
    private final BulkRescheduleProgressService progressService;

    @PersistenceContext
    private EntityManager entityManager;

    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();

    private static final int PREVIEW_PAGE_SIZE = 250;
    private static final int LOAN_THREADS = 4;
    private ExecutorService loanExecutor;

    @PostConstruct
    void initializeLoanExecutor() {
        loanExecutor = new ThreadPoolExecutor(LOAN_THREADS, LOAN_THREADS, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    void shutdownLoanExecutor() {
        if (loanExecutor != null) {
            loanExecutor.shutdown();
        }
    }


    public CommandProcessingResult performDryRun(final JsonCommand request) {

        // 1. Extract Filters and Rescheduling Details from the request

        final JsonElement filtersElement = request.jsonElement(BulkRescheduleLoansApiConstants.FILTERS_PARAM_NAME);
        final JsonElement rescheduleDetailElement = request.jsonElement(BulkRescheduleLoansApiConstants.RESCHEDULE_DETAIL_PARAM_NAME);

        // 2. Validate Filters and Details

        final var user = platformSecurityContext.authenticatedUser();
        user.validateHasPermissionTo("CREATE_RESCHEDULELOAN");
        final BulkRescheduleFilterDto filters = gson.fromJson(filtersElement, BulkRescheduleFilterDto.class);

        if (filters == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.request.invalid",
                    "Bulk reschedule request and filters are required");
        }
        if (rescheduleDetailElement == null || !rescheduleDetailElement.isJsonObject()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.details.required",
                    "Rescheduling details are required");
        }
        if (filters.getRescheduleFromDateStrategy() == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.strategy.required",
                    "Reschedule-from-date strategy is required");
        }
        final ReschedulingDetailsDto details = gson.fromJson(rescheduleDetailElement, ReschedulingDetailsDto.class);
        if (details.getSubmittedOnDate() == null || details.getRescheduleReasonId() == null || details.getNewInterestRate() == null
                || details.getOverdueChargeHandling() == null || details.getOverdueChargeHandling().getName() == null
                || details.getOverdueChargeHandling().getName().isBlank()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.details.invalid",
                    "Submitted date, reschedule reason, new interest rate, and overdue charge handling are required");
        }
        final String chargeHandlingName = details.getOverdueChargeHandling().getName();
        if (!org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants.IGNORE_CHARGES
                .equalsIgnoreCase(chargeHandlingName)
                && !org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants.CARRY_CHARGES_FORWARD
                        .equalsIgnoreCase(chargeHandlingName)) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.overdue.charge.handling.invalid",
                    "Overdue charge handling must be Ignore Charges or Carry Charges Forward");
        }
        if (org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants.CARRY_CHARGES_FORWARD
                .equalsIgnoreCase(chargeHandlingName)
                && (details.getCarryForwardChargeId() == null || details.getCarryForwardChargeDueDate() == null)) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.carry.forward.details.required",
                    "Carry-forward charge and due date are required when carrying charges forward");
        }
        if (filters.getCurrentInterestRate() == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.current.rate.required",
                    "Current interest rate is required");
        }
        if (filters.getInterestMethod() == null || filters.getInterestMethod().isBlank()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.interest.method.required",
                    "Interest method is required");
        }
        LoanBulkRescheduleSpecification.parseInterestMethod(filters.getInterestMethod());
        if (filters.getLoanStatus() == null || filters.getLoanStatus().isBlank()) {
            filters.setLoanStatus(String.valueOf(LoanStatus.ACTIVE.getValue()));
        }

        // 3. Persist matching loan IDs only. Row snapshots run in the background.
        final List<Long> officeIds = resolveOfficeIds(user.getOffice().getId(), filters.getOfficeId());
        validateExcludedLoans(filters.getExcludedLoanIds(), officeIds);

        final Specification<Loan> specification = LoanBulkRescheduleSpecification.createSpecification(filters, officeIds);

        final Long previewExecutionId = request.parameterExists("executionId") ? request.longValueOfParameterNamed("executionId") : null;
        final BulkRescheduleExecution execution = resolvePreviewExecution(previewExecutionId, user.getId());
        if (previewExecutionId == null) {
            execution.setUser(user);
            execution.setCreatedAt(DateUtils.getLocalDateTimeOfSystem());
        } else {
            resultRepository.deleteByExecutionId(execution.getId());
            resultRepository.flush();
        }
        execution.setOfficeId(filters.getOfficeId() != null ? filters.getOfficeId() : user.getOffice().getId());
        execution.setStatus(BulkRescheduleExecutionStatus.PREVIEWING);
        execution.setMode(BulkRescheduleExecution.BulkRescheduleMode.DRY_RUN);
        execution.setFiltersJson(gson.toJson(filters));
        execution.setReschedulingDetailsJson(gson.toJson(rescheduleDetailElement));
        execution.setWorkerToken(null);
        execution.setLeaseExpiresAt(null);
        execution.setLastHeartbeatAt(null);
        execution.setTotalLoansFound(0);
        execution.setTotalSucceeded(0);
        execution.setTotalFailed(0);
        execution.setTotalExecutionFailed(0);
        execution.setTotalExcluded(0);
        execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
        executionRepository.save(execution);

        final List<Object[]> rows = findMatchingLoanDisplays(specification);
        execution.setTotalLoansFound(rows.size());
        int totalExcluded = 0;
        final List<BulkRescheduleResult> batch = new ArrayList<>();
        for (Object[] row : rows) {
            final Long loanId = (Long) row[0];
            final boolean excluded = filters.getExcludedLoanIds() != null && filters.getExcludedLoanIds().contains(loanId);
            final BulkRescheduleResult result = new BulkRescheduleResult();
            result.setExecution(execution);
            result.setLoanId(loanId);
            result.setLoanAccountNumber((String) row[1]);
            result.setAccountNumber((String) row[1]);
            result.setClientName((String) row[2]);
            result.setOfficeId((Long) row[3]);
            result.setOfficeName((String) row[4]);
            result.setLoanProductName((String) row[5]);
            result.setLoanOfficerId((Long) row[6]);
            result.setLoanOfficerName((String) row[7]);
            result.setLoanStatus(loanStatusLabel(row[8]));
            result.setOriginalInterestRate((java.math.BigDecimal) row[9]);
            result.setInterestRateMethod(row[10] == null ? null : row[10].toString());
            result.setStatus(excluded ? BulkRescheduleResultStatus.EXCLUDED : BulkRescheduleResultStatus.PREVIEW_MATCHED);
            result.setNewInterestRate(details.getNewInterestRate());
            result.setExcludeReason(excluded ? "In manual exclusion list" : null);
            result.setCreatedAt(DateUtils.getLocalDateTimeOfSystem());
            if (excluded) {
                totalExcluded++;
            }
            batch.add(result);
            if (batch.size() == PREVIEW_PAGE_SIZE) {
                resultRepository.saveAllAndFlush(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            resultRepository.saveAllAndFlush(batch);
        }
        execution.setTotalExcluded(totalExcluded);
        execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
        executionRepository.save(execution);

        return new CommandProcessingResultBuilder().withCommandId(request.commandId()).withEntityId(execution.getId())
                .withOfficeId(filters.getOfficeId()).build();

    }

    public void runPreviewEnrichment(final Long executionId) {
        final String workerToken = UUID.randomUUID().toString();
        if (progressService.claimPreview(executionId, workerToken) == ClaimResult.NONE) {
            log.info("Preview {} has an active worker or is not previewing", executionId);
            return;
        }
        final BulkRescheduleExecution execution = executionRepository.findById(executionId).orElseThrow();
        final BulkRescheduleFilterDto filters = gson.fromJson(execution.getFiltersJson(), BulkRescheduleFilterDto.class);
        final ReschedulingDetailsDto details = gson.fromJson(execution.getReschedulingDetailsJson(), ReschedulingDetailsDto.class);
        final FineractContext context = copyContext(ThreadLocalContextUtil.getContext());
        try {
            while (true) {
                final Page<BulkRescheduleResult> page = resultRepository.findUnsnapshottedByExecutionId(executionId,
                        BulkRescheduleResultStatus.PREVIEW_MATCHED,
                        PageRequest.of(0, PREVIEW_PAGE_SIZE, Sort.by("id")));
                if (page.isEmpty()) {
                    break;
                }
                if (!progressService.renewLease(executionId, workerToken)) {
                    log.warn("Preview {} lost its worker lease; stopping this worker", executionId);
                    return;
                }
                final List<Future<?>> tasks = new ArrayList<>();
                for (BulkRescheduleResult result : page.getContent()) {
                    final Long resultId = result.getId();
                    tasks.add(loanExecutor.submit(() -> enrichResult(resultId, details, filters, context)));
                }
                for (Future<?> task : tasks) {
                    progressService.renewLease(executionId, workerToken);
                    try {
                        task.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Bulk reschedule preview was interrupted", e);
                    } catch (ExecutionException e) {
                        log.error("Unexpected error while enriching preview {}", executionId, e);
                    }
                }
            }
            progressService.completePreview(executionId, workerToken);
        } catch (Exception e) {
            log.error("Background preview failed for execution {}", executionId, e);
            throw e;
        }
    }

    private void enrichResult(final Long resultId, final ReschedulingDetailsDto details, final BulkRescheduleFilterDto filters,
            final FineractContext context) {
        final boolean inheritedContext = ThreadLocalContextUtil.getTenant() != null;
        try {
            ThreadLocalContextUtil.init(copyContext(context));
            previewWorker.enrichResult(resultId, details, filters.getRescheduleFromDateStrategy());
        } catch (Exception e) {
            log.error("Error enriching preview result {}: {}", resultId, e.getMessage(), e);
            try {
                ThreadLocalContextUtil.init(copyContext(context));
                previewWorker.markFailed(resultId, e.getMessage());
            } catch (Exception persistFailure) {
                log.error("Could not persist preview failure for result {}", resultId, persistFailure);
            }
        } finally {
            if (!inheritedContext) {
                ThreadLocalContextUtil.clear();
            } else {
                ThreadLocalContextUtil.init(copyContext(context));
            }
        }
    }

    private List<Object[]> findMatchingLoanDisplays(final Specification<Loan> specification) {
        final CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Object[]> query = builder.createQuery(Object[].class);
        final Root<Loan> root = query.from(Loan.class);
        final var client = root.join("client", javax.persistence.criteria.JoinType.LEFT);
        final var loanOffice = root.join("office", javax.persistence.criteria.JoinType.LEFT);
        final var clientOffice = client.join("office", javax.persistence.criteria.JoinType.LEFT);
        final var product = root.join("loanProduct", javax.persistence.criteria.JoinType.LEFT);
        final var officer = root.join("loanOfficer", javax.persistence.criteria.JoinType.LEFT);
        query.multiselect(root.get("id"), root.get("accountNumber"), client.get("displayName"),
                builder.coalesce(loanOffice.get("id"), clientOffice.get("id")),
                builder.coalesce(loanOffice.get("name"), clientOffice.get("name")), product.get("shortName"), officer.get("id"),
                officer.get("displayName"), root.get("loanStatus"),
                root.get("loanRepaymentScheduleDetail").get("nominalInterestRatePerPeriod"),
                root.get("loanRepaymentScheduleDetail").get("interestMethod"));
        query.where(specification.toPredicate(root, query, builder));
        query.orderBy(builder.asc(root.get("id")));
        return entityManager.createQuery(query).getResultList();
    }

    private static String loanStatusLabel(final Object statusValue) {
        if (statusValue == null) {
            return null;
        }
        if (statusValue instanceof Number) {
            return LoanStatus.fromInt(((Number) statusValue).intValue()).toString();
        }
        return statusValue.toString();
    }

    private FineractContext copyContext(final FineractContext source) {
        return new FineractContext(source.getContextHolder(), source.getTenantContext(), source.getAuthTokenContext(),
                source.getBusinessDateContext() == null ? null : new HashMap<>(source.getBusinessDateContext()),
                source.getActionContext());
    }

    private BulkRescheduleExecution resolvePreviewExecution(final Long executionId, final Long userId) {
        if (executionId == null) {
            return new BulkRescheduleExecution();
        }
        final BulkRescheduleExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.preview.not.found",
                        "Bulk reschedule preview not found with ID: " + executionId));
        if (execution.getStatus() != BulkRescheduleExecutionStatus.PREVIEW
                && execution.getStatus() != BulkRescheduleExecutionStatus.PREVIEWING) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.preview.cannot.refresh",
                    "Only a request in preview status can be refreshed");
        }
        if (execution.getUser() == null || !userId.equals(execution.getUser().getId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.preview.owner.required",
                    "Only the user who created this preview can refresh it");
        }
        return execution;
    }

    private List<Long> resolveOfficeIds(final Long userOfficeId, final Long selectedOfficeId) {
        if (selectedOfficeId == null) {
            return officeHierarchyService.getOfficeAndChildBranches(userOfficeId);
        }
        final var user = platformSecurityContext.authenticatedUser();
        if (!officeHierarchyService.validateUserAccessToOffice(user, selectedOfficeId)) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.office.access.denied",
                    "User does not have access to office: " + selectedOfficeId);
        }
        return officeHierarchyService.getOfficeAndChildBranches(selectedOfficeId);
    }

    private void validateExcludedLoans(final List<Long> excludedLoanIds, final List<Long> officeIds) {
        if (excludedLoanIds == null || excludedLoanIds.isEmpty()) {
            return;
        }
        final List<Loan> excludedLoans = loanRepository.findAllById(excludedLoanIds);
        final Set<Long> foundIds = excludedLoans.stream().map(Loan::getId).collect(java.util.stream.Collectors.toSet());
        if (foundIds.size() != Set.copyOf(excludedLoanIds).size()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.excluded.loan.not.found",
                    "One or more excluded loans could not be found");
        }
        final boolean inaccessible = excludedLoans.stream().anyMatch(loan -> loan.getOffice() == null
                || !officeIds.contains(loan.getOffice().getId()));
        if (inaccessible) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.excluded.loan.office.denied",
                    "All excluded loans must belong to the selected office or one of its child offices");
        }
    }
}
