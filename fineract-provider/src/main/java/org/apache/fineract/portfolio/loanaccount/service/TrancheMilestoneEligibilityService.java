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

import com.google.gson.JsonElement;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSubStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTrancheMilestone;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTrancheMilestoneHistory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTrancheMilestoneHistoryRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTrancheMilestoneRepository;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheMilestoneRules;
import org.apache.fineract.portfolio.loanaccount.domain.TrancheReleaseRules;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrancheMilestoneEligibilityService {

    private final LoanRepository loanRepository;
    private final LoanTrancheMilestoneRepository milestoneRepository;
    private final LoanTrancheMilestoneHistoryRepository historyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final FromJsonHelper fromApiJsonHelper;

    public static final class Refresh {

        public final LoanSubStatus status;
        public final boolean milestoneRequired;
        public final LocalDate firstRepaymentDueDate;
        public final int trancheNumber;

        public Refresh(final LoanSubStatus status, final boolean milestoneRequired, final LocalDate firstRepaymentDueDate,
                final int trancheNumber) {
            this.status = status;
            this.milestoneRequired = milestoneRequired;
            this.firstRepaymentDueDate = firstRepaymentDueDate;
            this.trancheNumber = trancheNumber;
        }

        public boolean sendSurveyNotice() {
            return this.milestoneRequired && this.status == LoanSubStatus.AWAITING_SURVEY;
        }
    }

    @Transactional
    public Refresh refresh(final Loan loan) {
        if (loan == null || loan.getId() == null || loan.loanProduct() == null || !loan.loanProduct().isMultiDisburseLoan()) {
            return new Refresh(null, false, null, 0);
        }
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final boolean disbursed = loan.getDisbursementDetails() != null
                && loan.getDisbursementDetails().stream().anyMatch(detail -> detail.actualDisbursementDate() != null);
        final boolean openTranche = disbursed && next != null && next.getId() != null;
        final TrancheReleaseRules.Assessment assessment = openTranche ? TrancheReleaseRules.assess(loan) : null;
        final boolean repaymentMet = openTranche && (assessment == null || assessment.met());
        final boolean requiresMilestone = productRequiresMilestone(loan);
        final LoanTrancheMilestone milestone = openTranche
                ? this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), next.getId()).orElse(null)
                : null;
        final TrancheMilestoneRules.SurveyAnswer survey = milestone == null ? TrancheMilestoneRules.SurveyAnswer.NONE
                : TrancheMilestoneRules.answer(milestone.getOperationalStatus(), milestone.getAdditionalFunding());
        final LocalDate dueDate = firstRepaymentDueDate(loan.getId());
        final boolean pastDeadline = dueDate != null && DateUtils.getBusinessLocalDate().isAfter(dueDate);
        final LoanSubStatus current = currentMilestoneStatus(loan.getLoanSubStatus());
        final LoanSubStatus nextStatus = TrancheMilestoneRules.decide(openTranche, repaymentMet, requiresMilestone, survey, pastDeadline,
                current);
        storeSubStatus(loan, nextStatus);
        final int trancheNumber = next == null ? 0 : loan.getDisbursementTrancheNumber(next);
        return new Refresh(nextStatus, requiresMilestone, dueDate, trancheNumber);
    }

    @Transactional
    public String ingest(final String loanKey, final String json) {
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final Loan loan = findLoan(loanKey);
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final boolean disbursed = loan.getDisbursementDetails() != null
                && loan.getDisbursementDetails().stream().anyMatch(detail -> detail.actualDisbursementDate() != null);
        if (!disbursed || next == null || next.getId() == null) {
            throw validation("A milestone can be stored after a tranche has been disbursed and while another tranche is waiting.",
                    "tranche.milestone.no.open.tranche");
        }
        final String submissionId = text(element, "submissionId");
        if (submissionId == null || submissionId.isBlank()) {
            throw validation("A submission id is required.", "submissionId");
        }
        if (this.milestoneRepository.findBySubmissionId(submissionId).isPresent()
                || this.historyRepository.existsBySubmissionId(submissionId)) {
            final Refresh refresh = refresh(loan);
            return response(loan, refresh, true);
        }
        LoanTrancheMilestone milestone = this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), next.getId())
                .orElse(null);
        if (milestone == null) {
            milestone = new LoanTrancheMilestone(loan.getId(), next.getId(), DateUtils.getOffsetDateTimeOfTenant());
        }
        applySubmission(milestone, element, submissionId);
        milestone.setSubmittedOn(DateUtils.getOffsetDateTimeOfTenant());
        this.milestoneRepository.save(milestone);
        final Refresh refresh = refresh(loan);
        this.historyRepository.save(new LoanTrancheMilestoneHistory(milestone,
                refresh.status == null ? null : refresh.status.getValue(), DateUtils.getOffsetDateTimeOfTenant()));
        return response(loan, refresh, false);
    }

    @Transactional(readOnly = true)
    public String read(final String loanKey) {
        final Loan loan = findLoan(loanKey);
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final LoanTrancheMilestone milestone = next == null || next.getId() == null ? null
                : this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), next.getId()).orElse(null);
        final LoanSubStatus status = currentMilestoneStatus(loan.getLoanSubStatus());
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("loanId", loan.getId());
        body.put("accountNo", loan.getAccountNumber());
        body.put("trancheNumber", next == null ? null : loan.getDisbursementTrancheNumber(next));
        body.put("status", status == null ? null : LoanSubStatus.loanSubStatus(status.getValue()));
        body.put("firstRepaymentDueDate", firstRepaymentDueDate(loan.getId()));
        body.put("milestoneRequired", productRequiresMilestone(loan));
        if (milestone != null) {
            body.put("conductedBy", milestone.getConductedBy());
            body.put("conductedOn", milestone.getConductedOn());
            body.put("operationalStatus", milestone.getOperationalStatus());
            body.put("additionalFunding", milestone.getAdditionalFunding());
            body.put("submissionId", milestone.getSubmissionId());
        }
        return new com.google.gson.Gson().toJson(body);
    }

    @CronTarget(jobName = JobName.REFRESH_TRANCHE_MILESTONE_STATUS)
    @Transactional
    public void refreshPastDeadline() {
        final LocalDate today = DateUtils.getBusinessLocalDate();
        this.jdbcTemplate.update("UPDATE m_loan l SET l.loan_sub_status_id = ? WHERE l.loan_status_id = 300 "
                + "AND (l.loan_sub_status_id IS NULL OR l.loan_sub_status_id IN (?, ?)) AND EXISTS (SELECT 1 FROM m_product_loan lp "
                + "WHERE lp.id = l.product_id AND lp.allow_multiple_disbursals = 1 AND lp.require_milestone_survey = 1) "
                + "AND EXISTS (SELECT 1 FROM m_loan_disbursement_detail d WHERE d.loan_id = l.id AND d.disbursedon_date IS NOT NULL) "
                + "AND EXISTS (SELECT 1 FROM m_loan_disbursement_detail u WHERE u.loan_id = l.id AND u.disbursedon_date IS NULL) "
                + "AND EXISTS (SELECT 1 FROM m_loan_repayment_schedule rs WHERE rs.loan_id = l.id AND rs.installment = 1 AND rs.duedate < ?)",
                LoanSubStatus.REQUIRES_REVIEW.getValue(), LoanSubStatus.REPAYMENT_NOT_MET.getValue(),
                LoanSubStatus.AWAITING_SURVEY.getValue(), Date.valueOf(today));
    }

    private void applySubmission(final LoanTrancheMilestone milestone, final JsonElement element, final String submissionId) {
        final List<ApiParameterError> errors = new ArrayList<>();
        milestone.setSubmissionId(submissionId);
        if (this.fromApiJsonHelper.parameterExists("conductedBy", element)) {
            milestone.setConductedBy(text(element, "conductedBy"));
        }
        if (this.fromApiJsonHelper.parameterExists("conductedOn", element)) {
            milestone.setConductedOn(conductedOn(element));
        }
        if (this.fromApiJsonHelper.parameterExists("operationalStatus", element)) {
            final String value = text(element, "operationalStatus");
            if (value != null && !TrancheMilestoneRules.OPERATIONAL.equals(value) && !TrancheMilestoneRules.NOT_OPERATIONAL.equals(value)) {
                errors.add(ApiParameterError.parameterError("validation.msg.tranche.milestone.operational.status.invalid",
                        "Operational status must be OPERATIONAL or NOT_OPERATIONAL.", "operationalStatus", value));
            }
            milestone.setOperationalStatus(value);
        }
        if (this.fromApiJsonHelper.parameterExists("additionalFunding", element)) {
            final String value = text(element, "additionalFunding");
            if (value != null && !TrancheMilestoneRules.NEEDS_FUNDS.equals(value)
                    && !TrancheMilestoneRules.DOES_NOT_NEED_FUNDS.equals(value)) {
                errors.add(ApiParameterError.parameterError("validation.msg.tranche.milestone.additional.funding.invalid",
                        "Additional funding must be NEEDS_FUNDS or DOES_NOT_NEED_FUNDS.", "additionalFunding", value));
            }
            milestone.setAdditionalFunding(value);
        }
        copyBoolean(element, "operationalVerifiedByBgs", milestone::setOperationalVerifiedByBgs);
        copyText(element, "operationalBgsName", milestone::setOperationalBgsName);
        copyBoolean(element, "operationalVerifiedByInvestment", milestone::setOperationalVerifiedByInvestment);
        copyText(element, "operationalInvestmentName", milestone::setOperationalInvestmentName);
        copyBoolean(element, "fundingVerifiedByBgs", milestone::setFundingVerifiedByBgs);
        copyText(element, "fundingBgsName", milestone::setFundingBgsName);
        copyBoolean(element, "fundingVerifiedByInvestment", milestone::setFundingVerifiedByInvestment);
        copyText(element, "fundingInvestmentName", milestone::setFundingInvestmentName);
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    private void copyBoolean(final JsonElement element, final String name,
            final java.util.function.Consumer<Boolean> setter) {
        if (this.fromApiJsonHelper.parameterExists(name, element)) {
            setter.accept(this.fromApiJsonHelper.extractBooleanNamed(name, element));
        }
    }

    private void copyText(final JsonElement element, final String name, final java.util.function.Consumer<String> setter) {
        if (this.fromApiJsonHelper.parameterExists(name, element)) {
            setter.accept(text(element, name));
        }
    }

    private LocalDate conductedOn(final JsonElement element) {
        final String raw = text(element, "conductedOn");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            return this.fromApiJsonHelper.extractLocalDateNamed("conductedOn", element);
        }
    }

    private String text(final JsonElement element, final String name) {
        if (!this.fromApiJsonHelper.parameterExists(name, element)) {
            return null;
        }
        return this.fromApiJsonHelper.extractStringNamed(name, element);
    }

    private void storeSubStatus(final Loan loan, final LoanSubStatus status) {
        final LoanSubStatus current = currentMilestoneStatus(loan.getLoanSubStatus());
        final Integer raw = loan.getLoanSubStatus();
        if (raw != null && current == null) {
            return;
        }
        final Integer value = status == null ? null : status.getValue();
        if (java.util.Objects.equals(raw, value)) {
            return;
        }
        if (value == null) {
            this.jdbcTemplate.update("UPDATE m_loan SET loan_sub_status_id = NULL WHERE id = ?", loan.getId());
        } else {
            this.jdbcTemplate.update("UPDATE m_loan SET loan_sub_status_id = ? WHERE id = ?", value, loan.getId());
        }
        loan.setLoanSubStatus(value);
    }

    private static LoanSubStatus currentMilestoneStatus(final Integer raw) {
        if (raw == null) {
            return null;
        }
        final LoanSubStatus status = LoanSubStatus.fromInt(raw);
        return status.isTrancheMilestone() ? status : null;
    }

    private static boolean productRequiresMilestone(final Loan loan) {
        return loan.loanProduct() != null && loan.loanProduct().isRequireMilestoneSurvey();
    }

    private LocalDate firstRepaymentDueDate(final Long loanId) {
        return this.jdbcTemplate.query(
                "select min(duedate) from m_loan_repayment_schedule where loan_id = ? and installment = 1", rs -> {
                    if (!rs.next() || rs.getDate(1) == null) {
                        return null;
                    }
                    return rs.getDate(1).toLocalDate();
                }, loanId);
    }

    private Loan findLoan(final String loanKey) {
        if (loanKey != null && loanKey.chars().allMatch(Character::isDigit)) {
            final Long id = Long.valueOf(loanKey);
            return this.loanRepository.findById(id).orElseThrow(() -> new LoanNotFoundException(id));
        }
        final Loan loan = this.loanRepository.findLoanAccountByAccountNumber(loanKey);
        if (loan == null) {
            throw new LoanNotFoundException(loanKey);
        }
        return loan;
    }

    private String response(final Loan loan, final Refresh refresh, final boolean duplicate) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("loanId", loan.getId());
        body.put("accountNo", loan.getAccountNumber());
        body.put("trancheNumber", refresh.trancheNumber);
        body.put("duplicate", duplicate);
        body.put("status", refresh.status == null ? null : LoanSubStatus.loanSubStatus(refresh.status.getValue()));
        body.put("firstRepaymentDueDate", refresh.firstRepaymentDueDate);
        return new com.google.gson.Gson().toJson(body);
    }

    private static PlatformApiDataValidationException validation(final String message, final String parameter) {
        final List<ApiParameterError> errors = new ArrayList<>();
        errors.add(ApiParameterError.parameterError("validation.msg." + parameter, message, parameter));
        return new PlatformApiDataValidationException(errors);
    }
}
