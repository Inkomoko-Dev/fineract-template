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
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final JdbcTemplate jdbcTemplate;
    private final FromJsonHelper fromApiJsonHelper;

    public static final class Refresh {

        public final LoanSubStatus status;
        public final boolean milestoneRequired;
        public final LocalDate firstRepaymentDueDate;
        public final int trancheNumber;
        public final TrancheMilestoneRules.SurveyAnswer survey;

        public Refresh(final LoanSubStatus status, final boolean milestoneRequired, final LocalDate firstRepaymentDueDate,
                final int trancheNumber) {
            this(status, milestoneRequired, firstRepaymentDueDate, trancheNumber, TrancheMilestoneRules.SurveyAnswer.NONE);
        }

        public Refresh(final LoanSubStatus status, final boolean milestoneRequired, final LocalDate firstRepaymentDueDate,
                final int trancheNumber, final TrancheMilestoneRules.SurveyAnswer survey) {
            this.status = status;
            this.milestoneRequired = milestoneRequired;
            this.firstRepaymentDueDate = firstRepaymentDueDate;
            this.trancheNumber = trancheNumber;
            this.survey = survey == null ? TrancheMilestoneRules.SurveyAnswer.NONE : survey;
        }

        public boolean sendSurveyNotice() {
            return this.milestoneRequired && surveyOutstanding();
        }

        public boolean surveyOutstanding() {
            return this.milestoneRequired && (this.survey == TrancheMilestoneRules.SurveyAnswer.NONE
                    || this.survey == TrancheMilestoneRules.SurveyAnswer.INCOMPLETE);
        }

        public boolean surveyNegative() {
            return this.milestoneRequired && this.survey == TrancheMilestoneRules.SurveyAnswer.NEGATIVE;
        }
    }

    @Transactional
    public Refresh refresh(final Loan loan) {
        final Refresh result = preview(loan);
        if (loan != null && loan.getId() != null && loan.loanProduct() != null && loan.loanProduct().isMultiDisburseLoan()) {
            storeSubStatus(loan, result.status);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Refresh preview(final Loan loan) {
        if (loan == null || loan.getId() == null || loan.loanProduct() == null || !loan.loanProduct().isMultiDisburseLoan()) {
            return new Refresh(null, false, null, 0, TrancheMilestoneRules.SurveyAnswer.NONE);
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
                : (disbursed ? milestoneForLatestDisbursedTranche(loan) : null);
        final TrancheMilestoneRules.SurveyAnswer survey = milestone == null ? TrancheMilestoneRules.SurveyAnswer.NONE
                : TrancheMilestoneRules.answer(milestone.getOperationalStatus(), milestone.getAdditionalFunding(),
                        milestone.getOperationalVerifiedByBgs(), milestone.getOperationalVerifiedByInvestment(),
                        milestone.getFundingVerifiedByBgs(), milestone.getFundingVerifiedByInvestment());
        final LocalDate dueDate = eligibilityDeadline(loan);
        final boolean pastDeadline = dueDate != null && DateUtils.getBusinessLocalDate().isAfter(dueDate);
        final LoanSubStatus nextStatus = TrancheMilestoneRules.decide(openTranche, repaymentMet, requiresMilestone, survey,
                pastDeadline);
        final int trancheNumber = next == null ? 0 : loan.getDisbursementTrancheNumber(next);
        return new Refresh(nextStatus, requiresMilestone, dueDate, trancheNumber, survey);
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
        String submissionId = text(element, "submissionId");
        if (submissionId == null || submissionId.isBlank()) {
            submissionId = loan.getAccountNumber() + "-" + DateUtils.getOffsetDateTimeOfTenant().toInstant().toEpochMilli();
        }
        if (this.milestoneRepository.findBySubmissionId(submissionId).isPresent()) {
            final Refresh refresh = refresh(loan);
            return response(loan, refresh, true);
        }
        if (this.milestoneRepository.existsByLoanId(loan.getId())) {
            throw validation("A milestone survey has already been submitted for this loan.", "tranche.milestone.already.submitted");
        }
        final LoanTrancheMilestone milestone = new LoanTrancheMilestone(loan.getId(), next.getId(),
                DateUtils.getOffsetDateTimeOfTenant());
        applySubmission(milestone, element, submissionId);
        milestone.setSubmittedOn(DateUtils.getOffsetDateTimeOfTenant());
        this.milestoneRepository.save(milestone);
        final Refresh refresh = refresh(loan);
        return response(loan, refresh, false);
    }

    @Transactional
    public String read(final String loanKey) {
        final Loan loan = findLoan(loanKey);
        final Refresh refresh = refresh(loan);
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final LoanTrancheMilestone milestone = milestoneForDisplay(loan, next);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("loanId", loan.getId());
        body.put("accountNo", loan.getAccountNumber());
        body.put("trancheNumber", next == null ? null : loan.getDisbursementTrancheNumber(next));
        body.put("status", refresh.status == null ? null : statusValue(refresh.status));
        body.put("firstRepaymentDueDate", dateText(refresh.firstRepaymentDueDate));
        body.put("milestoneRequired", refresh.milestoneRequired);
        putSurvey(body, milestone);
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
                + "AND EXISTS (SELECT 1 FROM m_loan_repayment_schedule rs WHERE rs.loan_id = l.id "
                + "AND rs.installment = COALESCE(l.grace_on_principal_periods, 0) + 1 AND rs.duedate < ?)",
                LoanSubStatus.REQUIRES_REVIEW.getValue(), LoanSubStatus.REPAYMENT_NOT_MET.getValue(),
                LoanSubStatus.AWAITING_SURVEY.getValue(), Date.valueOf(today));
    }

    /**
     * The loan screen keeps the submitted survey after its tranche is disbursed. While another tranche is still
     * waiting, that tranche's own survey is shown. Once nothing is waiting, the survey stored for the latest
     * disbursed tranche stays on the page.
     */
    private LoanTrancheMilestone milestoneForDisplay(final Loan loan, final LoanDisbursementDetails next) {
        if (next != null && next.getId() != null) {
            return this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), next.getId()).orElse(null);
        }
        return milestoneForLatestDisbursedTranche(loan);
    }

    private LoanTrancheMilestone milestoneForLatestDisbursedTranche(final Loan loan) {
        if (loan.getDisbursementDetails() != null) {
            LoanDisbursementDetails latest = null;
            for (final LoanDisbursementDetails detail : loan.getDisbursementDetails()) {
                if (detail.actualDisbursementDate() == null || detail.getId() == null) {
                    continue;
                }
                if (latest == null || disbursedAfter(detail, latest)) {
                    latest = detail;
                }
            }
            if (latest != null) {
                final Optional<LoanTrancheMilestone> stored = this.milestoneRepository
                        .findByLoanIdAndDisbursementDetailId(loan.getId(), latest.getId());
                if (stored.isPresent()) {
                    return stored.get();
                }
            }
        }
        return this.milestoneRepository.findFirstByLoanIdOrderBySubmittedOnDescIdDesc(loan.getId()).orElse(null);
    }

    private static boolean disbursedAfter(final LoanDisbursementDetails candidate, final LoanDisbursementDetails current) {
        final int byDate = candidate.actualDisbursementDate().compareTo(current.actualDisbursementDate());
        if (byDate != 0) {
            return byDate > 0;
        }
        return candidate.getId() > current.getId();
    }

    private void applySubmission(final LoanTrancheMilestone milestone, final JsonElement element, final String submissionId) {
        final List<ApiParameterError> errors = new ArrayList<>();
        milestone.setSubmissionId(submissionId);
        final String conductedBy = text(element, "conductedBy");
        if (conductedBy != null) {
            milestone.setConductedBy(conductedBy);
        }
        if (field(element, "conductedOn") != null) {
            milestone.setConductedOn(conductedOn(element, errors));
        }
        applyAnswer(milestone, element, errors, true);
        applyAnswer(milestone, element, errors, false);
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    private void applyAnswer(final LoanTrancheMilestone milestone, final JsonElement element, final List<ApiParameterError> errors,
            final boolean operational) {
        final String nodeName = operational ? "operationalStatus" : "additionalFundingRequired";
        final String flatName = operational ? "operational" : "additionalFundingRequired";
        final JsonElement node = field(element, nodeName);
        final boolean nested = node != null && node.isJsonObject();
        final JsonElement source = nested ? node : element;
        final JsonElement answer = nested ? firstField(node, "operational", "required", "value")
                : firstField(element, flatName, operational ? "operationalStatus" : "additionalFunding");
        if (answer != null) {
            final String stored = operational ? operationalCode(answer, errors) : fundingCode(answer, errors);
            if (stored != null) {
                if (operational) {
                    milestone.setOperationalStatus(stored);
                } else {
                    milestone.setAdditionalFunding(stored);
                }
            }
        }
        final Boolean verifiedByBgs = nested ? bool(source, "verifiedByBgs")
                : bool(source, operational ? "operationalVerifiedByBgs" : "fundingVerifiedByBgs");
        final Boolean verifiedByInvestment = nested ? bool(source, "verifiedByInvestment")
                : bool(source, operational ? "operationalVerifiedByInvestment" : "fundingVerifiedByInvestment");
        final String bgsName = nested ? text(source, "bgsName", "bgsVerifierName")
                : text(source, operational ? "operationalBgsName" : "fundingBgsName");
        final String investmentName = nested ? text(source, "investmentName", "investmentVerifierName")
                : text(source, operational ? "operationalInvestmentName" : "fundingInvestmentName");
        if (operational) {
            if (verifiedByBgs != null) {
                milestone.setOperationalVerifiedByBgs(verifiedByBgs);
            }
            if (bgsName != null) {
                milestone.setOperationalBgsName(bgsName);
            }
            if (verifiedByInvestment != null) {
                milestone.setOperationalVerifiedByInvestment(verifiedByInvestment);
            }
            if (investmentName != null) {
                milestone.setOperationalInvestmentName(investmentName);
            }
        } else {
            if (verifiedByBgs != null) {
                milestone.setFundingVerifiedByBgs(verifiedByBgs);
            }
            if (bgsName != null) {
                milestone.setFundingBgsName(bgsName);
            }
            if (verifiedByInvestment != null) {
                milestone.setFundingVerifiedByInvestment(verifiedByInvestment);
            }
            if (investmentName != null) {
                milestone.setFundingInvestmentName(investmentName);
            }
        }
    }

    private String operationalCode(final JsonElement answer, final List<ApiParameterError> errors) {
        final Boolean value = boolValue(answer);
        if (value != null) {
            return value ? TrancheMilestoneRules.OPERATIONAL : TrancheMilestoneRules.NOT_OPERATIONAL;
        }
        final String text = primitiveText(answer);
        if (TrancheMilestoneRules.OPERATIONAL.equals(text) || TrancheMilestoneRules.NOT_OPERATIONAL.equals(text)) {
            return text;
        }
        errors.add(ApiParameterError.parameterError("validation.msg.tranche.milestone.operational.status.invalid",
                "Operational status must be true when the business is operational, or false when it is not.", "operational", text));
        return null;
    }

    private String fundingCode(final JsonElement answer, final List<ApiParameterError> errors) {
        final Boolean value = boolValue(answer);
        if (value != null) {
            return value ? TrancheMilestoneRules.NEEDS_FUNDS : TrancheMilestoneRules.DOES_NOT_NEED_FUNDS;
        }
        final String text = primitiveText(answer);
        if (TrancheMilestoneRules.NEEDS_FUNDS.equals(text) || TrancheMilestoneRules.DOES_NOT_NEED_FUNDS.equals(text)) {
            return text;
        }
        errors.add(ApiParameterError.parameterError("validation.msg.tranche.milestone.additional.funding.invalid",
                "Additional funding required must be true when more funds are needed, or false when they are not.",
                "additionalFundingRequired", text));
        return null;
    }

    private OffsetDateTime conductedOn(final JsonElement element, final List<ApiParameterError> errors) {
        final String raw = primitiveText(field(element, "conductedOn"));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(raw).atZone(DateUtils.getDateTimeZoneOfTenant()).toOffsetDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDate.parse(raw).atStartOfDay(DateUtils.getDateTimeZoneOfTenant()).toOffsetDateTime();
                } catch (DateTimeParseException failed) {
                    errors.add(ApiParameterError.parameterError("validation.msg.tranche.milestone.conducted.on.invalid",
                            "Conducted on must be a date-time such as 2026-11-10T09:15:00+03:00.", "conductedOn", raw));
                    return null;
                }
            }
        }
    }

    private static JsonElement field(final JsonElement element, final String name) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        final JsonObject object = element.getAsJsonObject();
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return null;
        }
        return object.get(name);
    }

    private static JsonElement firstField(final JsonElement element, final String... names) {
        for (final String name : names) {
            final JsonElement value = field(element, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Boolean bool(final JsonElement element, final String... names) {
        return boolValue(firstField(element, names));
    }

    private static Boolean boolValue(final JsonElement value) {
        final JsonPrimitive primitive = primitive(value);
        if (primitive == null) {
            return null;
        }
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            if ("true".equalsIgnoreCase(primitive.getAsString())) {
                return true;
            }
            if ("false".equalsIgnoreCase(primitive.getAsString())) {
                return false;
            }
        }
        return null;
    }

    private static String text(final JsonElement element, final String... names) {
        return primitiveText(firstField(element, names));
    }

    private static String primitiveText(final JsonElement value) {
        final JsonPrimitive primitive = primitive(value);
        if (primitive == null || !primitive.isString()) {
            return null;
        }
        return primitive.getAsString();
    }

    private static JsonPrimitive primitive(final JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsJsonPrimitive();
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

    /**
     * Deadline is the due date of the first repayment after grace. One month of grace uses
     * installment 2; two months of grace uses installment 3.
     */
    private LocalDate eligibilityDeadline(final Loan loan) {
        if (loan == null || loan.getId() == null) {
            return null;
        }
        final Integer grace = loan.repaymentScheduleDetail() == null ? null : loan.repaymentScheduleDetail().graceOnPrincipalPayment();
        final int installment = Math.max(1, (grace == null ? 0 : grace) + 1);
        return this.jdbcTemplate.query(
                "select min(duedate) from m_loan_repayment_schedule where loan_id = ? and installment = ?", rs -> {
                    if (!rs.next() || rs.getDate(1) == null) {
                        return null;
                    }
                    return rs.getDate(1).toLocalDate();
                }, loan.getId(), installment);
    }

    private Loan findLoan(final String loanKey) {
        if (loanKey == null || loanKey.isBlank()) {
            throw new LoanNotFoundException(loanKey);
        }
        final Loan byAccount = this.loanRepository.findLoanAccountByAccountNumber(loanKey);
        if (byAccount != null) {
            return byAccount;
        }
        if (loanKey.chars().allMatch(Character::isDigit)) {
            final Long id = Long.valueOf(loanKey);
            return this.loanRepository.findById(id).orElseThrow(() -> new LoanNotFoundException(id));
        }
        throw new LoanNotFoundException(loanKey);
    }

    private String response(final Loan loan, final Refresh refresh, final boolean duplicate) {
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final LoanTrancheMilestone milestone = next == null || next.getId() == null ? null
                : this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), next.getId()).orElse(null);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("loanId", loan.getId());
        body.put("accountNo", loan.getAccountNumber());
        body.put("trancheNumber", refresh.trancheNumber);
        body.put("duplicate", duplicate);
        body.put("status", refresh.status == null ? null : statusValue(refresh.status));
        body.put("firstRepaymentDueDate", dateText(refresh.firstRepaymentDueDate));
        putSurvey(body, milestone);
        return new com.google.gson.Gson().toJson(body);
    }

    private static void putSurvey(final Map<String, Object> body, final LoanTrancheMilestone milestone) {
        body.put("submissionId", milestone == null ? null : milestone.getSubmissionId());
        body.put("conductedBy", milestone == null ? null : milestone.getConductedBy());
        body.put("conductedOn", milestone == null || milestone.getConductedOn() == null ? null : milestone.getConductedOn().toString());
        body.put("operational", milestone == null ? null : operationalBoolean(milestone.getOperationalStatus()));
        body.put("operationalVerifiedByBgs", milestone == null ? null : milestone.getOperationalVerifiedByBgs());
        body.put("operationalBgsName", milestone == null ? null : milestone.getOperationalBgsName());
        body.put("operationalVerifiedByInvestment", milestone == null ? null : milestone.getOperationalVerifiedByInvestment());
        body.put("operationalInvestmentName", milestone == null ? null : milestone.getOperationalInvestmentName());
        body.put("additionalFundingRequired", milestone == null ? null : fundingBoolean(milestone.getAdditionalFunding()));
        body.put("fundingVerifiedByBgs", milestone == null ? null : milestone.getFundingVerifiedByBgs());
        body.put("fundingBgsName", milestone == null ? null : milestone.getFundingBgsName());
        body.put("fundingVerifiedByInvestment", milestone == null ? null : milestone.getFundingVerifiedByInvestment());
        body.put("fundingInvestmentName", milestone == null ? null : milestone.getFundingInvestmentName());
    }

    private static Boolean operationalBoolean(final String stored) {
        if (TrancheMilestoneRules.OPERATIONAL.equals(stored)) {
            return true;
        }
        if (TrancheMilestoneRules.NOT_OPERATIONAL.equals(stored)) {
            return false;
        }
        return null;
    }

    private static Boolean fundingBoolean(final String stored) {
        if (TrancheMilestoneRules.NEEDS_FUNDS.equals(stored)) {
            return true;
        }
        if (TrancheMilestoneRules.DOES_NOT_NEED_FUNDS.equals(stored)) {
            return false;
        }
        return null;
    }

    private static Map<String, Object> statusValue(final LoanSubStatus status) {
        final org.apache.fineract.infrastructure.core.data.EnumOptionData option = LoanSubStatus.loanSubStatus(status.getValue());
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", option.getId());
        value.put("code", option.getCode());
        value.put("value", option.getValue());
        return value;
    }

    private static String dateText(final LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static PlatformApiDataValidationException validation(final String message, final String parameter) {
        final List<ApiParameterError> errors = new ArrayList<>();
        errors.add(ApiParameterError.parameterError("validation.msg." + parameter, message, parameter));
        return new PlatformApiDataValidationException(errors);
    }
}
