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
import java.time.LocalDate;
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
     * equally across the installments that follow. Each of those shares is rounded down to the
     * currency precision, and the final installment receives the leftover so the rounding
     * remainder stays on the last installment of the product term. Due dates are not part of this result.
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
                final BigDecimal share = tail.divide(BigDecimal.valueOf(tailCount), digits, RoundingMode.DOWN);
                amounts.set(grace + 1 + i, share);
                allocated = allocated.add(share);
            }
        }
        return amounts;
    }

    /**
     * The first disbursed tranche keeps the grace and fixed first repayment. Each later tranche is
     * added, in equal shares, only to installments due after that tranche was disbursed. Installments
     * already due stay as they were. The rounding remainder of each later tranche stays on the last
     * of those later installments.
     */
    public static List<BigDecimal> principalsForDisbursedTranches(final List<LocalDate> disbursedOn,
            final List<BigDecimal> tranchePrincipals, final List<LocalDate> dueDates, final BigDecimal firstInstallmentPrincipal,
            final int graceInstallments, final int digits) {
        if (dueDates == null || dueDates.isEmpty()) {
            return List.of();
        }
        final BigDecimal firstTranche = tranchePrincipals == null || tranchePrincipals.isEmpty() ? BigDecimal.ZERO : tranchePrincipals.get(0);
        final List<BigDecimal> principals = new ArrayList<>(
                installmentPrincipals(firstTranche, firstInstallmentPrincipal, graceInstallments, dueDates.size(), digits));
        if (tranchePrincipals == null) {
            return principals;
        }
        for (int tranche = 1; tranche < tranchePrincipals.size(); tranche++) {
            final LocalDate when = disbursedOn != null && tranche < disbursedOn.size() ? disbursedOn.get(tranche) : null;
            addTrancheToLaterInstallments(principals, dueDates, when, tranchePrincipals.get(tranche), digits);
        }
        return principals;
    }

    /**
     * Adds one disbursed tranche to installments whose due date is after the disbursement. An
     * installment due on the disbursement date is left unchanged. When every installment is already
     * due, the amount stays on the final installment so the schedule still matches the money paid out.
     */
    static void addTrancheToLaterInstallments(final List<BigDecimal> principals, final List<LocalDate> dueDates,
            final LocalDate disbursedOn, final BigDecimal tranchePrincipal, final int digits) {
        if (principals == null || principals.isEmpty() || tranchePrincipal == null || tranchePrincipal.signum() <= 0) {
            return;
        }
        final BigDecimal amount = tranchePrincipal.max(BigDecimal.ZERO).setScale(digits, RoundingMode.HALF_UP);
        final List<Integer> later = new ArrayList<>();
        if (disbursedOn != null && dueDates != null) {
            for (int i = 0; i < principals.size(); i++) {
                final LocalDate due = i < dueDates.size() ? dueDates.get(i) : null;
                if (due != null && due.isAfter(disbursedOn)) {
                    later.add(i);
                }
            }
        }
        final BigDecimal zero = BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP);
        if (later.isEmpty()) {
            final int last = principals.size() - 1;
            principals.set(last, principals.get(last).add(amount).setScale(digits, RoundingMode.HALF_UP));
            return;
        }
        BigDecimal allocated = zero;
        for (int n = 0; n < later.size(); n++) {
            final BigDecimal share = n == later.size() - 1 ? amount.subtract(allocated).setScale(digits, RoundingMode.HALF_UP)
                    : amount.divide(BigDecimal.valueOf(later.size()), digits, RoundingMode.DOWN);
            if (n < later.size() - 1) {
                allocated = allocated.add(share);
            }
            final int index = later.get(n);
            principals.set(index, principals.get(index).add(share).setScale(digits, RoundingMode.HALF_UP));
        }
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
        final Integer grace = loan.repaymentScheduleDetail() == null ? null : loan.repaymentScheduleDetail().graceOnPrincipalPayment();
        final int digits = loan.getCurrency() == null ? 2 : loan.getCurrency().getDigitsAfterDecimal();
        final List<BigDecimal> principals = principalsFromDisbursedTranches(loan, regular, firstInstallment, grace == null ? 0 : grace,
                digits);
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

    private static List<BigDecimal> principalsFromDisbursedTranches(final Loan loan, final List<LoanRepaymentScheduleInstallment> regular,
            final BigDecimal firstInstallment, final int grace, final int digits) {
        final List<LoanDisbursementDetails> disbursed = new ArrayList<>();
        if (loan.getDisbursementDetails() != null) {
            for (final LoanDisbursementDetails detail : loan.getDisbursementDetails()) {
                if (detail.actualDisbursementDate() != null && detail.principal() != null && detail.principal().signum() > 0) {
                    disbursed.add(detail);
                }
            }
        }
        disbursed.sort(Comparator.comparing(LoanDisbursementDetails::actualDisbursementDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LoanDisbursementDetails::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        if (disbursed.isEmpty()) {
            final BigDecimal disbursedAmount = loan.getDisbursedAmount() == null ? BigDecimal.ZERO : loan.getDisbursedAmount();
            return installmentPrincipals(schedulePrincipal(disbursedAmount, firstTrancheAmount(loan)), firstInstallment, grace, regular.size(),
                    digits);
        }
        final List<LocalDate> disbursedOn = new ArrayList<>();
        final List<BigDecimal> tranchePrincipals = new ArrayList<>();
        for (final LoanDisbursementDetails detail : disbursed) {
            disbursedOn.add(detail.actualDisbursementDate());
            tranchePrincipals.add(detail.principal());
        }
        final List<LocalDate> dueDates = new ArrayList<>();
        for (final LoanRepaymentScheduleInstallment installment : regular) {
            dueDates.add(installment.getDueDate());
        }
        return principalsForDisbursedTranches(disbursedOn, tranchePrincipals, dueDates, firstInstallment, grace, digits);
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
