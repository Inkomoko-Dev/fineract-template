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
package org.apache.fineract.portfolio.loanaccount.service;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.organisation.monetary.domain.Money;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.businessevent.BusinessEventListener;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanDisbursalBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanAdjustTransactionBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.TrancheRepaymentThresholdMetBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.transaction.LoanTransactionMakeRepaymentPostBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheReleaseNotice;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheReleaseNoticeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheReleaseRules;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrancheReleaseNotificationService {

    private final BusinessEventNotifierService businessEventNotifierService;
    private final TrancheReleaseNoticeRepository noticeRepository;
    private final LoanRepository loanRepository;
    private final PlatformEmailService emailService;
    private final AppUserRepository appUserRepository;
    private final TrancheMilestoneEligibilityService eligibility;
    private final Gson gson = new Gson();

    @PostConstruct
    public void addListeners() {
        this.businessEventNotifierService.addPostBusinessEventListener(LoanTransactionMakeRepaymentPostBusinessEvent.class,
                new RepaymentListener());
        this.businessEventNotifierService.addPostBusinessEventListener(LoanAdjustTransactionBusinessEvent.class, new AdjustmentListener());
        this.businessEventNotifierService.addPostBusinessEventListener(LoanDisbursalBusinessEvent.class, new DisbursalListener());
    }

    @Transactional
    public void onRepayment(final Loan loan) {
        final TrancheMilestoneEligibilityService.Refresh refresh = this.eligibility.refresh(loan);
        final TrancheReleaseRules.Assessment assessment = TrancheReleaseRules.assess(loan);
        if (refresh.milestoneRequired) {
            final Long noticeDetailId = surveyNoticeDetailId(loan, assessment);
            if (noticeDetailId == null) {
                return;
            }
            if (assessment != null && !assessment.met()) {
                this.noticeRepository.deleteByLoanIdAndDisbursementDetailId(loan.getId(), noticeDetailId);
                return;
            }
            if (!refresh.sendSurveyNotice() || this.noticeRepository.existsByLoanIdAndDisbursementDetailId(loan.getId(), noticeDetailId)) {
                return;
            }
            this.noticeRepository.save(new TrancheReleaseNotice(loan.getId(), noticeDetailId, DateUtils.getOffsetDateTimeOfTenant()));
            this.businessEventNotifierService.notifyPostBusinessEvent(new TrancheRepaymentThresholdMetBusinessEvent(loan));
            sendSurveyEmail(loan, refresh);
            return;
        }
        if (assessment == null || assessment.previous == null || assessment.previous.getId() == null) {
            return;
        }
        if (!assessment.met()) {
            this.noticeRepository.deleteByLoanIdAndDisbursementDetailId(loan.getId(), assessment.previous.getId());
            return;
        }
        if (this.noticeRepository.existsByLoanIdAndDisbursementDetailId(loan.getId(), assessment.previous.getId())) {
            return;
        }
        this.noticeRepository.save(new TrancheReleaseNotice(loan.getId(), assessment.previous.getId(),
                DateUtils.getOffsetDateTimeOfTenant()));
        this.businessEventNotifierService.notifyPostBusinessEvent(new TrancheRepaymentThresholdMetBusinessEvent(loan));
        sendEmail(loan, assessment);
    }

    @Transactional(readOnly = true)
    public String reportJson(final Long loanId) {
        final Loan loan = this.loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        return this.gson.toJson(report(loan, TrancheReleaseRules.assess(loan)));
    }

    private static Long surveyNoticeDetailId(final Loan loan, final TrancheReleaseRules.Assessment assessment) {
        if (assessment != null && assessment.previous != null && assessment.previous.getId() != null) {
            return assessment.previous.getId();
        }
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        return next == null ? null : next.getId();
    }

    private void sendSurveyEmail(final Loan loan, final TrancheMilestoneEligibilityService.Refresh refresh) {
        final String email = loanOfficerEmail(loan);
        final Staff officer = loan.getLoanOfficer();
        if (email == null || email.isBlank()) {
            log.warn("Tranche repayment conditions are met for loan {} but the loan officer has no email.", loan.getId());
            return;
        }
        final String officerName = officer == null ? "Investment Officer" : officer.displayName();
        final String deadline = refresh.firstRepaymentDueDate == null ? "" : refresh.firstRepaymentDueDate.toString();
        final String subject = "Action Required: Second-Tranche Due Diligence – " + loan.getAccountNumber();
        final StringBuilder body = new StringBuilder();
        body.append("Dear ").append(officerName).append(",\n\n");
        body.append("The first required repayment has been received for the loan below.\n\n");
        body.append("Loan ID: ").append(loan.getAccountNumber()).append("\n\n");
        body.append("Client Name: ").append(loan.getClient() == null ? "" : loan.getClient().getDisplayName()).append("\n\n");
        body.append("Second-Tranche Deadline: ").append(deadline).append("\n\n");
        body.append("Please complete the business verification and additional funding needs assessment in SurveyCTO ");
        body.append("before the deadline to facilitate second-tranche approval.\n");
        try {
            this.emailService.sendDefinedEmail(new EmailDetail(subject, body.toString(), email, officerName));
        } catch (RuntimeException ex) {
            log.error("Tranche milestone email could not be sent for loan {}. The repayment was still posted.", loan.getId(), ex);
        }
    }

    private void sendEmail(final Loan loan, final TrancheReleaseRules.Assessment assessment) {
        final String email = loanOfficerEmail(loan);
        final Staff officer = loan.getLoanOfficer();
        if (email == null || email.isBlank()) {
            log.warn("Tranche repayment threshold met for loan {} but the loan officer has no email.", loan.getId());
            return;
        }
        final String officerName = officer == null ? "Loan officer" : officer.displayName();
        final EmailDetail detail = new EmailDetail("Next tranche can be disbursed for loan " + loan.getAccountNumber(),
                emailBody(loan, assessment), email, officerName);
        try {
            this.emailService.sendDefinedEmail(detail);
        } catch (RuntimeException ex) {
            log.error("Tranche release email could not be sent for loan {}. The repayment was still posted.", loan.getId(), ex);
        }
    }

    private String emailBody(final Loan loan, final TrancheReleaseRules.Assessment assessment) {
        final StringBuilder body = new StringBuilder();
        final String clientName = loan.getClient() == null ? "" : loan.getClient().getDisplayName();
        body.append("Loan ").append(loan.getAccountNumber()).append(" for ").append(clientName);
        if (assessment.percentage == null) {
            body.append(" has repaid the required amount of the previous tranche.\n");
        } else {
            body.append(" has repaid ").append(assessment.percentage).append("% of the previous tranche.\n");
        }
        body.append("Previous tranche principal: ").append(assessment.previous.principal()).append('\n');
        body.append("Principal repaid since that disbursement: ").append(assessment.repaid).append('\n');
        body.append("Required: ").append(assessment.required).append('\n');
        body.append("Next tranche principal: ").append(assessment.next.principal()).append("\n\n");
        body.append("Repayments counted:\n");
        for (LoanTransaction transaction : assessment.repayments) {
            body.append(transaction.getTransactionDate()).append("  amount ").append(money(transaction.getAmount(loan.getCurrency())))
                    .append("  principal ").append(money(transaction.getPrincipalPortion(loan.getCurrency()))).append('\n');
        }
        return body.toString();
    }

    private Map<String, Object> report(final Loan loan, final TrancheReleaseRules.Assessment assessment) {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("loanId", loan.getId());
        report.put("accountNo", loan.getAccountNumber());
        report.put("clientName", loan.getClient() == null ? null : loan.getClient().getDisplayName());
        report.put("thresholdMet", assessment != null && assessment.met());
        report.put("minimumNotMet", assessment != null && !assessment.met());
        if (assessment == null) {
            report.put("repayments", List.of());
            return report;
        }
        report.put("requiredPercentage", assessment.percentage);
        report.put("previousTranchePrincipal", assessment.previous.principal());
        report.put("requiredPrincipal", assessment.required);
        report.put("principalRepaid", assessment.repaid);
        report.put("nextTranchePrincipal", assessment.next.principal());
        final List<Map<String, Object>> repayments = new ArrayList<>();
        for (LoanTransaction transaction : assessment.repayments) {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("transactionId", transaction.getId());
            row.put("date", transaction.getTransactionDate() == null ? null : transaction.getTransactionDate().toString());
            row.put("amount", money(transaction.getAmount(loan.getCurrency())));
            row.put("principalPortion", money(transaction.getPrincipalPortion(loan.getCurrency())));
            repayments.add(row);
        }
        report.put("repayments", repayments);
        return report;
    }

    private static BigDecimal money(final Money value) {
        return value == null ? BigDecimal.ZERO : value.getAmount();
    }

    private String loanOfficerEmail(final Loan loan) {
        final Staff officer = loan.getLoanOfficer();
        if (officer == null) {
            return null;
        }
        if (officer.emailAddress() != null && !officer.emailAddress().isBlank()) {
            return officer.emailAddress();
        }
        final AppUser user = this.appUserRepository.findAppUserByStaffId(officer.getId());
        return user == null ? null : user.getEmail();
    }

    private class DisbursalListener implements BusinessEventListener<LoanDisbursalBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanDisbursalBusinessEvent event) {
            if (event.get() != null) {
                eligibility.refresh(event.get());
            }
        }
    }

    private class RepaymentListener implements BusinessEventListener<LoanTransactionMakeRepaymentPostBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanTransactionMakeRepaymentPostBusinessEvent event) {
            if (event.get() == null || event.get().getLoan() == null) {
                return;
            }
            onRepayment(event.get().getLoan());
        }
    }

    private class AdjustmentListener implements BusinessEventListener<LoanAdjustTransactionBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanAdjustTransactionBusinessEvent event) {
            if (event.get() == null) {
                return;
            }
            final var transaction = event.get().getTransactionToAdjust() != null ? event.get().getTransactionToAdjust()
                    : event.get().getNewTransactionDetail();
            if (transaction != null && transaction.getLoan() != null) {
                onRepayment(transaction.getLoan());
            }
        }
    }
}
