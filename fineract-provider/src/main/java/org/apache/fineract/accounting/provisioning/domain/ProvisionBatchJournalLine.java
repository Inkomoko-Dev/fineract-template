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
package org.apache.fineract.accounting.provisioning.domain;

import lombok.Data;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "m_provisioning_batch_journal_line")
@Data
public class ProvisionBatchJournalLine extends AbstractPersistableCustom {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_id", nullable = false)
    private ProvisionBatchJournal journal;

    @Column(name = "gl_account_id")
    private Long glAccountId;


    @Column(name = "gl_account_code", nullable = false, length = 50)
    private String glAccountCode;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    @Column(
            name = "debit_amount",
            nullable = false,
            precision = 19,
            scale = 6
    )
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(
            name = "credit_amount",
            nullable = false,
            precision = 19,
            scale = 6
    )
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProvisionBatchJournalLine() {}

    public ProvisionBatchJournalLine(
            final Long glAccountId,
            final String glAccountCode,
            final String entryType,
            final BigDecimal debitAmount,
            final BigDecimal creditAmount,
            final LocalDate transactionDate
    ) {
        this.glAccountId = glAccountId;
        this.glAccountCode = glAccountCode;
        this.entryType = entryType;
        this.debitAmount = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        this.creditAmount = creditAmount != null ? creditAmount : BigDecimal.ZERO;
        this.transactionDate = transactionDate;
        this.createdAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    public ProvisionBatchJournal getJournal() {
        return journal;
    }

    public void setJournal(final ProvisionBatchJournal journal) {
        this.journal = journal;
    }

    public Long getGlAccountId() {
        return glAccountId;
    }

    public String getGlAccountCode() {
        return glAccountCode;
    }

    public String getEntryType() {
        return entryType;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}