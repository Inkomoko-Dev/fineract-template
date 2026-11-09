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
import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Getter
@Entity
@Table(name = "m_loan_tranche_milestone_history")
public class LoanTrancheMilestoneHistory extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "disbursement_detail_id", nullable = false)
    private Long disbursementDetailId;

    @Column(name = "submission_id", length = 100)
    private String submissionId;

    @Column(name = "conducted_by", length = 200)
    private String conductedBy;

    @Column(name = "conducted_on")
    private LocalDate conductedOn;

    @Column(name = "operational_status", length = 32)
    private String operationalStatus;

    @Column(name = "additional_funding", length = 32)
    private String additionalFunding;

    @Column(name = "loan_sub_status_id")
    private Integer loanSubStatusId;

    @Column(name = "recorded_on", nullable = false)
    private OffsetDateTime recordedOn;

    protected LoanTrancheMilestoneHistory() {}

    public LoanTrancheMilestoneHistory(final LoanTrancheMilestone milestone, final Integer loanSubStatusId,
            final OffsetDateTime recordedOn) {
        this.loanId = milestone.getLoanId();
        this.disbursementDetailId = milestone.getDisbursementDetailId();
        this.submissionId = milestone.getSubmissionId();
        this.conductedBy = milestone.getConductedBy();
        this.conductedOn = milestone.getConductedOn();
        this.operationalStatus = milestone.getOperationalStatus();
        this.additionalFunding = milestone.getAdditionalFunding();
        this.loanSubStatusId = loanSubStatusId;
        this.recordedOn = recordedOn;
    }
}
