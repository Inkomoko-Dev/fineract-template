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
package org.apache.fineract.portfolio.loanproduct.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheReleaseRules;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;

@Embeddable
public class LoanProductTrancheDetails {

    @Column(name = "allow_multiple_disbursals")
    private boolean multiDisburseLoan;

    @Column(name = "max_disbursals", nullable = true)
    private Integer maxTrancheCount;

    @Column(name = "min_tranche_count")
    private Integer minTrancheCount;

    @Column(name = "max_outstanding_loan_balance", scale = 6, precision = 19, nullable = true)
    private BigDecimal outstandingLoanBalance;

    @Column(name = "tranche_principal_percentages", length = 255)
    private String tranchePrincipalPercentages;

    @Column(name = "previous_tranche_repaid_percentage", scale = 2, precision = 5)
    private BigDecimal previousTrancheRepaidPercentage;

    @Column(name = "first_installment_principal", scale = 6, precision = 19)
    private BigDecimal firstInstallmentPrincipal;

    @Column(name = "first_installment_as_percentage")
    private Boolean firstInstallmentAsPercentage;

    @Column(name = "first_tranche_as_percentage")
    private Boolean firstTrancheAsPercentage;

    @Column(name = "first_tranche_amount", scale = 6, precision = 19)
    private BigDecimal firstTrancheAmount;

    protected LoanProductTrancheDetails() {
        // TODO Auto-generated constructor stub
    }

    public LoanProductTrancheDetails(final boolean multiDisburseLoan, final Integer maxTrancheCount,
            final BigDecimal outstandingLoanBalance) {
        this.multiDisburseLoan = multiDisburseLoan;
        this.maxTrancheCount = maxTrancheCount;
        this.outstandingLoanBalance = outstandingLoanBalance;
    }

    public void update(final JsonCommand command, final Map<String, Object> actualChanges, final String localeAsInput) {
        if (command.isChangeInBooleanParameterNamed(LoanProductConstants.MULTI_DISBURSE_LOAN_PARAMETER_NAME, this.multiDisburseLoan)) {
            final boolean newValue = command.booleanPrimitiveValueOfParameterNamed(LoanProductConstants.MULTI_DISBURSE_LOAN_PARAMETER_NAME);
            actualChanges.put(LoanProductConstants.MULTI_DISBURSE_LOAN_PARAMETER_NAME, newValue);
            this.multiDisburseLoan = newValue;
        }

        if (this.multiDisburseLoan) {
            if (command.isChangeInIntegerParameterNamed(LoanProductConstants.MAX_TRANCHE_COUNT_PARAMETER_NAME, this.maxTrancheCount)) {
                final Integer newValue = command.integerValueOfParameterNamed(LoanProductConstants.MAX_TRANCHE_COUNT_PARAMETER_NAME);
                actualChanges.put(LoanProductConstants.MAX_TRANCHE_COUNT_PARAMETER_NAME, newValue);
                this.maxTrancheCount = newValue;
            }

            if (command.isChangeInBigDecimalParameterNamed(LoanProductConstants.OUTSTANDING_LOAN_BALANCE_PARAMETER_NAME,
                    this.outstandingLoanBalance)) {
                final BigDecimal newValue = command
                        .bigDecimalValueOfParameterNamed(LoanProductConstants.OUTSTANDING_LOAN_BALANCE_PARAMETER_NAME);
                actualChanges.put(LoanProductConstants.OUTSTANDING_LOAN_BALANCE_PARAMETER_NAME, newValue);
                actualChanges.put(LoanProductConstants.OUTSTANDING_LOAN_BALANCE_PARAMETER_NAME, localeAsInput);
                this.outstandingLoanBalance = newValue;
            }
            applyPercentageConfiguration(command, actualChanges);
        } else {
            this.maxTrancheCount = null;
            this.minTrancheCount = null;
            this.outstandingLoanBalance = null;
            this.tranchePrincipalPercentages = null;
            this.previousTrancheRepaidPercentage = null;
            this.firstInstallmentPrincipal = null;
            this.firstInstallmentAsPercentage = null;
            this.firstTrancheAsPercentage = null;
            this.firstTrancheAmount = null;
        }
    }

