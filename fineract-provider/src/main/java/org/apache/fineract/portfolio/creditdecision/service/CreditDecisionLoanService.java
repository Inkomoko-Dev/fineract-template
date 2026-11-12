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
package org.apache.fineract.portfolio.creditdecision.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionCatalogue;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ActiveProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.CatalogueItem;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ConnectionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.LogEntryData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ParamData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.EventData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.ExceptionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.LoanDecisionData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.ProductProfileData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionOutcome;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per loan application: whether it needs an automated credit decision, whether someone checked its details, sending the
 * request to the decision engine and keeping what it replied. Fineract only builds the request from the profile and
 * stores the reply. The engine makes the decision.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditDecisionLoanService {

    /** The engine credential is never stored in the database. It is read from the environment when a request is sent. */
    static final String API_KEY_ENV = "CREDIT_DECISION_ENGINE_API_KEY";
    /** m_loan.loan_status_id of an application that was submitted and is waiting for approval. */
    private static final int SUBMITTED_AND_PENDING_APPROVAL = 100;
    /** m_loan.loan_decision_state while the application is in the Review Application stage. */
    private static final int REVIEW_APPLICATION = 1000;
    /** m_loan.loan_decision_state once Due Diligence has been accepted. */
    private static final int DUE_DILIGENCE = 1200;
    /** With this switched on, an application goes through Review Application, Due Diligence, IC review and so on. */
    private static final String EXTENDED_LIFECYCLE = "Add-More-Stages-To-A-Loan-Life-Cycle";
    private static final long RETRY_PAUSE_MILLIS = 300;
    /**
     * How long after the n-th failed send in a row the next automatic retry is due. When the list runs out the credit team is
     * alerted and the application waits in the exception queue for somebody to send it again.
     */
    static final int[] RETRY_DELAY_MINUTES = { 5, 15, 60, 240 };
    /** A send that has not finished after its own time limit plus this many seconds is taken to have died. */
    private static final int STALE_CLAIM_GRACE_SECONDS = 60;
    private static final int MAX_EXCEPTIONS = 200;
    private static final String ON_NO_REPLY_ALERT = "ALERT";
    private static final String SEND_IN_PROGRESS_CODE = "error.msg.creditdecision.loan.send.in.progress";
    private static final int MAX_NOTE = 500;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final JdbcTemplate jdbc;
    private final PlatformSecurityContext context;
    private final CreditDecisionService profiles;
    private final CreditDecisionLoanFacts facts;
    private final CreditDecisionEngineTransport transport;
    private final ApplicationEventPublisher events;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public LoanDecisionData status(final Long loanId) {
        final LoanRow loan = loan(loanId);
        final ProductProfileData profile = profiles.profileForProduct(loan.productId);
        final DecisionRow row = decision(loanId);
        final boolean required = row != null && row.required;
        final boolean decided = row != null && CreditDecisionLoanData.DECIDED.equals(row.requestStatus);
        final List<String> blockers = required ? blockers(loan, profile, row) : List.of(CreditDecisionLoanData.BLOCKER_NOT_REQUIRED);
        return LoanDecisionData.builder().loanId(loanId).productId(loan.productId).productName(loan.productName).eligible(profile != null)
                .profileName(profile == null ? null : profile.getProfileName()).profileMode(profile == null ? null : profile.getMode())
                .activeVersionNo(profile == null ? null : profile.getActiveVersionNo()).required(required)
                .canChangeFlag(profile != null && flagStageOpen(loan) && !decided).reviewStageOpen(reviewStageOpen(loan)).sendStageOpen(sendStageOpen(loan))
                .verificationStatus(row == null ? CreditDecisionLoanData.PENDING_VERIFICATION : row.verificationStatus)
                .verifiedBy(row == null ? null : row.verifiedBy).verifiedOn(row == null ? null : row.verifiedOn)
                .verificationNote(row == null ? null : row.verificationNote)
                .requestStatus(row == null ? CreditDecisionLoanData.NOT_SENT : row.requestStatus)
                .requestedBy(row == null ? null : row.requestedBy).requestedOn(row == null ? null : row.requestedOn)
                .decision(row == null ? null : row.decision).score(row == null ? null : row.score)
                .riskTier(row == null ? null : row.riskTier).maxExposure(row == null ? null : row.maxExposure)
                .modelVersion(row == null ? null : row.modelVersion).reasonCodes(row == null ? null : row.reasonCodes).replyMs(row == null ? null : row.replyMs)
                .errorMessage(row == null ? null : row.errorMessage).requestId(row == null ? null : row.requestId)
                .failureCount(row == null ? 0 : row.failureCount).nextRetryOn(row == null ? null : iso(row.nextRetryOn))
                .alertedOn(row == null ? null : iso(row.alertedOn)).provider(profiles.connection().getProvider())
                .requestedProfileName(row == null ? null : row.profileName).requestedVersionNo(row == null ? null : row.versionNo)
                .requestJson(row == null ? null : row.requestJson).replyJson(row == null ? null : row.replyJson).blockers(blockers)
                .blocksApproval(required && !decided && live(profile))
                .blocksContract(required && live(profile) && !(decided && contractOpen(row.decision, loan.decisionState)))
                .blocksIcReview(required && live(profile) && !(decided && CreditDecisionOutcome.IC_REVIEW_REQUIRED.equals(row.decision)))
                .build();
    }

    // ------------------------------------------------------------------ flag and verify

    /** Says whether this application needs an automated credit decision. Only while it waits for approval. */
    @Transactional
    public LoanDecisionData setRequired(final Long loanId, final boolean required) {
        final LoanRow loan = loan(loanId);
        requireFlagStageOpen(loan);
        if (!required && outcomeGiven(loanId)) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.already.decided",
                    "The decision engine has already given its outcome for this application, so the requirement cannot be removed. "
                            + "Re-open the verification first, which clears the outcome.");
        }
        if (required && profiles.profileForProduct(loan.productId) == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.product.not.in.profile",
                    "'" + loan.productName + "' is not part of a credit decision profile, so its applications cannot ask for a decision.");
        }
        final Timestamp now = Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem());
        final DecisionRow row = decision(loanId);
        if (row == null) {
            if (required) {
                jdbc.update("INSERT INTO m_loan_credit_decision (loan_id, required, flagged_by, flagged_on, verification_status, "
                        + "request_status) VALUES (?, TRUE, ?, ?, ?, ?)", loanId, username(), now,
                        CreditDecisionLoanData.PENDING_VERIFICATION, CreditDecisionLoanData.NOT_SENT);
            }
        } else if (row.required != required) {
            jdbc.update("UPDATE m_loan_credit_decision SET required = ?, flagged_by = ?, flagged_on = ? WHERE loan_id = ?", required,
                    username(), now, loanId);
        }
        if (row == null ? required : row.required != required) {
            event(loanId, required ? CreditDecisionLoanData.EVENT_FLAGGED : CreditDecisionLoanData.EVENT_UNFLAGGED, null, null, null, null);
        }
        return status(loanId);
    }

    /** Confirms the details of the application were checked, and corrected where needed. */
    @Transactional
    public LoanDecisionData verify(final Long loanId, final String note) {
        final LoanRow loan = loan(loanId);
        requireReviewStage(loan);
        requireFlagged(loanId);
        jdbc.update("UPDATE m_loan_credit_decision SET verification_status = ?, verified_by = ?, verified_on = ?, verification_note = ? "
                + "WHERE loan_id = ?", CreditDecisionLoanData.VERIFIED, username(), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()),
                note == null || note.isBlank() ? null : truncate(note.trim(), MAX_NOTE), loanId);
        event(loanId, CreditDecisionLoanData.EVENT_VERIFIED, note == null || note.isBlank() ? null : note.trim(), null, null, null);
        return status(loanId);
    }

    /**
     * Puts the application back to pending verification, for when its details need correcting again. An outcome the
     * engine gave for the earlier details no longer applies, so it is cleared.
     */
    @Transactional
    public LoanDecisionData unverify(final Long loanId) {
        final LoanRow loan = loan(loanId);
        requireReviewStage(loan);
        requireFlagged(loanId);
        final DecisionRow before = decision(loanId);
        jdbc.update("UPDATE m_loan_credit_decision SET verification_status = ?, verified_by = NULL, verified_on = NULL, "
                + "verification_note = NULL, request_status = ?, decision = NULL, score = NULL, reason_codes = NULL, risk_tier = NULL, max_exposure = NULL, model_version = NULL, "
                + "reply_ms = NULL, error_message = NULL, request_json = NULL, reply_json = NULL, request_id = NULL, last_attempt_on = NULL, "
                + "failure_count = 0, next_retry_on = NULL, alerted_on = NULL WHERE loan_id = ?",
                CreditDecisionLoanData.PENDING_VERIFICATION, CreditDecisionLoanData.NOT_SENT, loanId);
        final boolean cleared = before != null && CreditDecisionLoanData.DECIDED.equals(before.requestStatus);
        event(loanId, CreditDecisionLoanData.EVENT_REOPENED, cleared ? "Cleared the earlier outcome: " + before.decision : null, null, null,
                null);
        return status(loanId);
    }

    // ------------------------------------------------------------------ send

    /**
     * Builds the request from the profile, sends it to the decision engine and keeps the reply. A reply that cannot be
     * used is kept as FAILED, with the reason, so the person can see it and try again. Only one send per application runs at
     * a time: a second one is refused while the first is under way.
     */
    public LoanDecisionData send(final Long loanId) {
        final LoanDecisionData before = status(loanId);
        if (!before.getBlockers().isEmpty()) {
            throw refused(before.getBlockers().get(0));
        }
        claim(loanId);
        try {
            // somebody may have finished a send between the check above and the claim, so look again with the claim held
            final LoanRow loan = loan(loanId);
            final List<String> blockers = blockers(loan, profiles.profileForProduct(loan.productId), decision(loanId), true);
            if (!blockers.isEmpty()) {
                throw refused(blockers.get(0));
            }
            return sendClaimed(loanId, loan);
        } finally {
            release(loanId);
        }
    }

    /** Takes the right to send for this application, or says another send is under way. Stands in for a lock across servers. */
    private void claim(final Long loanId) {
        final ConnectionData connection = profiles.connection();
        final Timestamp now = Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem());
        final int claimed = jdbc.update("UPDATE m_loan_credit_decision SET send_started_on = ? WHERE loan_id = ? AND required = TRUE "
                + "AND (send_started_on IS NULL OR send_started_on < ?)", now, loanId, staleBefore(connection, now));
        if (claimed == 0) {
            throw refused(CreditDecisionLoanData.BLOCKER_SEND_IN_PROGRESS);
        }
    }

    private static GeneralPlatformDomainRuleException refused(final String blocker) {
        return new GeneralPlatformDomainRuleException(CreditDecisionLoanData.BLOCKER_SEND_IN_PROGRESS.equals(blocker) ? SEND_IN_PROGRESS_CODE
                : "error.msg.creditdecision.loan.send.blocked", describe(blocker));
    }

    private void release(final Long loanId) {
        jdbc.update("UPDATE m_loan_credit_decision SET send_started_on = NULL WHERE loan_id = ?", loanId);
    }

    /** A claim older than this was left by a send that died, so it no longer holds anything. */
    private static Timestamp staleBefore(final ConnectionData connection, final Timestamp now) {
        final long seconds = (long) Math.max(1, connection.getTimeoutSeconds()) * (1 + Math.max(0, connection.getRetries()))
                + STALE_CLAIM_GRACE_SECONDS;
        return Timestamp.valueOf(now.toLocalDateTime().minusSeconds(seconds));
    }

    private LoanDecisionData sendClaimed(final Long loanId, final LoanRow loan) {
        final ActiveProfileData profile = profiles.activeForProduct(loan.productId);
        final ConnectionData connection = profiles.connection();
        final DecisionRow earlier = decision(loanId);
        final String profileLabel = profile.getProfileName() + " v" + profile.getVersionNo();

        final List<String> keys = profile.getParams().stream().filter(ParamData::isEnabled).map(ParamData::getKey)
                .collect(Collectors.toList());
        final CreditDecisionRequestBuilder.Built built = CreditDecisionRequestBuilder.build(profile.getParams(),
                facts.resolve(loanId, keys));

        // a send that failed is asked again with the same identifier when nothing in it has changed, so the engine can
        // recognise it as the same request, and a request whose content changed gets a new one
        String reused = earlier != null && CreditDecisionLoanData.FAILED.equals(earlier.requestStatus) ? earlier.requestId : null;
        if (reused != null && !payload(reused, loan, profileLabel, built).equals(earlier.requestJson)) {
            reused = null;
        }
        final String requestId = reused == null ? newRequestId() : reused;
        final String requestJson = payload(requestId, loan, profileLabel, built);

        if (!built.getHeld().isEmpty()) {
            final String missing = built.getHeld().stream().map(CreditDecisionLoanService::label).collect(Collectors.joining(", "));
            final LogEntryData heldEntry = entry(loan, profile, "HELD", null, null, null, null,
                    "Complete the missing details: " + missing, requestJson, null);
            heldEntry.setRequestId(requestId);
            final Long heldLog = profiles.recordLog(heldEntry);
            jdbc.update("UPDATE m_loan_credit_decision SET error_message = ?, last_attempt_on = ? WHERE loan_id = ?",
                    truncate("Missing details: " + missing, MAX_NOTE), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()), loanId);
            event(loanId, CreditDecisionLoanData.EVENT_HELD, truncate("Not sent, missing: " + missing, MAX_NOTE), null, null, heldLog);
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.send.data.missing",
                    "The request was not sent because the profile needs these details and the application has none: " + missing
                            + ". Complete them on the application, then send again.");
        }

        final long started = System.currentTimeMillis();
        String replyJson = null;
        String failure = null;
        CreditDecisionEngineReply reply = null;
        final int attempts = 1 + Math.max(0, connection.getRetries());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                final CreditDecisionEngineTransport.Response response = transport.post(connection.getEndpointUrl(), requestJson,
                        connection.getTimeoutSeconds(), System.getenv(API_KEY_ENV));
                replyJson = response.getBody();
                if (response.getStatus() >= 500) {
                    failure = "The decision engine answered HTTP " + response.getStatus();
                } else if (response.getStatus() >= 400) {
                    failure = "The decision engine refused the request with HTTP " + response.getStatus();
                    break; // asking again will not change a refusal
                } else {
                    reply = CreditDecisionEngineReply.parse(response.getBody());
                    failure = reply.usable() ? null : reply.getProblem();
                    break; // the engine answered, so there is nothing to retry
                }
            } catch (final IOException e) {
                failure = "The decision engine could not be reached: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (attempt < attempts && !pause(attempt)) {
                break;
            }
        }
        final int replyMs = (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - started);

        final Timestamp now = Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem());
        if (failure == null) {
            jdbc.update("UPDATE m_loan_credit_decision SET request_status = ?, profile_id = ?, profile_name = ?, version_no = ?, "
                    + "requested_by = ?, requested_on = ?, decision = ?, score = ?, reason_codes = ?, risk_tier = ?, max_exposure = ?, "
                    + "model_version = ?, reply_ms = ?, error_message = NULL, request_json = ?, reply_json = ?, request_id = ?, "
                    + "last_attempt_on = ?, failure_count = 0, next_retry_on = NULL, alerted_on = NULL WHERE loan_id = ?",
                    CreditDecisionLoanData.DECIDED, profile.getProfileId(), profile.getProfileName(), profile.getVersionNo(), username(),
                    now, reply.getDecision(), reply.getScore(), reply.getReasonCodes(), reply.getRiskTier(), reply.getMaxExposure(),
                    reply.getModelVersion(), replyMs, requestJson, replyJson, requestId, now, loanId);
            final boolean live = "LIVE".equals(profile.getMode());
            final LogEntryData logged = entry(loan, profile, "DECIDED", reply.getDecision(), reply.getScore(), reply.getReasonCodes(),
                    replyMs, nextStep(reply.getDecision(), live), requestJson, replyJson);
            logged.setRiskTier(reply.getRiskTier());
            logged.setMaxExposure(reply.getMaxExposure());
            logged.setModelVersion(reply.getModelVersion());
            logged.setRequestId(requestId);
            final Long logId = profiles.recordLog(logged);
            event(loanId, CreditDecisionLoanData.EVENT_DECIDED, reply.getReasonCodes() == null ? null : truncate(reply.getReasonCodes(), MAX_NOTE),
                    reply.getDecision(), reply.getScore(), logId);
            events.publishEvent(new CreditDecisionOutcomeRecorded(loanId, reply.getDecision(), reply.getReasonCodes(), live));
        } else {
            final int failures = (earlier == null ? 0 : earlier.failureCount) + 1;
            final boolean alertInstead = ON_NO_REPLY_ALERT.equals(connection.getOnNoReply());
            final Timestamp retryOn = alertInstead ? null : nextRetry(now, failures);
            final boolean alert = retryOn == null;
            jdbc.update("UPDATE m_loan_credit_decision SET request_status = ?, profile_id = ?, profile_name = ?, version_no = ?, "
                    + "requested_by = ?, requested_on = ?, decision = NULL, score = NULL, reason_codes = NULL, risk_tier = NULL, "
                    + "max_exposure = NULL, model_version = NULL, reply_ms = ?, "
                    + "error_message = ?, request_json = ?, reply_json = ?, request_id = ?, last_attempt_on = ?, failure_count = ?, "
                    + "next_retry_on = ?, alerted_on = ? WHERE loan_id = ?", CreditDecisionLoanData.FAILED,
                    profile.getProfileId(), profile.getProfileName(), profile.getVersionNo(), username(), now, replyMs,
                    truncate(failure, MAX_NOTE), requestJson, replyJson, requestId, now, failures, retryOn, alert ? now : null, loanId);
            final LogEntryData failedEntry = entry(loan, profile, "AWAITING", null, null, null, replyMs,
                    retryOn == null ? "Send the request again" : "Retried automatically at " + retryOn.toLocalDateTime().withNano(0),
                    requestJson, replyJson);
            failedEntry.setRequestId(requestId);
            final Long logId = profiles.recordLog(failedEntry);
            event(loanId, CreditDecisionLoanData.EVENT_FAILED, truncate(failure, MAX_NOTE), null, null, logId);
            if (alert) {
                events.publishEvent(new CreditDecisionScoringFailed(loanId, loan.accountNo, truncate(failure, MAX_NOTE), failures,
                        !alertInstead));
            }
        }
        return status(loanId);
    }

    private static String newRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String payload(final String requestId, final LoanRow loan, final String profileLabel,
            final CreditDecisionRequestBuilder.Built built) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("applicationId", loan.accountNo);
        payload.put("customerId", loan.clientAccountNo);
        payload.put("product", loan.productName);
        payload.put("profile", profileLabel);
        payload.put("parameters", built.getParameters());
        return GSON.toJson(payload);
    }

    /** When the send that has just failed for the {@code failures}-th time in a row is tried again, or null when it is not. */
    static Timestamp nextRetry(final Timestamp from, final int failures) {
        if (failures < 1 || failures > RETRY_DELAY_MINUTES.length) {
            return null;
        }
        return Timestamp.valueOf(from.toLocalDateTime().plusMinutes(RETRY_DELAY_MINUTES[failures - 1]));
    }

    // ------------------------------------------------------------------ automatic start and rejection

    /**
     * Starts the decision without anyone pressing a button, as the process flow asks: every application whose product is in
     * an active decision profile is scored once its review is accepted. An officer who switched the decision off for the
     * application is respected, and so is an outcome that is already in. When the request cannot be sent, for example the
     * engine is down, it stays where an officer can see it and send it again.
     */
    public void autoStart(final Long loanId) {
        final LoanRow loan = loan(loanId);
        if (!reviewStageOpen(loan)) {
            return;
        }
        final ProductProfileData profile = profiles.profileForProduct(loan.productId);
        if (profile == null || "OFF".equals(profile.getMode()) || profile.getActiveVersionNo() == null) {
            return;
        }
        final DecisionRow row = decision(loanId);
        if (row != null && (!row.required || CreditDecisionLoanData.DECIDED.equals(row.requestStatus))) {
            return;
        }
        final Timestamp now = Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem());
        if (row == null) {
            jdbc.update("INSERT INTO m_loan_credit_decision (loan_id, required, flagged_by, flagged_on, verification_status, "
                    + "request_status) VALUES (?, TRUE, ?, ?, ?, ?)", loanId, username(), now,
                    CreditDecisionLoanData.PENDING_VERIFICATION, CreditDecisionLoanData.NOT_SENT);
            event(loanId, CreditDecisionLoanData.EVENT_FLAGGED, "Automatically, because '" + loan.productName + "' is in the decision profile '"
                    + profile.getProfileName() + "'", null, null, null);
        }
        if (row == null || !CreditDecisionLoanData.VERIFIED.equals(row.verificationStatus)) {
            final String note = "Verified automatically when the application review was accepted";
            jdbc.update("UPDATE m_loan_credit_decision SET verification_status = ?, verified_by = ?, verified_on = ?, verification_note = ? "
                    + "WHERE loan_id = ?", CreditDecisionLoanData.VERIFIED, username(), now, note, loanId);
            event(loanId, CreditDecisionLoanData.EVENT_VERIFIED, note, null, null, null);
        }
        try {
            send(loanId);
        } catch (final GeneralPlatformDomainRuleException e) {
            // the reason is already on the application's history, or is shown by its blockers
        }
    }

    /** Keeps on the history what became of the rejection an INELIGIBLE outcome asked for. */
    public void recordRejection(final Long loanId, final boolean rejected, final String detail) {
        event(loanId, rejected ? CreditDecisionLoanData.EVENT_REJECTED : CreditDecisionLoanData.EVENT_FAILED,
                detail == null ? null : truncate(detail, MAX_NOTE), CreditDecisionOutcome.INELIGIBLE, null, null);
    }

    // ------------------------------------------------------------------ failed scoring: retry and exception queue

    /**
     * Sends again the requests that failed and whose retry time has come. Run by the scheduled job. Each application is
     * handled on its own, so one that cannot be sent does not hold up the others. Returns how many were sent again.
     */
    public int retryDue() {
        final List<Long> due = jdbc.queryForList("SELECT d.loan_id FROM m_loan_credit_decision d WHERE d.required = TRUE AND d.request_status = ? "
                + "AND d.next_retry_on IS NOT NULL AND d.next_retry_on <= ? ORDER BY d.next_retry_on LIMIT " + MAX_EXCEPTIONS, Long.class,
                CreditDecisionLoanData.FAILED, now());
        int sent = 0;
        for (final Long loanId : due) {
            try {
                send(loanId);
                sent++;
            } catch (final GeneralPlatformDomainRuleException e) {
                if (!sendInProgress(e)) {
                    // the application moved on or can no longer be scored, so stop asking for it and leave the reason on its history
                    jdbc.update("UPDATE m_loan_credit_decision SET next_retry_on = NULL WHERE loan_id = ?", loanId);
                    event(loanId, CreditDecisionLoanData.EVENT_FAILED, truncate("Automatic retry stopped: " + e.getDefaultUserMessage(), MAX_NOTE),
                            null, null, null);
                }
            } catch (final RuntimeException e) {
                // keep going with the rest, this one is tried again at its next turn
                log.warn("Automatic retry of the credit decision for loan {} failed", loanId, e);
            }
        }
        return sent;
    }

    private static boolean sendInProgress(final GeneralPlatformDomainRuleException e) {
        return SEND_IN_PROGRESS_CODE.equals(e.getGlobalisationMessageCode());
    }

    /**
     * Applications waiting for approval whose scoring needs somebody: the engine did not give a usable reply, details are
     * missing, or a send began and never finished. The oldest come first.
     */
    @Transactional(readOnly = true)
    public List<ExceptionData> exceptions() {
        final Timestamp now = now();
        final Timestamp stale = staleBefore(profiles.connection(), now);
        return jdbc.query("SELECT d.loan_id, l.account_no, c.account_no AS client_account_no, p.name AS product_name, d.request_status, "
                + "d.error_message, d.failure_count, d.last_attempt_on, d.next_retry_on, d.alerted_on, d.request_id, d.send_started_on "
                + "FROM m_loan_credit_decision d JOIN m_loan l ON l.id = d.loan_id JOIN m_product_loan p ON p.id = l.product_id "
                + "LEFT JOIN m_client c ON c.id = l.client_id WHERE d.required = TRUE AND l.loan_status_id = ? AND (d.request_status = ? "
                + "OR (d.request_status = ? AND d.error_message IS NOT NULL) OR d.send_started_on < ?) "
                + "ORDER BY COALESCE(d.last_attempt_on, d.send_started_on) ASC, d.loan_id ASC LIMIT " + MAX_EXCEPTIONS,
                (rs, i) -> {
                    final Timestamp claimed = rs.getTimestamp("send_started_on");
                    final String status = rs.getString("request_status");
                    final String kind = claimed != null && claimed.before(stale) ? CreditDecisionLoanData.EXCEPTION_STUCK
                            : CreditDecisionLoanData.FAILED.equals(status) ? CreditDecisionLoanData.EXCEPTION_FAILED
                                    : CreditDecisionLoanData.EXCEPTION_HELD;
                    final String message = CreditDecisionLoanData.EXCEPTION_STUCK.equals(kind)
                            ? "A send began and never finished. It can be sent again." : rs.getString("error_message");
                    return ExceptionData.builder().loanId(rs.getLong("loan_id")).applicationRef(rs.getString("account_no"))
                            .customerRef(rs.getString("client_account_no")).productName(rs.getString("product_name")).kind(kind)
                            .message(message).failureCount(rs.getInt("failure_count")).lastAttemptOn(iso(rs.getTimestamp("last_attempt_on")))
                            .nextRetryOn(iso(rs.getTimestamp("next_retry_on"))).alertedOn(iso(rs.getTimestamp("alerted_on")))
                            .requestId(rs.getString("request_id")).build();
                },
                SUBMITTED_AND_PENDING_APPROVAL, CreditDecisionLoanData.FAILED, CreditDecisionLoanData.NOT_SENT, stale);
    }

    // ------------------------------------------------------------------ history

    /** What was done to this application's decision, newest first. */
    @Transactional(readOnly = true)
    public List<EventData> history(final Long loanId) {
        loan(loanId); // not found for an unknown loan
        return jdbc.query("SELECT id, event_type, actor, occurred_on, detail, decision, score, log_id FROM m_loan_credit_decision_event "
                + "WHERE loan_id = ? ORDER BY occurred_on DESC, id DESC",
                (rs, i) -> EventData.builder().id(rs.getLong("id")).eventType(rs.getString("event_type")).actor(rs.getString("actor"))
                        .occurredOn(iso(rs.getTimestamp("occurred_on"))).detail(rs.getString("detail")).decision(rs.getString("decision"))
                        .score(rs.getBigDecimal("score")).logId((Long) rs.getObject("log_id")).build(),
                loanId);
    }

    private void event(final Long loanId, final String type, final String detail, final String decision, final BigDecimal score,
            final Long logId) {
        jdbc.update("INSERT INTO m_loan_credit_decision_event (loan_id, event_type, actor, occurred_on, detail, decision, score, log_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", loanId, type, username(), Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()),
                detail, decision, score, logId);
    }

    // ------------------------------------------------------------------ approval

    /**
     * Refuses to approve an application that needs an automated credit decision until the engine has replied. Only a
     * profile in Live mode holds approval back. In Shadow mode the engine is consulted but nothing waits for it.
     */
    @Transactional(readOnly = true)
    public void assertOutcomeReceived(final Long loanId) {
        assertOutcomeReceived(loanId, "approved");
        assertNotIneligible(loanId, "approved");
    }

    /**
     * An INELIGIBLE outcome is the engine's last word in a Live profile: the application can only be rejected, so every step
     * that moves it forward is refused. Shadow mode never holds an application back.
     */
    @Transactional(readOnly = true)
    public void assertNotIneligible(final Long loanId, final String action) {
        final DecisionRow row = decision(loanId);
        if (row == null || !row.required || !CreditDecisionLoanData.DECIDED.equals(row.requestStatus)
                || !CreditDecisionOutcome.INELIGIBLE.equals(row.decision) || !live(loanId)) {
            return;
        }
        throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.ineligible",
                "The decision engine found this application INELIGIBLE, so it cannot be " + action + ". The application can only be rejected.");
    }

    /**
     * Prepare and sign contract is reached from Due Diligence when the engine said AUTO_APPROVE, because then no IC review is
     * needed. An IC_REVIEW_REQUIRED application reaches it through the IC levels, and an INELIGIBLE one never does.
     */
    @Transactional(readOnly = true)
    public void assertContractAllowed(final Long loanId, final String action) {
        final DecisionRow row = decision(loanId);
        if (row == null || !row.required || !live(loanId)) {
            return;
        }
        assertOutcomeReceived(loanId, action);
        assertNotIneligible(loanId, action);
        final LoanRow loan = loan(loanId);
        if (!contractOpen(row.decision, loan.decisionState)) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.ic.review.required",
                    "The decision engine returned IC_REVIEW_REQUIRED for this application, so it cannot be " + action
                            + " until it has been through IC review.");
        }
    }

    /** True when the engine's AUTO_APPROVE lets this application skip IC review. Shadow mode never skips anything. */
    @Transactional(readOnly = true)
    public boolean bypassesIcReview(final Long loanId) {
        final DecisionRow row = decision(loanId);
        return row != null && row.required && CreditDecisionLoanData.DECIDED.equals(row.requestStatus)
                && CreditDecisionOutcome.AUTO_APPROVE.equals(row.decision) && live(loanId);
    }

    /** The same hold, worded for the step that is being refused, for example "approved" or "moved to Due Diligence". */
    @Transactional(readOnly = true)
    public void assertOutcomeReceived(final Long loanId, final String action) {
        final List<Boolean> required = jdbc.queryForList(
                "SELECT required FROM m_loan_credit_decision WHERE loan_id = ? AND request_status <> ?", Boolean.class, loanId,
                CreditDecisionLoanData.DECIDED);
        if (required.isEmpty() || !Boolean.TRUE.equals(required.get(0))) {
            return;
        }
        final LoanDecisionData status = status(loanId);
        if (status.isBlocksApproval()) {
            final boolean verified = CreditDecisionLoanData.VERIFIED.equals(status.getVerificationStatus());
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.outcome.required",
                    "This application needs an automated credit decision before it can be " + action + ". "
                            + (verified ? "Send the request to the decision engine and wait for its reply."
                                    : "Verify the application details, then send the request to the decision engine."));
        }
    }

    // ------------------------------------------------------------------ rules the loan stages rely on

    /** Whether this application was set to need an automated credit decision. */
    @Transactional(readOnly = true)
    public boolean isRequired(final Long loanId) {
        final DecisionRow row = decision(loanId);
        return row != null && row.required;
    }

    /**
     * In a Live profile only an application the engine marked IC_REVIEW_REQUIRED goes through the IC levels. One it approved
     * goes straight to the contract, and one it found ineligible can only be rejected.
     */
    @Transactional(readOnly = true)
    public void assertIcReviewAllowed(final Long loanId) {
        final DecisionRow row = decision(loanId);
        if (row == null || !row.required || !live(loanId)) {
            return;
        }
        assertOutcomeReceived(loanId, "put through IC review");
        assertNotIneligible(loanId, "put through IC review");
        if (!CreditDecisionOutcome.IC_REVIEW_REQUIRED.equals(row.decision)) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.ic.review.not.allowed",
                    "IC review is not needed for this application because the decision engine returned " + row.decision
                            + ". It goes straight on to Prepare and sign contract.");
        }
    }

    /**
     * Once the engine has given its outcome, the application review cannot be returned: that would throw away the
     * review the outcome was given for. Re-open the verification first, which clears the outcome.
     */
    @Transactional(readOnly = true)
    public void assertReviewCanBeReturned(final Long loanId) {
        final DecisionRow row = decision(loanId);
        if (row != null && row.required && CreditDecisionLoanData.DECIDED.equals(row.requestStatus)) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.review.cannot.be.returned",
                    "The decision engine has already given its outcome for this application, so the application review cannot be "
                            + "returned. Re-open the verification first, which clears the outcome.");
        }
    }

    // ------------------------------------------------------------------ helpers

    private List<String> blockers(final LoanRow loan, final ProductProfileData profile, final DecisionRow row) {
        return blockers(loan, profile, row, false);
    }

    /** {@code ownClaim} is true while the caller holds the send claim itself, which then is not a reason to wait. */
    private List<String> blockers(final LoanRow loan, final ProductProfileData profile, final DecisionRow row, final boolean ownClaim) {
        final List<String> out = new ArrayList<>();
        if (loan.statusId != SUBMITTED_AND_PENDING_APPROVAL) {
            out.add(CreditDecisionLoanData.BLOCKER_LOAN_NOT_PENDING);
        } else if (!sendStageOpen(loan)) {
            out.add(CreditDecisionLoanData.BLOCKER_NOT_IN_REVIEW);
        }
        // an application that needs IC review can be asked again while still in review, AUTO_APPROVE and INELIGIBLE are final
        if (CreditDecisionLoanData.DECIDED.equals(row.requestStatus) && !CreditDecisionOutcome.IC_REVIEW_REQUIRED.equals(row.decision)) {
            out.add(CreditDecisionLoanData.BLOCKER_OUTCOME_FINAL);
        }
        if (!CreditDecisionLoanData.VERIFIED.equals(row.verificationStatus)) {
            out.add(CreditDecisionLoanData.BLOCKER_PENDING_VERIFICATION);
        }
        if (profile == null || "OFF".equals(profile.getMode())) {
            out.add(CreditDecisionLoanData.BLOCKER_PROFILE_OFF);
        } else if (profile.getActiveVersionNo() == null) {
            out.add(CreditDecisionLoanData.BLOCKER_NO_ACTIVE_VERSION);
        }
        final ConnectionData connection = profiles.connection();
        if (!connection.isEnabled() || connection.getEndpointUrl() == null || connection.getEndpointUrl().isBlank()) {
            out.add(CreditDecisionLoanData.BLOCKER_ENGINE_NOT_CONNECTED);
        }
        if (!ownClaim && row.sendStartedOn != null && row.sendStartedOn.after(staleBefore(connection, now()))) {
            out.add(CreditDecisionLoanData.BLOCKER_SEND_IN_PROGRESS);
        }
        return out;
    }

    static String describe(final String blocker) {
        switch (blocker) {
            case CreditDecisionLoanData.BLOCKER_NOT_REQUIRED:
                return "This application is not flagged for an automated credit decision.";
            case CreditDecisionLoanData.BLOCKER_LOAN_NOT_PENDING:
                return "The request can only be sent while the application is waiting for approval.";
            case CreditDecisionLoanData.BLOCKER_NOT_IN_REVIEW:
                return "The request can only be sent while the application is in the Review Application stage.";
            case CreditDecisionLoanData.BLOCKER_OUTCOME_FINAL:
                return "The decision engine has given a final outcome for this application. Only IC_REVIEW_REQUIRED can be sent again.";
            case CreditDecisionLoanData.BLOCKER_PENDING_VERIFICATION:
                return "Verify the application details before sending the request.";
            case CreditDecisionLoanData.BLOCKER_PROFILE_OFF:
                return "The decision profile for this product is switched off.";
            case CreditDecisionLoanData.BLOCKER_NO_ACTIVE_VERSION:
                return "The decision profile for this product has no approved version yet.";
            case CreditDecisionLoanData.BLOCKER_ENGINE_NOT_CONNECTED:
                return "The decision engine connection is switched off or has no address.";
            case CreditDecisionLoanData.BLOCKER_SEND_IN_PROGRESS:
                return "A request for this application is already being sent to the decision engine. Wait for its reply.";
            default:
                return "The request cannot be sent right now.";
        }
    }

    private static String label(final String key) {
        final CatalogueItem item = CreditDecisionCatalogue.find(key);
        return item == null ? key : item.getLabel();
    }

    private static LogEntryData entry(final LoanRow loan, final ActiveProfileData profile, final String status,
            final String decision, final BigDecimal score, final String reasonCodes, final Integer replyMs, final String nextStep,
            final String requestJson, final String replyJson) {
        final LogEntryData entry = new LogEntryData();
        entry.setApplicationRef(loan.accountNo);
        entry.setLoanId(loan.id);
        entry.setCustomerRef(loan.clientAccountNo);
        entry.setProfileId(profile.getProfileId());
        entry.setProfileName(profile.getProfileName());
        entry.setVersionNo(profile.getVersionNo());
        entry.setMode(profile.getMode());
        entry.setProvider(profile.getProvider());
        entry.setStatus(status);
        entry.setDecision(decision);
        entry.setScore(score);
        entry.setReasonCodes(reasonCodes);
        entry.setReplyMs(replyMs);
        entry.setNextStep(nextStep);
        entry.setRequestJson(requestJson);
        entry.setReplyJson(replyJson);
        return entry;
    }

    /** Waits a little before asking again. Returns false when the thread was interrupted. */
    private static boolean pause(final int attempt) {
        try {
            Thread.sleep(RETRY_PAUSE_MILLIS * attempt);
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void requirePendingApproval(final LoanRow loan) {
        if (loan.statusId != SUBMITTED_AND_PENDING_APPROVAL) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.not.pending",
                    "This can only be changed while the application is waiting for approval.");
        }
    }

    /** The flag can be set from the day the application is made until Review Application is done. */
    private void requireFlagStageOpen(final LoanRow loan) {
        requirePendingApproval(loan);
        if (!flagStageOpen(loan)) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.review.stage.over",
                    "This can no longer be changed because the application is past the Review Application stage.");
        }
    }

    /** Verifying, re-opening and sending belong to the Review Application stage. */
    private void requireReviewStage(final LoanRow loan) {
        requirePendingApproval(loan);
        if (!reviewStageOpen(loan)) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.not.in.review",
                    "The automated credit decision is handled while the application is in the Review Application stage. "
                            + (loan.decisionState == null ? "Accept the application review first."
                                    : "This application is already past that stage."));
        }
    }

    private boolean outcomeGiven(final Long loanId) {
        final DecisionRow row = decision(loanId);
        return row != null && CreditDecisionLoanData.DECIDED.equals(row.requestStatus);
    }

    private boolean flagStageOpen(final LoanRow loan) {
        return loan.statusId == SUBMITTED_AND_PENDING_APPROVAL
                && (!extendedLifecycle() || loan.decisionState == null || loan.decisionState == REVIEW_APPLICATION);
    }

    /** The request is sent from the Review Application stage, because the outcome decides which stage comes next. */
    private boolean sendStageOpen(final LoanRow loan) {
        return reviewStageOpen(loan);
    }

    private boolean reviewStageOpen(final LoanRow loan) {
        return loan.statusId == SUBMITTED_AND_PENDING_APPROVAL
                && (!extendedLifecycle() || Integer.valueOf(REVIEW_APPLICATION).equals(loan.decisionState));
    }

    private boolean extendedLifecycle() {
        final List<Boolean> rows = jdbc.queryForList("SELECT enabled FROM c_configuration WHERE name = ?", Boolean.class, EXTENDED_LIFECYCLE);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    private void requireFlagged(final Long loanId) {
        final DecisionRow row = decision(loanId);
        if (row == null || !row.required) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.loan.not.flagged",
                    "This application is not flagged for an automated credit decision.");
        }
    }

    private static Timestamp now() {
        return Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem());
    }

    private String username() {
        return context.authenticatedUser().getUsername();
    }

    private static String truncate(final String text, final int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String iso(final Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().toString();
    }

    private LoanRow loan(final Long loanId) {
        final List<LoanRow> rows = jdbc.query(
                "SELECT l.id, l.account_no, l.product_id, l.loan_status_id, l.loan_decision_state, p.name AS product_name, "
                        + "c.account_no AS client_account_no "
                        + "FROM m_loan l JOIN m_product_loan p ON p.id = l.product_id LEFT JOIN m_client c ON c.id = l.client_id "
                        + "WHERE l.id = ?",
                (rs, i) -> new LoanRow(rs.getLong("id"), rs.getString("account_no"), rs.getLong("product_id"), rs.getInt("loan_status_id"),
                        (Integer) rs.getObject("loan_decision_state"), rs.getString("product_name"), rs.getString("client_account_no")),
                loanId);
        if (rows.isEmpty()) {
            throw new LoanNotFoundException(loanId);
        }
        return rows.get(0);
    }

    private boolean live(final ProductProfileData profile) {
        return profile != null && "LIVE".equals(profile.getMode());
    }

    private boolean live(final Long loanId) {
        return live(profiles.profileForProduct(loan(loanId).productId));
    }

    /** From Due Diligence only AUTO_APPROVE reaches the contract, and past it anything the IC levels let through. */
    private static boolean contractOpen(final String decision, final Integer decisionState) {
        if (CreditDecisionOutcome.AUTO_APPROVE.equals(decision)) {
            return true;
        }
        return CreditDecisionOutcome.IC_REVIEW_REQUIRED.equals(decision) && decisionState != null && decisionState > DUE_DILIGENCE;
    }

    private static String nextStep(final String decision, final boolean live) {
        if (!live) {
            return "Recorded only: the profile is in Shadow mode";
        }
        switch (decision) {
            case CreditDecisionOutcome.AUTO_APPROVE:
                return "Skips IC review and goes to Prepare and sign contract";
            case CreditDecisionOutcome.INELIGIBLE:
                return "The application is rejected";
            default:
                return "Goes to IC review";
        }
    }

    private DecisionRow decision(final Long loanId) {
        final List<DecisionRow> rows = jdbc.query("SELECT * FROM m_loan_credit_decision WHERE loan_id = ?",
                (rs, i) -> new DecisionRow(rs.getBoolean("required"), rs.getString("verification_status"), rs.getString("verified_by"),
                        iso(rs.getTimestamp("verified_on")), rs.getString("verification_note"), rs.getString("request_status"),
                        rs.getString("requested_by"), iso(rs.getTimestamp("requested_on")), rs.getString("decision"),
                        rs.getBigDecimal("score"), rs.getString("reason_codes"), rs.getString("risk_tier"), rs.getBigDecimal("max_exposure"),
                        rs.getString("model_version"), (Integer) rs.getObject("reply_ms"),
                        rs.getString("error_message"), rs.getString("profile_name"), (Integer) rs.getObject("version_no"),
                        rs.getString("request_json"), rs.getString("reply_json"), rs.getString("request_id"), rs.getInt("failure_count"),
                        rs.getTimestamp("send_started_on"), rs.getTimestamp("next_retry_on"), rs.getTimestamp("alerted_on")),
                loanId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static final class LoanRow {

        final long id;
        final String accountNo;
        final long productId;
        final int statusId;
        final Integer decisionState;
        final String productName;
        final String clientAccountNo;

        LoanRow(final long id, final String accountNo, final long productId, final int statusId, final Integer decisionState,
                final String productName, final String clientAccountNo) {
            this.id = id;
            this.accountNo = accountNo;
            this.productId = productId;
            this.statusId = statusId;
            this.decisionState = decisionState;
            this.productName = productName;
            this.clientAccountNo = clientAccountNo;
        }
    }

    private static final class DecisionRow {

        final boolean required;
        final String verificationStatus;
        final String verifiedBy;
        final String verifiedOn;
        final String verificationNote;
        final String requestStatus;
        final String requestedBy;
        final String requestedOn;
        final String decision;
        final BigDecimal score;
        final String reasonCodes;
        final String riskTier;
        final BigDecimal maxExposure;
        final String modelVersion;
        final Integer replyMs;
        final String errorMessage;
        final String profileName;
        final Integer versionNo;
        final String requestJson;
        final String replyJson;
        final String requestId;
        final int failureCount;
        final Timestamp sendStartedOn;
        final Timestamp nextRetryOn;
        final Timestamp alertedOn;

        DecisionRow(final boolean required, final String verificationStatus, final String verifiedBy, final String verifiedOn,
                final String verificationNote, final String requestStatus, final String requestedBy, final String requestedOn,
                final String decision, final BigDecimal score, final String reasonCodes, final String riskTier,
                final BigDecimal maxExposure, final String modelVersion, final Integer replyMs, final String errorMessage, final String profileName, final Integer versionNo, final String requestJson,
                final String replyJson, final String requestId, final int failureCount, final Timestamp sendStartedOn,
                final Timestamp nextRetryOn, final Timestamp alertedOn) {
            this.requestId = requestId;
            this.failureCount = failureCount;
            this.sendStartedOn = sendStartedOn;
            this.nextRetryOn = nextRetryOn;
            this.alertedOn = alertedOn;
            this.required = required;
            this.verificationStatus = verificationStatus;
            this.verifiedBy = verifiedBy;
            this.verifiedOn = verifiedOn;
            this.verificationNote = verificationNote;
            this.requestStatus = requestStatus;
            this.requestedBy = requestedBy;
            this.requestedOn = requestedOn;
            this.decision = decision;
            this.score = score;
            this.reasonCodes = reasonCodes;
            this.riskTier = riskTier;
            this.maxExposure = maxExposure;
            this.modelVersion = modelVersion;
            this.replyMs = replyMs;
            this.errorMessage = errorMessage;
            this.profileName = profileName;
            this.versionNo = versionNo;
            this.requestJson = requestJson;
            this.replyJson = replyJson;
        }
    }
}
