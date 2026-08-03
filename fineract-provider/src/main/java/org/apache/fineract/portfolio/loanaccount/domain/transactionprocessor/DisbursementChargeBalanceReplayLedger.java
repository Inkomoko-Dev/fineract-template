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
package org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;

final class DisbursementChargeBalanceReplayLedger {

    private final List<ChargeBalanceCreditEntry> entries = new ArrayList<>();

    void recordInstallmentCredit(final LoanRepaymentScheduleInstallment installment, final Money principalPortion,
            final Money interestPortion, final Money penaltyChargesPortion) {
        if (principalPortion.plus(interestPortion).plus(penaltyChargesPortion).isGreaterThanZero()) {
            this.entries.add(new ChargeBalanceCreditEntry(installment, principalPortion, interestPortion,
                    penaltyChargesPortion, Money.zero(principalPortion.getCurrency())));
        }
    }

    void recordOverpaymentCredit(final Money overpaymentPortion) {
        if (overpaymentPortion.isGreaterThanZero()) {
            this.entries.add(new ChargeBalanceCreditEntry(null, Money.zero(overpaymentPortion.getCurrency()),
                    Money.zero(overpaymentPortion.getCurrency()), Money.zero(overpaymentPortion.getCurrency()),
                    overpaymentPortion));
        }
    }

    ChargeBalanceRestore restore(final LoanTransaction loanTransaction, final Money transactionAmount) {
        Money amountUnprocessed = transactionAmount;
        Money principalPortion = Money.zero(transactionAmount.getCurrency());
        Money interestPortion = Money.zero(transactionAmount.getCurrency());
        Money penaltyChargesPortion = Money.zero(transactionAmount.getCurrency());
        Money overpaymentPortion = Money.zero(transactionAmount.getCurrency());
        final List<LoanTransactionToRepaymentScheduleMapping> transactionMappings = new ArrayList<>();

        for (int i = this.entries.size() - 1; i >= 0 && amountUnprocessed.isGreaterThanZero(); i--) {
            final ChargeBalanceCreditEntry entry = this.entries.get(i);

            final Money restoredOverpayment = entry.restoreOverpayment(amountUnprocessed);
            amountUnprocessed = amountUnprocessed.minus(restoredOverpayment);
            overpaymentPortion = overpaymentPortion.plus(restoredOverpayment);

            final Money restoredPenaltyCharges = entry.restorePenaltyCharges(loanTransaction.getTransactionDate(),
                    amountUnprocessed);
            amountUnprocessed = amountUnprocessed.minus(restoredPenaltyCharges);
            penaltyChargesPortion = penaltyChargesPortion.plus(restoredPenaltyCharges);

            final Money restoredInterest = entry.restoreInterest(loanTransaction.getTransactionDate(), amountUnprocessed);
            amountUnprocessed = amountUnprocessed.minus(restoredInterest);
            interestPortion = interestPortion.plus(restoredInterest);

            final Money restoredPrincipal = entry.restorePrincipal(loanTransaction.getTransactionDate(), amountUnprocessed);
            amountUnprocessed = amountUnprocessed.minus(restoredPrincipal);
            principalPortion = principalPortion.plus(restoredPrincipal);

            if (entry.hasInstallment() && restoredPrincipal.plus(restoredInterest).plus(restoredPenaltyCharges).isGreaterThanZero()) {
                transactionMappings.add(LoanTransactionToRepaymentScheduleMapping.createFrom(loanTransaction, entry.installment(),
                        restoredPrincipal, restoredInterest, Money.zero(transactionAmount.getCurrency()), restoredPenaltyCharges));
            }
            if (entry.isFullyRestored()) {
                this.entries.remove(i);
            }
        }

        return new ChargeBalanceRestore(principalPortion, interestPortion, penaltyChargesPortion, overpaymentPortion,
                transactionMappings);
    }

    private static final class ChargeBalanceCreditEntry {

        private final LoanRepaymentScheduleInstallment installment;
        private Money principalPortion;
        private Money interestPortion;
        private Money penaltyChargesPortion;
        private Money overpaymentPortion;