    public void applyPercentageConfiguration(final JsonCommand command, final Map<String, Object> actualChanges) {
        if (!this.multiDisburseLoan) {
            return;
        }
        if (command.parameterExists(LoanProductConstants.MIN_TRANCHE_COUNT_PARAMETER_NAME)) {
            final Integer newValue = command.integerValueOfParameterNamed(LoanProductConstants.MIN_TRANCHE_COUNT_PARAMETER_NAME);
            if (!Objects.equals(newValue, this.minTrancheCount)) {
                this.minTrancheCount = newValue;
                if (actualChanges != null) {
                    actualChanges.put(LoanProductConstants.MIN_TRANCHE_COUNT_PARAMETER_NAME, newValue);
                }
            }
        }
        if (command.parameterExists(LoanProductConstants.TRANCHE_PRINCIPAL_PERCENTAGES)) {
            final String raw = command.stringValueOfParameterNamedAllowingNull(LoanProductConstants.TRANCHE_PRINCIPAL_PERCENTAGES);
            final String normalized = raw == null || raw.isBlank() ? null : TrancheReleaseRules.normalize(raw);
            if (!Objects.equals(normalized, this.tranchePrincipalPercentages)) {
                this.tranchePrincipalPercentages = normalized;
                if (actualChanges != null) {
                    actualChanges.put(LoanProductConstants.TRANCHE_PRINCIPAL_PERCENTAGES, normalized);
                }
            }
        }
        if (command.parameterExists(LoanProductConstants.PREVIOUS_TRANCHE_REPAID_PERCENTAGE)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(LoanProductConstants.PREVIOUS_TRANCHE_REPAID_PERCENTAGE);
            if (!sameAmount(newValue, this.previousTrancheRepaidPercentage)) {
                this.previousTrancheRepaidPercentage = newValue;
                if (actualChanges != null) {
                    actualChanges.put(LoanProductConstants.PREVIOUS_TRANCHE_REPAID_PERCENTAGE, newValue);
                }
            }
        }
        if (command.parameterExists(LoanProductConstants.FIRST_INSTALLMENT_PRINCIPAL)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(LoanProductConstants.FIRST_INSTALLMENT_PRINCIPAL);
            if (!sameAmount(newValue, this.firstInstallmentPrincipal)) {
                this.firstInstallmentPrincipal = newValue;
                if (actualChanges != null) {
                    actualChanges.put(LoanProductConstants.FIRST_INSTALLMENT_PRINCIPAL, newValue);
                }
            }
        }
        if (command.parameterExists(LoanProductConstants.FIRST_INSTALLMENT_AS_PERCENTAGE)) {
            final Boolean newValue = command.booleanObjectValueOfParameterNamed(LoanProductConstants.FIRST_INSTALLMENT_AS_PERCENTAGE);
            if (!Objects.equals(newValue, this.firstInstallmentAsPercentage)) {
                this.firstInstallmentAsPercentage = newValue;
                if (actualChanges != null) {
                    actualChanges.put(LoanProductConstants.FIRST_INSTALLMENT_AS_PERCENTAGE, newValue);
                }
            }
        }
        if (command.parameterExists(LoanProductConstants.FIRST_TRANCHE_AS_PERCENTAGE)) {
            final Boolean newValue = command.booleanObjectValueOfParameterNamed(LoanProductConstants.FIRST_TRANCHE_AS_PERCENTAGE);
            if (!Objects.equals(newValue, this.firstTrancheAsPercentage)) {
                this.firstTrancheAsPercentage = newValue;
                if (actualChanges != null) {
                    actualChanges.put(LoanProductConstants.FIRST_TRANCHE_AS_PERCENTAGE, newValue);
                }
            }
        }
        if (command.parameterExists(LoanProductConstants.FIRST_TRANCHE_AMOUNT)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(LoanProductConstants.FIRST_TRANCHE_AMOUNT);
            if (!sameAmount(newValue, this.firstTrancheAmount)) {
                this.firstTrancheAmount = newValue;
                if (actualChanges != null) {
                    actualChanges.put(LoanProductConstants.FIRST_TRANCHE_AMOUNT, newValue);
                }
            }
        }
    }

    public boolean isMultiDisburseLoan() {
        return this.multiDisburseLoan;
    }

    public BigDecimal outstandingLoanBalance() {
        return this.outstandingLoanBalance;
    }

    public Integer maxTrancheCount() {
        return this.maxTrancheCount;
    }

    public Integer minTrancheCount() {
        return this.minTrancheCount;
    }

    public String getTranchePrincipalPercentages() {
        return this.tranchePrincipalPercentages;
    }

    public BigDecimal getPreviousTrancheRepaidPercentage() {
        return this.previousTrancheRepaidPercentage;
    }

    public BigDecimal getFirstInstallmentPrincipal() {
        return this.firstInstallmentPrincipal;
    }

    public Boolean getFirstInstallmentAsPercentage() {
        return this.firstInstallmentAsPercentage;
    }

    public Boolean getFirstTrancheAsPercentage() {
        return this.firstTrancheAsPercentage;
    }

    public BigDecimal getFirstTrancheAmount() {
        return this.firstTrancheAmount;
    }

    private static boolean sameAmount(final BigDecimal left, final BigDecimal right) {
        if (left == null) {
            return right == null;
        }
        if (right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

}
