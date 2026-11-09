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
import java.util.Comparator;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.exception.BelowMinimumTrancheCountException;
import org.apache.fineract.portfolio.loanaccount.exception.ExceedingTrancheCountException;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;

/**
 * Percentage splits and the repayment gate for any multi-disburse loan product.
 * A blank configuration leaves existing tranche behavior unchanged.
 */
public final class TrancheReleaseRules {

    public static final String TRANCHE_PRINCIPAL_PERCENTAGES = "tranchePrincipalPercentages";
    public static final String PREVIOUS_TRANCHE_REPAID_PERCENTAGE = "previousTrancheRepaidPercentage";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private TrancheReleaseRules() {}

    public static BigDecimal percentageOf(final BigDecimal amount, final BigDecimal percentage, final int digits) {
        if (amount == null || percentage == null) {
            return null;
        }
        return amount.max(BigDecimal.ZERO).multiply(percentage).divide(ONE_HUNDRED, digits, RoundingMode.HALF_UP);
    }

    public static void requireTrancheCount(final int count, final Integer minimum, final Integer maximum) {
        if (maximum != null && count > maximum) {
            throw new ExceedingTrancheCountException("disbursementData", "Number of tranches cannot be greater than " + maximum, maximum,
                    count);
        }
        if (minimum != null && count < minimum) {
            throw new BelowMinimumTrancheCountException("disbursementData", "Number of tranches cannot be less than " + minimum, minimum,
                    count);
        }
    }

    public static List<BigDecimal> parsePercentages(final String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        final List<BigDecimal> values = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        int start = 0;
        for (int index = 0; index <= raw.length(); index++) {
            if (index != raw.length() && raw.charAt(index) != ',') {
                continue;
            }
            final String token = raw.substring(start, index).trim();
            start = index + 1;
            if (token.isEmpty()) {
                continue;
            }
            final BigDecimal value;
            try {
                value = new BigDecimal(token);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Each tranche percentage must be a number.");
            }
            if (value.signum() <= 0 || value.compareTo(ONE_HUNDRED) > 0) {
                throw new IllegalArgumentException("Each tranche percentage must be greater than 0 and at most 100.");
            }
            values.add(value);
            sum = sum.add(value);
        }
        if (values.size() < 2) {
            throw new IllegalArgumentException("Enter at least two tranche percentages.");
        }
        if (sum.compareTo(ONE_HUNDRED) != 0) {
            throw new IllegalArgumentException("Tranche percentages must add up to 100.");
        }
        return List.copyOf(values);
    }

