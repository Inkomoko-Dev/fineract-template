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
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;

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
     * equally across the installments that follow (whatever term the loan has). Each of those
     * shares is rounded down to the currency precision, and the final installment receives the
     * leftover so the rounding remainder stays on the last installment of the product term.
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
     * After any disbursed amount, the schedule keeps grace at zero, the configured first repayment,
     * and equal shares of the remainder across every installment that follows for the loan term.
     */
    public static List<BigDecimal> principalsForDisbursedAmount(final BigDecimal totalDisbursed,
            final BigDecimal firstInstallmentPrincipal, final int graceInstallments, final int installmentCount, final int digits) {
        return installmentPrincipals(totalDisbursed, firstInstallmentPrincipal, graceInstallments, installmentCount, digits);
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
        final MonetaryCurrency currency = loan.getCurrency();
        final List<BigDecimal> principals = principalsFromDisbursedTranches(loan, regular, firstInstallment, grace == null ? 0 : grace,
                digits);
        for (int i = 0; i < regular.size(); i++) {
            final LoanRepaymentScheduleInstallment installment = regular.get(i);
            BigDecimal principal = principals.get(i);
            final BigDecimal completed = installment.getPrincipalCompleted(currency).getAmount();
            if (completed != null && principal.compareTo(completed) < 0) {
                principal = completed;
            }
            final boolean raisedAbovePaid = completed != null && principal.compareTo(completed) > 0 && installment.isObligationsMet();
            installment.updatePrincipal(principal);
            if (raisedAbovePaid) {
                installment.updateObligationMet(false);
                installment.updateObligationMetOnDate(null);
            } else if (currency != null) {
                installment.updateDerivedFields(currency, loan.getDisbursementDate());
            }
        }
        loan.updateLoanSummaryDerivedFields();
    }

    /**
     * A regenerated schedule keeps the maturity already stored on the loan unless an officer
     * deliberately moves it. The last installment is moved back to that date when the generator
     * shifted it, provided the date is still after the previous installment.
     */
    public static void keepOriginalMaturity(final Loan loan, final LocalDate originalMaturity) {
        if (loan == null || loan.loanProduct() == null || !loan.loanProduct().isMultiDisburseLoan() || originalMaturity == null
                || configuredFirstRepayment(loan) == null) {
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
        final LoanRepaymentScheduleInstallment last = regular.get(regular.size() - 1);
        final LocalDate previousDue = regular.size() > 1 ? regular.get(regular.size() - 2).getDueDate() : null;
        final LocalDate maturity = maturityToRetain(originalMaturity, last.getDueDate(), previousDue);
        if (maturity != null && !maturity.equals(last.getDueDate())) {
            last.updateDueDate(maturity);
        }
        if (maturity != null) {
            loan.retainMaturityDate(maturity);
        }
    }

    /**
     * Returns the maturity the last installment must keep. A null original maturity means the
     * schedule is being created and the generated date stands. The original date is rejected when
     * it is not after the previous installment.
     */
    static LocalDate maturityToRetain(final LocalDate originalMaturity, final LocalDate regeneratedLastDue, final LocalDate previousDue) {
        if (originalMaturity == null) {
            return regeneratedLastDue;
        }
        if (previousDue != null && !originalMaturity.isAfter(previousDue)) {
            final String message = "The original maturity date " + originalMaturity
                    + " is no longer after the previous installment due on " + previousDue + ".";
            final List<ApiParameterError> errors = new ArrayList<>();
            errors.add(ApiParameterError.parameterError("error.msg.loan.repayment.schedule.maturity.moved", message, "maturityDate",
                    originalMaturity, regeneratedLastDue));
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
        }
        return originalMaturity;
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
        BigDecimal totalDisbursed = BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP);
        if (loan.getDisbursementDetails() != null) {
            for (final LoanDisbursementDetails detail : loan.getDisbursementDetails()) {
                if (detail.actualDisbursementDate() != null && detail.principal() != null && detail.principal().signum() > 0) {
                    totalDisbursed = totalDisbursed.add(detail.principal().setScale(digits, RoundingMode.HALF_UP));
                }
            }
        }
        if (totalDisbursed.signum() <= 0) {
            final BigDecimal disbursedAmount = loan.getDisbursedAmount() == null ? BigDecimal.ZERO : loan.getDisbursedAmount();
            totalDisbursed = schedulePrincipal(disbursedAmount, firstTrancheAmount(loan));
            if (totalDisbursed == null) {
                totalDisbursed = BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP);
            } else {
                totalDisbursed = totalDisbursed.setScale(digits, RoundingMode.HALF_UP);
            }
        }
        return principalsForDisbursedAmount(totalDisbursed, firstInstallment, grace, regular.size(), digits);
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
