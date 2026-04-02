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
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;

/**
 * Entity representing approval matrix configuration for a specific IC review level
 * This replaces the hardcoded level fields in LoanApprovalMatrix
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "m_loan_approval_matrix_level")
public class LoanApprovalMatrixLevel extends AbstractAuditableCustom {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_matrix_id", nullable = false)
    private LoanApprovalMatrix approvalMatrix;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ic_review_level_id", nullable = false)
    private IcReviewLevelConfig icReviewLevel;

    @Column(name = "level_number", nullable = false)
    private Integer levelNumber;

    // Unsecured First Cycle
    @Column(name = "unsecured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal unsecuredFirstCycleMaxAmount;

    @Column(name = "unsecured_first_cycle_min_term", nullable = false)
    private Integer unsecuredFirstCycleMinTerm;

    @Column(name = "unsecured_first_cycle_max_term", nullable = false)
    private Integer unsecuredFirstCycleMaxTerm;

    // Unsecured Second Cycle
    @Column(name = "unsecured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal unsecuredSecondCycleMaxAmount;

    @Column(name = "unsecured_second_cycle_min_term", nullable = false)
    private Integer unsecuredSecondCycleMinTerm;

    @Column(name = "unsecured_second_cycle_max_term", nullable = false)
    private Integer unsecuredSecondCycleMaxTerm;

    // Secured First Cycle
    @Column(name = "secured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal securedFirstCycleMaxAmount;

    @Column(name = "secured_first_cycle_min_term", nullable = false)
    private Integer securedFirstCycleMinTerm;

    @Column(name = "secured_first_cycle_max_term", nullable = false)
    private Integer securedFirstCycleMaxTerm;

    // Secured Second Cycle
    @Column(name = "secured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal securedSecondCycleMaxAmount;

    @Column(name = "secured_second_cycle_min_term", nullable = false)
    private Integer securedSecondCycleMinTerm;

    @Column(name = "secured_second_cycle_max_term", nullable = false)
    private Integer securedSecondCycleMaxTerm;

    public LoanApprovalMatrixLevel(LoanApprovalMatrix approvalMatrix, IcReviewLevelConfig icReviewLevel,
                                    Integer levelNumber, BigDecimal unsecuredFirstCycleMaxAmount,
                                    Integer unsecuredFirstCycleMinTerm, Integer unsecuredFirstCycleMaxTerm,
                                    BigDecimal unsecuredSecondCycleMaxAmount, Integer unsecuredSecondCycleMinTerm,
                                    Integer unsecuredSecondCycleMaxTerm, BigDecimal securedFirstCycleMaxAmount,
                                    Integer securedFirstCycleMinTerm, Integer securedFirstCycleMaxTerm,
                                    BigDecimal securedSecondCycleMaxAmount, Integer securedSecondCycleMinTerm,
                                    Integer securedSecondCycleMaxTerm) {
        this.approvalMatrix = approvalMatrix;
        this.icReviewLevel = icReviewLevel;
        this.levelNumber = levelNumber;
        this.unsecuredFirstCycleMaxAmount = unsecuredFirstCycleMaxAmount;
        this.unsecuredFirstCycleMinTerm = unsecuredFirstCycleMinTerm;
        this.unsecuredFirstCycleMaxTerm = unsecuredFirstCycleMaxTerm;
        this.unsecuredSecondCycleMaxAmount = unsecuredSecondCycleMaxAmount;
        this.unsecuredSecondCycleMinTerm = unsecuredSecondCycleMinTerm;
        this.unsecuredSecondCycleMaxTerm = unsecuredSecondCycleMaxTerm;
        this.securedFirstCycleMaxAmount = securedFirstCycleMaxAmount;
        this.securedFirstCycleMinTerm = securedFirstCycleMinTerm;
        this.securedFirstCycleMaxTerm = securedFirstCycleMaxTerm;
        this.securedSecondCycleMaxAmount = securedSecondCycleMaxAmount;
        this.securedSecondCycleMinTerm = securedSecondCycleMinTerm;
        this.securedSecondCycleMaxTerm = securedSecondCycleMaxTerm;
    }
}