    public static String normalize(final String raw) {
        final List<BigDecimal> values = parsePercentages(raw);
        if (values.isEmpty()) {
            return null;
        }
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values.get(i).stripTrailingZeros().toPlainString());
        }
        return builder.toString();
    }

    public static List<BigDecimal> principals(final BigDecimal principal, final List<BigDecimal> percentages, final int digits) {
        final List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < percentages.size(); i++) {
            if (i == percentages.size() - 1) {
                amounts.add(principal.subtract(allocated).setScale(digits, RoundingMode.HALF_UP));
            } else {
                final BigDecimal amount = principal.multiply(percentages.get(i)).divide(ONE_HUNDRED, digits, RoundingMode.HALF_UP);
                amounts.add(amount);
                allocated = allocated.add(amount);
            }
        }
        return amounts;
    }

    /**
     * A loan value replaces the product value. A blank loan value keeps the product value.
     */
    public static BigDecimal effectiveAmount(final BigDecimal loanValue, final BigDecimal productValue) {
        return loanValue != null ? loanValue : productValue;
    }

    public static String effectiveText(final String loanValue, final String productValue) {
        if (loanValue != null && !loanValue.isBlank()) {
            return loanValue;
        }
        return productValue;
    }

    public static BigDecimal requiredPrincipal(final BigDecimal previousPrincipal, final BigDecimal percentage, final int digits) {
        return previousPrincipal.multiply(percentage).divide(ONE_HUNDRED, digits, RoundingMode.HALF_UP);
    }

    /**
     * A percentage is a number above 0 and below 100. An amount is the currency value that must already be repaid.
     */
    public static BigDecimal requiredFromPrevious(final BigDecimal previousPrincipal, final BigDecimal minimum, final boolean asPercentage,
            final int digits) {
        if (minimum == null || previousPrincipal == null) {
            return null;
        }
        if (asPercentage) {
            return requiredPrincipal(previousPrincipal, minimum, digits);
        }
        return minimum.setScale(digits, RoundingMode.HALF_UP);
    }

    public static boolean acceptablePercentage(final BigDecimal value) {
        return value != null && value.signum() > 0 && value.compareTo(ONE_HUNDRED) < 0;
    }

    public static boolean acceptablePrincipalPercentage(final BigDecimal value) {
        return value != null && value.signum() > 0 && value.compareTo(ONE_HUNDRED) <= 0;
    }

    public static Assessment assess(final Loan loan) {
        if (loan == null || loan.loanProduct() == null || !loan.loanProduct().isMultiDisburseLoan()) {
            return null;
        }
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final LoanDisbursementDetails previous = lastDisbursed(loan);
        if (next == null || previous == null || previous.principal() == null || previous.actualDisbursementDate() == null) {
            return null;
        }
        final int digits = digits(loan);
        final BigDecimal trancheMinimum = next.getPreviousTrancheRepaidMinimum();
        final BigDecimal percentage;
        final BigDecimal required;
        if (trancheMinimum != null) {
            final Boolean rowChoice = next.getPreviousTrancheRepaidAsPercentage();
            final boolean asPercentage = rowChoice != null ? rowChoice
                    : loan.getPreviousTrancheThresholdAsPercentage() == null || loan.getPreviousTrancheThresholdAsPercentage();
            percentage = asPercentage ? trancheMinimum : null;
            required = requiredFromPrevious(previous.principal(), trancheMinimum, asPercentage, digits);
        } else {
            percentage = effectiveAmount(loan.getPreviousTrancheRepaidPercentage(),
                    loan.loanProduct().getPreviousTrancheRepaidPercentage());
            if (percentage == null) {
                return null;
            }
            required = requiredPrincipal(previous.principal(), percentage, digits);
        }
        if (required == null) {
            return null;
        }
        final List<LoanTransaction> repayments = new ArrayList<>();
        BigDecimal repaid = BigDecimal.ZERO;
        final LocalDate from = previous.actualDisbursementDate();
        for (LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction == null || !transaction.isRepayment() || transaction.getTransactionDate() == null
                    || transaction.getTransactionDate().isBefore(from)) {
                continue;
            }
            repayments.add(transaction);
            if (transaction.getPrincipalPortion(loan.getCurrency()) != null) {
                repaid = repaid.add(transaction.getPrincipalPortion(loan.getCurrency()).getAmount());
            }
        }
        return new Assessment(previous, next, percentage, required, repaid, List.copyOf(repayments));
    }

    public static void applyConfiguredPrincipals(final Loan loan) {
        final LoanProduct product = loan.loanProduct();
        if (product == null || !product.isMultiDisburseLoan()) {
            return;
        }
        if (loan.getDisbursementDetails() == null || loan.getDisbursementDetails().isEmpty()) {
            return;
        }
        final BigDecimal base = loan.getApprovedPrincipal() != null ? loan.getApprovedPrincipal() : loan.getProposedPrincipal();
        final List<LoanDisbursementDetails> ordered = loan.getDisbursementDetails().stream()
                .sorted(Comparator
                        .comparing(LoanDisbursementDetails::expectedDisbursementDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LoanDisbursementDetails::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        final List<PlannedTranche> planned = new ArrayList<>();
        boolean rowPercentage = false;
        for (LoanDisbursementDetails detail : ordered) {
            if (Boolean.TRUE.equals(detail.getPrincipalAsPercentage()) && detail.getPrincipalPercentage() != null) {
                rowPercentage = true;
            }
            planned.add(new PlannedTranche(detail.actualDisbursementDate() != null, detail.principal(),
                    detail.getPrincipalAsPercentage(), detail.getPrincipalPercentage()));
        }
        if (rowPercentage) {
            writeResolvedPrincipals(ordered, resolvePrincipals(base, planned, null, null, digits(loan)));
            return;
        }
        final String raw = loan.getTranchePrincipalPercentages();
        if (raw != null && !raw.isBlank()) {
            if (base == null) {
                return;
            }
            final List<BigDecimal> percentages = parsePercentages(raw);
            if (ordered.size() != percentages.size()) {
                throw new org.apache.fineract.portfolio.loanaccount.exception.LoanDisbursalException(
                        "This product splits the principal into " + percentages.size()
                                + " tranches. Enter that many tranche rows. Amounts are set from the percentages.",
                        "tranche.percentage.count.mismatch", percentages.size(), ordered.size());
            }
            final List<BigDecimal> amounts = principals(base, percentages, digits(loan));
            for (int i = 0; i < ordered.size(); i++) {
                final LoanDisbursementDetails detail = ordered.get(i);
                if (detail.actualDisbursementDate() == null) {
                    detail.updatePrincipal(amounts.get(i));
                }
            }
            return;
        }
        writeResolvedPrincipals(ordered,
                resolvePrincipals(base, planned, product.getFirstTrancheAsPercentage(), product.getFirstTrancheAmount(), digits(loan)));
    }

    private static void writeResolvedPrincipals(final List<LoanDisbursementDetails> ordered, final List<BigDecimal> amounts) {
        if (amounts == null) {
            return;
        }
        for (int i = 0; i < ordered.size(); i++) {
            final LoanDisbursementDetails detail = ordered.get(i);
            if (detail.actualDisbursementDate() == null) {
                detail.updatePrincipal(amounts.get(i));
            }
        }
    }

    /**
     * Currency amounts for each tranche, in the same order as {@code rows}. A row percentage replaces
     * the product. Otherwise the product first tranche is taken from the approved principal and the last
     * undisbursed tranche receives whatever principal is still left. Returns null when the submitted
     * principals should be kept.
     */
    public static List<BigDecimal> resolvePrincipals(final BigDecimal approved, final List<PlannedTranche> rows,
            final Boolean productFirstAsPercentage, final BigDecimal productFirstAmount, final int digits) {
        if (approved == null || rows == null || rows.isEmpty()) {
            return null;
        }
        boolean anyRowPercentage = false;
        boolean anyDisbursed = false;
        int firstUndisbursed = -1;
        int lastUndisbursed = -1;
        for (int i = 0; i < rows.size(); i++) {
            final PlannedTranche row = rows.get(i);
            if (row.disbursed) {
                anyDisbursed = true;
                continue;
            }
            if (firstUndisbursed < 0) {
                firstUndisbursed = i;
            }
            lastUndisbursed = i;
            if (Boolean.TRUE.equals(row.principalAsPercentage) && row.principalPercentage != null) {
                anyRowPercentage = true;
            }
        }
        final boolean productFirst = productFirstAmount != null && !anyRowPercentage;
        if (!anyRowPercentage && !productFirst) {
            return null;
        }
        if (productFirst && firstUndisbursed == lastUndisbursed && !anyDisbursed) {
            return null;
        }
        final BigDecimal base = approved.max(BigDecimal.ZERO).setScale(digits, RoundingMode.HALF_UP);
        BigDecimal remaining = base;
        final List<BigDecimal> amounts = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            final PlannedTranche row = rows.get(i);
            final boolean percentageRow = Boolean.TRUE.equals(row.principalAsPercentage) && row.principalPercentage != null;
            final BigDecimal amount;
            if (row.disbursed) {
                amount = row.principal == null ? BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP)
                        : row.principal.setScale(digits, RoundingMode.HALF_UP);
            } else if (productFirst && i == firstUndisbursed && firstUndisbursed != lastUndisbursed) {
                amount = cap(nextTrancheAmount(base, Boolean.TRUE.equals(productFirstAsPercentage), productFirstAmount, digits), remaining,
                        digits);
            } else if (i == lastUndisbursed && !percentageRow && (firstUndisbursed != lastUndisbursed || anyDisbursed)) {
                amount = remaining.max(BigDecimal.ZERO).setScale(digits, RoundingMode.HALF_UP);
            } else {
                final BigDecimal value = percentageRow ? row.principalPercentage : row.principal;
                amount = nextTrancheAmount(remaining, percentageRow, value, digits);
            }
            amounts.add(amount);
            remaining = remaining.subtract(amount);
            if (remaining.signum() < 0) {
                remaining = BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP);
            }
        }
        return amounts;
    }

    private static BigDecimal cap(final BigDecimal amount, final BigDecimal remaining, final int digits) {
        final BigDecimal left = remaining.max(BigDecimal.ZERO).setScale(digits, RoundingMode.HALF_UP);
        if (amount.compareTo(left) > 0) {
            return left;
        }
        return amount;
    }

    /**
     * The first tranche is a share of the loan principal. Each later tranche is a share of the
     * principal still left. A percentage uses that remainder. A fixed amount is capped by it.
     */
    public static BigDecimal nextTrancheAmount(final BigDecimal remaining, final boolean asPercentage, final BigDecimal value,
            final int digits) {
        final BigDecimal left = remaining == null ? BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP)
                : remaining.max(BigDecimal.ZERO).setScale(digits, RoundingMode.HALF_UP);
        if (value == null) {
            return BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP);
        }
        BigDecimal amount = asPercentage ? left.multiply(value).divide(ONE_HUNDRED, digits, RoundingMode.HALF_UP)
                : value.setScale(digits, RoundingMode.HALF_UP);
        if (amount.compareTo(left) > 0) {
            amount = left;
        }
        if (amount.signum() < 0) {
            return BigDecimal.ZERO.setScale(digits, RoundingMode.HALF_UP);
        }
        return amount;
    }

    private static LoanDisbursementDetails lastDisbursed(final Loan loan) {
        return loan.getDisbursementDetails().stream().filter(detail -> detail.actualDisbursementDate() != null)
                .max(Comparator.comparing(LoanDisbursementDetails::actualDisbursementDate)
                        .thenComparing(LoanDisbursementDetails::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static int digits(final Loan loan) {
        if (loan.getCurrency() == null) {
            return 2;
        }
        return loan.getCurrency().getDigitsAfterDecimal();
    }

    /**
     * One planned tranche. A null {@code principalAsPercentage} means the row does not override the product.
     */
    public static final class PlannedTranche {

        public final boolean disbursed;
        public final BigDecimal principal;
        public final Boolean principalAsPercentage;
        public final BigDecimal principalPercentage;

        public PlannedTranche(final boolean disbursed, final BigDecimal principal, final Boolean principalAsPercentage,
                final BigDecimal principalPercentage) {
            this.disbursed = disbursed;
            this.principal = principal;
            this.principalAsPercentage = principalAsPercentage;
            this.principalPercentage = principalPercentage;
        }
    }

    public static final class Assessment {

        public final LoanDisbursementDetails previous;
        public final LoanDisbursementDetails next;
        public final BigDecimal percentage;
        public final BigDecimal required;
        public final BigDecimal repaid;
        public final List<LoanTransaction> repayments;

        public Assessment(final LoanDisbursementDetails previous, final LoanDisbursementDetails next, final BigDecimal percentage,
                final BigDecimal required, final BigDecimal repaid, final List<LoanTransaction> repayments) {
            this.previous = previous;
            this.next = next;
            this.percentage = percentage;
            this.required = required;
            this.repaid = repaid;
            this.repayments = repayments;
        }

        public boolean met() {
            return this.repaid.compareTo(this.required) >= 0;
        }
    }
}
