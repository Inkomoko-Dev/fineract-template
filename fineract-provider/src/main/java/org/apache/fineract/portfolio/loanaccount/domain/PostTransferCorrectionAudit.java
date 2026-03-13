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
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.Getter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Getter
@Entity
@Table(name = "m_post_transfer_correction_audit")
public class PostTransferCorrectionAudit extends AbstractPersistableCustom {

    @Column(name = "loan_transaction_id", nullable = false)
    private Long loanTransactionId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "original_transfer_id", nullable = false)
    private Long originalTransferId;

    @Column(name = "correction_type", nullable = false, length = 50)
    private String correctionType;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    @Column(name = "corrected_by", nullable = false)
    private Long correctedBy;

    @Column(name = "corrected_on", nullable = false)
    private LocalDate correctedOn;

    @Column(name = "original_office_id", nullable = false)
    private Long originalOfficeId;

    @Column(name = "new_office_id", nullable = false)
    private Long newOfficeId;

    protected PostTransferCorrectionAudit() {
        // for JPA
    }

    private PostTransferCorrectionAudit(final Long loanTransactionId, final Long clientId, final Long originalTransferId,
            final String correctionType, final String correctionReason, final Long correctedBy, final LocalDate correctedOn,
            final Long originalOfficeId, final Long newOfficeId) {
        this.loanTransactionId = loanTransactionId;
        this.clientId = clientId;
        this.originalTransferId = originalTransferId;
        this.correctionType = correctionType;
        this.correctionReason = correctionReason;
        this.correctedBy = correctedBy;
        this.correctedOn = correctedOn;
        this.originalOfficeId = originalOfficeId;
        this.newOfficeId = newOfficeId;
    }

    public static PostTransferCorrectionAudit instance(final Long loanTransactionId, final Long clientId, final Long originalTransferId,
            final String correctionType, final String correctionReason, final Long correctedBy, final LocalDate correctedOn,
            final Long originalOfficeId, final Long newOfficeId) {
        return new PostTransferCorrectionAudit(loanTransactionId, clientId, originalTransferId, correctionType, correctionReason,
                correctedBy, correctedOn, originalOfficeId, newOfficeId);
    }

}
