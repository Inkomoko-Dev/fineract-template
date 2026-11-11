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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.apache.fineract.organisation.monetary.domain.Money;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.persistence.AfterCommitExecutor;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanSubStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheReleaseNotice;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheReleaseNoticeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheReleaseRules;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;
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
            final Long noticeDetailId = upcomingDetailId(loan);
            if (noticeDetailId == null) {
                return;
            }
            if (assessment == null || !assessment.met()) {
                this.noticeRepository.deleteByLoanIdAndDisbursementDetailId(loan.getId(), noticeDetailId);
                if (assessment != null && assessment.previous != null && assessment.previous.getId() != null) {
                    this.noticeRepository.deleteByLoanIdAndDisbursementDetailId(loan.getId(), assessment.previous.getId());
                }
                return;
            }
            if (!refresh.sendSurveyNotice() || this.noticeRepository.existsByLoanIdAndDisbursementDetailId(loan.getId(), noticeDetailId)) {
                return;
            }
            this.businessEventNotifierService.notifyPostBusinessEvent(new TrancheRepaymentThresholdMetBusinessEvent(loan));
            final String surveyEmail = loanOfficerEmail(loan);
            final String surveyOfficerName = loan.getLoanOfficer() == null ? "Investment Officer" : loan.getLoanOfficer().displayName();
            final String surveyClientName = loan.getClient() == null ? "" : loan.getClient().getDisplayName();
            final String surveyAccount = loan.getAccountNumber();
            final String surveyDeadline = refresh.firstRepaymentDueDate == null ? ""
                    : refresh.firstRepaymentDueDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            sendNoticeAfterCommit(loan.getId(), noticeDetailId,
                    () -> sendSurveyEmail(surveyEmail, surveyOfficerName, surveyClientName, surveyAccount, surveyDeadline));
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
        this.businessEventNotifierService.notifyPostBusinessEvent(new TrancheRepaymentThresholdMetBusinessEvent(loan));
        final String releaseEmail = loanOfficerEmail(loan);
        final String releaseOfficerName = loan.getLoanOfficer() == null ? "Loan officer" : loan.getLoanOfficer().displayName();
        final String releaseSubject = "Next tranche can be disbursed for loan " + loan.getAccountNumber();
        final String releaseBody = emailBody(loan, assessment);
        final String releaseAccount = loan.getAccountNumber();
        sendNoticeAfterCommit(loan.getId(), assessment.previous.getId(),
                () -> sendEmail(releaseEmail, releaseOfficerName, releaseSubject, releaseBody, releaseAccount));
    }

    /**
     * The repayment transaction has already committed before the email is attempted. The notice row is written
     * only after a successful send, so a missing address or a mail failure can be retried on the next repayment.
     */
    private void sendNoticeAfterCommit(final Long loanId, final Long disbursementDetailId, final BooleanSupplier sender) {
        AfterCommitExecutor.execute(() -> {
            final boolean alreadyNotified = Boolean.TRUE.equals(inNewTransaction(
                    () -> this.noticeRepository.existsByLoanIdAndDisbursementDetailId(loanId, disbursementDetailId)));
            if (alreadyNotified || !sender.getAsBoolean()) {
                return;
            }
            inNewTransaction(() -> {
                recordNotice(loanId, disbursementDetailId);
                return null;
            });
        });
    }

    private <T> T inNewTransaction(final Supplier<T> work) {
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> work.get());
    }

    /**
     * Returns false when another repayment already recorded the notice. A unique-key collision is
     * treated as already notified so the repayment transaction is not rolled back.
     */
    private boolean recordNotice(final Long loanId, final Long disbursementDetailId) {
        try {
            this.noticeRepository.save(new TrancheReleaseNotice(loanId, disbursementDetailId, DateUtils.getOffsetDateTimeOfTenant()));
            this.noticeRepository.flush();
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.info("Tranche release notice already exists for loan {} disbursement {}. Treating as already notified.", loanId,
                    disbursementDetailId);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public String reportJson(final Long loanId) {
        final Loan loan = this.loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        return this.gson.toJson(report(loan, TrancheReleaseRules.assess(loan)));
    }

    private static Long upcomingDetailId(final Loan loan) {
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        return next == null ? null : next.getId();
    }

    private boolean sendSurveyEmail(final String email, final String officerName, final String clientName, final String accountNo,
            final String deadline) {
        if (email == null || email.isBlank()) {
            log.warn("Tranche repayment conditions are met for loan {} but the loan officer has no email.", accountNo);
            return false;
        }
        final String subject = "Action Required: Second-Tranche Due Diligence – " + accountNo;
        final String body = "<html><body style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;line-height:1.5;\">"
                + "<p>Dear " + html(officerName) + ",</p>"
                + "<p>The first required repayment has been received for the loan below.</p>"
                + "<p style=\"margin:0 0 8px;\"><strong>Loan ID:</strong> " + html(accountNo) + "</p>"
                + "<p style=\"margin:0 0 8px;\"><strong>Client Name:</strong> " + html(clientName) + "</p>"
                + "<p style=\"margin:0 0 16px;\"><strong>Second-Tranche Deadline:</strong> " + html(deadline) + "</p>"
                + "<p>Please complete the business verification and additional funding needs assessment in SurveyCTO "
                + "before the deadline to facilitate second-tranche approval.</p>"
                + "</body></html>";
        try {
            this.emailService.sendDefinedEmail(new EmailDetail(subject, body, email, officerName));
            return true;
        } catch (RuntimeException ex) {
            log.error("Tranche milestone email could not be sent for loan {}. The repayment was still posted.", accountNo, ex);
            return false;
        }
    }

    private boolean sendEmail(final String email, final String officerName, final String subject, final String body,
            final String accountNo) {
        if (email == null || email.isBlank()) {
            log.warn("Tranche repayment threshold is met for loan {} but the loan officer has no email.", accountNo);
            return false;
        }
        try {
            this.emailService.sendDefinedEmail(new EmailDetail(subject, body, email, officerName));
            return true;
        } catch (RuntimeException ex) {
            log.error("Tranche release email could not be sent for loan {}. The repayment was still posted.", accountNo, ex);
            return false;
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
        report.put("thresholdNotConfigured", false);
        addSurveyWarning(report, loan);
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

    private void addSurveyWarning(final Map<String, Object> report, final Loan loan) {
        final TrancheMilestoneEligibilityService.Refresh milestone = this.eligibility.preview(loan);
        final LoanSubStatus status = milestone.status;
        final boolean subsequentTranche = milestone.subsequentTranche;
        report.put("subsequentTranche", subsequentTranche);
        report.put("trancheNumber", milestone.trancheNumber);
        report.put("surveyOutstanding", subsequentTranche && milestone.surveyOutstanding());
        report.put("surveyIncomplete", subsequentTranche && milestone.surveyIncomplete());
        report.put("surveyNegative", subsequentTranche && milestone.surveyNegative());
        report.put("pastDeadline", subsequentTranche && milestone.pastDeadline);
        report.put("deadlineExceptionApproved", milestone.deadlineExceptionApproved);
        report.put("requiresReview", subsequentTranche && status == LoanSubStatus.REQUIRES_REVIEW);
        report.put("milestoneRequired", milestone.milestoneRequired);
        report.put("trancheMilestoneStatus", status == null ? null : status.getValue());
        if (milestone.milestoneRequired && subsequentTranche) {
            report.put("thresholdNotConfigured", !milestone.thresholdConfigured);
        }
    }

    private static String html(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
