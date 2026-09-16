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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleFilterDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleLoansApiConstants;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.RescheduleFromDateStrategy;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
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
    private final BulkRescheduleValidationService validationService;

    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();

    private static final int PREVIEW_PAGE_SIZE = 250;


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

        // 3. Fetch Loans matching the filters
        final List<Long> officeIds = resolveOfficeIds(user.getOffice().getId(), filters.getOfficeId());
        validateExcludedLoans(filters.getExcludedLoanIds(), officeIds);

        final Specification<Loan> specification = LoanBulkRescheduleSpecification.createSpecification(filters, officeIds);

        // 4. Create the first preview, or refresh the preview already created by this UI flow.

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
        execution.setStatus(BulkRescheduleExecution.BulkRescheduleExecutionStatus.PREVIEW);
        execution.setMode(BulkRescheduleExecution.BulkRescheduleMode.DRY_RUN);
        execution.setFiltersJson(gson.toJson(filters));
        execution.setReschedulingDetailsJson(gson.toJson(rescheduleDetailElement));
        execution.setTotalLoansFound(0);
        execution.setTotalSucceeded(0);
        execution.setTotalFailed(0);
        execution.setTotalExecutionFailed(0);
        execution.setTotalExcluded(0);
        execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
        executionRepository.save(execution);

        // Snapshot current loan data and eligibility only. Creating and rolling back a
        // real reschedule request per loan made preview take minutes for 100+ loans.
        int totalExcluded = 0;
        int totalFailed = 0;
        int pageNumber = 0;
        Page<Loan> loanPage;
        do {
            loanPage = loanRepository.findAll(specification, PageRequest.of(pageNumber, PREVIEW_PAGE_SIZE, Sort.by("id").ascending()));
            if (pageNumber == 0) {
                execution.setTotalLoansFound((int) loanPage.getTotalElements());
                executionRepository.save(execution);
            }
            final List<BulkRescheduleResult> results = new ArrayList<>();
            for (Loan loan : loanPage.getContent()) {
                final boolean excluded = filters.getExcludedLoanIds() != null && filters.getExcludedLoanIds().contains(loan.getId());
                final BulkRescheduleResult result = new BulkRescheduleResult();
                result.setExecution(execution);
                result.setLoanId(loan.getId());
                result.setStatus(excluded ? BulkRescheduleResult.BulkRescheduleResultStatus.EXCLUDED
                        : BulkRescheduleResult.BulkRescheduleResultStatus.PREVIEW_MATCHED);
                result.setOriginalInterestRate(currentInterestRate(loan));
                result.setNewInterestRate(details.getNewInterestRate());
                result.setExcludeReason(excluded ? "In manual exclusion list" : null);
                result.setCreatedAt(DateUtils.getLocalDateTimeOfSystem());
                populateSnapshot(result, loan, details);

                try {
                    if (excluded) {
                        totalExcluded++;
                        results.add(result);
                        continue;
                    }
                    final List<String> eligibilityErrors = validationService.validateLoanEligibilityForReschedule(loan);
                    if (!eligibilityErrors.isEmpty()) {
                        throw new IllegalArgumentException(String.join("; ", eligibilityErrors));
                    }
                    final LocalDate rescheduleFromDate = resolveRescheduleFromDate(loan, filters.getRescheduleFromDateStrategy());
                    if (rescheduleFromDate == null) {
                        throw new IllegalArgumentException("Loan has no repayment installment available for the selected strategy");
                    }
                    details.setRescheduleFromDate(rescheduleFromDate);
                    validationService.validateRescheduleParameters(details, loan);
                    populateProposedSnapshot(result, details, rescheduleFromDate);
                } catch (Exception e) {
                    result.setStatus(BulkRescheduleResult.BulkRescheduleResultStatus.FAILED);
                    result.setErrorMessage(e.getMessage() == null ? "Unable to calculate reschedule preview" : e.getMessage());
                    totalFailed++;
                }
                results.add(result);
            }
            resultRepository.saveAllAndFlush(results);
            pageNumber++;
        } while (loanPage.hasNext());

        execution.setTotalExcluded(totalExcluded);
        execution.setTotalFailed(totalFailed);
        execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
        executionRepository.save(execution);

        return new CommandProcessingResultBuilder().withCommandId(request.commandId()).withEntityId(execution.getId())
                .withOfficeId(filters.getOfficeId()).build();

    }

    private BulkRescheduleExecution resolvePreviewExecution(final Long executionId, final Long userId) {
        if (executionId == null) {
            return new BulkRescheduleExecution();
        }
        final BulkRescheduleExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.preview.not.found",
                        "Bulk reschedule preview not found with ID: " + executionId));
        if (execution.getStatus() != BulkRescheduleExecution.BulkRescheduleExecutionStatus.PREVIEW) {
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


    /**
     * Derives the reschedule-from date for an individual loan based on the chosen strategy.
     * FIRST_INSTALLMENT uses the due date of the first installment.
     * NEXT_UNPAID uses the due date of the first installment that is not yet fully paid.
     * Falls back to FIRST_INSTALLMENT behaviour when strategy is null or the schedule is empty.
     */
    private LocalDate resolveRescheduleFromDate(final Loan loan, final RescheduleFromDateStrategy strategy) {
        List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null || installments.isEmpty()) {
            return null;
        }
        if (strategy == RescheduleFromDateStrategy.NEXT_UNPAID) {
            return installments.stream()
                    .filter(i -> !i.isObligationsMet())
                    .map(LoanRepaymentScheduleInstallment::getDueDate)
                    .findFirst()
                    .orElse(null);
        }
        // Default: FIRST_INSTALLMENT
        return installments.get(0).getDueDate();
    }

    private BigDecimal currentInterestRate(final Loan loan) {
        if (loan.getLoanProductRelatedDetail() == null) {
            return null;
        }
        return loan.getLoanProductRelatedDetail().getNominalInterestRatePerPeriod();
    }

    private void populateSnapshot(final BulkRescheduleResult result, final Loan loan, final ReschedulingDetailsDto details) {
        result.setLoanAccountNumber(loan.getAccountNumber());
        result.setAccountNumber(loan.getAccountNumber());
        result.setClientName(loan.getClient() == null ? null : loan.getClient().getDisplayName());
        result.setOfficeId(loan.getOffice() == null ? null : loan.getOffice().getId());
        result.setOfficeName(loan.getOffice() == null ? null : loan.getOffice().getName());
        result.setLoanProductName(loan.getLoanProduct() == null ? null : loan.getLoanProduct().getShortName());
        result.setLoanOfficerId(loan.getLoanOfficer() == null ? null : loan.getLoanOfficer().getId());
        result.setLoanOfficerName(loan.getLoanOfficer() == null ? null : loan.getLoanOfficer().displayName());
        result.setLoanStatus(loan.status() == null ? null : loan.status().toString());
        result.setInterestRateMethod(loan.getLoanProductRelatedDetail() == null || loan.getLoanProductRelatedDetail().getInterestMethod() == null ? null
                : loan.getLoanProductRelatedDetail().getInterestMethod().name());
        result.setTotalOutstanding(loan.getSummary() == null ? null : loan.getSummary().getTotalOutstanding());
        result.setCurrentTerm(loan.getRepaymentScheduleInstallments() == null ? 0
                : (int) loan.getRepaymentScheduleInstallments().stream().filter(i -> !i.isObligationsMet()).count());
        result.setRescheduleReason(details.getRescheduleReasonComment());
    }

    private void populateProposedSnapshot(final BulkRescheduleResult result, final ReschedulingDetailsDto details,
            final LocalDate rescheduleFromDate) {
        final int extraTerms = details.getExtraTerms() == null ? 0 : details.getExtraTerms();
        result.setNewTerm((result.getCurrentTerm() == null ? 0 : result.getCurrentTerm()) + extraTerms);
        result.setNextScheduledInstallment(rescheduleFromDate);
        result.setNewTotalOutstanding(result.getTotalOutstanding());
    }
}