        private ChargeBalanceCreditEntry(final LoanRepaymentScheduleInstallment installment, final Money principalPortion,
                final Money interestPortion, final Money penaltyChargesPortion, final Money overpaymentPortion) {
            this.installment = installment;
            this.principalPortion = principalPortion;
            this.interestPortion = interestPortion;
            this.penaltyChargesPortion = penaltyChargesPortion;
            this.overpaymentPortion = overpaymentPortion;
        }

        private boolean hasInstallment() {
            return this.installment != null;
        }

        private LoanRepaymentScheduleInstallment installment() {
            return this.installment;
        }

        private Money restoreOverpayment(final Money amountUnprocessed) {
            final Money amountToRestore = min(this.overpaymentPortion, amountUnprocessed);
            this.overpaymentPortion = this.overpaymentPortion.minus(amountToRestore);
            return amountToRestore;
        }

        private Money restorePenaltyCharges(final LocalDate transactionDate, final Money amountUnprocessed) {
            final Money amountToRestore = min(this.penaltyChargesPortion, amountUnprocessed);
            if (amountToRestore.isGreaterThanZero() && this.installment != null) {
                this.installment.restorePenaltyBalanceCredit(transactionDate, amountToRestore);
                this.penaltyChargesPortion = this.penaltyChargesPortion.minus(amountToRestore);
            }
            return amountToRestore;
        }

        private Money restoreInterest(final LocalDate transactionDate, final Money amountUnprocessed) {
            final Money amountToRestore = min(this.interestPortion, amountUnprocessed);
            if (amountToRestore.isGreaterThanZero() && this.installment != null) {
                this.installment.restoreInterestBalanceCredit(transactionDate, amountToRestore);
                this.interestPortion = this.interestPortion.minus(amountToRestore);
            }
            return amountToRestore;
        }

        private Money restorePrincipal(final LocalDate transactionDate, final Money amountUnprocessed) {
            final Money amountToRestore = min(this.principalPortion, amountUnprocessed);
            if (amountToRestore.isGreaterThanZero() && this.installment != null) {
                this.installment.restorePrincipalBalanceCredit(transactionDate, amountToRestore);
                this.principalPortion = this.principalPortion.minus(amountToRestore);
            }
            return amountToRestore;
        }

        private boolean isFullyRestored() {
            return !this.principalPortion.plus(this.interestPortion).plus(this.penaltyChargesPortion).plus(this.overpaymentPortion)
                    .isGreaterThanZero();
        }

        private static Money min(final Money left, final Money right) {
            return left.isGreaterThan(right) ? right : left;
        }
    }

    static final class ChargeBalanceRestore {

        private final Money principalPortion;
        private final Money interestPortion;
        private final Money penaltyChargesPortion;
        private final Money overpaymentPortion;
        private final List<LoanTransactionToRepaymentScheduleMapping> transactionMappings;

        private ChargeBalanceRestore(final Money principalPortion, final Money interestPortion,
                final Money penaltyChargesPortion, final Money overpaymentPortion,
                final List<LoanTransactionToRepaymentScheduleMapping> transactionMappings) {
            this.principalPortion = principalPortion;
            this.interestPortion = interestPortion;
            this.penaltyChargesPortion = penaltyChargesPortion;
            this.overpaymentPortion = overpaymentPortion;
            this.transactionMappings = transactionMappings;
        }

        Money principalPortion() {
            return this.principalPortion;
        }

        Money interestPortion() {
            return this.interestPortion;
        }

        Money penaltyChargesPortion() {
            return this.penaltyChargesPortion;
        }

        Money overpaymentPortion() {
            return this.overpaymentPortion;
        }

        Money totalInstallmentPortion() {
            return this.principalPortion.plus(this.interestPortion).plus(this.penaltyChargesPortion);
        }

        List<LoanTransactionToRepaymentScheduleMapping> transactionMappings() {
            return this.transactionMappings;
        }
    }
}
