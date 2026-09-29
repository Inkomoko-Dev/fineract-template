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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;

/**
 * DTO for previewing a loan in bulk reschedule operation. Shows current loan details and how it will be
 * affected by the reschedule operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRescheduleLoanPreviewDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The ID of the loan */
    private Long loanId;

    /** The name of the client who owns the loan */
    private String clientName;

    /** The account number of the loan */
    private String accountNumber;

    private String loanAccountNumber;

    /** Office where the loan is managed */
    private Long officeId;

    /** Office name where the loan is managed */
    private String officeName;

    /** Product ID for the loan */
    private Long loanProductId;

    /** Product name for the loan */
    private String loanProductName;

    /** Assigned loan officer ID */
    private Long loanOfficerId;

    /** Assigned loan officer name */
    private String loanOfficerName;

    /** Current loan status */
    private String loanStatus;

    /** Current interest rate of the loan */
    private BigDecimal currentInterestRate;

    /** New interest rate after reschedule */
    private BigDecimal newInterestRate;

    /** Type of interest rate (FIXED, FLOATING, etc.) */
    private String interestRateType;

    private String interestRateMethod;

    /** Total outstanding amount for the loan */
    private BigDecimal totalOutstanding;

    /** Projected total outstanding after reschedule (if computed) */
    private BigDecimal newTotalOutstanding;

    /** Current remaining term in installments */
    private Integer currentTerm;

    /** Projected new term in installments (if computed) */
    private Integer newTerm;

    /** Date of the next scheduled installment */
    private LocalDate nextScheduledInstallment;

    /** Reason for including or excluding the loan (MATCH or EXCLUDED) */
    private String reason;

    private String rescheduleReason;

    private String status;

    /** Detailed reason for exclusion, if applicable */
    private String excludeReason;

    private String errorMessage;

    /** Stable code for UI filtering and integrations. */
    private String resultReasonCode;

    /** Human-readable reason why this loan is unavailable or failed. */
    private String resultReason;

    public static BulkRescheduleLoanPreviewDto fromResult(final BulkRescheduleResult result) {
        final BulkRescheduleLoanPreviewDto dto = new BulkRescheduleLoanPreviewDto();
        dto.setLoanId(result.getLoanId());
        dto.setLoanAccountNumber(result.getLoanAccountNumber());
        dto.setAccountNumber(result.getAccountNumber());
        dto.setClientName(result.getClientName());
        dto.setOfficeId(result.getOfficeId());
        dto.setOfficeName(result.getOfficeName());
        dto.setLoanProductName(result.getLoanProductName());
        dto.setLoanOfficerId(result.getLoanOfficerId());
        dto.setLoanOfficerName(result.getLoanOfficerName());
        dto.setLoanStatus(result.getLoanStatus());
        dto.setCurrentInterestRate(result.getOriginalInterestRate());
        dto.setNewInterestRate(result.getNewInterestRate());
        dto.setInterestRateMethod(result.getInterestRateMethod());
        dto.setTotalOutstanding(result.getTotalOutstanding());
        dto.setNewTotalOutstanding(result.getNewTotalOutstanding());
        dto.setCurrentTerm(result.getCurrentTerm());
        dto.setNewTerm(result.getNewTerm());
        dto.setNextScheduledInstallment(result.getNextScheduledInstallment());
        dto.setRescheduleReason(result.getRescheduleReason());
        dto.setExcludeReason(result.getExcludeReason());
        dto.setErrorMessage(result.getErrorMessage());
        dto.setStatus(result.getStatus() == null ? null : result.getStatus().name());
        dto.setResultReasonCode(dto.getStatus());
        dto.setResultReason(StringUtils.defaultIfBlank(result.getExcludeReason(), result.getErrorMessage()));
        return dto;
    }
}
