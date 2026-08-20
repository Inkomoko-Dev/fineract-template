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
import javax.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
        name = "m_provisioning_batch_entry",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provisioning_batch_entry",
                        columnNames = {
                                "batch_id",
                                "provisioning_entry_id"
                        }
                )
        }
)
@Data
public class ProvisionBatchEntry extends AbstractPersistableCustom {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProvisionBatch batch;

    /**
     * ID of the original Fineract provisioning entry.
     */
    @Column(name = "provisioning_entry_id", nullable = false)
    private Long provisioningEntryId;

    /**
     * Currency of the source provisioning entry.
     */
    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProvisionBatchEntry() {}

    public ProvisionBatchEntry(
            final Long provisioningEntryId,
            final String currencyCode
    ) {
        this.provisioningEntryId = provisioningEntryId;
        this.currencyCode = currencyCode;
        this.createdAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    public ProvisionBatch getBatch() {
        return batch;
    }

    public Long getProvisioningEntryId() {
        return provisioningEntryId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setBatch(final ProvisionBatch batch) {
        this.batch = batch;
    }
}