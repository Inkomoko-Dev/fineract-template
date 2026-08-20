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

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "m_provisioning_batch",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provisioning_batch_reference",
                        columnNames = "batch_reference"
                ),
                @UniqueConstraint(
                        name = "uk_provisioning_batch_period",
                        columnNames = "accounting_period"
                )
        }
)
@Data
public class ProvisionBatch extends AbstractPersistableCustom {

    @Column(name = "batch_reference", nullable = false, length = 100)
    private String batchReference;

    /**
     * The accounting period represented by this batch.
     *
     * Example:
     * 2026-08-31 represents August 2026.
     */
    @Column(name = "accounting_period", nullable = false)
    private LocalDate accountingPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProvisionBatchStatus status;


    /**
     * Number of original Fineract provisioning entries
     * included in this batch.
     */
    @Column(name = "entry_count", nullable = false)
    private Integer entryCount = 0;

    /**
     * Number of aggregated journals generated from this batch.
     */
    @Column(name = "journal_count", nullable = false)
    private Integer journalCount = 0;

    /**
     * Number of aggregated journals successfully posted to Odoo.
     */
    @Column(name = "posted_journal_count", nullable = false)
    private Integer postedJournalCount = 0;

    /**
     * Number of aggregated journals that failed to post to Odoo.
     */
    @Column(name = "failed_journal_count", nullable = false)
    private Integer failedJournalCount = 0;

    /**
     * The previous month's batch that this batch reverses.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_batch_id")
    private ProvisionBatch reversalOfBatch;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @OneToMany(
            mappedBy = "batch",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ProvisionBatchEntry> entries = new ArrayList<>();

    @OneToMany(
            mappedBy = "batch",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ProvisionBatchJournal> journals = new ArrayList<>();

    protected ProvisionBatch() {}

    public ProvisionBatch(
            final String batchReference,
            final LocalDate accountingPeriod,
            final ProvisionBatchStatus status
    ) {
        this.batchReference = batchReference;
        this.accountingPeriod = accountingPeriod;
        this.status = status;
        this.createdAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    public void addEntry(final ProvisionBatchEntry entry) {
        this.entries.add(entry);
        entry.setBatch(this);
    }

    public void addJournal(final ProvisionBatchJournal journal) {
        this.journals.add(journal);
        journal.setBatch(this);
    }


}