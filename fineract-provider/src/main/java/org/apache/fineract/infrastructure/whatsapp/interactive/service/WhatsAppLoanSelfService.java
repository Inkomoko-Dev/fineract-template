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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppLoanSelfService {

    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final AfricasTalkingProperties africasTalkingProperties;

    @Transactional(readOnly = true)
    public List<Loan> findSelfServiceLoans(final Long clientId) {
        final List<Loan> loans = loanRepositoryWrapper.findLoanByClientId(clientId);
        return loans.stream().filter(this::isSelfServiceEligible).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Loan> findExceptionalLoans(final Long clientId) {
        final List<Loan> loans = loanRepositoryWrapper.findLoanByClientId(clientId);
        return loans.stream().filter(this::isExceptionalStatus).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String buildLoanSelectionMenu(final List<Loan> loans, final String language) {
        final StringBuilder builder = new StringBuilder(WhatsAppInteractiveMessages.loanSelectionHeader(language)).append("\n");
        int index = 1;
        for (final Loan loan : loans) {
            builder.append(index).append(". ").append(maskAccountNumber(loan.getAccountNumber())).append(" - ")
                    .append(loan.getLoanProduct().productName()).append("\n");
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
        if ("REPAYMENT_INSTRUCTIONS".equals(actionTarget)) {
            return formatRepaymentInstructions(language);
        }
        final Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        return switch (actionTarget) {
            case "LOAN_BALANCE" -> formatBalance(loan, language);
            case "NEXT_REPAYMENT" -> formatNextRepayment(loan, language);
            case "AMOUNT_DUE" -> formatAmountDue(loan, language);
            case "LOAN_STATUS" -> formatLoanStatus(loan, language);
            default -> WhatsAppInteractiveMessages.loanServicePending(language);
        };
    }

    private String formatRepaymentInstructions(final String language) {
        final var voiceIvr = africasTalkingProperties.getVoiceIvr();
        final String text = "rw".equalsIgnoreCase(language) ? voiceIvr.getRepaymentInstructionsRw()
                : voiceIvr.getRepaymentInstructionsEn();
        return StringUtils.defaultIfBlank(text, WhatsAppInteractiveMessages.loanServicePending(language));
    }

    @Transactional(readOnly = true)
    public String buildExceptionalLoansResponse(final List<Loan> exceptionalLoans, final String language) {
        if (exceptionalLoans.isEmpty()) {
            return WhatsAppInteractiveMessages.noActiveLoans(language);
        }
        final StringBuilder builder = new StringBuilder(WhatsAppInteractiveMessages.exceptionalLoansHeader(language)).append("\n");
        for (final Loan loan : exceptionalLoans) {
            builder.append(WhatsAppInteractiveMessages.loanStatusResponse(language, maskAccountNumber(loan.getAccountNumber()),
                    loan.status().getCode())).append("\n");
        }
        builder.append(WhatsAppInteractiveMessages.advisorOptionHint(language));
        return builder.toString().trim();
    }

    static String maskAccountNumber(final String accountNumber) {
        if (StringUtils.isBlank(accountNumber)) {
            return "****";
        }
        final String trimmed = accountNumber.trim();
        if (trimmed.length() <= 4) {
            return "****" + trimmed;
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    private boolean isSelfServiceEligible(final Loan loan) {
        final LoanStatus status = loan.status();
        return status.isActive() || status.isOverpaid();
    }

    private boolean isExceptionalStatus(final Loan loan) {
        final LoanStatus status = loan.status();
        return status.isClosedObligationsMet() || status.isClosedWrittenOff() || status.isClosedWithOutsandingAmountMarkedForReschedule()
                || status.isWithdrawnByClient() || status.isRejected() || status.isApproved() || status.isSubmittedAndPendingApproval();
    }

    private String formatBalance(final Loan loan, final String language) {
        final LoanSummary summary = loan.getSummary();
        final String currency = loan.getCurrencyCode();
        final String account = maskAccountNumber(loan.getAccountNumber());
        return WhatsAppInteractiveMessages.loanBalanceComponentsResponse(language, account, currency, summary.getTotalOutstanding(),
                summary.getTotalPrincipalOutstanding(), summary.getTotalInterestOutstanding(), summary.getTotalFeeChargesOutstanding(),
                summary.getTotalPenaltyChargesOutstanding());
    }

    private String formatNextRepayment(final Loan loan, final String language) {
        final LoanRepaymentScheduleInstallment installment = findNextUnpaidInstallment(loan);
        if (installment == null) {
            return WhatsAppInteractiveMessages.noUpcomingRepayment(language, maskAccountNumber(loan.getAccountNumber()));
        }
        final LocalDate today = DateUtils.getLocalDateOfTenant();
        final LocalDate dueDate = installment.getDueDate();
        final boolean overdue = dueDate.isBefore(today);
        final long days = Math.abs(ChronoUnit.DAYS.between(today, dueDate));
        return WhatsAppInteractiveMessages.nextRepaymentWithDaysResponse(language, maskAccountNumber(loan.getAccountNumber()), dueDate,
                days, overdue);
    }

    private String formatAmountDue(final Loan loan, final String language) {
        final LoanRepaymentScheduleInstallment installment = findNextUnpaidInstallment(loan);
        if (installment == null) {
            return WhatsAppInteractiveMessages.noAmountDue(language, maskAccountNumber(loan.getAccountNumber()));
        }
        final MonetaryCurrency currency = loan.getCurrency();
        final BigDecimal amountDue = installment.getTotalOutstanding(currency).getAmount();
        final LocalDate today = DateUtils.getLocalDateOfTenant();
        final LocalDate dueDate = installment.getDueDate();
        final boolean overdue = dueDate.isBefore(today);
        final long days = Math.abs(ChronoUnit.DAYS.between(today, dueDate));
        return WhatsAppInteractiveMessages.amountDueWithDaysResponse(language, maskAccountNumber(loan.getAccountNumber()),
                currency.getCode(), amountDue, dueDate, days, overdue);
    }

    private String formatLoanStatus(final Loan loan, final String language) {
        return WhatsAppInteractiveMessages.loanStatusResponse(language, maskAccountNumber(loan.getAccountNumber()),
                loan.status().getCode());
    }

    private LoanRepaymentScheduleInstallment findNextUnpaidInstallment(final Loan loan) {
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(loan.getRepaymentScheduleInstallments());
        installments.sort(Comparator.comparing(LoanRepaymentScheduleInstallment::getDueDate));
        final LocalDate today = DateUtils.getLocalDateOfTenant();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment.isNotFullyPaidOff() && installment.getDueDate().isBefore(today)) {
                return installment;
            }
        }
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
