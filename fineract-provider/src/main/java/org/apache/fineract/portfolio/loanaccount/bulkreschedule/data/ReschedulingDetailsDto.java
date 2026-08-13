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
import org.apache.fineract.infrastructure.codes.data.CodeValueData;

/**
 * DTO for capturing rescheduling details. Contains information about how to reschedule the loans
 * including dates, additional terms, and new interest rates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReschedulingDetailsDto implements Serializable {

    private static final long serialVersionUID = 1L;


    /** Request submission date */
    private LocalDate submittedOnDate;

    /** Per-loan date derived from the selected bulk strategy during execution. */
    private LocalDate rescheduleFromDate;

    /** Reason code ID used by existing reschedule flow */
    private Long rescheduleReasonId;

    /** Optional free-text comment for reschedule reason */
    private String rescheduleReasonComment;

    /** Number of extra terms to add */
    private Integer extraTerms;

    /** Grace period on principal in number of installments */
    private Integer graceOnPrincipal;

    /** Grace period on interest in number of installments */
    private Integer graceOnInterest;

    /** Number of extra days to add */
    private Integer extraDays;

    /** Number of exception days (days to skip in rescheduling) */
    private Integer exceptionDays;

    /** New interest rate to apply */
    private BigDecimal newInterestRate;

    /** Whether interest should be recalculated */
    private Boolean recalculateInterest;

    /** Optional end date used together with EMI */
    private LocalDate endDate;

    /** Optional EMI used together with end date */
    private BigDecimal emi;

    /** Optional fixed principal amount per installment */
    private BigDecimal newPrincipalDueFixedAmount;

    /** Optional repayment interval when changing frequency */
    private Integer repaymentEvery;

    /** Optional repayment frequency type (e.g. days/weeks/months/years enum value) */
    private Integer repaymentFrequencyType;

    /** Preserve original loan term duration when changing repayment frequency */
    private Boolean preserveLoanTermDuration;

    /** Overdue charge handling option, matching the individual loan reschedule contract. */
    private CodeValueData overdueChargeHandling;

    /** Charge definition ID used when carrying charges forward */
    private Long carryForwardChargeId;

    /** Due date for carried-forward charge */
    private LocalDate carryForwardChargeDueDate;

    /** Optional adjusted due date for the rescheduled loan */
    private LocalDate adjustedDueDate;

}
