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
import javax.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Getter
@Setter
@Entity
@Table(name = "m_loan_tranche_milestone", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "loan_id", "disbursement_detail_id" }, name = "uk_tranche_milestone_tranche"),
        @UniqueConstraint(columnNames = { "submission_id" }, name = "uk_tranche_milestone_submission") })
public class LoanTrancheMilestone extends AbstractPersistableCustom {

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

    @Column(name = "submitted_on", nullable = false)
    private OffsetDateTime submittedOn;

    protected LoanTrancheMilestone() {}

    public LoanTrancheMilestone(final Long loanId, final Long disbursementDetailId, final OffsetDateTime submittedOn) {
        this.loanId = loanId;
        this.disbursementDetailId = disbursementDetailId;
        this.submittedOn = submittedOn;
    }
}
