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

import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.data.PaymentHubTransactionStatus;

@Entity
@Table(name = "m_payment_hub_disbursement_poll", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ph_disb_poll_request_id", columnNames = { "request_id" }) })
public class PaymentHubDisbursementPoll extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "loan_disbursement_detail_id")
    private Long loanDisbursementDetailId;

    @Column(name = "request_id", length = 100, nullable = false)
    private String requestId;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "payment_type_id")
    private Long paymentTypeId;

    @Column(name = "actual_disbursement_date")
    private LocalDate actualDisbursementDate;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "last_checked_on")
    private LocalDateTime lastCheckedOn;

    protected PaymentHubDisbursementPoll() {}

    public static PaymentHubDisbursementPoll pending(final Long loanId, final Long loanDisbursementDetailId, final String requestId,
            final String transactionId, final Long paymentTypeId, final LocalDate actualDisbursementDate) {
        final PaymentHubDisbursementPoll poll = new PaymentHubDisbursementPoll();
        poll.loanId = loanId;
        poll.loanDisbursementDetailId = loanDisbursementDetailId;
        poll.requestId = requestId;
        poll.transactionId = transactionId;
        poll.paymentTypeId = paymentTypeId;
        poll.actualDisbursementDate = actualDisbursementDate;
        poll.status = PaymentHubTransactionStatus.PENDING;
        poll.createdOn = DateUtils.getLocalDateTimeOfTenant();
        return poll;
    }

    public void reopenPending(final String transactionId, final Long paymentTypeId, final LocalDate actualDisbursementDate,
            final Long loanDisbursementDetailId) {
        if (PaymentHubTransactionStatus.SUCCESS.equals(this.status)) {
            return;
        }
        this.status = PaymentHubTransactionStatus.PENDING;
        this.statusCode = null;
        this.reason = null;
        this.transactionRef = null;
        this.lastCheckedOn = null;
        if (StringUtils.isNotBlank(transactionId)) {
            this.transactionId = transactionId;
        }
        if (paymentTypeId != null) {
            this.paymentTypeId = paymentTypeId;
        }
        if (actualDisbursementDate != null) {
            this.actualDisbursementDate = actualDisbursementDate;
        }
        if (loanDisbursementDetailId != null) {
            this.loanDisbursementDetailId = loanDisbursementDetailId;
        }
    }

    public void recordStillPending(final PaymentHubTransactionStatus hubStatus) {
        this.status = PaymentHubTransactionStatus.PENDING;
        applyHubFields(hubStatus);
        this.lastCheckedOn = DateUtils.getLocalDateTimeOfTenant();
    }

    public void markTerminal(final String status, final PaymentHubTransactionStatus hubStatus, final String reason) {
        this.status = status;
        applyHubFields(hubStatus);
        if (StringUtils.isNotBlank(reason)) {
            this.reason = truncate(reason);
        }
        this.lastCheckedOn = DateUtils.getLocalDateTimeOfTenant();
    }

    public boolean isSuccess() {
        return PaymentHubTransactionStatus.SUCCESS.equals(this.status);
    }

    private void applyHubFields(final PaymentHubTransactionStatus hubStatus) {
        if (hubStatus == null) {
            return;
        }
        if (hubStatus.getStatusCode() != null) {
            this.statusCode = hubStatus.getStatusCode();
        }
        if (StringUtils.isNotBlank(hubStatus.getTransactionId())) {
            this.transactionId = hubStatus.getTransactionId();
        }
        if (StringUtils.isNotBlank(hubStatus.getTransactionRef())) {
            this.transactionRef = truncate(hubStatus.getTransactionRef(), 100);
        }
        if (StringUtils.isNotBlank(hubStatus.getReason())) {
            this.reason = truncate(hubStatus.getReason());
        }
    }

    private static String truncate(final String value) {
        return truncate(value, 1000);
    }

    private static String truncate(final String value, final int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getLoanId() {
        return this.loanId;
    }

    public Long getLoanDisbursementDetailId() {
        return this.loanDisbursementDetailId;
    }

    public String getRequestId() {
        return this.requestId;
    }

    public String getTransactionId() {
        return this.transactionId;
    }

    public Long getPaymentTypeId() {
        return this.paymentTypeId;
    }

    public LocalDate getActualDisbursementDate() {
        return this.actualDisbursementDate;
    }

    public String getStatus() {
        return this.status;
    }

    public Integer getStatusCode() {
        return this.statusCode;
    }

    public String getReason() {
        return this.reason;
    }

    public String getTransactionRef() {
        return this.transactionRef;
    }

    public LocalDateTime getCreatedOn() {
        return this.createdOn;
    }

    public LocalDateTime getLastCheckedOn() {
        return this.lastCheckedOn;
    }
}
