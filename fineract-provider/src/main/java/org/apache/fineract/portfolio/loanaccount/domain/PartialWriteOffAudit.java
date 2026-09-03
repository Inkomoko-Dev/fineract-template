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
import java.time.ZoneId;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Size;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "m_partial_writeoff_audit", uniqueConstraints = { @UniqueConstraint(columnNames = { "external_id" }, name = "unique_partial_writeoff_audit_external_id") })
public class PartialWriteOffAudit extends AbstractAuditableWithUTCDateTimeCustom {

    @ManyToOne
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne
    @JoinColumn(name = "loan_transaction_id", nullable = false)
    private LoanTransaction loanTransaction;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "principal_portion", scale = 6, precision = 19)
    private BigDecimal principalPortion;

    @Column(name = "interest_portion", scale = 6, precision = 19)
    private BigDecimal interestPortion;

    @Column(name = "fee_charges_portion", scale = 6, precision = 19)
    private BigDecimal feeChargesPortion;

    @Column(name = "penalty_charges_portion", scale = 6, precision = 19)
    private BigDecimal penaltyChargesPortion;

    @Column(name = "total_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "loan_balance_before", scale = 6, precision = 19, nullable = false)
    private BigDecimal loanBalanceBefore;

    @Column(name = "loan_balance_after", scale = 6, precision = 19, nullable = false)
    private BigDecimal loanBalanceAfter;

    @Column(name = "reason", nullable = false, length = 500)
    @Size(max = 500)
    private String reason;

    @ManyToOne
    @JoinColumn(name = "initiated_by", nullable = false)
    private AppUser initiatedBy;

    @Column(name = "initiated_on", nullable = false)
    private OffsetDateTime initiatedOn;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @Column(name = "approved_on")
    private OffsetDateTime approvedOn;

    @ManyToOne
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "external_id", length = 100, unique = true)
    private String externalId;

    protected PartialWriteOffAudit() {
    }

    public static PartialWriteOffAudit create(Loan loan, LoanTransaction loanTransaction, LocalDate transactionDate,
            BigDecimal principalPortion, BigDecimal interestPortion, BigDecimal feeChargesPortion,
            BigDecimal penaltyChargesPortion, BigDecimal totalAmount, BigDecimal loanBalanceBefore,
            BigDecimal loanBalanceAfter, String reason, AppUser initiatedBy, Office office, String note, String externalId) {
        
        PartialWriteOffAudit audit = new PartialWriteOffAudit();
        audit.loan = loan;
        audit.loanTransaction = loanTransaction;
        audit.transactionDate = transactionDate;
        audit.principalPortion = principalPortion;
        audit.interestPortion = interestPortion;
        audit.feeChargesPortion = feeChargesPortion;
        audit.penaltyChargesPortion = penaltyChargesPortion;
        audit.totalAmount = totalAmount;
        audit.loanBalanceBefore = loanBalanceBefore;
        audit.loanBalanceAfter = loanBalanceAfter;
        audit.reason = reason;
        audit.initiatedBy = initiatedBy;
        audit.initiatedOn = OffsetDateTime.now(ZoneId.systemDefault());;
        audit.office = office;
        audit.note = note;
        audit.externalId = externalId;
        return audit;
    }

    public void markAsApproved(AppUser approvedBy) {
        this.approvedBy = approvedBy;
        this.approvedOn = OffsetDateTime.now(ZoneId.systemDefault());;
    }

    public Loan getLoan() {
        return loan;
    }

    public LoanTransaction getLoanTransaction() {
        return loanTransaction;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public BigDecimal getPrincipalPortion() {
        return principalPortion;
    }

    public BigDecimal getInterestPortion() {
        return interestPortion;
    }

    public BigDecimal getFeeChargesPortion() {
        return feeChargesPortion;
    }

    public BigDecimal getPenaltyChargesPortion() {
        return penaltyChargesPortion;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getLoanBalanceBefore() {
        return loanBalanceBefore;
    }

    public BigDecimal getLoanBalanceAfter() {
        return loanBalanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public AppUser getInitiatedBy() {
        return initiatedBy;
    }

    public OffsetDateTime getInitiatedOn() {
        return initiatedOn;
    }

    public AppUser getApprovedBy() {
        return approvedBy;
    }

    public OffsetDateTime getApprovedOn() {
        return approvedOn;
    }

    public Office getOffice() {
        return office;
    }

    public String getNote() {
        return note;
    }

    public String getExternalId() {
        return externalId;
    }
}
