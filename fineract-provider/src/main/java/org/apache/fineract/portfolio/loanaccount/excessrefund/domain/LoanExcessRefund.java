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
package org.apache.fineract.portfolio.loanaccount.excessrefund.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "m_loan_excess_refund")
@Getter
@Setter
@NoArgsConstructor
public class LoanExcessRefund extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private LoanExcessRefundBatch batch;

    @Column(name = "amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "payment_mode_enum", nullable = false)
    private Integer paymentModeEnum;

    @Column(name = "payment_type_id")
    private Long paymentTypeId;

    @Column(name = "status_enum", nullable = false)
    private Integer statusEnum;

    @Column(name = "beneficiary_json", columnDefinition = "LONGTEXT", nullable = false)
    private String beneficiaryJson;

    @Column(name = "hub_request_id", length = 100)
    private String hubRequestId;

    @Column(name = "hub_transaction_ref", length = 100)
    private String hubTransactionRef;

    @Column(name = "manual_payment_ref", length = 100)
    private String manualPaymentRef;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    private AppUser submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private AppUser rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_note", columnDefinition = "LONGTEXT")
    private String rejectionNote;

    @Column(name = "posted_transaction_id")
    private Long postedTransactionId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LoanExcessRefundStatus getStatus() {
        return LoanExcessRefundStatus.fromInt(this.statusEnum);
    }

    public void setStatus(final LoanExcessRefundStatus status) {
        this.statusEnum = status == null ? null : status.getValue();
    }

    public LoanExcessRefundPaymentMode getPaymentMode() {
        return LoanExcessRefundPaymentMode.fromInt(this.paymentModeEnum);
    }

    public void setPaymentMode(final LoanExcessRefundPaymentMode mode) {
        this.paymentModeEnum = mode == null ? null : mode.getValue();
    }
}
