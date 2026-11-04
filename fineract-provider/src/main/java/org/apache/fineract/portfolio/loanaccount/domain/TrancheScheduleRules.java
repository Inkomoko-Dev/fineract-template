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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Optional schedule shape for a multi-disburse product. A blank first installment leaves the
 * generated schedule unchanged, so other multi-disburse products keep their current behavior.
 * The amount is whatever the product stores in its own currency. There is no country table.
 */
public final class TrancheScheduleRules {

    private TrancheScheduleRules() {}

    public static boolean rescheduleAllowed(final boolean multiDisburseLoan, final boolean disallowExpectedDisbursements,
            final BigDecimal firstInstallmentPrincipal) {
        if (!multiDisburseLoan || disallowExpectedDisbursements) {
            return true;
        }
        return firstInstallmentPrincipal != null;
    }

    /**
     * Principals for each installment. Grace installments are zero. The next installment is the
     * configured first repayment, capped by the disbursed principal. The remainder is split
     * equally across the installments that follow, and the last of those absorbs the rounding
     * remainder. Due dates are not part of this result.
     */
    public static List<BigDecimal> installmentPrincipals(final BigDecimal disbursedPrincipal, final BigDecimal firstInstallmentPrincipal,
            final int graceInstallments, final int installmentCount, final int digits) {
        if (installmentCount <= 0) {
            return List.of();
        }
        final BigDecimal zero = BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP);
        final List<BigDecimal> amounts = new ArrayList<>(Collections.nCopies(installmentCount, zero));
        final BigDecimal outstanding = disbursedPrincipal == null ? zero
                : disbursedPrincipal.max(BigDecimal.ZERO).setScale(digits, RoundingMode.HALF_UP);
        if (outstanding.signum() == 0) {
            return amounts;
        }
        final int grace = Math.max(0, Math.min(graceInstallments, installmentCount));
        if (grace >= installmentCount) {
            amounts.set(installmentCount - 1, outstanding);
            return amounts;
        }
        BigDecimal first = firstInstallmentPrincipal == null ? zero : firstInstallmentPrincipal.max(BigDecimal.ZERO);
        first = first.min(outstanding).setScale(digits, RoundingMode.HALF_UP);
        final int tailCount = installmentCount - grace - 1;
        if (tailCount <= 0) {
            amounts.set(grace, outstanding);
            return amounts;
        }
        amounts.set(grace, first);
        final BigDecimal tail = outstanding.subtract(first);
        BigDecimal allocated = zero;
        for (int i = 0; i < tailCount; i++) {
            if (i == tailCount - 1) {
                amounts.set(grace + 1 + i, tail.subtract(allocated).setScale(digits, RoundingMode.HALF_UP));
            } else {
                final BigDecimal share = tail.divide(BigDecimal.valueOf(tailCount), digits, RoundingMode.HALF_UP);
                amounts.set(grace + 1 + i, share);
                allocated = allocated.add(share);
            }
        }
        return amounts;
    }

    public static void apply(final Loan loan) {
        if (loan == null || loan.loanProduct() == null || !loan.loanProduct().isMultiDisburseLoan()) {
            return;
        }
        final BigDecimal firstInstallment = configuredFirstRepayment(loan);
        if (firstInstallment == null) {
            return;
        }
        final List<LoanRepaymentScheduleInstallment> regular = new ArrayList<>();
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (!installment.isRecalculatedInterestComponent()) {
                regular.add(installment);
            }
        }
        if (regular.isEmpty()) {
            return;
        }
        regular.sort(LoanRepaymentScheduleInstallment.installmentNumberComparator);
        final BigDecimal disbursed = loan.getDisbursedAmount() == null ? BigDecimal.ZERO : loan.getDisbursedAmount();
        final Integer grace = loan.repaymentScheduleDetail() == null ? null : loan.repaymentScheduleDetail().graceOnPrincipalPayment();
        final int digits = loan.getCurrency() == null ? 2 : loan.getCurrency().getDigitsAfterDecimal();
        final List<BigDecimal> principals = installmentPrincipals(schedulePrincipal(disbursed, firstTrancheAmount(loan)), firstInstallment,
                grace == null ? 0 : grace, regular.size(), digits);
        for (int i = 0; i < regular.size(); i++) {
            final LoanRepaymentScheduleInstallment installment = regular.get(i);
            BigDecimal principal = principals.get(i);
            final BigDecimal completed = installment.getPrincipalCompleted(loan.getCurrency()).getAmount();
            if (completed != null && principal.compareTo(completed) < 0) {
                principal = completed;
            }
            installment.updatePrincipal(principal);
        }
        loan.updateLoanSummaryDerivedFields();
    }

    /**
     * Before any tranche is disbursed the schedule uses the first tranche. Once money has gone
     * out, the schedule uses only the amount disbursed so far.
     */
    static BigDecimal schedulePrincipal(final BigDecimal disbursed, final BigDecimal firstTranche) {
        if (disbursed != null && disbursed.signum() > 0) {
            return disbursed;
        }
        return firstTranche;
    }

    /**
     * A loan amount is a fixed override. A blank loan amount uses the product. When the amount is
     * marked as a percentage, it is a percentage of the approved loan amount.
     */
    public static BigDecimal firstRepaymentPrincipal(final BigDecimal configured, final boolean asPercentage,
            final BigDecimal approvedAmount, final int digits) {
        if (configured == null) {
            return null;
        }
        if (!asPercentage) {
            return configured;
        }
        return TrancheReleaseRules.percentageOf(approvedAmount, configured, digits);
    }

    static BigDecimal configuredFirstRepayment(final Loan loan) {
        final int digits = loan.getCurrency() == null ? 2 : loan.getCurrency().getDigitsAfterDecimal();
        final BigDecimal approved = loan.getApprovedPrincipal() != null ? loan.getApprovedPrincipal() : loan.getProposedPrincipal();
        if (loan.getFirstInstallmentPrincipal() != null) {
            return firstRepaymentPrincipal(loan.getFirstInstallmentPrincipal(),
                    Boolean.TRUE.equals(loan.getFirstInstallmentAsPercentage()), approved, digits);
        }
        return firstRepaymentPrincipal(loan.loanProduct().getFirstInstallmentPrincipal(),
                Boolean.TRUE.equals(loan.loanProduct().getFirstInstallmentAsPercentage()), approved, digits);
    }

    private static BigDecimal firstTrancheAmount(final Loan loan) {
        if (loan.getDisbursementDetails() != null && !loan.getDisbursementDetails().isEmpty()) {
            final LoanDisbursementDetails first = loan.getDisbursementDetails().stream()
                    .sorted(Comparator.comparing(LoanDisbursementDetails::expectedDisbursementDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(LoanDisbursementDetails::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .findFirst().orElse(null);
            if (first != null && first.principal() != null) {
                return first.principal();
            }
        }
        final BigDecimal base = loan.getApprovedPrincipal() != null ? loan.getApprovedPrincipal() : loan.getProposedPrincipal();
        if (Boolean.TRUE.equals(loan.loanProduct().getFirstTrancheAsPercentage())) {
            final int digits = loan.getCurrency() == null ? 2 : loan.getCurrency().getDigitsAfterDecimal();
            return TrancheReleaseRules.percentageOf(base, loan.loanProduct().getFirstTrancheAmount(), digits);
        }
        return loan.loanProduct().getFirstTrancheAmount();
    }
}
