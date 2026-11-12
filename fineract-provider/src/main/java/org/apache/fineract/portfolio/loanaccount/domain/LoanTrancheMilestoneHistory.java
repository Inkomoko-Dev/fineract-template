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

import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * Append-only copy of a survey submission and the eligibility status it produced.
 */
@Entity
@Table(name = "m_loan_tranche_milestone_history")
public class LoanTrancheMilestoneHistory extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "disbursement_detail_id")
    private Long disbursementDetailId;

    @Column(name = "submission_id", length = 100)
    private String submissionId;

    @Column(name = "conducted_by", length = 200)
    private String conductedBy;

    @Column(name = "conducted_on")
    private OffsetDateTime conductedOn;

    @Column(name = "operational_status", length = 32)
    private String operationalStatus;

    @Column(name = "operational_verified_by_bgs")
    private Boolean operationalVerifiedByBgs;

    @Column(name = "operational_bgs_name", length = 200)
    private String operationalBgsName;

    @Column(name = "operational_verified_by_investment")
    private Boolean operationalVerifiedByInvestment;

    @Column(name = "operational_investment_name", length = 200)
    private String operationalInvestmentName;

    @Column(name = "additional_funding", length = 32)
    private String additionalFunding;

    @Column(name = "funding_verified_by_bgs")
    private Boolean fundingVerifiedByBgs;

    @Column(name = "funding_bgs_name", length = 200)
    private String fundingBgsName;

    @Column(name = "funding_verified_by_investment")
    private Boolean fundingVerifiedByInvestment;

    @Column(name = "funding_investment_name", length = 200)
    private String fundingInvestmentName;

    @Column(name = "comments", length = 2000)
    private String comments;

    @Column(name = "evidence_references", length = 2000)
    private String evidenceReferences;

    @Column(name = "deadline_exception_approved")
    private Boolean deadlineExceptionApproved;

    @Column(name = "deadline_exception_by", length = 200)
    private String deadlineExceptionBy;

    @Column(name = "loan_sub_status_id")
    private Integer loanSubStatusId;

    @Column(name = "recorded_on", nullable = false)
    private OffsetDateTime recordedOn;

    protected LoanTrancheMilestoneHistory() {}

    public LoanTrancheMilestoneHistory(final Long loanId, final Long disbursementDetailId, final Integer loanSubStatusId,
            final OffsetDateTime recordedOn) {
        this.loanId = loanId;
        this.disbursementDetailId = disbursementDetailId;
        this.loanSubStatusId = loanSubStatusId;
        this.recordedOn = recordedOn;
    }

    public void copyFrom(final LoanTrancheMilestone milestone) {
        if (milestone == null) {
            return;
        }
        this.submissionId = milestone.getSubmissionId();
        this.conductedBy = milestone.getConductedBy();
        this.conductedOn = milestone.getConductedOn();
        this.operationalStatus = milestone.getOperationalStatus();
        this.operationalVerifiedByBgs = milestone.getOperationalVerifiedByBgs();
        this.operationalBgsName = milestone.getOperationalBgsName();
        this.operationalVerifiedByInvestment = milestone.getOperationalVerifiedByInvestment();
        this.operationalInvestmentName = milestone.getOperationalInvestmentName();
        this.additionalFunding = milestone.getAdditionalFunding();
        this.fundingVerifiedByBgs = milestone.getFundingVerifiedByBgs();
        this.fundingBgsName = milestone.getFundingBgsName();
        this.fundingVerifiedByInvestment = milestone.getFundingVerifiedByInvestment();
        this.fundingInvestmentName = milestone.getFundingInvestmentName();
        this.comments = milestone.getComments();
        this.evidenceReferences = milestone.getEvidenceReferences();
        this.deadlineExceptionApproved = milestone.getDeadlineExceptionApproved();
        this.deadlineExceptionBy = milestone.getDeadlineExceptionBy();
    }
}
