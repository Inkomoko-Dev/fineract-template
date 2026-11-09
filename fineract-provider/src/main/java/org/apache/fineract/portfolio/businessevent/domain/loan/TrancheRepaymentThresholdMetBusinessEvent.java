package org.apache.fineract.portfolio.businessevent.domain.loan;

import org.apache.fineract.portfolio.loanaccount.domain.Loan;

public class TrancheRepaymentThresholdMetBusinessEvent extends LoanBusinessEvent {

    public TrancheRepaymentThresholdMetBusinessEvent(final Loan value) {
        super(value);
    }
}
