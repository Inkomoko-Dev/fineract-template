package org.apache.fineract.portfolio.loanaccount.service;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.organisation.monetary.domain.Money;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.businessevent.BusinessEventListener;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanAdjustTransactionBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.TrancheRepaymentThresholdMetBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.transaction.LoanTransactionMakeRepaymentPostBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
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
    private final Gson gson = new Gson();

    @PostConstruct
    public void addListeners() {
        this.businessEventNotifierService.addPostBusinessEventListener(LoanTransactionMakeRepaymentPostBusinessEvent.class,
                new RepaymentListener());
        this.businessEventNotifierService.addPostBusinessEventListener(LoanAdjustTransactionBusinessEvent.class, new AdjustmentListener());
    }

    @Transactional
    public void onRepayment(final Loan loan) {
        final TrancheReleaseRules.Assessment assessment = TrancheReleaseRules.assess(loan);
        if (assessment == null || assessment.previous.getId() == null) {
            return;
        }
        if (!assessment.met()) {
            this.noticeRepository.deleteByLoanIdAndDisbursementDetailId(loan.getId(), assessment.previous.getId());
            return;
        }
        if (this.noticeRepository.existsByLoanIdAndDisbursementDetailId(loan.getId(), assessment.previous.getId())) {
            return;
        }
        this.noticeRepository.save(new TrancheReleaseNotice(loan.getId(), assessment.previous.getId(), OffsetDateTime.now()));
        this.businessEventNotifierService.notifyPostBusinessEvent(new TrancheRepaymentThresholdMetBusinessEvent(loan));
        sendEmail(loan, assessment);
    }

    @Transactional(readOnly = true)
    public String reportJson(final Long loanId) {
        final Loan loan = this.loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        return this.gson.toJson(report(loan, TrancheReleaseRules.assess(loan)));
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
        body.append("Loan ").append(loan.getAccountNumber()).append(" for ").append(clientName)
                .append(" has repaid ").append(assessment.percentage).append("% of the previous tranche.\n");
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
