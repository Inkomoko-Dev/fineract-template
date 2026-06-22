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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.audit.AuditChangeRecorder;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;
import org.apache.fineract.portfolio.loanaccount.api.LoanApprovalMatrixConstants;

@Getter
@Setter
@Entity
@Table(name = "m_loan_approval_matrix")
public class LoanApprovalMatrix extends AbstractAuditableCustom {

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    @Column(name = "level_one_unsecured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelOneUnsecuredFirstCycleMaxAmount;
    @Column(name = "level_one_unsecured_first_cycle_min_term")
    private Integer levelOneUnsecuredFirstCycleMinTerm;
    @Column(name = "level_one_unsecured_first_cycle_max_term")
    private Integer levelOneUnsecuredFirstCycleMaxTerm;
    @Column(name = "level_one_unsecured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelOneUnsecuredSecondCycleMaxAmount;
    @Column(name = "level_one_unsecured_second_cycle_min_term")
    private Integer levelOneUnsecuredSecondCycleMinTerm;
    @Column(name = "level_one_unsecured_second_cycle_max_term")
    private Integer levelOneUnsecuredSecondCycleMaxTerm;
    @Column(name = "level_one_secured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelOneSecuredFirstCycleMaxAmount;
    @Column(name = "level_one_secured_first_cycle_min_term")
    private Integer levelOneSecuredFirstCycleMinTerm;
    @Column(name = "level_one_secured_first_cycle_max_term")
    private Integer levelOneSecuredFirstCycleMaxTerm;
    @Column(name = "level_one_secured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelOneSecuredSecondCycleMaxAmount;
    @Column(name = "level_one_secured_second_cycle_min_term")
    private Integer levelOneSecuredSecondCycleMinTerm;
    @Column(name = "level_one_secured_second_cycle_max_term")
    private Integer levelOneSecuredSecondCycleMaxTerm;

    @Column(name = "level_two_unsecured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelTwoUnsecuredFirstCycleMaxAmount;
    @Column(name = "level_two_unsecured_first_cycle_min_term")
    private Integer levelTwoUnsecuredFirstCycleMinTerm;
    @Column(name = "level_two_unsecured_first_cycle_max_term")
    private Integer levelTwoUnsecuredFirstCycleMaxTerm;
    @Column(name = "level_two_unsecured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelTwoUnsecuredSecondCycleMaxAmount;
    @Column(name = "level_two_unsecured_second_cycle_min_term")
    private Integer levelTwoUnsecuredSecondCycleMinTerm;
    @Column(name = "level_two_unsecured_second_cycle_max_term")
    private Integer levelTwoUnsecuredSecondCycleMaxTerm;
    @Column(name = "level_two_secured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelTwoSecuredFirstCycleMaxAmount;
    @Column(name = "level_two_secured_first_cycle_min_term")
    private Integer levelTwoSecuredFirstCycleMinTerm;
    @Column(name = "level_two_secured_first_cycle_max_term")
    private Integer levelTwoSecuredFirstCycleMaxTerm;
    @Column(name = "level_two_secured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelTwoSecuredSecondCycleMaxAmount;
    @Column(name = "level_two_secured_second_cycle_min_term")
    private Integer levelTwoSecuredSecondCycleMinTerm;
    @Column(name = "level_two_secured_second_cycle_max_term")
    private Integer levelTwoSecuredSecondCycleMaxTerm;

    @Column(name = "level_three_unsecured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelThreeUnsecuredFirstCycleMaxAmount;
    @Column(name = "level_three_unsecured_first_cycle_min_term")
    private Integer levelThreeUnsecuredFirstCycleMinTerm;
    @Column(name = "level_three_unsecured_first_cycle_max_term")
    private Integer levelThreeUnsecuredFirstCycleMaxTerm;
    @Column(name = "level_three_unsecured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelThreeUnsecuredSecondCycleMaxAmount;
    @Column(name = "level_three_unsecured_second_cycle_min_term")
    private Integer levelThreeUnsecuredSecondCycleMinTerm;
    @Column(name = "level_three_unsecured_second_cycle_max_term")
    private Integer levelThreeUnsecuredSecondCycleMaxTerm;
    @Column(name = "level_three_secured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelThreeSecuredFirstCycleMaxAmount;
    @Column(name = "level_three_secured_first_cycle_min_term")
    private Integer levelThreeSecuredFirstCycleMinTerm;
    @Column(name = "level_three_secured_first_cycle_max_term")
    private Integer levelThreeSecuredFirstCycleMaxTerm;
    @Column(name = "level_three_secured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelThreeSecuredSecondCycleMaxAmount;
    @Column(name = "level_three_secured_second_cycle_min_term")
    private Integer levelThreeSecuredSecondCycleMinTerm;
    @Column(name = "level_three_secured_second_cycle_max_term")
    private Integer levelThreeSecuredSecondCycleMaxTerm;

    @Column(name = "level_four_unsecured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelFourUnsecuredFirstCycleMaxAmount;
    @Column(name = "level_four_unsecured_first_cycle_min_term")
    private Integer levelFourUnsecuredFirstCycleMinTerm;
    @Column(name = "level_four_unsecured_first_cycle_max_term")
    private Integer levelFourUnsecuredFirstCycleMaxTerm;
    @Column(name = "level_four_unsecured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelFourUnsecuredSecondCycleMaxAmount;
    @Column(name = "level_four_unsecured_second_cycle_min_term")
    private Integer levelFourUnsecuredSecondCycleMinTerm;
    @Column(name = "level_four_unsecured_second_cycle_max_term")
    private Integer levelFourUnsecuredSecondCycleMaxTerm;
    @Column(name = "level_four_secured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelFourSecuredFirstCycleMaxAmount;
    @Column(name = "level_four_secured_first_cycle_min_term")
    private Integer levelFourSecuredFirstCycleMinTerm;
    @Column(name = "level_four_secured_first_cycle_max_term")
    private Integer levelFourSecuredFirstCycleMaxTerm;
    @Column(name = "level_four_secured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelFourSecuredSecondCycleMaxAmount;
    @Column(name = "level_four_secured_second_cycle_min_term")
    private Integer levelFourSecuredSecondCycleMinTerm;
    @Column(name = "level_four_secured_second_cycle_max_term")
    private Integer levelFourSecuredSecondCycleMaxTerm;

    @Column(name = "level_five_unsecured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelFiveUnsecuredFirstCycleMaxAmount;
    @Column(name = "level_five_unsecured_first_cycle_min_term")
    private Integer levelFiveUnsecuredFirstCycleMinTerm;
    @Column(name = "level_five_unsecured_first_cycle_max_term")
    private Integer levelFiveUnsecuredFirstCycleMaxTerm;
    @Column(name = "level_five_unsecured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelFiveUnsecuredSecondCycleMaxAmount;
    @Column(name = "level_five_unsecured_second_cycle_min_term")
    private Integer levelFiveUnsecuredSecondCycleMinTerm;
    @Column(name = "level_five_unsecured_second_cycle_max_term")
    private Integer levelFiveUnsecuredSecondCycleMaxTerm;
    @Column(name = "level_five_secured_first_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelFiveSecuredFirstCycleMaxAmount;
    @Column(name = "level_five_secured_first_cycle_min_term")
    private Integer levelFiveSecuredFirstCycleMinTerm;
    @Column(name = "level_five_secured_first_cycle_max_term")
    private Integer levelFiveSecuredFirstCycleMaxTerm;
    @Column(name = "level_five_secured_second_cycle_max_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal levelFiveSecuredSecondCycleMaxAmount;
    @Column(name = "level_five_secured_second_cycle_min_term")
    private Integer levelFiveSecuredSecondCycleMinTerm;
    @Column(name = "level_five_secured_second_cycle_max_term")
    private Integer levelFiveSecuredSecondCycleMaxTerm;

    /**
     * Dynamic IC Review Levels - relationship to the new m_loan_approval_matrix_level table.
     * This allows for unlimited IC review levels beyond the hardcoded 5 levels above.
     * The hardcoded fields above are kept for backward compatibility.
     */
    @OneToMany(mappedBy = "approvalMatrix", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LoanApprovalMatrixLevel> approvalMatrixLevels = new ArrayList<>();

    public LoanApprovalMatrix() {}

    public LoanApprovalMatrix(String currency, BigDecimal levelOneUnsecuredFirstCycleMaxAmount, Integer levelOneUnsecuredFirstCycleMinTerm,
            Integer levelOneUnsecuredFirstCycleMaxTerm, BigDecimal levelOneUnsecuredSecondCycleMaxAmount,
            Integer levelOneUnsecuredSecondCycleMinTerm, Integer levelOneUnsecuredSecondCycleMaxTerm,
            BigDecimal levelOneSecuredFirstCycleMaxAmount, Integer levelOneSecuredFirstCycleMinTerm,
            Integer levelOneSecuredFirstCycleMaxTerm, BigDecimal levelOneSecuredSecondCycleMaxAmount,
            Integer levelOneSecuredSecondCycleMinTerm, Integer levelOneSecuredSecondCycleMaxTerm,
            BigDecimal levelTwoUnsecuredFirstCycleMaxAmount, Integer levelTwoUnsecuredFirstCycleMinTerm,
            Integer levelTwoUnsecuredFirstCycleMaxTerm, BigDecimal levelTwoUnsecuredSecondCycleMaxAmount,
            Integer levelTwoUnsecuredSecondCycleMinTerm, Integer levelTwoUnsecuredSecondCycleMaxTerm,
            BigDecimal levelTwoSecuredFirstCycleMaxAmount, Integer levelTwoSecuredFirstCycleMinTerm,
            Integer levelTwoSecuredFirstCycleMaxTerm, BigDecimal levelTwoSecuredSecondCycleMaxAmount,
            Integer levelTwoSecuredSecondCycleMinTerm, Integer levelTwoSecuredSecondCycleMaxTerm,
            BigDecimal levelThreeUnsecuredFirstCycleMaxAmount, Integer levelThreeUnsecuredFirstCycleMinTerm,
            Integer levelThreeUnsecuredFirstCycleMaxTerm, BigDecimal levelThreeUnsecuredSecondCycleMaxAmount,
            Integer levelThreeUnsecuredSecondCycleMinTerm, Integer levelThreeUnsecuredSecondCycleMaxTerm,
            BigDecimal levelThreeSecuredFirstCycleMaxAmount, Integer levelThreeSecuredFirstCycleMinTerm,
            Integer levelThreeSecuredFirstCycleMaxTerm, BigDecimal levelThreeSecuredSecondCycleMaxAmount,
            Integer levelThreeSecuredSecondCycleMinTerm, Integer levelThreeSecuredSecondCycleMaxTerm,
            BigDecimal levelFourUnsecuredFirstCycleMaxAmount, Integer levelFourUnsecuredFirstCycleMinTerm,
            Integer levelFourUnsecuredFirstCycleMaxTerm, BigDecimal levelFourUnsecuredSecondCycleMaxAmount,
            Integer levelFourUnsecuredSecondCycleMinTerm, Integer levelFourUnsecuredSecondCycleMaxTerm,
            BigDecimal levelFourSecuredFirstCycleMaxAmount, Integer levelFourSecuredFirstCycleMinTerm,
            Integer levelFourSecuredFirstCycleMaxTerm, BigDecimal levelFourSecuredSecondCycleMaxAmount,
            Integer levelFourSecuredSecondCycleMinTerm, Integer levelFourSecuredSecondCycleMaxTerm,
            BigDecimal levelFiveUnsecuredFirstCycleMaxAmount, Integer levelFiveUnsecuredFirstCycleMinTerm,
            Integer levelFiveUnsecuredFirstCycleMaxTerm, BigDecimal levelFiveUnsecuredSecondCycleMaxAmount,
            Integer levelFiveUnsecuredSecondCycleMinTerm, Integer levelFiveUnsecuredSecondCycleMaxTerm,
            BigDecimal levelFiveSecuredFirstCycleMaxAmount, Integer levelFiveSecuredFirstCycleMinTerm,
            Integer levelFiveSecuredFirstCycleMaxTerm, BigDecimal levelFiveSecuredSecondCycleMaxAmount,
            Integer levelFiveSecuredSecondCycleMinTerm, Integer levelFiveSecuredSecondCycleMaxTerm) {
        this.currency = currency;
        this.levelOneUnsecuredFirstCycleMaxAmount = levelOneUnsecuredFirstCycleMaxAmount;
        this.levelOneUnsecuredFirstCycleMinTerm = levelOneUnsecuredFirstCycleMinTerm;
        this.levelOneUnsecuredFirstCycleMaxTerm = levelOneUnsecuredFirstCycleMaxTerm;
        this.levelOneUnsecuredSecondCycleMaxAmount = levelOneUnsecuredSecondCycleMaxAmount;
        this.levelOneUnsecuredSecondCycleMinTerm = levelOneUnsecuredSecondCycleMinTerm;
        this.levelOneUnsecuredSecondCycleMaxTerm = levelOneUnsecuredSecondCycleMaxTerm;
        this.levelOneSecuredFirstCycleMaxAmount = levelOneSecuredFirstCycleMaxAmount;
        this.levelOneSecuredFirstCycleMinTerm = levelOneSecuredFirstCycleMinTerm;
        this.levelOneSecuredFirstCycleMaxTerm = levelOneSecuredFirstCycleMaxTerm;
        this.levelOneSecuredSecondCycleMaxAmount = levelOneSecuredSecondCycleMaxAmount;
        this.levelOneSecuredSecondCycleMinTerm = levelOneSecuredSecondCycleMinTerm;
        this.levelOneSecuredSecondCycleMaxTerm = levelOneSecuredSecondCycleMaxTerm;

        this.levelTwoUnsecuredFirstCycleMaxAmount = levelTwoUnsecuredFirstCycleMaxAmount;
        this.levelTwoUnsecuredFirstCycleMinTerm = levelTwoUnsecuredFirstCycleMinTerm;
        this.levelTwoUnsecuredFirstCycleMaxTerm = levelTwoUnsecuredFirstCycleMaxTerm;
        this.levelTwoUnsecuredSecondCycleMaxAmount = levelTwoUnsecuredSecondCycleMaxAmount;
        this.levelTwoUnsecuredSecondCycleMinTerm = levelTwoUnsecuredSecondCycleMinTerm;
        this.levelTwoUnsecuredSecondCycleMaxTerm = levelTwoUnsecuredSecondCycleMaxTerm;
        this.levelTwoSecuredFirstCycleMaxAmount = levelTwoSecuredFirstCycleMaxAmount;
        this.levelTwoSecuredFirstCycleMinTerm = levelTwoSecuredFirstCycleMinTerm;
        this.levelTwoSecuredFirstCycleMaxTerm = levelTwoSecuredFirstCycleMaxTerm;
        this.levelTwoSecuredSecondCycleMaxAmount = levelTwoSecuredSecondCycleMaxAmount;
        this.levelTwoSecuredSecondCycleMinTerm = levelTwoSecuredSecondCycleMinTerm;
        this.levelTwoSecuredSecondCycleMaxTerm = levelTwoSecuredSecondCycleMaxTerm;

        this.levelThreeUnsecuredFirstCycleMaxAmount = levelThreeUnsecuredFirstCycleMaxAmount;
        this.levelThreeUnsecuredFirstCycleMinTerm = levelThreeUnsecuredFirstCycleMinTerm;
        this.levelThreeUnsecuredFirstCycleMaxTerm = levelThreeUnsecuredFirstCycleMaxTerm;
        this.levelThreeUnsecuredSecondCycleMaxAmount = levelThreeUnsecuredSecondCycleMaxAmount;
        this.levelThreeUnsecuredSecondCycleMinTerm = levelThreeUnsecuredSecondCycleMinTerm;
        this.levelThreeUnsecuredSecondCycleMaxTerm = levelThreeUnsecuredSecondCycleMaxTerm;
        this.levelThreeSecuredFirstCycleMaxAmount = levelThreeSecuredFirstCycleMaxAmount;
        this.levelThreeSecuredFirstCycleMinTerm = levelThreeSecuredFirstCycleMinTerm;
        this.levelThreeSecuredFirstCycleMaxTerm = levelThreeSecuredFirstCycleMaxTerm;
        this.levelThreeSecuredSecondCycleMaxAmount = levelThreeSecuredSecondCycleMaxAmount;
        this.levelThreeSecuredSecondCycleMinTerm = levelThreeSecuredSecondCycleMinTerm;
        this.levelThreeSecuredSecondCycleMaxTerm = levelThreeSecuredSecondCycleMaxTerm;

        this.levelFourUnsecuredFirstCycleMaxAmount = levelFourUnsecuredFirstCycleMaxAmount;
        this.levelFourUnsecuredFirstCycleMinTerm = levelFourUnsecuredFirstCycleMinTerm;
        this.levelFourUnsecuredFirstCycleMaxTerm = levelFourUnsecuredFirstCycleMaxTerm;
        this.levelFourUnsecuredSecondCycleMaxAmount = levelFourUnsecuredSecondCycleMaxAmount;
        this.levelFourUnsecuredSecondCycleMinTerm = levelFourUnsecuredSecondCycleMinTerm;
        this.levelFourUnsecuredSecondCycleMaxTerm = levelFourUnsecuredSecondCycleMaxTerm;
        this.levelFourSecuredFirstCycleMaxAmount = levelFourSecuredFirstCycleMaxAmount;
        this.levelFourSecuredFirstCycleMinTerm = levelFourSecuredFirstCycleMinTerm;
        this.levelFourSecuredFirstCycleMaxTerm = levelFourSecuredFirstCycleMaxTerm;
        this.levelFourSecuredSecondCycleMaxAmount = levelFourSecuredSecondCycleMaxAmount;
        this.levelFourSecuredSecondCycleMinTerm = levelFourSecuredSecondCycleMinTerm;
        this.levelFourSecuredSecondCycleMaxTerm = levelFourSecuredSecondCycleMaxTerm;

        this.levelFiveUnsecuredFirstCycleMaxAmount = levelFiveUnsecuredFirstCycleMaxAmount;
        this.levelFiveUnsecuredFirstCycleMinTerm = levelFiveUnsecuredFirstCycleMinTerm;
        this.levelFiveUnsecuredFirstCycleMaxTerm = levelFiveUnsecuredFirstCycleMaxTerm;
        this.levelFiveUnsecuredSecondCycleMaxAmount = levelFiveUnsecuredSecondCycleMaxAmount;
        this.levelFiveUnsecuredSecondCycleMinTerm = levelFiveUnsecuredSecondCycleMinTerm;
        this.levelFiveUnsecuredSecondCycleMaxTerm = levelFiveUnsecuredSecondCycleMaxTerm;
        this.levelFiveSecuredFirstCycleMaxAmount = levelFiveSecuredFirstCycleMaxAmount;
        this.levelFiveSecuredFirstCycleMinTerm = levelFiveSecuredFirstCycleMinTerm;
        this.levelFiveSecuredFirstCycleMaxTerm = levelFiveSecuredFirstCycleMaxTerm;
        this.levelFiveSecuredSecondCycleMaxAmount = levelFiveSecuredSecondCycleMaxAmount;
        this.levelFiveSecuredSecondCycleMinTerm = levelFiveSecuredSecondCycleMinTerm;
        this.levelFiveSecuredSecondCycleMaxTerm = levelFiveSecuredSecondCycleMaxTerm;
    }

    public Map<String, Object> update(final JsonCommand command) {

        final Map<String, Object> actualChanges = new LinkedHashMap<>(9);

        if (command.isChangeInStringParameterNamed(LoanApprovalMatrixConstants.currencyParameterName, this.currency)) {
            final String newValue = command.stringValueOfParameterNamed(LoanApprovalMatrixConstants.currencyParameterName);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.currencyParameterName, this.currency, newValue);
            this.currency = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxAmount,
                this.levelOneUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxAmount, this.levelOneUnsecuredFirstCycleMaxAmount, newValue);
            this.levelOneUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMinTerm,
                this.levelOneUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMinTerm, this.levelOneUnsecuredFirstCycleMinTerm, newValue);
            this.levelOneUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxTerm,
                this.levelOneUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxTerm, this.levelOneUnsecuredFirstCycleMaxTerm, newValue);
            this.levelOneUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxAmount,
                this.levelOneUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxAmount, this.levelOneUnsecuredSecondCycleMaxAmount, newValue);
            this.levelOneUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMinTerm,
                this.levelOneUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMinTerm, this.levelOneUnsecuredSecondCycleMinTerm, newValue);
            this.levelOneUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxTerm,
                this.levelOneUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxTerm, this.levelOneUnsecuredSecondCycleMaxTerm, newValue);
            this.levelOneUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxAmount,
                this.levelOneSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxAmount, this.levelOneSecuredFirstCycleMaxAmount, newValue);
            this.levelOneSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMinTerm,
                this.levelOneSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMinTerm, this.levelOneSecuredFirstCycleMinTerm, newValue);
            this.levelOneSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxTerm,
                this.levelOneSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxTerm, this.levelOneSecuredFirstCycleMaxTerm, newValue);
            this.levelOneSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxAmount,
                this.levelOneSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxAmount, this.levelOneSecuredSecondCycleMaxAmount, newValue);
            this.levelOneSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMinTerm,
                this.levelOneSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMinTerm, this.levelOneSecuredSecondCycleMinTerm, newValue);
            this.levelOneSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxTerm,
                this.levelOneSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxTerm, this.levelOneSecuredSecondCycleMaxTerm, newValue);
            this.levelOneSecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxAmount,
                this.levelTwoUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxAmount, this.levelTwoUnsecuredFirstCycleMaxAmount, newValue);
            this.levelTwoUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMinTerm,
                this.levelTwoUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMinTerm, this.levelTwoUnsecuredFirstCycleMinTerm, newValue);
            this.levelTwoUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxTerm,
                this.levelTwoUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxTerm, this.levelTwoUnsecuredFirstCycleMaxTerm, newValue);
            this.levelTwoUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxAmount,
                this.levelTwoUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxAmount, this.levelTwoUnsecuredSecondCycleMaxAmount, newValue);
            this.levelTwoUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMinTerm,
                this.levelTwoUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMinTerm, this.levelTwoUnsecuredSecondCycleMinTerm, newValue);
            this.levelTwoUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxTerm,
                this.levelTwoUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxTerm, this.levelTwoUnsecuredSecondCycleMaxTerm, newValue);
            this.levelTwoUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxAmount,
                this.levelTwoSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxAmount, this.levelTwoSecuredFirstCycleMaxAmount, newValue);
            this.levelTwoSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMinTerm,
                this.levelTwoSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMinTerm, this.levelTwoSecuredFirstCycleMinTerm, newValue);
            this.levelTwoSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxTerm,
                this.levelTwoSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxTerm, this.levelTwoSecuredFirstCycleMaxTerm, newValue);
            this.levelTwoSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxAmount,
                this.levelTwoSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxAmount, this.levelTwoSecuredSecondCycleMaxAmount, newValue);
            this.levelTwoSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMinTerm,
                this.levelTwoSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMinTerm, this.levelTwoSecuredSecondCycleMinTerm, newValue);
            this.levelTwoSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxTerm,
                this.levelTwoSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxTerm, this.levelTwoSecuredSecondCycleMaxTerm, newValue);
            this.levelTwoSecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxAmount,
                this.levelThreeUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxAmount, this.levelThreeUnsecuredFirstCycleMaxAmount, newValue);
            this.levelThreeUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMinTerm,
                this.levelThreeUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMinTerm, this.levelThreeUnsecuredFirstCycleMinTerm, newValue);
            this.levelThreeUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxTerm,
                this.levelThreeUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxTerm, this.levelThreeUnsecuredFirstCycleMaxTerm, newValue);
            this.levelThreeUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxAmount,
                this.levelThreeUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxAmount, this.levelThreeUnsecuredSecondCycleMaxAmount, newValue);
            this.levelThreeUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMinTerm,
                this.levelThreeUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command
                    .integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMinTerm, this.levelThreeUnsecuredSecondCycleMinTerm, newValue);
            this.levelThreeUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxTerm,
                this.levelThreeUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command
                    .integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxTerm, this.levelThreeUnsecuredSecondCycleMaxTerm, newValue);
            this.levelThreeUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxAmount,
                this.levelThreeSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxAmount, this.levelThreeSecuredFirstCycleMaxAmount, newValue);
            this.levelThreeSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMinTerm,
                this.levelThreeSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMinTerm, this.levelThreeSecuredFirstCycleMinTerm, newValue);
            this.levelThreeSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxTerm,
                this.levelThreeSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxTerm, this.levelThreeSecuredFirstCycleMaxTerm, newValue);
            this.levelThreeSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxAmount,
                this.levelThreeSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxAmount, this.levelThreeSecuredSecondCycleMaxAmount, newValue);
            this.levelThreeSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMinTerm,
                this.levelThreeSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMinTerm, this.levelThreeSecuredSecondCycleMinTerm, newValue);
            this.levelThreeSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxTerm,
                this.levelThreeSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxTerm, this.levelThreeSecuredSecondCycleMaxTerm, newValue);
            this.levelThreeSecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxAmount,
                this.levelFourUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxAmount, this.levelFourUnsecuredFirstCycleMaxAmount, newValue);
            this.levelFourUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMinTerm,
                this.levelFourUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMinTerm, this.levelFourUnsecuredFirstCycleMinTerm, newValue);
            this.levelFourUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxTerm,
                this.levelFourUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxTerm, this.levelFourUnsecuredFirstCycleMaxTerm, newValue);
            this.levelFourUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxAmount,
                this.levelFourUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxAmount, this.levelFourUnsecuredSecondCycleMaxAmount, newValue);
            this.levelFourUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMinTerm,
                this.levelFourUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMinTerm, this.levelFourUnsecuredSecondCycleMinTerm, newValue);
            this.levelFourUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxTerm,
                this.levelFourUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxTerm, this.levelFourUnsecuredSecondCycleMaxTerm, newValue);
            this.levelFourUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxAmount,
                this.levelFourSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxAmount, this.levelFourSecuredFirstCycleMaxAmount, newValue);
            this.levelFourSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMinTerm,
                this.levelFourSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMinTerm, this.levelFourSecuredFirstCycleMinTerm, newValue);
            this.levelFourSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxTerm,
                this.levelFourSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxTerm, this.levelFourSecuredFirstCycleMaxTerm, newValue);
            this.levelFourSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxAmount,
                this.levelFourSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxAmount, this.levelFourSecuredSecondCycleMaxAmount, newValue);
            this.levelFourSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMinTerm,
                this.levelFourSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMinTerm, this.levelFourSecuredSecondCycleMinTerm, newValue);
            this.levelFourSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxTerm,
                this.levelFourSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxTerm, this.levelFourSecuredSecondCycleMaxTerm, newValue);
            this.levelFourSecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxAmount,
                this.levelFiveUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxAmount, this.levelFiveUnsecuredFirstCycleMaxAmount, newValue);
            this.levelFiveUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMinTerm,
                this.levelFiveUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMinTerm, this.levelFiveUnsecuredFirstCycleMinTerm, newValue);
            this.levelFiveUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxTerm,
                this.levelFiveUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxTerm, this.levelFiveUnsecuredFirstCycleMaxTerm, newValue);
            this.levelFiveUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxAmount,
                this.levelFiveUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxAmount, this.levelFiveUnsecuredSecondCycleMaxAmount, newValue);
            this.levelFiveUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMinTerm,
                this.levelFiveUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMinTerm, this.levelFiveUnsecuredSecondCycleMinTerm, newValue);
            this.levelFiveUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxTerm,
                this.levelFiveUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxTerm, this.levelFiveUnsecuredSecondCycleMaxTerm, newValue);
            this.levelFiveUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxAmount,
                this.levelFiveSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxAmount, this.levelFiveSecuredFirstCycleMaxAmount, newValue);
            this.levelFiveSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMinTerm,
                this.levelFiveSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMinTerm, this.levelFiveSecuredFirstCycleMinTerm, newValue);
            this.levelFiveSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxTerm,
                this.levelFiveSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxTerm, this.levelFiveSecuredFirstCycleMaxTerm, newValue);
            this.levelFiveSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxAmount,
                this.levelFiveSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxAmount);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxAmount, this.levelFiveSecuredSecondCycleMaxAmount, newValue);
            this.levelFiveSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMinTerm,
                this.levelFiveSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMinTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMinTerm, this.levelFiveSecuredSecondCycleMinTerm, newValue);
            this.levelFiveSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxTerm,
                this.levelFiveSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxTerm);
            AuditChangeRecorder.recordChange(actualChanges, LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxTerm, this.levelFiveSecuredSecondCycleMaxTerm, newValue);
            this.levelFiveSecuredSecondCycleMaxTerm = newValue;
        }

        return actualChanges;
    }
}
