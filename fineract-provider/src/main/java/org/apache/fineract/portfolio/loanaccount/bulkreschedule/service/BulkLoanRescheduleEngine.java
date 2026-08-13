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
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ReschedulingDetailsDto;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.service.LoanRescheduleRequestWritePlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * Engine for performing loan reschedule operations using the individual reschedule service.
 * Delegates to LoanRescheduleRequestWritePlatformService to ensure bulk reschedules use
 * the exact same validation and processing logic as single-loan reschedules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkLoanRescheduleEngine {
    private final LoanRescheduleRequestWritePlatformService loanRescheduleRequestService;
    private final FromJsonHelper fromJsonHelper;
    private final Gson gson = GoogleGsonSerializerHelper.createGsonBuilder().create();
    /**
     * Performs reschedule operation on a loan by delegating to the individual
     * reschedule service. This ensures bulk reschedules use the exact same
     * validation and processing logic as single-loan reschedules.
     *
     * @param loan the loan to reschedule
     * @param details the rescheduling details
     * @return reschedule request ID returned from create()
     */
    @Transactional
    public Long performReschedule(final Loan loan, final ReschedulingDetailsDto details) {
        try {
            log.info("Performing reschedule for loan {} via LoanRescheduleRequestWritePlatformService", loan.getId());
            JsonCommand command = buildJsonCommand(loan, details);
            CommandProcessingResult result = loanRescheduleRequestService.create(command);
            Long rescheduleRequestId = result.resourceId();
            log.info("Created reschedule request {} for loan {}", rescheduleRequestId, loan.getId());
            return rescheduleRequestId;
        } catch (Exception e) {
            log.error("Reschedule failed for loan {}: {}", loan.getId(), e.getMessage(), e);
            throw new RuntimeException("Reschedule failed: " + e.getMessage(), e);
        }
    }
    /**
     * Approves a reschedule request by calling the individual reschedule service.
     * This ensures the same approval workflow as single-loan reschedules.
     *
     * @param rescheduleRequestId the reschedule request ID to approve
     */
    @Transactional
    public void approveReschedule(final Long rescheduleRequestId) {
        try {
            log.info("Approving reschedule request {}", rescheduleRequestId);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("approvedOnDate", DateUtils.getLocalDateOfTenant().toString());
            jsonObject.addProperty("locale", "en");
            jsonObject.addProperty("dateFormat", "yyyy-MM-dd");
            JsonCommand command = new JsonCommand(rescheduleRequestId, jsonObject, fromJsonHelper);
            loanRescheduleRequestService.approve(command);
            log.info("Approved reschedule request {}", rescheduleRequestId);
        } catch (Exception e) {
            log.error("Approval failed for reschedule {}: {}", rescheduleRequestId, e.getMessage(), e);
            throw new RuntimeException("Approval failed: " + e.getMessage(), e);
        }
    }
    /**
     * Converts ReschedulingDetailsDto to JsonCommand for use with the individual service.
     * Maps all DTO fields to the JsonCommand object format expected by the service.
     *
     * @param loan the loan being rescheduled
     * @param details the reschedule details DTO
     * @return JsonCommand ready to pass to create()
     */
    private JsonCommand buildJsonCommand(final Loan loan, final ReschedulingDetailsDto details) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("loanId", loan.getId());
        if (details.getRescheduleFromDate() != null) {
            jsonObject.addProperty("rescheduleFromDate", details.getRescheduleFromDate().toString());
        }
        if (details.getSubmittedOnDate() != null) {
            jsonObject.addProperty("submittedOnDate", details.getSubmittedOnDate().toString());
        }
        if (details.getRescheduleReasonId() != null) {
            jsonObject.addProperty("rescheduleReasonId", details.getRescheduleReasonId());
        }
        if (details.getRescheduleReasonComment() != null) {
            jsonObject.addProperty("rescheduleReasonComment", details.getRescheduleReasonComment());
        }
        if (details.getGraceOnPrincipal() != null) {
            jsonObject.addProperty("graceOnPrincipal", details.getGraceOnPrincipal());
        }
        if (details.getGraceOnInterest() != null) {
            jsonObject.addProperty("graceOnInterest", details.getGraceOnInterest());
        }
        if (details.getExtraTerms() != null) {
            jsonObject.addProperty("extraTerms", details.getExtraTerms());
        }
        if (details.getExtraDays() != null) {
            jsonObject.addProperty("extraDays", details.getExtraDays());
        }
        if (details.getExceptionDays() != null) {
            jsonObject.addProperty("exceptionDays", details.getExceptionDays());
        }
        if (details.getNewInterestRate() != null) {
            jsonObject.addProperty("newInterestRate", details.getNewInterestRate());
        }
        if (details.getRecalculateInterest() != null) {
            jsonObject.addProperty("recalculateInterest", details.getRecalculateInterest());
        }
        if (details.getEndDate() != null) {
            jsonObject.addProperty("endDate", details.getEndDate().toString());
        }
        if (details.getEmi() != null) {
            jsonObject.addProperty("emi", details.getEmi());
        }
        if (details.getNewPrincipalDueFixedAmount() != null) {
            jsonObject.addProperty("newPrincipalDueFixedAmount", details.getNewPrincipalDueFixedAmount());
        }
        if (details.getRepaymentEvery() != null) {
            jsonObject.addProperty("repaymentEvery", details.getRepaymentEvery());
        }
        if (details.getRepaymentFrequencyType() != null) {
            jsonObject.addProperty("repaymentFrequencyType", details.getRepaymentFrequencyType());
        }
        if (details.getPreserveLoanTermDuration() != null) {
            jsonObject.addProperty("preserveLoanTermDuration", details.getPreserveLoanTermDuration());
        }
        if (details.getAdjustedDueDate() != null) {
            jsonObject.addProperty("adjustedDueDate", details.getAdjustedDueDate().toString());
        }
        if (details.getOverdueChargeHandling() != null) {
            jsonObject.add("overdueChargeHandling", gson.toJsonTree(details.getOverdueChargeHandling()));
        }
        if (details.getCarryForwardChargeId() != null) {
            jsonObject.addProperty("carryForwardChargeId", details.getCarryForwardChargeId());
        }
        if (details.getCarryForwardChargeDueDate() != null) {
            jsonObject.addProperty("carryForwardChargeDueDate", details.getCarryForwardChargeDueDate().toString());
        }
        jsonObject.addProperty("locale", "en");
        jsonObject.addProperty("dateFormat", "yyyy-MM-dd");
        return new JsonCommand(loan.getId(), jsonObject, fromJsonHelper);
    }
}
