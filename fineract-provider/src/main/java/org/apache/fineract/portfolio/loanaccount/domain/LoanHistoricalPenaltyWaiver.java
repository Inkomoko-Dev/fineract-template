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
package org.apache.fineract.portfolio.loanaccount.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * A historical penalty waiver: the request, its approval, its audit trail and its status, in one row. Requests that
 * need approval exist here before anything on the loan changes, which is what lets the correction be reviewed without
 * a rollback trick.
 */
@Entity
@Getter
@Table(name = "m_loan_historical_penalty_waiver")
public class LoanHistoricalPenaltyWaiver extends AbstractPersistableCustom {

    @Column(name = "correction_reference", length = 32)
    private String correctionReference;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "loan_charge_id", nullable = false)
    private Long loanChargeId;

    @Column(name = "charge_id")
    private Long chargeId;

    @Column(name = "charge_name", length = 200)
    private String chargeName;

    @Column(name = "charge_due_date")
    private LocalDate chargeDueDate;

    @Column(name = "penalty_age_days")
    private Integer penaltyAgeDays;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "original_charge_amount", scale = 6, precision = 19)
    private BigDecimal originalChargeAmount;

    @Column(name = "amount_paid_before", scale = 6, precision = 19)
    private BigDecimal amountPaidBefore;

    @Column(name = "amount_waived_before", scale = 6, precision = 19)
    private BigDecimal amountWaivedBefore;

    @Column(name = "amount_outstanding_before", scale = 6, precision = 19)
    private BigDecimal amountOutstandingBefore;

    @Column(name = "waiver_amount", scale = 6, precision = 19)
    private BigDecimal waiverAmount;

    @Column(name = "amount_waived_after", scale = 6, precision = 19)
    private BigDecimal amountWaivedAfter;

    @Column(name = "amount_outstanding_after", scale = 6, precision = 19)
    private BigDecimal amountOutstandingAfter;

    @Column(name = "is_partial_waiver")
    private boolean partialWaiver;

    @Column(name = "waiver_effective_date")
    private LocalDate waiverEffectiveDate;

    @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @Column(name = "requires_approval")
    private boolean requiresApproval;

    @Column(name = "approval_trigger", length = 20)
    private String approvalTrigger;

    @Column(name = "next_approver_id")
    private Long nextApproverId;

    @Column(name = "submitted_by_id")
    private Long submittedById;

    @Column(name = "submitted_on_date")
    private OffsetDateTime submittedOnDate;

    @Column(name = "approved_by_id")
    private Long approvedById;

    @Column(name = "approved_on_date")
    private OffsetDateTime approvedOnDate;

    @Column(name = "rejected_by_id")
    private Long rejectedById;

    @Column(name = "rejected_on_date")
    private OffsetDateTime rejectedOnDate;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "escalated_on_date")
    private OffsetDateTime escalatedOnDate;

    @Column(name = "escalated_to_id")
    private Long escalatedToId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40, nullable = false)
    private HistoricalPenaltyWaiverStatus status;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "waive_transaction_id")
    private Long waiveTransactionId;

    @Column(name = "reprocessed_transaction_count")
    private Integer reprocessedTransactionCount;

    @Column(name = "loan_status_before")
    private Integer loanStatusBefore;

    @Column(name = "loan_status_after")
    private Integer loanStatusAfter;

    @Column(name = "total_outstanding_before", scale = 6, precision = 19)
    private BigDecimal totalOutstandingBefore;

    @Column(name = "total_outstanding_after", scale = 6, precision = 19)
    private BigDecimal totalOutstandingAfter;

    @Column(name = "transaction_processing_strategy_id")
    private Long transactionProcessingStrategyId;

    @Column(name = "transaction_processing_strategy_name", length = 200)
    private String transactionProcessingStrategyName;

    @Column(name = "accounting_rule_type")
    private Integer accountingRuleType;

    @Column(name = "correction_date")
    private LocalDate correctionDate;

    @Column(name = "gl_closure_id")
    private Long glClosureId;

    @Column(name = "odoo_sync_completed_on_date")
    private OffsetDateTime odooSyncCompletedOnDate;

    @Column(name = "performed_by_id")
    private Long performedById;

    @Column(name = "performed_by_username", length = 100)
    private String performedByUsername;

    @Column(name = "performed_by_roles", length = 1000)
    private String performedByRoles;

    @Column(name = "created_on_date", nullable = false)
    private OffsetDateTime createdOnDate;

    protected LoanHistoricalPenaltyWaiver() {
        // for JPA
    }

    public static LoanHistoricalPenaltyWaiver submit(final Long loanId, final Long clientId, final Long productId, final Long officeId,
            final Long loanChargeId, final Long chargeId, final String chargeName, final LocalDate chargeDueDate,
            final Integer penaltyAgeDays, final Integer installmentNumber, final BigDecimal originalChargeAmount,
            final BigDecimal amountPaidBefore, final BigDecimal amountWaivedBefore, final BigDecimal amountOutstandingBefore,
            final BigDecimal waiverAmount, final boolean partialWaiver, final LocalDate waiverEffectiveDate, final String reason,
            final boolean requiresApproval, final String approvalTrigger, final Long nextApproverId, final Long submittedById,
            final OffsetDateTime submittedOnDate) {

        final LoanHistoricalPenaltyWaiver waiver = new LoanHistoricalPenaltyWaiver();
        waiver.loanId = loanId;
        waiver.clientId = clientId;
        waiver.productId = productId;
        waiver.officeId = officeId;
        waiver.loanChargeId = loanChargeId;
        waiver.chargeId = chargeId;
        waiver.chargeName = chargeName;
        waiver.chargeDueDate = chargeDueDate;
        waiver.penaltyAgeDays = penaltyAgeDays;
        waiver.installmentNumber = installmentNumber;
        waiver.originalChargeAmount = originalChargeAmount;
        waiver.amountPaidBefore = amountPaidBefore;
        waiver.amountWaivedBefore = amountWaivedBefore;
        waiver.amountOutstandingBefore = amountOutstandingBefore;
        waiver.waiverAmount = waiverAmount;
        waiver.partialWaiver = partialWaiver;
        waiver.waiverEffectiveDate = waiverEffectiveDate;
        waiver.reason = reason;
        waiver.requiresApproval = requiresApproval;
        waiver.approvalTrigger = approvalTrigger;
        waiver.nextApproverId = nextApproverId;
        waiver.submittedById = submittedById;
        waiver.submittedOnDate = submittedOnDate;
        waiver.createdOnDate = submittedOnDate;
        waiver.status = requiresApproval ? HistoricalPenaltyWaiverStatus.PENDING_APPROVAL : HistoricalPenaltyWaiverStatus.PROCESSING;
        return waiver;
    }

    /** Derived from the row id, so a reference always identifies a posted correction and never a pending request. */
    public void assignCorrectionReference() {
        if (this.correctionReference == null && getId() != null) {
            this.correctionReference = String.format("HPW-%d-%06d", this.createdOnDate.getYear(), getId());
        }
    }

    public void markApproved(final Long approvedById, final OffsetDateTime approvedOnDate) {
        this.approvedById = approvedById;
        this.approvedOnDate = approvedOnDate;
        this.status = HistoricalPenaltyWaiverStatus.PROCESSING;
    }

    public void markRejected(final Long rejectedById, final OffsetDateTime rejectedOnDate, final String decisionReason) {
        this.rejectedById = rejectedById;
        this.rejectedOnDate = rejectedOnDate;
        this.decisionReason = decisionReason;
        this.status = HistoricalPenaltyWaiverStatus.REJECTED;
    }

    public void markEscalated(final Long escalatedToId, final OffsetDateTime escalatedOnDate) {
        this.escalatedToId = escalatedToId;
        this.escalatedOnDate = escalatedOnDate;
    }

    public void markAwaitingOdooSync(final Long waiveTransactionId, final Integer reprocessedTransactionCount,
            final Integer loanStatusBefore, final Integer loanStatusAfter, final BigDecimal totalOutstandingBefore,
            final BigDecimal totalOutstandingAfter, final BigDecimal amountWaivedAfter, final BigDecimal amountOutstandingAfter) {
        this.waiveTransactionId = waiveTransactionId;
        this.reprocessedTransactionCount = reprocessedTransactionCount;
        this.loanStatusBefore = loanStatusBefore;
        this.loanStatusAfter = loanStatusAfter;
        this.totalOutstandingBefore = totalOutstandingBefore;
        this.totalOutstandingAfter = totalOutstandingAfter;
        this.amountWaivedAfter = amountWaivedAfter;
        this.amountOutstandingAfter = amountOutstandingAfter;
        this.status = HistoricalPenaltyWaiverStatus.PENDING_ODOO_SYNC;
    }

    public void recordStrategy(final Long strategyId, final String strategyName, final Integer accountingRuleType) {
        this.transactionProcessingStrategyId = strategyId;
        this.transactionProcessingStrategyName = strategyName;
        this.accountingRuleType = accountingRuleType;
    }

    public void recordCorrectionDate(final LocalDate correctionDate, final Long glClosureId) {
        this.correctionDate = correctionDate;
        this.glClosureId = glClosureId;
    }

    public void recordPerformedBy(final Long userId, final String username, final String roles) {
        this.performedById = userId;
        this.performedByUsername = username;
        this.performedByRoles = roles;
    }

}
