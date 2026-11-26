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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies penalty already stored on a non-reversed repayment to the closed installment it was mapped to.
 *
 * <p>
 * The repayment, its portions, and its journal stay as posted. This service does not call
 * {@code reprocessTransactions()}. For each supplied loan it:
 *
 * <ol>
 * <li>Selects installments that are complete, have penalty paid of zero, and still have penalty outstanding.
 * <li>Sums penalty on non-reversed repayment mappings for that installment.
 * <li>Applies that sum to active unpaid penalty charges for the installment, oldest due date first, capped at the
 * charge outstanding. A charge is placed by {@code m_loan_overdue_installment_charge}, or, when it has no link, on the
 * installment whose due date is on or before the charge date.
 * <li>Rebuilds schedule penalty columns from the active penalty charges.
 * <li>Leaves the installment complete when its penalty outstanding is then zero, and clears {@code completed_derived}
 * and {@code obligations_met_on_date} when a remainder is still outstanding.
 * <li>Rewrites the loan penalty summary and total outstanding from the schedule, then refreshes arrears for an active
 * loan using the same rule as the arrears job: incomplete installments due before the business date minus grace.
 * </ol>
 *
 * A closed installment with no positive mapped penalty is left unchanged. Loans absent from the supplied list are not
 * read.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClosedInstallmentPenaltyBalanceService {

    public static final int MAX_LOAN_IDS = 200;
    public static final String LOAN_IDS_PARAM = "loanIds";

    private static final String INSTALLMENTS_SQL = "select id, duedate,"
            + " coalesce(recalculated_interest_component, 0) as recalculated_interest_component,"
            + " coalesce(completed_derived, 0) as completed_derived," + " coalesce(penalty_charges_completed_derived, 0) as penalty_paid,"
            + " coalesce(penalty_charges_amount, 0) - coalesce(penalty_charges_completed_derived, 0)"
            + " - coalesce(penalty_charges_waived_derived, 0) - coalesce(penalty_charges_writtenoff_derived, 0)"
            + " as penalty_outstanding from m_loan_repayment_schedule where loan_id = ? order by duedate, installment, id";

    private static final String MAPPED_PENALTY_SQL = "select m.loan_repayment_schedule_id as schedule_id,"
            + " coalesce(sum(m.penalty_charges_portion_derived), 0) as mapped_penalty"
            + " from m_loan_transaction_repayment_schedule_mapping m" + " join m_loan_transaction t on t.id = m.loan_transaction_id"
            + " where t.loan_id = ? and t.is_reversed = 0 and t.transaction_type_enum = ?"
            + " and coalesce(m.penalty_charges_portion_derived, 0) > 0 group by m.loan_repayment_schedule_id";

    private static final String CHARGES_SQL = "select lc.id, lc.due_for_collection_as_of_date as due_date,"
            + " coalesce(lc.amount, 0) as amount, coalesce(lc.amount_paid_derived, 0) as amount_paid,"
            + " coalesce(lc.amount_waived_derived, 0) as amount_waived,"
            + " coalesce(lc.amount_writtenoff_derived, 0) as amount_writtenoff, coalesce(lc.waived, 0) as waived,"
            + " oic.loan_schedule_id as schedule_id from m_loan_charge lc"
            + " left join m_loan_overdue_installment_charge oic on oic.loan_charge_id = lc.id"
            + " where lc.loan_id = ? and lc.is_penalty = 1 and lc.is_active = 1"
            + " order by lc.due_for_collection_as_of_date, lc.id, oic.id";

    private static final String ARREARS_SQL = "select"
            + " coalesce(sum(coalesce(principal_amount, 0) - coalesce(principal_completed_derived, 0)"
            + " - coalesce(principal_writtenoff_derived, 0)), 0) as principal_overdue,"
            + " coalesce(sum(coalesce(interest_amount, 0) - coalesce(interest_completed_derived, 0)"
            + " - coalesce(interest_waived_derived, 0) - coalesce(interest_writtenoff_derived, 0)), 0) as interest_overdue,"
            + " coalesce(sum(coalesce(fee_charges_amount, 0) - coalesce(fee_charges_completed_derived, 0)"
            + " - coalesce(fee_charges_waived_derived, 0) - coalesce(fee_charges_writtenoff_derived, 0)), 0) as fee_overdue,"
            + " coalesce(sum(coalesce(penalty_charges_amount, 0) - coalesce(penalty_charges_completed_derived, 0)"
            + " - coalesce(penalty_charges_waived_derived, 0) - coalesce(penalty_charges_writtenoff_derived, 0)), 0)"
            + " as penalty_overdue, min(duedate) as overdue_since from m_loan_repayment_schedule"
            + " where loan_id = ? and completed_derived = 0 and duedate < ?";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public static List<Long> readLoanIds(final JsonElement commandJson) {
        if (commandJson == null || !commandJson.isJsonObject()) {
            throw loanIdsRequired();
        }
        final JsonObject object = commandJson.getAsJsonObject();
        for (final String key : object.keySet()) {
            if (!LOAN_IDS_PARAM.equals(key)) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.stored.penalty.unexpected.parameter",
                        "Only loanIds is accepted.");
            }
        }
        if (!object.has(LOAN_IDS_PARAM) || !object.get(LOAN_IDS_PARAM).isJsonArray()) {
            throw loanIdsRequired();
        }
        final JsonArray loanIds = object.getAsJsonArray(LOAN_IDS_PARAM);
        if (loanIds.size() == 0) {
            throw loanIdsRequired();
        }
        if (loanIds.size() > MAX_LOAN_IDS) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.stored.penalty.loanIds.too.many",
                    "At most " + MAX_LOAN_IDS + " loan ids can be repaired in one request.", MAX_LOAN_IDS);
        }
        final LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (final JsonElement element : loanIds) {
            uniqueIds.add(readLoanId(element));
        }
        return List.copyOf(uniqueIds);
    }

    public CommandProcessingResult applyStoredPenalty(final List<Long> loanIds, final LocalDate businessDate) {
        final List<Long> requested = validatedLoanIds(loanIds);
        if (businessDate == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.stored.penalty.business.date.required",
                    "A business date is required to refresh arrears.");
        }
        final TransactionTemplate transaction = requiresNewTransaction();
        final List<Map<String, Object>> results = new ArrayList<>();
        for (final Long loanId : requested) {
            results.add(repairOne(transaction, loanId, businessDate));
        }
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("loanCount", requested.size());
        changes.put("results", results);
        return new CommandProcessingResultBuilder().with(changes).build();
    }

    private Map<String, Object> repairOne(final TransactionTemplate transaction, final Long loanId, final LocalDate businessDate) {
        try {
            return transaction.execute(status -> repairLoan(loanId, businessDate));
        } catch (final RuntimeException ex) {
            log.warn("Stored installment penalty repair failed for loan {}", loanId, ex);
            final Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("loanId", loanId);
            failed.put("status", "failed");
            return failed;
        }
    }

    private Map<String, Object> repairLoan(final Long loanId, final LocalDate businessDate) {
        final LoanHeader loan = loadLoan(loanId);
        if (loan == null) {
            return statusRow(loanId, "notFound");
        }
        final List<ScheduleRow> installments = loadInstallments(loanId);
        final Map<Long, BigDecimal> mappedPenalty = loadMappedPenalty(loanId);
        final List<ScheduleRow> qualifying = new ArrayList<>();
        for (final ScheduleRow installment : installments) {
            final BigDecimal mapped = mappedPenalty.get(installment.id);
            if (installment.isClosedUnpaidPenalty() && mapped != null && mapped.signum() > 0) {
                qualifying.add(installment);
            }
        }
        if (qualifying.isEmpty()) {
            return statusRow(loanId, "unchanged");
        }
        final List<ChargeRow> charges = loadCharges(loanId);
        final Allocation allocation = allocate(qualifying, charges, installments, mappedPenalty);
        if (allocation.applied.signum() <= 0) {
            return statusRow(loanId, "unchanged");
        }
        writeCharges(loanId, allocation.changed);
        final Map<Long, PenaltyBucket> buckets = rebuildSchedulePenalties(loanId, charges, installments);
        final int reopened = reopenShortfalls(loanId, qualifying, buckets);
        final BigDecimal penaltyOutstanding = writeLoanSummary(loanId);
        if (loan.statusId == LoanStatus.ACTIVE.getValue()) {
            refreshArrears(loanId, loan.graceDays, businessDate);
        }
        final Map<String, Object> applied = statusRow(loanId, "applied");
        applied.put("penaltyApplied", allocation.applied);
        applied.put("installmentsReopened", reopened);
        applied.put("penaltyOutstanding", penaltyOutstanding);
        applied.put("totalOutstanding", readTotalOutstanding(loanId));
        return applied;
    }

    private Allocation allocate(final List<ScheduleRow> qualifying, final List<ChargeRow> charges, final List<ScheduleRow> installments,
            final Map<Long, BigDecimal> mappedPenalty) {
        final Comparator<ChargeRow> oldestFirst = Comparator.comparing(ChargeRow::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(charge -> charge.id);
        BigDecimal applied = BigDecimal.ZERO;
        final List<ChargeRow> changed = new ArrayList<>();
        for (final ScheduleRow installment : qualifying) {
            BigDecimal remaining = mappedPenalty.get(installment.id);
            final List<ChargeRow> mine = new ArrayList<>();
            for (final ChargeRow charge : charges) {
                if (!charge.waived && place(charge, installments) == installment.id) {
                    mine.add(charge);
                }
            }
            mine.sort(oldestFirst);
            for (final ChargeRow charge : mine) {
                if (remaining.signum() <= 0) {
                    break;
                }
                final BigDecimal outstanding = charge.outstanding();
                if (outstanding.signum() <= 0) {
                    continue;
                }
                final BigDecimal payment = remaining.min(outstanding);
                charge.paid = charge.paid.add(payment);
                remaining = remaining.subtract(payment);
                applied = applied.add(payment);
                changed.add(charge);
            }
        }
        return new Allocation(applied, changed);
    }

    private static long place(final ChargeRow charge, final List<ScheduleRow> installments) {
        if (charge.linkedScheduleId != null) {
            for (final ScheduleRow installment : installments) {
                if (installment.id == charge.linkedScheduleId) {
                    return installment.id;
                }
            }
        }
        ScheduleRow onOrBefore = null;
        ScheduleRow after = null;
        ScheduleRow lastReal = null;
        for (final ScheduleRow installment : installments) {
            if (installment.recalculatedInterest) {
                continue;
            }
            if (installment.dueDate == null) {
                continue;
            }
            lastReal = installment;
            if (charge.dueDate == null) {
                continue;
            }
            if (!installment.dueDate.isAfter(charge.dueDate)) {
                onOrBefore = installment;
            } else if (after == null) {
                after = installment;
            }
        }
        if (onOrBefore != null) {
            return onOrBefore.id;
        }
        if (after != null) {
            return after.id;
        }
        return lastReal == null ? -1L : lastReal.id;
    }

    private void writeCharges(final Long loanId, final List<ChargeRow> changed) {
        this.jdbcTemplate
                .batchUpdate("update m_loan_charge set amount_paid_derived = ?, amount_outstanding_derived = ?, is_paid_derived = ?"
                        + " where id = ? and loan_id = ?", changed, changed.size(), (ps, charge) -> {
                            final BigDecimal outstanding = charge.outstanding();
                            ps.setBigDecimal(1, charge.paid);
                            ps.setBigDecimal(2, outstanding);
                            ps.setInt(3, outstanding.signum() == 0 && charge.paid.signum() > 0 ? 1 : 0);
                            ps.setLong(4, charge.id);
                            ps.setLong(5, loanId);
                        });
    }

    private Map<Long, PenaltyBucket> rebuildSchedulePenalties(final Long loanId, final List<ChargeRow> charges,
            final List<ScheduleRow> installments) {
        final Map<Long, PenaltyBucket> buckets = new LinkedHashMap<>();
        for (final ChargeRow charge : charges) {
            final long scheduleId = place(charge, installments);
            if (scheduleId < 0) {
                continue;
            }
            buckets.computeIfAbsent(scheduleId, ignored -> new PenaltyBucket()).add(charge);
        }
        this.jdbcTemplate.update("update m_loan_repayment_schedule set penalty_charges_amount = 0,"
                + " penalty_charges_completed_derived = 0, penalty_charges_waived_derived = 0,"
                + " penalty_charges_writtenoff_derived = 0 where loan_id = ?", loanId);
        if (!buckets.isEmpty()) {
            final List<Map.Entry<Long, PenaltyBucket>> rows = new ArrayList<>(buckets.entrySet());
            this.jdbcTemplate.batchUpdate(
                    "update m_loan_repayment_schedule set penalty_charges_amount = ?, penalty_charges_completed_derived = ?,"
                            + " penalty_charges_waived_derived = ?, penalty_charges_writtenoff_derived = ? where id = ? and loan_id = ?",
                    rows, rows.size(), (ps, entry) -> {
                        final PenaltyBucket bucket = entry.getValue();
                        ps.setBigDecimal(1, bucket.amount);
                        ps.setBigDecimal(2, bucket.paid);
                        ps.setBigDecimal(3, bucket.waived);
                        ps.setBigDecimal(4, bucket.writtenOff);
                        ps.setLong(5, entry.getKey());
                        ps.setLong(6, loanId);
                    });
        }
        return buckets;
    }

    private int reopenShortfalls(final Long loanId, final List<ScheduleRow> qualifying, final Map<Long, PenaltyBucket> buckets) {
        int reopened = 0;
        for (final ScheduleRow installment : qualifying) {
            final PenaltyBucket bucket = buckets.get(installment.id);
            final BigDecimal outstanding = bucket == null ? BigDecimal.ZERO : bucket.outstanding();
            if (outstanding.signum() > 0) {
                this.jdbcTemplate.update("update m_loan_repayment_schedule set completed_derived = 0, obligations_met_on_date = null"
                        + " where id = ? and loan_id = ?", installment.id, loanId);
                reopened++;
            }
        }
        return reopened;
    }

    private BigDecimal writeLoanSummary(final Long loanId) {
        final BigDecimal[] sums = this.jdbcTemplate.query("select coalesce(sum(penalty_charges_amount), 0) as charged,"
                + " coalesce(sum(penalty_charges_completed_derived), 0) as paid,"
                + " coalesce(sum(penalty_charges_waived_derived), 0) as waived,"
                + " coalesce(sum(penalty_charges_writtenoff_derived), 0) as writtenoff"
                + " from m_loan_repayment_schedule where loan_id = ?", rs -> {
                    rs.next();
                    return new BigDecimal[] { money(rs.getBigDecimal("charged")), money(rs.getBigDecimal("paid")),
                            money(rs.getBigDecimal("waived")), money(rs.getBigDecimal("writtenoff")) };
                }, loanId);
        final BigDecimal charged = sums[0];
        final BigDecimal paid = sums[1];
        final BigDecimal waived = sums[2];
        final BigDecimal writtenOff = sums[3];
        final BigDecimal outstanding = charged.subtract(paid).subtract(waived).subtract(writtenOff).max(BigDecimal.ZERO);
        this.jdbcTemplate.update("update m_loan set penalty_charges_charged_derived = ?, penalty_charges_repaid_derived = ?,"
                + " penalty_charges_waived_derived = ?, penalty_charges_writtenoff_derived = ?, penalty_charges_outstanding_derived = ?,"
                + " total_waived_derived = coalesce(interest_waived_derived, 0) + coalesce(fee_charges_waived_derived, 0) + ?,"
                + " total_expected_repayment_derived = coalesce(principal_disbursed_derived, 0) + coalesce(interest_charged_derived, 0)"
                + " + coalesce(fee_charges_charged_derived, 0) + ?,"
                + " total_expected_costofloan_derived = coalesce(interest_charged_derived, 0) + coalesce(fee_charges_charged_derived, 0) + ?,"
                + " total_repayment_derived = coalesce(principal_repaid_derived, 0) + coalesce(interest_repaid_derived, 0)"
                + " + coalesce(fee_charges_repaid_derived, 0) + ?,"
                + " total_outstanding_derived = coalesce(principal_outstanding_derived, 0) + coalesce(interest_outstanding_derived, 0)"
                + " + coalesce(fee_charges_outstanding_derived, 0) + ? where id = ?", charged, paid, waived, writtenOff, outstanding,
                waived, charged, charged, paid, outstanding, loanId);
        return outstanding;
    }

    private void refreshArrears(final Long loanId, final int graceDays, final LocalDate businessDate) {
        final Date cutoff = Date.valueOf(businessDate.minusDays(Math.max(graceDays, 0)));
        final Overdue overdue = this.jdbcTemplate.query(ARREARS_SQL, rs -> {
            rs.next();
            return new Overdue(money(rs.getBigDecimal("principal_overdue")), money(rs.getBigDecimal("interest_overdue")),
                    money(rs.getBigDecimal("fee_overdue")), money(rs.getBigDecimal("penalty_overdue")),
                    toLocalDate(rs.getDate("overdue_since")));
        }, loanId, cutoff);
        final BigDecimal total = overdue.principal.add(overdue.interest).add(overdue.fee).add(overdue.penalty);
        this.jdbcTemplate.update("delete from m_loan_arrears_aging where loan_id = ?", loanId);
        if (total.signum() > 0 && overdue.since != null) {
            this.jdbcTemplate.update(
                    "insert into m_loan_arrears_aging (loan_id, principal_overdue_derived, interest_overdue_derived,"
                            + " fee_charges_overdue_derived, penalty_charges_overdue_derived, total_overdue_derived,"
                            + " overdue_since_date_derived) values (?,?,?,?,?,?,?)",
                    loanId, overdue.principal, overdue.interest, overdue.fee, overdue.penalty, total, Date.valueOf(overdue.since));
        }
    }

    private BigDecimal readTotalOutstanding(final Long loanId) {
        return this.jdbcTemplate.queryForObject("select coalesce(total_outstanding_derived, 0) from m_loan where id = ?", BigDecimal.class,
                loanId);
    }

    private LoanHeader loadLoan(final Long loanId) {
        final List<LoanHeader> rows = this.jdbcTemplate.query("select loan_status_id, grace_on_arrears_ageing from m_loan where id = ?",
                (rs, rowNum) -> {
                    final int grace = rs.getObject("grace_on_arrears_ageing") == null ? 0 : rs.getInt("grace_on_arrears_ageing");
                    return new LoanHeader(rs.getInt("loan_status_id"), grace);
                }, loanId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<ScheduleRow> loadInstallments(final Long loanId) {
        return this.jdbcTemplate.query(INSTALLMENTS_SQL, scheduleRowMapper(), loanId);
    }

    private Map<Long, BigDecimal> loadMappedPenalty(final Long loanId) {
        final Map<Long, BigDecimal> mapped = new HashMap<>();
        this.jdbcTemplate.query(MAPPED_PENALTY_SQL, rs -> {
            mapped.put(rs.getLong("schedule_id"), money(rs.getBigDecimal("mapped_penalty")));
        }, loanId, LoanTransactionType.REPAYMENT.getValue());
        return mapped;
    }

    private List<ChargeRow> loadCharges(final Long loanId) {
        final Map<Long, ChargeRow> charges = new LinkedHashMap<>();
        this.jdbcTemplate.query(CHARGES_SQL, rs -> {
            final long chargeId = rs.getLong("id");
            if (!charges.containsKey(chargeId)) {
                final long linkedRaw = rs.getLong("schedule_id");
                final Long linked = rs.wasNull() ? null : linkedRaw;
                charges.put(chargeId,
                        new ChargeRow(chargeId, toLocalDate(rs.getDate("due_date")), money(rs.getBigDecimal("amount")),
                                money(rs.getBigDecimal("amount_paid")), money(rs.getBigDecimal("amount_waived")),
                                money(rs.getBigDecimal("amount_writtenoff")), rs.getInt("waived") != 0, linked));
            }
        }, loanId);
        return new ArrayList<>(charges.values());
    }

    private TransactionTemplate requiresNewTransaction() {
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static List<Long> validatedLoanIds(final List<Long> loanIds) {
        if (loanIds == null || loanIds.isEmpty()) {
            throw loanIdsRequired();
        }
        if (loanIds.size() > MAX_LOAN_IDS) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.stored.penalty.loanIds.too.many",
                    "At most " + MAX_LOAN_IDS + " loan ids can be repaired in one request.", MAX_LOAN_IDS);
        }
        final LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (final Long loanId : loanIds) {
            if (loanId == null || loanId <= 0) {
                throw invalidLoanId();
            }
            uniqueIds.add(loanId);
        }
        return List.copyOf(uniqueIds);
    }

    private static long readLoanId(final JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            throw invalidLoanId();
        }
        final JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw invalidLoanId();
        }
        final String raw = primitive.getAsString();
        if (!raw.chars().allMatch(Character::isDigit)) {
            throw invalidLoanId();
        }
        try {
            final long loanId = Long.parseLong(raw);
            if (loanId <= 0) {
                throw invalidLoanId();
            }
            return loanId;
        } catch (final NumberFormatException ex) {
            throw invalidLoanId();
        }
    }

    private static RowMapper<ScheduleRow> scheduleRowMapper() {
        return (rs, rowNum) -> new ScheduleRow(rs.getLong("id"), toLocalDate(rs.getDate("duedate")),
                rs.getInt("recalculated_interest_component") != 0, rs.getInt("completed_derived") != 0,
                money(rs.getBigDecimal("penalty_paid")), money(rs.getBigDecimal("penalty_outstanding")));
    }

    private static Map<String, Object> statusRow(final Long loanId, final String status) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("loanId", loanId);
        row.put("status", status);
        return row;
    }

    private static GeneralPlatformDomainRuleException loanIdsRequired() {
        return new GeneralPlatformDomainRuleException("error.msg.loan.stored.penalty.loanIds.required", "loanIds is required.");
    }

    private static GeneralPlatformDomainRuleException invalidLoanId() {
        return new GeneralPlatformDomainRuleException("error.msg.loan.stored.penalty.loanIds.invalid",
                "Each loanIds entry must be a positive whole number.");
    }

    private static BigDecimal money(final Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(value.toString());
    }

    private static LocalDate toLocalDate(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return new Date(((java.util.Date) value).getTime()).toLocalDate();
        }
        return null;
    }

    private static final class LoanHeader {

        private final int statusId;
        private final int graceDays;

        private LoanHeader(final int statusId, final int graceDays) {
            this.statusId = statusId;
            this.graceDays = graceDays;
        }
    }

    private static final class ScheduleRow {

        private final long id;
        private final LocalDate dueDate;
        private final boolean recalculatedInterest;
        private final boolean completed;
        private final BigDecimal penaltyPaid;
        private final BigDecimal penaltyOutstanding;

        private ScheduleRow(final long id, final LocalDate dueDate, final boolean recalculatedInterest, final boolean completed,
                final BigDecimal penaltyPaid, final BigDecimal penaltyOutstanding) {
            this.id = id;
            this.dueDate = dueDate;
            this.recalculatedInterest = recalculatedInterest;
            this.completed = completed;
            this.penaltyPaid = penaltyPaid;
            this.penaltyOutstanding = penaltyOutstanding;
        }

        private boolean isClosedUnpaidPenalty() {
            return !this.recalculatedInterest && this.completed && this.penaltyPaid.signum() == 0 && this.penaltyOutstanding.signum() > 0;
        }
    }

    private static final class ChargeRow {

        private final long id;
        private final LocalDate dueDate;
        private final BigDecimal amount;
        private BigDecimal paid;
        private final BigDecimal waivedAmount;
        private final BigDecimal writtenOff;
        private final boolean waived;
        private final Long linkedScheduleId;

        private ChargeRow(final long id, final LocalDate dueDate, final BigDecimal amount, final BigDecimal paid,
                final BigDecimal waivedAmount, final BigDecimal writtenOff, final boolean waived, final Long linkedScheduleId) {
            this.id = id;
            this.dueDate = dueDate;
            this.amount = amount;
            this.paid = paid;
            this.waivedAmount = waivedAmount;
            this.writtenOff = writtenOff;
            this.waived = waived;
            this.linkedScheduleId = linkedScheduleId;
        }

        private LocalDate dueDate() {
            return this.dueDate;
        }

        private BigDecimal outstanding() {
            return this.amount.subtract(this.paid).subtract(this.waivedAmount).subtract(this.writtenOff).max(BigDecimal.ZERO);
        }
    }

    private static final class PenaltyBucket {

        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal paid = BigDecimal.ZERO;
        private BigDecimal waived = BigDecimal.ZERO;
        private BigDecimal writtenOff = BigDecimal.ZERO;

        private void add(final ChargeRow charge) {
            this.amount = this.amount.add(charge.amount);
            this.paid = this.paid.add(charge.paid);
            this.waived = this.waived.add(charge.waivedAmount);
            this.writtenOff = this.writtenOff.add(charge.writtenOff);
        }

        private BigDecimal outstanding() {
            return this.amount.subtract(this.paid).subtract(this.waived).subtract(this.writtenOff).max(BigDecimal.ZERO);
        }
    }

    private static final class Overdue {

        private final BigDecimal principal;
        private final BigDecimal interest;
        private final BigDecimal fee;
        private final BigDecimal penalty;
        private final LocalDate since;

        private Overdue(final BigDecimal principal, final BigDecimal interest, final BigDecimal fee, final BigDecimal penalty,
                final LocalDate since) {
            this.principal = principal;
            this.interest = interest;
            this.fee = fee;
            this.penalty = penalty;
            this.since = since;
        }
    }

    private static final class Allocation {

        private final BigDecimal applied;
        private final List<ChargeRow> changed;

        private Allocation(final BigDecimal applied, final List<ChargeRow> changed) {
            this.applied = applied;
            this.changed = changed;
        }
    }
}
