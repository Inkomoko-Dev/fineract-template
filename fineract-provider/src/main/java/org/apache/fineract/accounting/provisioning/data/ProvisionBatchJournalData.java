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
package org.apache.fineract.accounting.provisioning.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Summary of an aggregated provision journal prepared for / posted to Odoo.
 * Used by Finance to reconcile originals vs reversals and review failures.
 */
public class ProvisionBatchJournalData {

    private final Long id;
    private final Long batchId;
    private final String batchReference;
    private final String journalReference;
    private final String reference;
    private final LocalDate entryDate;
    private final String currencyCode;
    private final Long officeId;
    private final String officeName;
    private final boolean reversal;
    private final String status;
    private final String odooJournalId;
    private final BigDecimal totalDebit;
    private final BigDecimal totalCredit;
    private final Long provisioningHistoryId;
    private final Long reversedJournalId;
    private final String failureReason;
    private final LocalDateTime postedAt;
    private final LocalDateTime createdAt;

    public ProvisionBatchJournalData(Long id, Long batchId, String batchReference, String journalReference, String reference,
            LocalDate entryDate, String currencyCode, Long officeId, String officeName, boolean reversal, String status,
            String odooJournalId, BigDecimal totalDebit, BigDecimal totalCredit, Long provisioningHistoryId,
            Long reversedJournalId, String failureReason, LocalDateTime postedAt, LocalDateTime createdAt) {
        this.id = id;
        this.batchId = batchId;
        this.batchReference = batchReference;
        this.journalReference = journalReference;
        this.reference = reference;
        this.entryDate = entryDate;
        this.currencyCode = currencyCode;
        this.officeId = officeId;
        this.officeName = officeName;
        this.reversal = reversal;
        this.status = status;
        this.odooJournalId = odooJournalId;
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.provisioningHistoryId = provisioningHistoryId;
        this.reversedJournalId = reversedJournalId;
        this.failureReason = failureReason;
        this.postedAt = postedAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public String getBatchReference() {
        return batchReference;
    }

    public String getJournalReference() {
        return journalReference;
    }

    public String getReference() {
        return reference;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Long getOfficeId() {
        return officeId;
    }

    public String getOfficeName() {
        return officeName;
    }

    public boolean isReversal() {
        return reversal;
    }

    public String getStatus() {
        return status;
    }

    public String getOdooJournalId() {
        return odooJournalId;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public Long getProvisioningHistoryId() {
        return provisioningHistoryId;
    }

    public Long getReversedJournalId() {
        return reversedJournalId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
