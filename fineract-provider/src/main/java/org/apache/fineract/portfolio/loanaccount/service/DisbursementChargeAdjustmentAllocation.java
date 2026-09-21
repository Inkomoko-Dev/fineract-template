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
package org.apache.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;

final class DisbursementChargeAdjustmentAllocation {

    private final BigDecimal previousAmount;
    private final BigDecimal newAmount;
    private final BigDecimal paidAtDisbursementAmount;
    private final BigDecimal previousFeePaidPortion;
    private final BigDecimal previousFeeOutstandingPortion;
    private final BigDecimal previousOverpaymentPortion;
    private final BigDecimal feePaidPortion;
    private final BigDecimal feeOutstandingPortion;
    private final BigDecimal chargeIncomeIncrease;
    private final BigDecimal chargeIncomeDecrease;
    private final BigDecimal customerBalanceIncrease;
    private final BigDecimal customerBalanceDecrease;
    private final BigDecimal feeReceivableIncrease;
    private final BigDecimal feeReceivableDecrease;
    private final BigDecimal paidIncomeReclassificationPortion;
    private final BigDecimal outstandingIncomeReclassificationPortion;

    private DisbursementChargeAdjustmentAllocation(final BigDecimal previousAmount, final BigDecimal newAmount,
            final BigDecimal paidAtDisbursementAmount, final BigDecimal previousFeePaidPortion,
            final BigDecimal previousFeeOutstandingPortion, final BigDecimal previousOverpaymentPortion,
            final BigDecimal feePaidPortion, final BigDecimal feeOutstandingPortion,
            final BigDecimal chargeIncomeIncrease, final BigDecimal chargeIncomeDecrease,
            final BigDecimal customerBalanceIncrease, final BigDecimal customerBalanceDecrease,
            final BigDecimal feeReceivableIncrease, final BigDecimal feeReceivableDecrease,
            final BigDecimal paidIncomeReclassificationPortion,
            final BigDecimal outstandingIncomeReclassificationPortion) {
        this.previousAmount = previousAmount;
        this.newAmount = newAmount;
        this.paidAtDisbursementAmount = paidAtDisbursementAmount;
        this.previousFeePaidPortion = previousFeePaidPortion;
        this.previousFeeOutstandingPortion = previousFeeOutstandingPortion;
        this.previousOverpaymentPortion = previousOverpaymentPortion;
        this.feePaidPortion = feePaidPortion;
        this.feeOutstandingPortion = feeOutstandingPortion;
        this.chargeIncomeIncrease = chargeIncomeIncrease;
        this.chargeIncomeDecrease = chargeIncomeDecrease;
        this.customerBalanceIncrease = customerBalanceIncrease;
        this.customerBalanceDecrease = customerBalanceDecrease;
        this.feeReceivableIncrease = feeReceivableIncrease;
        this.feeReceivableDecrease = feeReceivableDecrease;
        this.paidIncomeReclassificationPortion = paidIncomeReclassificationPortion;
        this.outstandingIncomeReclassificationPortion = outstandingIncomeReclassificationPortion;
    }

    static DisbursementChargeAdjustmentAllocation from(final BigDecimal previousAmount, final BigDecimal newAmount,
            final BigDecimal paidAtDisbursementAmount) {
        final BigDecimal safePreviousAmount = zeroIfNull(previousAmount);
        final BigDecimal safeNewAmount = zeroIfNull(newAmount);
        final BigDecimal safePaidAtDisbursementAmount = zeroIfNull(paidAtDisbursementAmount);
        final BigDecimal previousFeePaidPortion = safePreviousAmount.min(safePaidAtDisbursementAmount);
        final BigDecimal feePaidPortion = safeNewAmount.min(safePaidAtDisbursementAmount);
        final BigDecimal previousFeeOutstandingPortion = safePreviousAmount.subtract(previousFeePaidPortion).max(BigDecimal.ZERO);
        final BigDecimal feeOutstandingPortion = safeNewAmount.subtract(feePaidPortion).max(BigDecimal.ZERO);
        return new DisbursementChargeAdjustmentAllocation(safePreviousAmount, safeNewAmount, safePaidAtDisbursementAmount,
                previousFeePaidPortion, previousFeeOutstandingPortion,
                safePaidAtDisbursementAmount.subtract(previousFeePaidPortion).max(BigDecimal.ZERO), feePaidPortion,
                feeOutstandingPortion, positive(safeNewAmount.subtract(safePreviousAmount)),
                positive(safePreviousAmount.subtract(safeNewAmount)), positive(feePaidPortion.subtract(previousFeePaidPortion)),
                positive(previousFeePaidPortion.subtract(feePaidPortion)),
                positive(feeOutstandingPortion.subtract(previousFeeOutstandingPortion)),
                positive(previousFeeOutstandingPortion.subtract(feeOutstandingPortion)),
                previousFeePaidPortion.min(feePaidPortion),
                previousFeeOutstandingPortion.min(feeOutstandingPortion));
    }

    boolean requiresAmountAdjustmentTransaction() {
        return this.chargeIncomeIncrease.compareTo(BigDecimal.ZERO) > 0
                || this.customerBalanceDecrease.compareTo(BigDecimal.ZERO) > 0;
    }

    BigDecimal amountAdjustmentTransactionAmount() {
        if (this.chargeIncomeIncrease.compareTo(BigDecimal.ZERO) > 0) {
            return this.chargeIncomeIncrease;
        }
        return this.customerBalanceDecrease;
    }

    BigDecimal previousFeePaidPortion() {
        return this.previousFeePaidPortion;
    }

    BigDecimal previousFeeOutstandingPortion() {
        return this.previousFeeOutstandingPortion;
    }

    BigDecimal previousOverpaymentPortion() {
        return this.previousOverpaymentPortion;
    }

    BigDecimal feePaidPortion() {
        return this.feePaidPortion;
    }

    BigDecimal feeOutstandingPortion() {
        return this.feeOutstandingPortion;
    }

    BigDecimal chargeIncomeIncrease() {
        return this.chargeIncomeIncrease;
    }

    BigDecimal chargeIncomeDecrease() {
        return this.chargeIncomeDecrease;
    }

    BigDecimal customerBalanceIncrease() {
        return this.customerBalanceIncrease;
    }

    BigDecimal customerBalanceDecrease() {
        return this.customerBalanceDecrease;
    }

    BigDecimal feeReceivableIncrease() {
        return this.feeReceivableIncrease;
    }

    BigDecimal feeReceivableDecrease() {
        return this.feeReceivableDecrease;
    }

    BigDecimal paidIncomeReclassificationPortion() {
        return this.paidIncomeReclassificationPortion;
    }

    BigDecimal outstandingIncomeReclassificationPortion() {
        return this.outstandingIncomeReclassificationPortion;
    }

    BigDecimal previousAmount() {
        return this.previousAmount;
    }

    BigDecimal newAmount() {
        return this.newAmount;
    }

    BigDecimal paidAtDisbursementAmount() {
        return this.paidAtDisbursementAmount;
    }

    private static BigDecimal positive(final BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) > 0 ? amount : BigDecimal.ZERO;
    }

    private static BigDecimal zeroIfNull(final BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
