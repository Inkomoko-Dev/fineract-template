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
import lombok.Data;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;
import org.apache.fineract.portfolio.loanaccount.api.LoanApprovalMatrixConstants;

@Data
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
            actualChanges.put(LoanApprovalMatrixConstants.currencyParameterName, newValue);
            this.currency = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxAmount,
                this.levelOneUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxAmount, newValue);
            this.levelOneUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMinTerm,
                this.levelOneUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMinTerm, newValue);
            this.levelOneUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxTerm,
                this.levelOneUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneUnsecuredFirstCycleMaxTerm, newValue);
            this.levelOneUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxAmount,
                this.levelOneUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxAmount, newValue);
            this.levelOneUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMinTerm,
                this.levelOneUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMinTerm, newValue);
            this.levelOneUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxTerm,
                this.levelOneUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneUnsecuredSecondCycleMaxTerm, newValue);
            this.levelOneUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxAmount,
                this.levelOneSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxAmount, newValue);
            this.levelOneSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMinTerm,
                this.levelOneSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMinTerm, newValue);
            this.levelOneSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxTerm,
                this.levelOneSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneSecuredFirstCycleMaxTerm, newValue);
            this.levelOneSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxAmount,
                this.levelOneSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxAmount, newValue);
            this.levelOneSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMinTerm,
                this.levelOneSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMinTerm, newValue);
            this.levelOneSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxTerm,
                this.levelOneSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelOneSecuredSecondCycleMaxTerm, newValue);
            this.levelOneSecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxAmount,
                this.levelTwoUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxAmount, newValue);
            this.levelTwoUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMinTerm,
                this.levelTwoUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMinTerm, newValue);
            this.levelTwoUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxTerm,
                this.levelTwoUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoUnsecuredFirstCycleMaxTerm, newValue);
            this.levelTwoUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxAmount,
                this.levelTwoUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxAmount, newValue);
            this.levelTwoUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMinTerm,
                this.levelTwoUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMinTerm, newValue);
            this.levelTwoUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxTerm,
                this.levelTwoUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoUnsecuredSecondCycleMaxTerm, newValue);
            this.levelTwoUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxAmount,
                this.levelTwoSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxAmount, newValue);
            this.levelTwoSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMinTerm,
                this.levelTwoSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMinTerm, newValue);
            this.levelTwoSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxTerm,
                this.levelTwoSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoSecuredFirstCycleMaxTerm, newValue);
            this.levelTwoSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxAmount,
                this.levelTwoSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxAmount, newValue);
            this.levelTwoSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMinTerm,
                this.levelTwoSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMinTerm, newValue);
            this.levelTwoSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxTerm,
                this.levelTwoSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelTwoSecuredSecondCycleMaxTerm, newValue);
            this.levelTwoSecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxAmount,
                this.levelThreeUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxAmount, newValue);
            this.levelThreeUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMinTerm,
                this.levelThreeUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMinTerm, newValue);
            this.levelThreeUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxTerm,
                this.levelThreeUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeUnsecuredFirstCycleMaxTerm, newValue);
            this.levelThreeUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxAmount,
                this.levelThreeUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxAmount, newValue);
            this.levelThreeUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMinTerm,
                this.levelThreeUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command
                    .integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMinTerm, newValue);
            this.levelThreeUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxTerm,
                this.levelThreeUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command
                    .integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeUnsecuredSecondCycleMaxTerm, newValue);
            this.levelThreeUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxAmount,
                this.levelThreeSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxAmount, newValue);
            this.levelThreeSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMinTerm,
                this.levelThreeSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMinTerm, newValue);
            this.levelThreeSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxTerm,
                this.levelThreeSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeSecuredFirstCycleMaxTerm, newValue);
            this.levelThreeSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxAmount,
                this.levelThreeSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxAmount, newValue);
            this.levelThreeSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMinTerm,
                this.levelThreeSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMinTerm, newValue);
            this.levelThreeSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxTerm,
                this.levelThreeSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelThreeSecuredSecondCycleMaxTerm, newValue);
            this.levelThreeSecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxAmount,
                this.levelFourUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxAmount, newValue);
            this.levelFourUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMinTerm,
                this.levelFourUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMinTerm, newValue);
            this.levelFourUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxTerm,
                this.levelFourUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourUnsecuredFirstCycleMaxTerm, newValue);
            this.levelFourUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxAmount,
                this.levelFourUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxAmount, newValue);
            this.levelFourUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMinTerm,
                this.levelFourUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMinTerm, newValue);
            this.levelFourUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxTerm,
                this.levelFourUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourUnsecuredSecondCycleMaxTerm, newValue);
            this.levelFourUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxAmount,
                this.levelFourSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxAmount, newValue);
            this.levelFourSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMinTerm,
                this.levelFourSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMinTerm, newValue);
            this.levelFourSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxTerm,
                this.levelFourSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourSecuredFirstCycleMaxTerm, newValue);
            this.levelFourSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxAmount,
                this.levelFourSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxAmount, newValue);
            this.levelFourSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMinTerm,
                this.levelFourSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMinTerm, newValue);
            this.levelFourSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxTerm,
                this.levelFourSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFourSecuredSecondCycleMaxTerm, newValue);
            this.levelFourSecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxAmount,
                this.levelFiveUnsecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxAmount, newValue);
            this.levelFiveUnsecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMinTerm,
                this.levelFiveUnsecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMinTerm, newValue);
            this.levelFiveUnsecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxTerm,
                this.levelFiveUnsecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveUnsecuredFirstCycleMaxTerm, newValue);
            this.levelFiveUnsecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxAmount,
                this.levelFiveUnsecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxAmount, newValue);
            this.levelFiveUnsecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMinTerm,
                this.levelFiveUnsecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMinTerm, newValue);
            this.levelFiveUnsecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxTerm,
                this.levelFiveUnsecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveUnsecuredSecondCycleMaxTerm, newValue);
            this.levelFiveUnsecuredSecondCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxAmount,
                this.levelFiveSecuredFirstCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxAmount, newValue);
            this.levelFiveSecuredFirstCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMinTerm,
                this.levelFiveSecuredFirstCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMinTerm, newValue);
            this.levelFiveSecuredFirstCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxTerm,
                this.levelFiveSecuredFirstCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveSecuredFirstCycleMaxTerm, newValue);
            this.levelFiveSecuredFirstCycleMaxTerm = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxAmount,
                this.levelFiveSecuredSecondCycleMaxAmount)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxAmount);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxAmount, newValue);
            this.levelFiveSecuredSecondCycleMaxAmount = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMinTerm,
                this.levelFiveSecuredSecondCycleMinTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMinTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMinTerm, newValue);
            this.levelFiveSecuredSecondCycleMinTerm = newValue;
        }
        if (command.isChangeInIntegerParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxTerm,
                this.levelFiveSecuredSecondCycleMaxTerm)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxTerm);
            actualChanges.put(LoanApprovalMatrixConstants.levelFiveSecuredSecondCycleMaxTerm, newValue);
            this.levelFiveSecuredSecondCycleMaxTerm = newValue;
        }

        return actualChanges;
    }
}
