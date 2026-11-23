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
package org.apache.fineract.portfolio.loanaccount.rescheduleloan.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;

/**
 * A multi-disburse loan can be rescheduled, but only after the caller has confirmed what the
 * schedule will and will not include. The new schedule is built from principal already disbursed.
 * A tranche that is disbursed later rebuilds that schedule.
 */
public final class MultiTrancheRescheduleRules {

    public static final String NOTHING_DISBURSED = "validation.msg.rescheduleloan.multiTranche.nothing.disbursed";
    public static final String ACKNOWLEDGEMENT_REQUIRED = "validation.msg.rescheduleloan.acknowledgeMultiTrancheReschedule.required";

    private MultiTrancheRescheduleRules() {}

    /**
     * @return the reason the reschedule must be refused, or null when it may proceed
     */
    public static String rejection(final Loan loan, final LocalDate rescheduleFromDate, final Boolean acknowledged) {
        if (loan == null || !loan.isMultiDisburmentLoan()) {
            return null;
        }
        final Assessment assessment = assess(loan.getDisbursementDetails(), rescheduleFromDate);
        if (assessment.disbursedCount == 0 && assessment.pendingCount > 0) {
            return NOTHING_DISBURSED;
        }
        if (!Boolean.TRUE.equals(acknowledged)) {
            return message(assessment);
        }
        return null;
    }

    public static String message(final Assessment assessment) {
        final StringBuilder message = new StringBuilder();
        if (assessment.disbursedCount > 0) {
            message.append("This reschedule covers only principal already disbursed (").append(assessment.disbursedCount)
                    .append(" tranche(s), ").append(plain(assessment.disbursedPrincipal))
                    .append("). Installments before the selected date stay as they are, including amounts already paid. ");
        } else {
            message.append(
                    "This reschedule applies to principal already disbursed. Installments before the selected date stay as they are, including amounts already paid. ");
        }
        if (assessment.disbursedCount > 1) {
            message.append(
                    "Other tranches have already been disbursed. Their principal is part of the outstanding that moves onto the new installments. ");
            if (assessment.rescheduleOnOrBeforeLatestDisbursement) {
                message.append("The selected date is on or before a later disbursement");
                if (assessment.latestDisbursement != null) {
                    message.append(" (").append(assessment.latestDisbursement).append(")");
                }
                message.append(", so that later tranche is folded into the rebuilt installments. ");
            }
        }
        if (assessment.pendingCount > 0) {
            message.append(assessment.pendingCount).append(" expected tranche(s)");
            if (assessment.pendingPrincipal.signum() > 0) {
                message.append(" (").append(plain(assessment.pendingPrincipal)).append(")");
            }
            message.append(
                    " are not on this schedule. Disbursing one of them afterwards rebuilds the schedule from that disbursement date and can replace this reschedule. ");
        } else {
            message.append(
                    "A later disbursement rebuilds the schedule from that disbursement date and can replace this reschedule. ");
        }
        message.append("Set acknowledgeMultiTrancheReschedule to true to confirm you have read this.");
        return message.toString().trim();
    }

    public static Assessment assess(final Collection<LoanDisbursementDetails> details, final LocalDate rescheduleFromDate) {
        int disbursedCount = 0;
        int pendingCount = 0;
        BigDecimal disbursedPrincipal = BigDecimal.ZERO;
        BigDecimal pendingPrincipal = BigDecimal.ZERO;
        LocalDate latestDisbursement = null;
        if (details != null) {
            for (final LoanDisbursementDetails detail : details) {
                if (detail == null) {
                    continue;
                }
                final BigDecimal principal = detail.principal() == null ? BigDecimal.ZERO : detail.principal();
                if (detail.actualDisbursementDate() != null) {
                    disbursedCount++;
                    disbursedPrincipal = disbursedPrincipal.add(principal);
                    if (latestDisbursement == null || detail.actualDisbursementDate().isAfter(latestDisbursement)) {
                        latestDisbursement = detail.actualDisbursementDate();
                    }
                } else {
                    pendingCount++;
                    pendingPrincipal = pendingPrincipal.add(principal);
                }
            }
        }
        final boolean onOrBeforeLatest = latestDisbursement != null && rescheduleFromDate != null
                && !rescheduleFromDate.isAfter(latestDisbursement);
        return new Assessment(disbursedCount, disbursedPrincipal, pendingCount, pendingPrincipal, latestDisbursement, onOrBeforeLatest);
    }

    private static String plain(final BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    public static final class Assessment {

        public final int disbursedCount;
        public final BigDecimal disbursedPrincipal;
        public final int pendingCount;
        public final BigDecimal pendingPrincipal;
        public final LocalDate latestDisbursement;
        public final boolean rescheduleOnOrBeforeLatestDisbursement;

        public Assessment(final int disbursedCount, final BigDecimal disbursedPrincipal, final int pendingCount,
                final BigDecimal pendingPrincipal, final LocalDate latestDisbursement,
                final boolean rescheduleOnOrBeforeLatestDisbursement) {
            this.disbursedCount = disbursedCount;
            this.disbursedPrincipal = disbursedPrincipal;
            this.pendingCount = pendingCount;
            this.pendingPrincipal = pendingPrincipal;
            this.latestDisbursement = latestDisbursement;
            this.rescheduleOnOrBeforeLatestDisbursement = rescheduleOnOrBeforeLatestDisbursement;
        }
    }
}
