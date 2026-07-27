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

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;

@Entity
@Table(name = "m_loan_disbursement_instruction", uniqueConstraints = {
        @UniqueConstraint(name = "uk_loan_disb_instruction_provider_idempotency", columnNames = { "disbursement_provider_code",
                "idempotency_key" }) })
public class LoanDisbursementInstruction extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "disbursement_provider_code", length = 50, nullable = false)
    private String disbursementProviderCode;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "supplier_external_id", length = 100, nullable = false)
    private String supplierExternalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private DisbursementInstructionStatus status;

    @Column(name = "idempotency_key", length = 100, nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 128)
    private String requestHash;

    @Column(name = "loan_disbursement_detail_id")
    private Long loanDisbursementDetailId;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_date", nullable = false)
    private LocalDateTime createdOnDate;

    @Column(name = "last_modified_on_date")
    private LocalDateTime lastModifiedOnDate;

    protected LoanDisbursementInstruction() {}

    public static LoanDisbursementInstruction createReceived(final Long loanId, final String providerCode, final Long supplierId,
            final String supplierExternalId, final String idempotencyKey, final String requestHash, final Long createdBy) {
        final LoanDisbursementInstruction instruction = new LoanDisbursementInstruction();
        instruction.loanId = loanId;
        instruction.disbursementProviderCode = providerCode;
        instruction.supplierId = supplierId;
        instruction.supplierExternalId = supplierExternalId;
        instruction.status = DisbursementInstructionStatus.RECEIVED;
        instruction.idempotencyKey = idempotencyKey;
        instruction.requestHash = requestHash;
        instruction.createdBy = createdBy;
        instruction.createdOnDate = DateUtils.getLocalDateTimeOfTenant();
        instruction.lastModifiedOnDate = instruction.createdOnDate;
        return instruction;
    }

    public void markPendingDisbursement(final Long loanDisbursementDetailId) {
        this.status = DisbursementInstructionStatus.PENDING_DISBURSEMENT;
        this.loanDisbursementDetailId = loanDisbursementDetailId;
        this.failureMessage = null;
        this.lastModifiedOnDate = DateUtils.getLocalDateTimeOfTenant();
    }

    public void markFailed(final String message) {
        this.status = DisbursementInstructionStatus.FAILED;
        this.failureMessage = truncate(message, 1000);
        this.lastModifiedOnDate = DateUtils.getLocalDateTimeOfTenant();
    }

    public void markRejected(final String message) {
        this.status = DisbursementInstructionStatus.REJECTED;
        this.failureMessage = truncate(message, 1000);
        this.lastModifiedOnDate = DateUtils.getLocalDateTimeOfTenant();
    }

    public void markDisbursed() {
        this.status = DisbursementInstructionStatus.DISBURSED;
        this.failureMessage = null;
        this.lastModifiedOnDate = DateUtils.getLocalDateTimeOfTenant();
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

    public String getDisbursementProviderCode() {
        return this.disbursementProviderCode;
    }

    public Long getSupplierId() {
        return this.supplierId;
    }

    public String getSupplierExternalId() {
        return this.supplierExternalId;
    }

    public DisbursementInstructionStatus getStatus() {
        return this.status;
    }

    public String getIdempotencyKey() {
        return this.idempotencyKey;
    }

    public String getRequestHash() {
        return this.requestHash;
    }

    public Long getLoanDisbursementDetailId() {
        return this.loanDisbursementDetailId;
    }

    public String getFailureMessage() {
        return this.failureMessage;
    }

    public Long getCreatedBy() {
        return this.createdBy;
    }

    public LocalDateTime getCreatedOnDate() {
        return this.createdOnDate;
    }

    public LocalDateTime getLastModifiedOnDate() {
        return this.lastModifiedOnDate;
    }
}
