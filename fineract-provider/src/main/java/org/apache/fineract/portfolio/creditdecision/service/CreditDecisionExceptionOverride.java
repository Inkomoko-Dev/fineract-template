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

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData.LoanDecisionData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maker-Checker resolution of exception-queue items (CGLT-849 FR-02): REEVALUATE or MANUAL_IC.
 */
@Service
@RequiredArgsConstructor
public class CreditDecisionExceptionOverride {

    public static final String ACTION_REEVALUATE = "REEVALUATE";
    public static final String ACTION_MANUAL_IC = "MANUAL_IC";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    private static final Set<String> ACTIONS = Set.of(ACTION_REEVALUATE, ACTION_MANUAL_IC);

    private final JdbcTemplate jdbc;
    private final PlatformSecurityContext context;
    private final CreditDecisionLoanService loans;

    @Getter
    @Builder
    public static class OverrideData {
        private final Long id;
        private final Long loanId;
        private final String action;
        private final String status;
        private final String note;
        private final String requestedBy;
        private final String requestedOn;
        private final String decidedBy;
        private final String decidedOn;
        private final String decisionNote;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RequestBody {
        private String action;
        private String note;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DecideBody {
        private String note;
    }

    @Transactional(readOnly = true)
    public OverrideData pendingForLoan(final Long loanId) {
        final List<OverrideData> rows = jdbc.query(
                "SELECT id, loan_id, action, status, note, requested_by, requested_on, decided_by, decided_on, decision_note "
                        + "FROM m_credit_decision_exception_override WHERE loan_id = ? AND status = ? ORDER BY id DESC LIMIT 1",
                (rs, i) -> map(rs), loanId, STATUS_PENDING);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public OverrideData request(final Long loanId, final RequestBody body) {
        final Map<String, String> problems = new LinkedHashMap<>();
        final String action = body.getAction() == null ? "" : body.getAction().trim().toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            problems.put("action", "Action must be REEVALUATE or MANUAL_IC");
        }
        final String note = body.getNote() == null ? null : body.getNote().trim();
        if (note != null && note.length() > 500) {
            problems.put("note", "The note can be at most 500 characters");
        }
        CreditDecisionRules.throwIfAny(problems);
        loans.status(loanId); // not-found for unknown loan
        requireOnExceptionQueue(loanId);
        if (pendingForLoan(loanId) != null) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.exception.override.pending",
                    "This application already has a pending exception override waiting for a supervisor.");
        }
        final Timestamp now = Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem());
        final String user = context.authenticatedUser().getUsername();
        jdbc.update("INSERT INTO m_credit_decision_exception_override (loan_id, action, status, note, requested_by, requested_on) "
                + "VALUES (?, ?, ?, ?, ?, ?)", loanId, action, STATUS_PENDING, note == null || note.isEmpty() ? null : note, user, now);
        loans.recordOverrideEvent(loanId, "Exception override requested: " + action + (note == null || note.isEmpty() ? "" : " — " + note));
        return pendingForLoan(loanId);
    }

    @Transactional
    public LoanDecisionData approve(final Long overrideId, final DecideBody body) {
        final OverrideData pending = requirePending(overrideId);
        final String checker = context.authenticatedUser().getUsername();
        if (checker.equalsIgnoreCase(pending.getRequestedBy())) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.exception.override.same.user",
                    "The person who requested the override cannot approve it.");
        }
        requireOnExceptionQueue(pending.getLoanId());
        final String note = body == null || body.getNote() == null ? null : body.getNote().trim();
        decide(overrideId, STATUS_APPROVED, checker, note);
        loans.recordOverrideEvent(pending.getLoanId(), "Exception override approved: " + pending.getAction()
                + (note == null || note.isEmpty() ? "" : " — " + note));
        if (ACTION_REEVALUATE.equals(pending.getAction())) {
            return loans.send(pending.getLoanId());
        }
        loans.forceManualIc(pending.getLoanId(), note);
        return loans.status(pending.getLoanId());
    }

    private void requireOnExceptionQueue(final Long loanId) {
        if (!loans.isOnExceptionQueue(loanId)) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.exception.override.not.eligible",
                    "Exception overrides can only be requested or approved for applications on the exception queue.");
        }
    }

    @Transactional
    public OverrideData cancel(final Long overrideId, final DecideBody body) {
        final OverrideData pending = requirePending(overrideId);
        final String user = context.authenticatedUser().getUsername();
        final boolean maker = user.equalsIgnoreCase(pending.getRequestedBy());
        if (!maker) {
            context.authenticatedUser().validateHasPermissionTo("APPROVE_CREDITDECISIONEXCEPTIONOVERRIDE");
        }
        final String note = body == null || body.getNote() == null ? null : body.getNote().trim();
        decide(overrideId, STATUS_CANCELLED, user, note);
        loans.recordOverrideEvent(pending.getLoanId(), "Exception override cancelled"
                + (note == null || note.isEmpty() ? "" : ": " + note));
        return byId(overrideId);
    }

    private OverrideData requirePending(final Long id) {
        final OverrideData row = byId(id);
        if (row == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.exception.override.not.found",
                    "Exception override " + id + " was not found");
        }
        if (!STATUS_PENDING.equals(row.getStatus())) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.exception.override.closed",
                    "This exception override has already been decided.");
        }
        return row;
    }

    private void decide(final Long id, final String status, final String user, final String note) {
        jdbc.update("UPDATE m_credit_decision_exception_override SET status = ?, decided_by = ?, decided_on = ?, decision_note = ? WHERE id = ?",
                status, user, Timestamp.valueOf(DateUtils.getLocalDateTimeOfSystem()),
                note == null || note.isEmpty() ? null : note, id);
    }

    private OverrideData byId(final Long id) {
        final List<OverrideData> rows = jdbc.query(
                "SELECT id, loan_id, action, status, note, requested_by, requested_on, decided_by, decided_on, decision_note "
                        + "FROM m_credit_decision_exception_override WHERE id = ?",
                (rs, i) -> map(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static OverrideData map(final java.sql.ResultSet rs) throws java.sql.SQLException {
        return OverrideData.builder().id(rs.getLong("id")).loanId(rs.getLong("loan_id")).action(rs.getString("action"))
                .status(rs.getString("status")).note(rs.getString("note")).requestedBy(rs.getString("requested_by"))
                .requestedOn(rs.getTimestamp("requested_on").toLocalDateTime().toString())
                .decidedBy(rs.getString("decided_by"))
                .decidedOn(rs.getTimestamp("decided_on") == null ? null : rs.getTimestamp("decided_on").toLocalDateTime().toString())
                .decisionNote(rs.getString("decision_note")).build();
    }
}
