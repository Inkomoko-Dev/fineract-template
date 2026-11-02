package org.apache.fineract.portfolio.loanaccount.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public static List<BigDecimal> parsePercentages(final String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        final List<BigDecimal> values = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (String part : raw.split(",")) {
            final String token = part.trim();
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

    public static BigDecimal requiredPrincipal(final BigDecimal previousPrincipal, final BigDecimal percentage, final int digits) {
        return previousPrincipal.multiply(percentage).divide(ONE_HUNDRED, digits, RoundingMode.HALF_UP);
    }

    public static Assessment assess(final Loan loan) {
        if (loan == null || loan.loanProduct() == null || !loan.loanProduct().isMultiDisburseLoan()) {
            return null;
        }
        final BigDecimal percentage = loan.loanProduct().getPreviousTrancheRepaidPercentage();
        if (percentage == null) {
            return null;
        }
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final LoanDisbursementDetails previous = lastDisbursed(loan);
        if (next == null || previous == null || previous.principal() == null || previous.actualDisbursementDate() == null) {
            return null;
        }
        final int digits = digits(loan);
        final BigDecimal required = requiredPrincipal(previous.principal(), percentage, digits);
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
        final String raw = product.getTranchePrincipalPercentages();
        if (raw == null || raw.isBlank()) {
            return;
        }
        final List<BigDecimal> percentages = parsePercentages(raw);
        final BigDecimal base = loan.getApprovedPrincipal() != null ? loan.getApprovedPrincipal() : loan.getProposedPrincipal();
        if (base == null || loan.getDisbursementDetails() == null || loan.getDisbursementDetails().isEmpty()) {
            return;
        }
        final List<LoanDisbursementDetails> ordered = loan.getDisbursementDetails().stream()
                .sorted(Comparator
                        .comparing(LoanDisbursementDetails::expectedDisbursementDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LoanDisbursementDetails::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
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
