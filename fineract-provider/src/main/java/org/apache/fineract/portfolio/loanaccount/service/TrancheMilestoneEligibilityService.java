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
        public final TrancheMilestoneRules.SurveyAnswer survey;
        public final boolean pastDeadline;
        public final boolean deadlineExceptionApproved;
        public final boolean thresholdConfigured;
        /** True when a tranche is already disbursed and a later tranche is still waiting. */
        public final boolean subsequentTranche;

        public Refresh(final LoanSubStatus status, final boolean milestoneRequired, final LocalDate firstRepaymentDueDate,
                final int trancheNumber) {
            this(status, milestoneRequired, firstRepaymentDueDate, trancheNumber, TrancheMilestoneRules.SurveyAnswer.NONE, false, false,
                    false, false);
        }

        public Refresh(final LoanSubStatus status, final boolean milestoneRequired, final LocalDate firstRepaymentDueDate,
                final int trancheNumber, final TrancheMilestoneRules.SurveyAnswer survey, final boolean pastDeadline,
                final boolean deadlineExceptionApproved, final boolean thresholdConfigured, final boolean subsequentTranche) {
            this.status = status;
            this.milestoneRequired = milestoneRequired;
            this.firstRepaymentDueDate = firstRepaymentDueDate;
            this.trancheNumber = trancheNumber;
            this.survey = survey == null ? TrancheMilestoneRules.SurveyAnswer.NONE : survey;
            this.pastDeadline = pastDeadline;
            this.deadlineExceptionApproved = deadlineExceptionApproved;
            this.thresholdConfigured = thresholdConfigured;
            this.subsequentTranche = subsequentTranche;
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

        public boolean surveyIncomplete() {
            return this.milestoneRequired && this.survey == TrancheMilestoneRules.SurveyAnswer.INCOMPLETE;
        }
    }

    @Transactional
    public Refresh refresh(final Loan loan) {
        return refresh(loan, false);
    }

    private Refresh refresh(final Loan loan, final boolean alwaysRecord) {
        final Refresh result = preview(loan);
        if (loan != null && loan.getId() != null && loan.loanProduct() != null && loan.loanProduct().isMultiDisburseLoan()) {
            final Integer before = loan.getLoanSubStatus();
            storeSubStatus(loan, result.status);
            if (alwaysRecord || !java.util.Objects.equals(before, loan.getLoanSubStatus())) {
                recordStatusHistory(loan, result.status);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Refresh preview(final Loan loan) {
        if (loan == null || loan.getId() == null || loan.loanProduct() == null || !loan.loanProduct().isMultiDisburseLoan()) {
            return new Refresh(null, false, null, 0);
        }
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final boolean disbursed = hasDisbursedTranche(loan);
        final boolean openTranche = disbursed && next != null && next.getId() != null;
        final boolean requiresMilestone = productRequiresMilestone(loan);
        final TrancheReleaseRules.Assessment assessment = openTranche ? TrancheReleaseRules.assess(loan) : null;
        final boolean thresholdConfigured = assessment != null;
        final boolean repaymentMet = openTranche && (thresholdConfigured ? assessment.met() : !requiresMilestone);
        final LoanTrancheMilestone milestone = openTranche
                ? this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), next.getId()).orElse(null)
                : (disbursed ? milestoneForLatestDisbursedTranche(loan) : null);
        final TrancheMilestoneRules.SurveyAnswer survey = surveyAnswer(milestone);
        final boolean exceptionApproved = milestone != null && Boolean.TRUE.equals(milestone.getDeadlineExceptionApproved());
        final LocalDate dueDate = eligibilityDeadline(loan);
        final boolean pastDeadline = dueDate != null && DateUtils.getBusinessLocalDate().isAfter(dueDate);
        final LoanSubStatus nextStatus = TrancheMilestoneRules.decide(openTranche, repaymentMet, requiresMilestone, survey, pastDeadline,
                exceptionApproved);
        final int trancheNumber = next == null ? 0 : loan.getDisbursementTrancheNumber(next);
        return new Refresh(nextStatus, requiresMilestone, dueDate, trancheNumber, survey, pastDeadline, exceptionApproved,
                thresholdConfigured, openTranche);
    }

    @Transactional
    public String ingest(final String loanKey, final String json) {
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final Loan loan = findLoan(loanKey);
        final LoanDisbursementDetails target = waitingTranche(loan, element);
        String submissionId = text(element, "submissionId");
        if (submissionId == null || submissionId.isBlank()) {
            submissionId = loan.getAccountNumber() + "-" + target.getId() + "-"
                    + DateUtils.getOffsetDateTimeOfTenant().toInstant().toEpochMilli();
        }
        final Optional<LoanTrancheMilestone> bySubmission = this.milestoneRepository.findBySubmissionId(submissionId);
        if (bySubmission.isPresent()) {
            final LoanTrancheMilestone stored = bySubmission.get();
            if (loan.getId().equals(stored.getLoanId()) && target.getId().equals(stored.getDisbursementDetailId())) {
                final Refresh refresh = refresh(loan);
                return response(loan, refresh, true, stored);
            }
            throw validation("This submission id is already stored for another loan or tranche.",
                    "tranche.milestone.submission.duplicate");
        }
        LoanTrancheMilestone milestone = this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), target.getId())
                .orElse(null);
        if (milestone != null && milestone.getSubmissionId() != null && !milestone.getSubmissionId().isBlank()) {
            throw validation("A milestone survey has already been submitted for this tranche.", "tranche.milestone.already.submitted");
        }
        if (milestone == null) {
            milestone = new LoanTrancheMilestone(loan.getId(), target.getId(), DateUtils.getOffsetDateTimeOfTenant());
        }
        applySubmission(milestone, element, submissionId);
        milestone.setSubmittedOn(DateUtils.getOffsetDateTimeOfTenant());
        this.milestoneRepository.save(milestone);
        final Refresh refresh = refresh(loan, true);
        return response(loan, refresh, false, milestone);
    }

    @Transactional
    public String approveDeadlineException(final String loanKey, final String json, final String approvedBy) {
        final JsonElement element = this.fromApiJsonHelper.parse(json == null || json.isBlank() ? "{}" : json);
        final Loan loan = findLoan(loanKey);
        final LoanDisbursementDetails target = waitingTranche(loan, element);
        final LocalDate dueDate = eligibilityDeadline(loan);
        final boolean pastDeadline = dueDate != null && DateUtils.getBusinessLocalDate().isAfter(dueDate);
        if (!pastDeadline) {
            throw validation("A deadline exception can be approved after the eligibility deadline.",
                    "tranche.milestone.deadline.not.passed");
        }
        LoanTrancheMilestone milestone = this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), target.getId())
                .orElse(null);
        if (milestone == null) {
            milestone = new LoanTrancheMilestone(loan.getId(), target.getId(), DateUtils.getOffsetDateTimeOfTenant());
        }
        milestone.setDeadlineExceptionApproved(true);
        milestone.setDeadlineExceptionBy(approvedBy);
        milestone.setDeadlineExceptionOn(DateUtils.getOffsetDateTimeOfTenant());
        final String note = text(element, "note");
        if (note != null) {
            milestone.setDeadlineExceptionNote(note);
        }
        this.milestoneRepository.save(milestone);
        final Refresh refresh = refresh(loan, true);
        return response(loan, refresh, false, milestone);
    }

    @Transactional(readOnly = true)
    public String read(final String loanKey) {
        final Loan loan = findLoan(loanKey);
        final Refresh refresh = preview(loan);
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final LoanTrancheMilestone milestone = milestoneForDisplay(loan, next);
        final Map<String, Object> body = loanBody(loan, refresh, false, milestone);
        body.put("tranches", trancheSurveys(loan));
        return new com.google.gson.Gson().toJson(body);
    }

    @CronTarget(jobName = JobName.REFRESH_TRANCHE_MILESTONE_STATUS)
    @Transactional
    public void refreshPastDeadline() {
        final LocalDate today = DateUtils.getBusinessLocalDate();
        final List<Long> loanIds = this.jdbcTemplate.queryForList("SELECT l.id FROM m_loan l WHERE l.loan_status_id = 300 "
                + "AND EXISTS (SELECT 1 FROM m_product_loan lp WHERE lp.id = l.product_id AND lp.allow_multiple_disbursals = 1 "
                + "AND lp.require_milestone_survey = 1) "
                + "AND EXISTS (SELECT 1 FROM m_loan_disbursement_detail d WHERE d.loan_id = l.id AND d.disbursedon_date IS NOT NULL) "
                + "AND EXISTS (SELECT 1 FROM m_loan_disbursement_detail u WHERE u.loan_id = l.id AND u.disbursedon_date IS NULL) "
                + "AND EXISTS (SELECT 1 FROM m_loan_repayment_schedule rs WHERE rs.loan_id = l.id "
                + "AND rs.installment = COALESCE(l.grace_on_principal_periods, 0) + 1 AND rs.duedate < ?)", Long.class, Date.valueOf(today));
        for (final Long loanId : loanIds) {
            this.loanRepository.findById(loanId).ifPresent(this::refresh);
        }
    }

    private LoanDisbursementDetails waitingTranche(final Loan loan, final JsonElement element) {
        if (!hasDisbursedTranche(loan)) {
            throw validation("A milestone can be stored after a tranche has been disbursed and while another tranche is waiting.",
                    "tranche.milestone.no.open.tranche");
        }
        final Long requestedId = longValue(element, "disbursementDetailId");
        if (requestedId != null) {
            LoanDisbursementDetails match = null;
            if (loan.getDisbursementDetails() != null) {
                for (final LoanDisbursementDetails detail : loan.getDisbursementDetails()) {
                    if (requestedId.equals(detail.getId())) {
                        match = detail;
                        break;
                    }
                }
            }
            if (match == null || match.getId() == null || match.actualDisbursementDate() != null) {
                throw validation("The survey must be stored against a tranche that is still waiting to be disbursed.",
                        "tranche.milestone.tranche.not.waiting");
            }
            return match;
        }
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        if (next == null || next.getId() == null) {
            throw validation("A milestone can be stored after a tranche has been disbursed and while another tranche is waiting.",
                    "tranche.milestone.no.open.tranche");
        }
        return next;
    }

    private static boolean hasDisbursedTranche(final Loan loan) {
        return loan.getDisbursementDetails() != null
                && loan.getDisbursementDetails().stream().anyMatch(detail -> detail.actualDisbursementDate() != null);
    }

    private static TrancheMilestoneRules.SurveyAnswer surveyAnswer(final LoanTrancheMilestone milestone) {
        if (milestone == null || milestone.getSubmissionId() == null || milestone.getSubmissionId().isBlank()) {
            return TrancheMilestoneRules.SurveyAnswer.NONE;
        }
        final TrancheMilestoneRules.SurveyAnswer answer = TrancheMilestoneRules.answer(milestone.getOperationalStatus(),
                milestone.getAdditionalFunding(), milestone.getOperationalVerifiedByBgs(), milestone.getOperationalVerifiedByInvestment(),
                milestone.getFundingVerifiedByBgs(), milestone.getFundingVerifiedByInvestment());
        return answer == TrancheMilestoneRules.SurveyAnswer.NONE ? TrancheMilestoneRules.SurveyAnswer.INCOMPLETE : answer;
    }

    private void recordStatusHistory(final Loan loan, final LoanSubStatus status) {
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final Long detailId = next == null ? null : next.getId();
        final LoanTrancheMilestone milestone = detailId == null ? null
                : this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), detailId).orElse(null);
        final LoanTrancheMilestoneHistory history = new LoanTrancheMilestoneHistory(loan.getId(), detailId,
                status == null ? null : status.getValue(), DateUtils.getOffsetDateTimeOfTenant());
        history.copyFrom(milestone);
        this.historyRepository.save(history);
    }

    private List<Map<String, Object>> trancheSurveys(final Loan loan) {
        final List<Map<String, Object>> rows = new ArrayList<>();
        if (loan.getDisbursementDetails() == null) {
            return rows;
        }
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final List<LoanDisbursementDetails> ordered = loan.getDisbursementDetails().stream()
                .sorted(java.util.Comparator
                        .comparing(LoanDisbursementDetails::expectedDisbursementDate,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(LoanDisbursementDetails::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        int number = 0;
        for (final LoanDisbursementDetails detail : ordered) {
            number++;
            if (number < 2 || detail.getId() == null) {
                continue;
            }
            final LoanTrancheMilestone milestone = this.milestoneRepository.findByLoanIdAndDisbursementDetailId(loan.getId(), detail.getId())
                    .orElse(null);
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("disbursementDetailId", detail.getId());
            row.put("trancheNumber", number);
            row.put("expectedDisbursementDate", dateText(detail.expectedDisbursementDate()));
            row.put("actualDisbursementDate", dateText(detail.actualDisbursementDate()));
            row.put("disbursed", detail.actualDisbursementDate() != null);
            row.put("waiting", next != null && detail.getId().equals(next.getId()));
            final TrancheMilestoneRules.SurveyAnswer answer = surveyAnswer(milestone);
            row.put("surveyAnswer", answer.name());
            final Map<String, Object> survey = new LinkedHashMap<>();
            putSurvey(survey, milestone);
            row.put("survey", survey);
            rows.add(row);
        }
        return rows;
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
        final String comments = text(element, "comments");
        if (comments != null) {
            milestone.setComments(limit(comments, 2000));
        }
        final String evidence = evidenceText(element);
        if (evidence != null) {
            milestone.setEvidenceReferences(limit(evidence, 2000));
        }
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

    private static String evidenceText(final JsonElement element) {
        final JsonElement value = firstField(element, "evidenceReferences", "evidence");
        if (value == null) {
            return null;
        }
        if (value.isJsonArray()) {
            final StringBuilder builder = new StringBuilder();
            for (final JsonElement item : value.getAsJsonArray()) {
                final String piece = primitiveText(item);
                if (piece == null || piece.isBlank()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(piece.trim());
            }
            return builder.length() == 0 ? null : builder.toString();
        }
        return primitiveText(value);
    }

    private static Long longValue(final JsonElement element, final String name) {
        final JsonElement value = field(element, name);
        final JsonPrimitive primitive = primitive(value);
        if (primitive == null) {
            return null;
        }
        if (primitive.isNumber()) {
            return primitive.getAsLong();
        }
        if (primitive.isString() && !primitive.getAsString().isBlank()) {
            try {
                return Long.valueOf(primitive.getAsString().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static String limit(final String value, final int max) {
        final String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
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

    private String response(final Loan loan, final Refresh refresh, final boolean duplicate, final LoanTrancheMilestone milestone) {
        final Map<String, Object> body = loanBody(loan, refresh, duplicate, milestone);
        body.put("tranches", trancheSurveys(loan));
        return new com.google.gson.Gson().toJson(body);
    }

    private Map<String, Object> loanBody(final Loan loan, final Refresh refresh, final boolean duplicate,
            final LoanTrancheMilestone milestone) {
        final LoanDisbursementDetails next = loan.getNextUndisbursedDisbursementDetail();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("loanId", loan.getId());
        body.put("accountNo", loan.getAccountNumber());
        body.put("trancheNumber", next == null ? refresh.trancheNumber : loan.getDisbursementTrancheNumber(next));
        body.put("disbursementDetailId", milestone == null ? (next == null ? null : next.getId()) : milestone.getDisbursementDetailId());
        body.put("duplicate", duplicate);
        body.put("status", refresh.status == null ? null : statusValue(refresh.status));
        body.put("firstRepaymentDueDate", dateText(refresh.firstRepaymentDueDate));
        body.put("milestoneRequired", refresh.milestoneRequired);
        body.put("pastDeadline", refresh.subsequentTranche && refresh.pastDeadline);
        body.put("subsequentTranche", refresh.subsequentTranche);
        body.put("deadlineExceptionApproved", refresh.deadlineExceptionApproved);
        body.put("surveyAnswer", refresh.survey.name());
        putSurvey(body, milestone);
        return body;
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
        body.put("comments", milestone == null ? null : milestone.getComments());
        body.put("evidenceReferences", milestone == null ? null : milestone.getEvidenceReferences());
        body.put("deadlineExceptionApproved", milestone != null && Boolean.TRUE.equals(milestone.getDeadlineExceptionApproved()));
        body.put("deadlineExceptionBy", milestone == null ? null : milestone.getDeadlineExceptionBy());
        body.put("deadlineExceptionOn",
                milestone == null || milestone.getDeadlineExceptionOn() == null ? null : milestone.getDeadlineExceptionOn().toString());
        body.put("deadlineExceptionNote", milestone == null ? null : milestone.getDeadlineExceptionNote());
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
