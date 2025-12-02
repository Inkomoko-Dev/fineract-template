package org.apache.fineract.portfolio.businessevent.domain.loan.transaction;

import lombok.Getter;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanBusinessEvent;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;

@Getter
public class LoanDecisionRejectEvent extends LoanBusinessEvent {

    private final LoanDecision loanDecision;

    public LoanDecisionRejectEvent(Loan loan, LoanDecision loanDecision) {
        super(loan);
        this.loanDecision = loanDecision;
    }

}