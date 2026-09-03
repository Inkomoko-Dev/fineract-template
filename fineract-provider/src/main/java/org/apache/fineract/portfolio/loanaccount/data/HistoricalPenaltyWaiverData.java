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
package org.apache.fineract.portfolio.loanaccount.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import lombok.Getter;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiver;

/** A historical penalty waiver as the approval queue and the loan's correction history show it. */
@Getter
public class HistoricalPenaltyWaiverData implements Serializable {

    private final Long id;
    private final String correctionReference;
    private final Long loanId;
    private final Long clientId;
    private final Long productId;
    private final Long officeId;
    private final Long loanChargeId;
    private final String chargeName;
    private final LocalDate chargeDueDate;
    private final Integer penaltyAgeDays;
    private final Integer installmentNumber;

    private final BigDecimal originalChargeAmount;
    private final BigDecimal amountPaidBefore;
    private final BigDecimal waiverAmount;
    private final BigDecimal amountWaivedAfter;
    private final BigDecimal amountOutstandingAfter;
    private final boolean partialWaiver;

    private final LocalDate waiverEffectiveDate;
    private final String reason;

    private final boolean requiresApproval;
    private final String approvalTrigger;
    private final Long nextApproverId;
    private final Long submittedById;
    private final OffsetDateTime submittedOnDate;
    private final Long approvedById;
    private final OffsetDateTime approvedOnDate;
    private final Long rejectedById;
    private final OffsetDateTime rejectedOnDate;
    private final String decisionReason;
    private final OffsetDateTime escalatedOnDate;
    private final Long escalatedToId;

    private final String status;
    private final String failureMessage;
    private final Long waiveTransactionId;
    private final Integer reprocessedTransactionCount;
    private final Integer loanStatusBefore;
    private final Integer loanStatusAfter;
    private final BigDecimal totalOutstandingBefore;
    private final BigDecimal totalOutstandingAfter;
    private final String transactionProcessingStrategyName;
    private final LocalDate correctionDate;
    private final OffsetDateTime odooSyncCompletedOnDate;
    private final String performedByUsername;

    /** Populated only on the template/queue responses, never on a list row. */
    private final Collection<HistoricalPenaltyWaiverTxnData> transactions;

    private HistoricalPenaltyWaiverData(final LoanHistoricalPenaltyWaiver waiver,
            final Collection<HistoricalPenaltyWaiverTxnData> transactions) {

        this.id = waiver.getId();
        this.correctionReference = waiver.getCorrectionReference();
        this.loanId = waiver.getLoanId();
        this.clientId = waiver.getClientId();
        this.productId = waiver.getProductId();
        this.officeId = waiver.getOfficeId();
        this.loanChargeId = waiver.getLoanChargeId();
        this.chargeName = waiver.getChargeName();
        this.chargeDueDate = waiver.getChargeDueDate();
        this.penaltyAgeDays = waiver.getPenaltyAgeDays();
        this.installmentNumber = waiver.getInstallmentNumber();
        this.originalChargeAmount = waiver.getOriginalChargeAmount();
        this.amountPaidBefore = waiver.getAmountPaidBefore();
        this.waiverAmount = waiver.getWaiverAmount();
        this.amountWaivedAfter = waiver.getAmountWaivedAfter();
        this.amountOutstandingAfter = waiver.getAmountOutstandingAfter();
        this.partialWaiver = waiver.isPartialWaiver();
        this.waiverEffectiveDate = waiver.getWaiverEffectiveDate();
        this.reason = waiver.getReason();
        this.requiresApproval = waiver.isRequiresApproval();
        this.approvalTrigger = waiver.getApprovalTrigger();
        this.nextApproverId = waiver.getNextApproverId();
        this.submittedById = waiver.getSubmittedById();
        this.submittedOnDate = waiver.getSubmittedOnDate();
        this.approvedById = waiver.getApprovedById();
        this.approvedOnDate = waiver.getApprovedOnDate();
        this.rejectedById = waiver.getRejectedById();
        this.rejectedOnDate = waiver.getRejectedOnDate();
        this.decisionReason = waiver.getDecisionReason();
        this.escalatedOnDate = waiver.getEscalatedOnDate();
        this.escalatedToId = waiver.getEscalatedToId();
        this.status = waiver.getStatus() == null ? null : waiver.getStatus().name();
        this.failureMessage = waiver.getFailureMessage();
        this.waiveTransactionId = waiver.getWaiveTransactionId();
        this.reprocessedTransactionCount = waiver.getReprocessedTransactionCount();
        this.loanStatusBefore = waiver.getLoanStatusBefore();
        this.loanStatusAfter = waiver.getLoanStatusAfter();
        this.totalOutstandingBefore = waiver.getTotalOutstandingBefore();
        this.totalOutstandingAfter = waiver.getTotalOutstandingAfter();
        this.transactionProcessingStrategyName = waiver.getTransactionProcessingStrategyName();
        this.correctionDate = waiver.getCorrectionDate();
        this.odooSyncCompletedOnDate = waiver.getOdooSyncCompletedOnDate();
        this.performedByUsername = waiver.getPerformedByUsername();
        this.transactions = transactions;
    }

    public static HistoricalPenaltyWaiverData from(final LoanHistoricalPenaltyWaiver waiver) {
        return new HistoricalPenaltyWaiverData(waiver, null);
    }

    public static HistoricalPenaltyWaiverData from(final LoanHistoricalPenaltyWaiver waiver,
            final Collection<HistoricalPenaltyWaiverTxnData> transactions) {
        return new HistoricalPenaltyWaiverData(waiver, transactions);
    }
}
