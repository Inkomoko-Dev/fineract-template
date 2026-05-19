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

import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;
import org.apache.fineract.useradministration.domain.AppUser;

/**
 * Entity representing IC review decision for a specific level
 * This replaces the hardcoded level fields in LoanDecision
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "m_loan_decision_level")
public class LoanDecisionLevel extends AbstractAuditableCustom {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_decision_id", nullable = false)
    private LoanDecision loanDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ic_review_level_id", nullable = false)
    private IcReviewLevelConfig icReviewLevel;

    @Column(name = "level_number", nullable = false)
    private Integer levelNumber;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_signed", nullable = false)
    private Boolean isSigned;

    @Column(name = "is_rejected", nullable = false)
    private Boolean isRejected;

    @Column(name = "decision_on")
    private LocalDate decisionOn;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_by")
    private AppUser decisionBy;

    @Column(name = "recommended_amount", scale = 6, precision = 19)
    private BigDecimal recommendedAmount;

    @Column(name = "term_frequency")
    private Integer termFrequency;

    @Column(name = "term_period_frequency_enum")
    private Integer termPeriodFrequencyEnum;

    public LoanDecisionLevel(LoanDecision loanDecision, IcReviewLevelConfig icReviewLevel, Integer levelNumber,
                              String note, Boolean isSigned, Boolean isRejected, LocalDate decisionOn,
                              AppUser decisionBy, BigDecimal recommendedAmount, Integer termFrequency,
                              Integer termPeriodFrequencyEnum) {
        this.loanDecision = loanDecision;
        this.icReviewLevel = icReviewLevel;
        this.levelNumber = levelNumber;
        this.note = note;
        this.isSigned = isSigned != null ? isSigned : Boolean.FALSE;
        this.isRejected = isRejected != null ? isRejected : Boolean.FALSE;
        this.decisionOn = decisionOn;
        this.decisionBy = decisionBy;
        this.recommendedAmount = recommendedAmount;
        this.termFrequency = termFrequency;
        this.termPeriodFrequencyEnum = termPeriodFrequencyEnum;
    }

    public void updateDecision(String note, Boolean isSigned, Boolean isRejected, LocalDate decisionOn,
                                AppUser decisionBy, BigDecimal recommendedAmount, Integer termFrequency,
                                Integer termPeriodFrequencyEnum) {
        this.note = note;
        this.isSigned = isSigned != null ? isSigned : this.isSigned;
        this.isRejected = isRejected != null ? isRejected : this.isRejected;
        this.decisionOn = decisionOn;
        this.decisionBy = decisionBy;
        this.recommendedAmount = recommendedAmount;
        this.termFrequency = termFrequency;
        this.termPeriodFrequencyEnum = termPeriodFrequencyEnum;
    }

    public boolean isSigned() {
        return this.isSigned != null && this.isSigned;
    }

    public boolean isRejected() {
        return this.isRejected != null && this.isRejected;
    }
}
