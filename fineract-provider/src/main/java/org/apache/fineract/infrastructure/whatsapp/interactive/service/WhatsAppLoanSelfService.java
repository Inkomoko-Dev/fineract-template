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
package org.apache.fineract.infrastructure.whatsapp.interactive.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppLoanSelfService {

    private final LoanRepositoryWrapper loanRepositoryWrapper;

    @Transactional(readOnly = true)
    public List<Loan> findSelfServiceLoans(final Long clientId) {
        final List<Loan> loans = loanRepositoryWrapper.findLoanByClientId(clientId);
        return loans.stream().filter(this::isSelfServiceEligible).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String buildLoanSelectionMenu(final List<Loan> loans, final String language) {
        final StringBuilder builder = new StringBuilder(WhatsAppInteractiveMessages.loanSelectionHeader(language)).append("\n");
        int index = 1;
        for (final Loan loan : loans) {
            builder.append(index).append(". ").append(loan.getAccountNumber()).append(" - ").append(loan.getLoanProduct().productName())
                    .append("\n");
            index++;
        }
        return builder.toString().trim();
    }

    @Transactional(readOnly = true)
    public Loan resolveLoanSelection(final List<Loan> loans, final String body) {
        if (!StringUtils.isNumeric(body.trim())) {
            return null;
        }
        final int index = Integer.parseInt(body.trim());
        if (index < 1 || index > loans.size()) {
            return null;
        }
        return loans.get(index - 1);
    }

    @Transactional(readOnly = true)
    public String buildLoanServiceResponse(final Long loanId, final String actionTarget, final String language) {
        final Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        return switch (actionTarget) {
            case "LOAN_BALANCE" -> formatBalance(loan, language);
            case "NEXT_REPAYMENT" -> formatNextRepayment(loan, language);
            case "AMOUNT_DUE" -> formatAmountDue(loan, language);
            case "LOAN_STATUS" -> formatLoanStatus(loan, language);
            default -> WhatsAppInteractiveMessages.loanServicePending(language);
        };
    }

    private boolean isSelfServiceEligible(final Loan loan) {
        final LoanStatus status = loan.status();
        return status.isActive() || status.isOverpaid();
    }

    private String formatBalance(final Loan loan, final String language) {
        final BigDecimal outstanding = loan.getSummary().getTotalOutstanding();
        final String currency = loan.getCurrencyCode();
        return WhatsAppInteractiveMessages.loanBalanceResponse(language, loan.getAccountNumber(), currency, outstanding);
    }

    private String formatNextRepayment(final Loan loan, final String language) {
        final LoanRepaymentScheduleInstallment installment = findNextUnpaidInstallment(loan);
        if (installment == null) {
            return WhatsAppInteractiveMessages.noUpcomingRepayment(language, loan.getAccountNumber());
        }
        return WhatsAppInteractiveMessages.nextRepaymentResponse(language, loan.getAccountNumber(), installment.getDueDate());
    }

    private String formatAmountDue(final Loan loan, final String language) {
        final LoanRepaymentScheduleInstallment installment = findNextUnpaidInstallment(loan);
        if (installment == null) {
            return WhatsAppInteractiveMessages.noAmountDue(language, loan.getAccountNumber());
        }
        final MonetaryCurrency currency = loan.getCurrency();
        final BigDecimal amountDue = installment.getTotalOutstanding(currency).getAmount();
        return WhatsAppInteractiveMessages.amountDueResponse(language, loan.getAccountNumber(), currency.getCode(), amountDue,
                installment.getDueDate());
    }

    private String formatLoanStatus(final Loan loan, final String language) {
        return WhatsAppInteractiveMessages.loanStatusResponse(language, loan.getAccountNumber(), loan.status().getCode());
    }

    private LoanRepaymentScheduleInstallment findNextUnpaidInstallment(final Loan loan) {
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(loan.getRepaymentScheduleInstallments());
        installments.sort(Comparator.comparing(LoanRepaymentScheduleInstallment::getDueDate));
        final LocalDate today = DateUtils.getLocalDateOfTenant();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment.isNotFullyPaidOff() && !installment.getDueDate().isBefore(today)) {
                return installment;
            }
        }
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment.isNotFullyPaidOff()) {
                return installment;
            }
        }
        return null;
    }
}
