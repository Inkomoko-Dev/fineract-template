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

import java.time.LocalDateTime;
import org.apache.fineract.portfolio.loanaccount.domain.DisbursementInstructionStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstruction;

public final class DisbursementInstructionData {

    private final Long id;
    private final Long loanId;
    private final String disbursementProviderCode;
    private final Long supplierId;
    private final String supplierExternalId;
    private final DisbursementInstructionStatus status;
    private final String idempotencyKey;
    private final Long loanDisbursementDetailId;
    private final String failureMessage;
    private final LocalDateTime createdOnDate;
    private final LocalDateTime lastModifiedOnDate;

    public DisbursementInstructionData(final Long id, final Long loanId, final String disbursementProviderCode, final Long supplierId,
            final String supplierExternalId, final DisbursementInstructionStatus status, final String idempotencyKey,
            final Long loanDisbursementDetailId, final String failureMessage, final LocalDateTime createdOnDate,
            final LocalDateTime lastModifiedOnDate) {
        this.id = id;
        this.loanId = loanId;
        this.disbursementProviderCode = disbursementProviderCode;
        this.supplierId = supplierId;
        this.supplierExternalId = supplierExternalId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.loanDisbursementDetailId = loanDisbursementDetailId;
        this.failureMessage = failureMessage;
        this.createdOnDate = createdOnDate;
        this.lastModifiedOnDate = lastModifiedOnDate;
    }

    public static DisbursementInstructionData from(final LoanDisbursementInstruction instruction) {
        return new DisbursementInstructionData(instruction.getId(), instruction.getLoanId(), instruction.getDisbursementProviderCode(),
                instruction.getSupplierId(), instruction.getSupplierExternalId(), instruction.getStatus(), instruction.getIdempotencyKey(),
                instruction.getLoanDisbursementDetailId(), instruction.getFailureMessage(), instruction.getCreatedOnDate(),
                instruction.getLastModifiedOnDate());
    }

    public Long getId() {
        return this.id;
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

    public Long getLoanDisbursementDetailId() {
        return this.loanDisbursementDetailId;
    }

    public String getFailureMessage() {
        return this.failureMessage;
    }

    public LocalDateTime getCreatedOnDate() {
        return this.createdOnDate;
    }

    public LocalDateTime getLastModifiedOnDate() {
        return this.lastModifiedOnDate;
    }
}
