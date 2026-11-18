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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Late-fee repair in two steps: (1) quietly remove daily/flat late fees that should not have gone to prod;
 * (2) gap-fill missing original Overdue Fees using the Sep-2026 / {@code inkomoko-prod-bkp21092026} rules from
 * {@code applyChargeToOverdueLoanInstallment} (per incomplete installment, fee frequency/interval, maturity cap,
 * max_occurrence, % of installment outstanding), then sync charged/paid/outstanding. Independent of
 * {@link LoanDailyLateFeeService}.
 */
@Service
@RequiredArgsConstructor
public class LoanLateFeeRectifyService {

    private static final int CHARGE_CALC_PERCENT_OF_AMOUNT = ChargeCalculationType.PERCENT_OF_AMOUNT.getValue();
    private static final int OVERDUE_INSTALLMENT = ChargeTimeType.OVERDUE_INSTALLMENT.getValue();
    private static final int SPECIFIED_DUE_DATE = ChargeTimeType.SPECIFIED_DUE_DATE.getValue();
    /** Prefix for {@code m_loan_charge.external_id} on gap-filled monthlies (unique per loan+date). */
    static final String GAP_FILL_EXTERNAL_ID_PREFIX = "CGLT-852-GAPFILL:";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final ConfigurationDomainService configurationDomainService;
    private final JournalEntryWritePlatformService journalEntryWritePlatformService;
    private final LoanAssembler loanAssembler;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanArrearsAgingService loanArrearsAgingService;

    public CommandProcessingResult rectifyLateFeesForLoan(final Long loanId) {
        final LocalDate effectiveDate = DateUtils.getBusinessLocalDate();
        final RectifyContext ctx = loadContext(loanId, effectiveDate);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("loanId", loanId);

        int removedDaily = 0;
        for (final Long loanChargeId : ctx.dailyChargeIds) {
            if (removeDailyChargeQuietly(ctx, loanChargeId)) {
                removedDaily++;
            }
        }

        final BigDecimal orphanPaidToPrincipal = newRequiresNew().execute(status -> {
            this.jdbcTemplate.update("update m_loan_daily_late_fee set is_active = 0 where loan_id = ? and is_active = 1", loanId);
            final BigDecimal moved = reallocateOrphanPenaltyPaidToPrincipal(loanId);
            syncPenaltiesFromActiveCharges(loanId);
            refreshLoanPrincipalSummary(loanId);
            return moved;
        });

        final int posted = postMissingMonthlyPenalties(ctx);
        final Map<String, BigDecimal> penaltyTotals = refreshSummaryAndArrears(loanId);

        changes.put("removedDailyCharges", removedDaily);
        changes.put("postedMonthlyCharges", posted);
        changes.put("orphanPenaltyPaidMovedToPrincipal", orphanPaidToPrincipal);
        changes.put("penaltyCharged", penaltyTotals.get("charged"));
        changes.put("penaltyPaid", penaltyTotals.get("paid"));
        changes.put("penaltyOutstanding", penaltyTotals.get("outstanding"));
        changes.put("chargeDefinitionId", ctx.chargeDefinitionId);
        changes.put("chargeName", ctx.chargeName);
        changes.put("penaltyPercentFromCharge", ctx.monthlyRate);
        changes.put("feeIntervalMonths", ctx.monthInterval);
        changes.put("penaltyGraceDays", ctx.gracePeriod);
        changes.put("arrearsRefreshed", true);
        changes.put("recomputed", true);
        return new CommandProcessingResultBuilder().withLoanId(loanId).with(changes).build();
    }

    private RectifyContext loadContext(final Long loanId, final LocalDate effectiveDate) {
        return newRequiresNew().execute(status -> {
            final List<Map<String, Object>> loanRows = this.jdbcTemplate.queryForList(
                    "select l.id, l.loan_status_id, coalesce(l.office_id, c.office_id) as office_id, l.product_id,"
                            + " l.currency_code, l.currency_digits, l.currency_multiplesof,"
                            + " coalesce(l.interest_charged_derived, 0) as total_interest_charged,"
                            + " coalesce(l.principal_disbursed_derived, 0) as principal_disbursed,"
                            + " l.expected_maturedon_date,"
                            + " lp.accounting_type, l.fund_id,"
                            + " c.status_enum as client_status, g.status_enum as group_status"
                            + " from m_loan l"
                            + " left join m_client c on c.id = l.client_id"
                            + " left join m_group g on g.id = l.group_id"
                            + " join m_product_loan lp on lp.id = l.product_id"
                            + " where l.id = ?",
                    loanId);
            if (loanRows.isEmpty()) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.late.fee.rectify.not.found",
                        "Loan not found for late-fee rectify.", loanId);
            }
            final Map<String, Object> loan = loanRows.get(0);
            if (loan.get("client_status") != null && ((Number) loan.get("client_status")).intValue() != 300) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.late.fee.rectify.client.not.active",
                        "Client is not active.", loanId);
            }
            if (loan.get("group_status") != null && ((Number) loan.get("group_status")).intValue() != 300) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.late.fee.rectify.group.not.active",
                        "Group is not active.", loanId);
            }
            if (((Number) loan.get("loan_status_id")).intValue() != LoanStatus.ACTIVE.getValue()) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.late.fee.rectify.not.active",
                        "Late fees can only be rectified on an active loan.", loanId);
            }

            // Percentage, frequency and interval always come from the product overdue charge (e.g. Late Fee RW) —
            // never hard-coded. Gap-fill only runs when this definition exists and the loan is past penalty grace.
            final List<Map<String, Object>> chargeDefs = this.jdbcTemplate.queryForList(
                    "select c.id, c.name, c.amount, c.fee_interval, c.fee_frequency, c.charge_calculation_enum,"
                            + " c.max_occurrence"
                            + " from m_product_loan_charge plc"
                            + " join m_charge c on c.id = plc.charge_id"
                            + " where plc.product_loan_id = ? and c.is_active = 1 and c.is_penalty = 1"
                            + " and c.charge_time_enum = ? and c.charge_calculation_enum = ?",
                    loan.get("product_id"), OVERDUE_INSTALLMENT, ChargeCalculationType.PERCENT_OF_AMOUNT.getValue());
            if (chargeDefs.isEmpty()) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.late.fee.rectify.no.charge.definition",
                        "The loan product has no active overdue penalty configured as a percentage of principal.", loanId);
            }
            if (chargeDefs.size() > 1) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.late.fee.rectify.multiple.charge.definitions",
                        "The loan product has more than one overdue percentage penalty; rectify cannot choose one.", loanId);
            }
            final Map<String, Object> chargeDef = chargeDefs.get(0);
            final Long chargeDefinitionId = ((Number) chargeDef.get("id")).longValue();
            final BigDecimal monthlyRate = toBigDecimal(chargeDef.get("amount"));
            if (monthlyRate == null || monthlyRate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.late.fee.rectify.invalid.charge.amount",
                        "The overdue penalty charge has no usable percentage amount configured.", loanId);
            }
            final int monthInterval = monthlyPostingInterval(chargeDef);
            final Integer feeFrequency = chargeDef.get("fee_frequency") == null ? null
                    : ((Number) chargeDef.get("fee_frequency")).intValue();
            final Integer feeInterval = chargeDef.get("fee_interval") == null ? null
                    : ((Number) chargeDef.get("fee_interval")).intValue();
            final Integer maxOccurrence = chargeDef.get("max_occurrence") == null ? null
                    : ((Number) chargeDef.get("max_occurrence")).intValue();

            final long gracePeriod = this.configurationDomainService.retrievePenaltyWaitPeriod();
            final long penaltyPostingWaitPeriod = this.configurationDomainService.retrieveGraceOnPenaltyPostingPeriod();
            final LocalDate expectedMaturity = toLocalDate(loan.get("expected_maturedon_date"));
            final LocalDate earliestDue = this.jdbcTemplate.query(
                    "select min(rs.duedate) as earliest_due from m_loan_repayment_schedule rs"
                            + " where rs.loan_id = ? and rs.recalculated_interest_component = 0"
                            + " and coalesce(rs.principal_amount, 0) > 0",
                    rs -> rs.next() ? JdbcSupport.getLocalDate(rs, "earliest_due") : null, loanId);
            // Same first posting anchor as applyChargeToOverdueLoanInstallment when waits are zero: installment due date.
            long diff = gracePeriod + 1 - penaltyPostingWaitPeriod;
            if (diff < 1) {
                diff = 1L;
            }
            final LocalDate firstPenaltyDate = earliestDue == null ? null
                    : earliestDue.plusDays((int) gracePeriod + 1).minusDays(diff);

            // Remove every flat late-fee row (daily and the flat “monthly” posts from the daily late-fee job).
            // Keep percentage-of-amount rows — those are the original monthly overdue penalties.
            final List<Map<String, Object>> activeCharges = this.jdbcTemplate.queryForList(
                    "select lc.id, lc.charge_calculation_enum, lc.due_for_collection_as_of_date as due_date"
                            + " from m_loan_charge lc where lc.loan_id = ? and lc.charge_id = ?"
                            + " and lc.is_active = 1 and lc.waived = 0 order by lc.due_for_collection_as_of_date, lc.id",
                    loanId, chargeDefinitionId);

            final List<Long> dailyChargeIds = new ArrayList<>();
            for (final Map<String, Object> charge : activeCharges) {
                final int calc = ((Number) charge.get("charge_calculation_enum")).intValue();
                if (!ChargeCalculationType.fromInt(calc).isPercentageOfAmount()) {
                    dailyChargeIds.add(((Number) charge.get("id")).longValue());
                }
            }

            final String chargeName = chargeDef.get("name") == null ? null : String.valueOf(chargeDef.get("name"));
            return new RectifyContext(loanId, effectiveDate, loan, chargeDefinitionId, chargeName, monthlyRate, monthInterval,
                    feeFrequency, feeInterval, maxOccurrence, gracePeriod, penaltyPostingWaitPeriod, expectedMaturity,
                    firstPenaltyDate, dailyChargeIds);
        });
    }

    /**
     * Deactivate a daily late-fee charge without inserting WAIVE_CHARGES (avoids Accrual+Waive pairs per day). Linked
     * accrual transactions are soft-reversed. Paid amounts stay on the charge row until
     * {@link #reallocateOrphanPenaltyPaidToPrincipal(Long)} moves them onto principal (no repayment replay).
     */
    private boolean removeDailyChargeQuietly(final RectifyContext ctx, final Long loanChargeId) {
        return Boolean.TRUE.equals(newRequiresNew().execute(status -> {
            final List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
                    "select lc.id, lc.is_active, lc.waived"
                            + " from m_loan_charge lc where lc.id = ? and lc.loan_id = ?",
                    loanChargeId, ctx.loanId);
            if (rows.isEmpty()) {
                return false;
            }
            final Map<String, Object> row = rows.get(0);
            if (!flag(row.get("is_active")) || flag(row.get("waived"))) {
                return false;
            }

            this.jdbcTemplate.update(
                    "update m_loan_charge set is_active = 0, amount_outstanding_derived = 0 where id = ?", loanChargeId);
            deleteChargeChildren(loanChargeId);
            reverseAccrualsForCharge(loanChargeId);
            return true;
        }));
    }

    private void reverseAccrualsForCharge(final Long loanChargeId) {
        final List<Long> accrualIds = this.jdbcTemplate.queryForList(
                "select distinct t.id from m_loan_transaction t"
                        + " join m_loan_charge_paid_by cpb on cpb.loan_transaction_id = t.id"
                        + " where cpb.loan_charge_id = ? and t.is_reversed = 0 and t.transaction_type_enum = ?",
                Long.class, loanChargeId, LoanTransactionType.ACCRUAL.getValue());
        for (final Long txnId : accrualIds) {
            this.jdbcTemplate.update("update m_loan_transaction set is_reversed = 1 where id = ?", txnId);
        }
    }

    private void deleteChargeChildren(final Long loanChargeId) {
        this.jdbcTemplate.update("delete from m_loan_overdue_installment_charge where loan_charge_id = ?", loanChargeId);
        this.jdbcTemplate.update("delete from m_loan_installment_charge where loan_charge_id = ?", loanChargeId);
    }

    /**
     * Map a charge/posting date onto the overdue installment (due on or before that date), not the next one.
     */
    private Long findScheduleId(final Long loanId, final LocalDate dueDate) {
        if (dueDate == null) {
            return null;
        }
        final List<Long> scheduleIds = this.jdbcTemplate.queryForList(
                "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                        + " and duedate <= ? order by duedate desc, installment desc limit 1",
                Long.class, loanId, dueDate);
        if (!scheduleIds.isEmpty()) {
            return scheduleIds.get(0);
        }
        final List<Long> fallback = this.jdbcTemplate.queryForList(
                "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                        + " and duedate >= ? order by duedate, installment limit 1",
                Long.class, loanId, dueDate);
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    /**
     * Single source of truth: active penalty charges. Normalize each charge so
     * {@code amount = paid + waived + writtenOff + outstanding}, zero all schedule penalty columns (including
     * recalculated-interest rows), push charge figures onto real installments, then set loan summary penalty Original /
     * Paid / Outstanding from the same charge sums so Charges, Schedule and Summary tally.
     */
    private Map<String, BigDecimal> syncPenaltiesFromActiveCharges(final Long loanId) {
        normalizeActivePenaltyCharges(loanId);
        rebuildSchedulePenaltiesFromActiveCharges(loanId);
        return writeLoanPenaltySummaryFromActiveCharges(loanId);
    }

    private void normalizeActivePenaltyCharges(final Long loanId) {
        this.jdbcTemplate.update(
                "update m_loan_charge set"
                        + " amount_paid_derived = least(coalesce(amount_paid_derived, 0), coalesce(amount, 0)),"
                        + " amount_waived_derived = least(coalesce(amount_waived_derived, 0),"
                        + "   greatest(0, coalesce(amount, 0) - coalesce(amount_paid_derived, 0))),"
                        + " amount_writtenoff_derived = least(coalesce(amount_writtenoff_derived, 0),"
                        + "   greatest(0, coalesce(amount, 0) - coalesce(amount_paid_derived, 0) - coalesce(amount_waived_derived, 0))),"
                        + " amount_outstanding_derived = greatest(0, coalesce(amount, 0) - coalesce(amount_paid_derived, 0)"
                        + "   - coalesce(amount_waived_derived, 0) - coalesce(amount_writtenoff_derived, 0)),"
                        + " is_paid_derived = case"
                        + "   when coalesce(amount, 0) > 0 and coalesce(amount_outstanding_derived, 0) = 0"
                        + "        and coalesce(amount_paid_derived, 0) > 0 then 1 else 0 end"
                        + " where loan_id = ? and is_penalty = 1 and is_active = 1 and waived = 0",
                loanId);
        // Second pass: recompute outstanding after paid/waived/writtenoff caps (MySQL evaluates SET left-to-right).
        this.jdbcTemplate.update(
                "update m_loan_charge set"
                        + " amount_outstanding_derived = greatest(0, coalesce(amount, 0) - coalesce(amount_paid_derived, 0)"
                        + "   - coalesce(amount_waived_derived, 0) - coalesce(amount_writtenoff_derived, 0)),"
                        + " is_paid_derived = case"
                        + "   when coalesce(amount, 0) > 0"
                        + "        and greatest(0, coalesce(amount, 0) - coalesce(amount_paid_derived, 0)"
                        + "          - coalesce(amount_waived_derived, 0) - coalesce(amount_writtenoff_derived, 0)) = 0"
                        + "        and coalesce(amount_paid_derived, 0) > 0 then 1 else 0 end"
                        + " where loan_id = ? and is_penalty = 1 and is_active = 1 and waived = 0",
                loanId);
    }

    private void rebuildSchedulePenaltiesFromActiveCharges(final Long loanId) {
        // Clear every schedule row — including recalculated-interest / post-maturity penalty-only installments.
        // Leaving those uncleared made Schedule Penalties (e.g. +30,000.06) exceed Summary Original from charges.
        this.jdbcTemplate.update(
                "update m_loan_repayment_schedule set penalty_charges_amount = 0,"
                        + " penalty_charges_completed_derived = 0, penalty_charges_waived_derived = 0,"
                        + " penalty_charges_writtenoff_derived = 0"
                        + " where loan_id = ?",
                loanId);

        final List<Map<String, Object>> activePenalties = this.jdbcTemplate.queryForList(
                "select coalesce(lc.amount, 0) as amount,"
                        + " coalesce(lc.amount_paid_derived, 0) as amount_paid,"
                        + " coalesce(lc.amount_waived_derived, 0) as amount_waived,"
                        + " coalesce(lc.amount_writtenoff_derived, 0) as amount_writtenoff,"
                        + " lc.due_for_collection_as_of_date as due_date"
                        + " from m_loan_charge lc"
                        + " where lc.loan_id = ? and lc.is_penalty = 1 and lc.is_active = 1 and lc.waived = 0",
                loanId);

        for (final Map<String, Object> charge : activePenalties) {
            Long scheduleId = findScheduleId(loanId, toLocalDate(charge.get("due_date")));
            if (scheduleId == null) {
                scheduleId = findLastScheduleId(loanId);
            }
            if (scheduleId == null) {
                continue;
            }
            final BigDecimal amount = nullToZero(toBigDecimal(charge.get("amount")));
            final BigDecimal paid = nullToZero(toBigDecimal(charge.get("amount_paid")));
            final BigDecimal waived = nullToZero(toBigDecimal(charge.get("amount_waived")));
            final BigDecimal writtenOff = nullToZero(toBigDecimal(charge.get("amount_writtenoff")));
            this.jdbcTemplate.update(
                    "update m_loan_repayment_schedule set"
                            + " penalty_charges_amount = coalesce(penalty_charges_amount, 0) + ?,"
                            + " penalty_charges_completed_derived = coalesce(penalty_charges_completed_derived, 0) + ?,"
                            + " penalty_charges_waived_derived = coalesce(penalty_charges_waived_derived, 0) + ?,"
                            + " penalty_charges_writtenoff_derived = coalesce(penalty_charges_writtenoff_derived, 0) + ?"
                            + " where id = ?",
                    amount, paid, waived, writtenOff, scheduleId);
        }
    }

    private Long findLastScheduleId(final Long loanId) {
        final List<Long> ids = this.jdbcTemplate.queryForList(
                "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                        + " order by duedate desc, installment desc limit 1",
                Long.class, loanId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Write loan summary penalty columns from active charges (not only schedule), so Summary always matches Charges.
     */
    private Map<String, BigDecimal> writeLoanPenaltySummaryFromActiveCharges(final Long loanId) {
        final Map<String, Object> sums = this.jdbcTemplate.queryForMap(
                "select coalesce(sum(lc.amount), 0) as charged,"
                        + " coalesce(sum(lc.amount_paid_derived), 0) as paid,"
                        + " coalesce(sum(lc.amount_waived_derived), 0) as waived,"
                        + " coalesce(sum(lc.amount_writtenoff_derived), 0) as writtenoff,"
                        + " coalesce(sum(lc.amount_outstanding_derived), 0) as outstanding"
                        + " from m_loan_charge lc"
                        + " where lc.loan_id = ? and lc.is_penalty = 1 and lc.is_active = 1 and lc.waived = 0",
                loanId);
        final BigDecimal charged = nullToZero(toBigDecimal(sums.get("charged")));
        final BigDecimal paid = nullToZero(toBigDecimal(sums.get("paid")));
        final BigDecimal waived = nullToZero(toBigDecimal(sums.get("waived")));
        final BigDecimal writtenOff = nullToZero(toBigDecimal(sums.get("writtenoff")));
        final BigDecimal outstanding = nullToZero(toBigDecimal(sums.get("outstanding")));

        this.jdbcTemplate.update(
                "update m_loan set"
                        + " penalty_charges_charged_derived = ?,"
                        + " penalty_charges_repaid_derived = ?,"
                        + " penalty_charges_waived_derived = ?,"
                        + " penalty_charges_writtenoff_derived = ?,"
                        + " penalty_charges_outstanding_derived = ?,"
                        + " total_waived_derived = coalesce(interest_waived_derived, 0) + coalesce(fee_charges_waived_derived, 0) + ?,"
                        + " total_expected_repayment_derived = coalesce(principal_disbursed_derived, 0)"
                        + "   + coalesce(interest_charged_derived, 0) + coalesce(fee_charges_charged_derived, 0) + ?,"
                        + " total_expected_costofloan_derived = coalesce(interest_charged_derived, 0)"
                        + "   + coalesce(fee_charges_charged_derived, 0) + ?,"
                        + " total_repayment_derived = coalesce(principal_repaid_derived, 0) + coalesce(interest_repaid_derived, 0)"
                        + "   + coalesce(fee_charges_repaid_derived, 0) + ?,"
                        + " total_outstanding_derived = coalesce(principal_outstanding_derived, 0)"
                        + "   + coalesce(interest_outstanding_derived, 0) + coalesce(fee_charges_outstanding_derived, 0) + ?"
                        + " where id = ?",
                charged, paid, waived, writtenOff, outstanding, waived, charged, charged, paid, outstanding, loanId);

        final Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("charged", charged);
        totals.put("paid", paid);
        totals.put("waived", waived);
        totals.put("writtenOff", writtenOff);
        totals.put("outstanding", outstanding);
        return totals;
    }

    /**
     * Money already collected against removed (inactive) daily penalties must not disappear from Total Paid. Move it
     * onto principal completed on the schedule and clear {@code amount_paid_derived} on those inactive rows so a
     * second run is idempotent. Does not reverse or rewrite repayment transactions.
     */
    private BigDecimal reallocateOrphanPenaltyPaidToPrincipal(final Long loanId) {
        final List<Map<String, Object>> orphans = this.jdbcTemplate.queryForList(
                "select lc.id, coalesce(lc.amount_paid_derived, 0) as amount_paid"
                        + " from m_loan_charge lc"
                        + " where lc.loan_id = ? and lc.is_penalty = 1 and lc.is_active = 0"
                        + " and coalesce(lc.amount_paid_derived, 0) > 0",
                loanId);
        BigDecimal moved = BigDecimal.ZERO;
        for (final Map<String, Object> orphan : orphans) {
            final BigDecimal paid = toBigDecimal(orphan.get("amount_paid"));
            if (paid == null || paid.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            applyPaymentToPrincipalSchedule(loanId, paid);
            this.jdbcTemplate.update("update m_loan_charge set amount_paid_derived = 0 where id = ?", orphan.get("id"));
            moved = moved.add(paid);
        }
        return moved;
    }

    private void applyPaymentToPrincipalSchedule(final Long loanId, final BigDecimal amount) {
        BigDecimal remaining = amount;
        final List<Map<String, Object>> installments = this.jdbcTemplate.queryForList(
                "select rs.id,"
                        + " greatest(coalesce(rs.principal_amount, 0) - coalesce(rs.principal_completed_derived, 0)"
                        + " - coalesce(rs.principal_writtenoff_derived, 0), 0) as outstanding_principal"
                        + " from m_loan_repayment_schedule rs"
                        + " where rs.loan_id = ? and rs.recalculated_interest_component = 0"
                        + " order by rs.duedate, rs.installment",
                loanId);
        for (final Map<String, Object> installment : installments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            final BigDecimal outstanding = toBigDecimal(installment.get("outstanding_principal"));
            if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            final BigDecimal apply = remaining.min(outstanding);
            this.jdbcTemplate.update(
                    "update m_loan_repayment_schedule set"
                            + " principal_completed_derived = coalesce(principal_completed_derived, 0) + ?"
                            + " where id = ?",
                    apply, installment.get("id"));
            remaining = remaining.subtract(apply);
        }
    }

    private void refreshLoanPrincipalSummary(final Long loanId) {
        this.jdbcTemplate.update("update m_loan l set "
                + "l.principal_repaid_derived = (select coalesce(sum(rs.principal_completed_derived), 0)"
                + " from m_loan_repayment_schedule rs where rs.loan_id = l.id), "
                + "l.principal_outstanding_derived = greatest(0, coalesce(l.principal_disbursed_derived, 0)"
                + " - (select coalesce(sum(rs.principal_completed_derived), 0) from m_loan_repayment_schedule rs"
                + " where rs.loan_id = l.id)"
                + " - coalesce(l.principal_writtenoff_derived, 0))"
                + " where l.id = ?", loanId);
        this.jdbcTemplate.update("update m_loan l set "
                + "l.total_repayment_derived ="
                + " coalesce(l.principal_repaid_derived, 0) + coalesce(l.interest_repaid_derived, 0)"
                + " + coalesce(l.fee_charges_repaid_derived, 0) + coalesce(l.penalty_charges_repaid_derived, 0),"
                + "l.total_outstanding_derived ="
                + " coalesce(l.principal_outstanding_derived, 0) + coalesce(l.interest_outstanding_derived, 0)"
                + " + coalesce(l.fee_charges_outstanding_derived, 0) + coalesce(l.penalty_charges_outstanding_derived, 0)"
                + " where l.id = ?", loanId);
    }

    /**
     * Final pass: sync penalties from active charges (Charges = Summary), refresh principal/total, then arrears.
     * Skips {@code updateLoanSummaryDerivedFields()} so JPA cannot overwrite the charge-based penalty totals.
     */
    private Map<String, BigDecimal> refreshSummaryAndArrears(final Long loanId) {
        return newRequiresNew().execute(status -> {
            final Map<String, BigDecimal> totals = syncPenaltiesFromActiveCharges(loanId);
            refreshLoanPrincipalSummary(loanId);
            final Loan loan = this.loanAssembler.assembleFrom(loanId);
            if (loan.isOpen() && loan.repaymentScheduleDetail().isInterestRecalculationEnabled()
                    && loan.loanProduct().isArrearsBasedOnOriginalSchedule()) {
                this.loanArrearsAgingService.updateLoanArrearsAgeingDetailsWithOriginalSchedule(loan);
            } else {
                this.loanArrearsAgingService.updateLoanArrearsAgeingDetails(loan);
            }
            return totals;
        });
    }

    /**
     * Gap-fill missing Overdue Fees using {@code inkomoko-prod-bkp21092026}
     * {@code applyChargeToOverdueLoanInstallment}: incomplete installments past penalty wait, fee frequency/interval,
     * one occurrence before maturity and recurring through the business date on/after maturity while unpaid, optional
     * max_occurrence, % of installment outstanding principal. Clears prior incorrect specified-due-date / gap-fill rows
     * first. Closed loans were never charged by that job ({@code loan_status_id = 300} only).
     */
    private int postMissingMonthlyPenalties(final RectifyContext ctx) {
        if (ctx.firstPenaltyDate == null || ctx.effectiveDate.isBefore(ctx.firstPenaltyDate)) {
            return 0;
        }
        return newRequiresNew().execute(status -> {
            deactivateIncorrectPriorGapFillCharges(ctx);

            int posted = 0;
            final int scale = ((Number) ctx.loanRow.get("currency_digits")).intValue();
            final BigDecimal cap = (BigDecimal) ctx.loanRow.get("principal_disbursed");
            BigDecimal cumulative = this.jdbcTemplate.queryForObject(
                    "select coalesce(sum(lc.amount), 0) from m_loan_charge lc"
                            + " where lc.loan_id = ? and lc.is_active = 1 and lc.is_penalty = 1",
                    BigDecimal.class, ctx.loanId);
            if (cumulative == null) {
                cumulative = BigDecimal.ZERO;
            }

            // Same population as the classic overdue job: incomplete schedule rows past penalty wait.
            final List<Map<String, Object>> installments = this.jdbcTemplate.queryForList(
                    "select rs.id as schedule_id, rs.installment, rs.duedate,"
                            + " coalesce(rs.principal_amount, 0) as principal_amount,"
                            + " greatest(coalesce(rs.principal_amount, 0) - coalesce(rs.principal_completed_derived, 0)"
                            + " - coalesce(rs.principal_writtenoff_derived, 0), 0) as outstanding_principal"
                            + " from m_loan_repayment_schedule rs"
                            + " where rs.loan_id = ? and rs.recalculated_interest_component = 0"
                            + " and rs.completed_derived = 0"
                            + " and coalesce(rs.principal_amount, 0) > 0"
                            + " order by rs.duedate, rs.installment",
                    ctx.loanId);

            final ScheduledDateGenerator scheduledDateGenerator = new DefaultScheduledDateGenerator();
            long diff = ctx.gracePeriod + 1 - ctx.penaltyPostingWaitPeriod;
            if (diff < 1) {
                diff = 1L;
            }

            for (final Map<String, Object> installment : installments) {
                if (cumulative.compareTo(cap) >= 0) {
                    break;
                }
                final Integer periodNumber = ((Number) installment.get("installment")).intValue();
                // applyMaxOccurrenceWhileApplyingOverdueChargesForLoan: only installments with period <= max_occurrence
                if (ctx.maxOccurrence != null && ctx.maxOccurrence > 0 && periodNumber > ctx.maxOccurrence) {
                    continue;
                }
                final LocalDate dueDate = toLocalDate(installment.get("duedate"));
                if (dueDate == null) {
                    continue;
                }
                // businessDate > dueDate + wait  ⇔  dueDate + wait < businessDate
                if (!ctx.effectiveDate.isAfter(dueDate.plusDays(ctx.gracePeriod))) {
                    continue;
                }
                final BigDecimal outstandingPrincipal = nullToZero(toBigDecimal(installment.get("outstanding_principal")));
                if (outstandingPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                final Long scheduleId = ((Number) installment.get("schedule_id")).longValue();
                final Map<Integer, LocalDate> scheduleDates = buildClassicOverdueScheduleDates(ctx, dueDate, diff,
                        scheduledDateGenerator);
                final Set<Integer> existingFrequencies = new HashSet<>(retrieveExistingOverdueFrequencies(ctx.loanId,
                        ctx.chargeDefinitionId, periodNumber));
                for (final Integer frequency : existingFrequencies) {
                    scheduleDates.remove(frequency);
                }

                for (final Map.Entry<Integer, LocalDate> entry : scheduleDates.entrySet()) {
                    if (cumulative.compareTo(cap) >= 0) {
                        break;
                    }
                    final LocalDate postingDate = entry.getValue();
                    if (postingDate == null || postingDate.isAfter(ctx.effectiveDate)) {
                        continue;
                    }
                    if (hasActiveChargeOnDate(ctx, postingDate)) {
                        continue;
                    }

                    BigDecimal penalty = outstandingPrincipal.multiply(ctx.monthlyRate, MoneyHelper.getMathContext())
                            .divide(BigDecimal.valueOf(100L), scale, MoneyHelper.getRoundingMode());
                    final BigDecimal remainingCap = cap.subtract(cumulative);
                    if (penalty.compareTo(remainingCap) > 0) {
                        penalty = remainingCap.setScale(scale, MoneyHelper.getRoundingMode());
                    }
                    if (penalty.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    insertMonthlyPenaltyCharge(ctx, postingDate, penalty, outstandingPrincipal, scheduleId, entry.getKey());
                    cumulative = cumulative.add(penalty);
                    posted++;
                }
            }
            syncPenaltiesFromActiveCharges(ctx.loanId);
            return posted;
        });
    }

    /**
     * Exact schedule-date generation from {@code inkomoko-prod-bkp21092026}
     * {@code applyChargeToOverdueLoanInstallment}: before maturity only the first fee interval posts; on/after maturity
     * recurrence continues through the business date while the installment is open.
     */
    private Map<Integer, LocalDate> buildClassicOverdueScheduleDates(final RectifyContext ctx, final LocalDate dueDate,
            final long diff, final ScheduledDateGenerator scheduledDateGenerator) {
        final Map<Integer, LocalDate> scheduleDates = new TreeMap<>();
        LocalDate startDate = dueDate.plusDays((int) ctx.gracePeriod + 1);
        LocalDate endDate = ctx.effectiveDate;
        // Backup: if due before maturity and feeInterval is set, endDate = start + one fee interval (one post only).
        if (ctx.expectedMaturity != null && dueDate.isBefore(ctx.expectedMaturity) && ctx.feeInterval != null
                && ctx.feeFrequency != null) {
            endDate = scheduledDateGenerator.getRepaymentPeriodDate(PeriodFrequencyType.fromInt(ctx.feeFrequency), ctx.feeInterval,
                    startDate);
        }
        int frequencyNumber = 1;
        if (ctx.feeFrequency == null) {
            scheduleDates.put(frequencyNumber, startDate.minusDays(diff));
            return scheduleDates;
        }
        final int interval = ctx.feeInterval == null ? 1 : Math.max(ctx.feeInterval, 1);
        while (!startDate.isAfter(endDate) && !startDate.isEqual(endDate)) {
            scheduleDates.put(frequencyNumber++, startDate.minusDays(diff));
            startDate = scheduledDateGenerator.getRepaymentPeriodDate(PeriodFrequencyType.fromInt(ctx.feeFrequency), interval,
                    startDate);
        }
        return scheduleDates;
    }

    private Collection<Integer> retrieveExistingOverdueFrequencies(final Long loanId, final Long chargeDefinitionId,
            final Integer periodNumber) {
        return this.jdbcTemplate.queryForList(
                "select oic.frequency_number from m_loan_overdue_installment_charge oic"
                        + " inner join m_loan_charge lc on lc.id = oic.loan_charge_id"
                        + " inner join m_loan_repayment_schedule rs on rs.id = oic.loan_schedule_id"
                        + " where lc.is_active = 1 and rs.loan_id = ? and rs.installment = ? and lc.charge_id = ?",
                Integer.class, loanId, periodNumber, chargeDefinitionId);
    }

    private boolean hasActiveChargeOnDate(final RectifyContext ctx, final LocalDate postingDate) {
        final Integer existing = this.jdbcTemplate.queryForObject(
                "select count(1) from m_loan_charge lc where lc.loan_id = ? and lc.charge_id = ?"
                        + " and lc.is_active = 1 and lc.waived = 0 and lc.due_for_collection_as_of_date = ?",
                Integer.class, ctx.loanId, ctx.chargeDefinitionId, postingDate);
        return existing != null && existing > 0;
    }

    /**
     * Remove earlier incorrect / previous gap-fill rows (Specified due date, or tagged {@link #GAP_FILL_EXTERNAL_ID_PREFIX})
     * so months can be restored cleanly using the original Overdue Fees pattern.
     */
    private void deactivateIncorrectPriorGapFillCharges(final RectifyContext ctx) {
        final List<Long> ids = this.jdbcTemplate.queryForList(
                "select lc.id from m_loan_charge lc where lc.loan_id = ? and lc.charge_id = ?"
                        + " and lc.is_active = 1 and lc.waived = 0"
                        + " and (lc.charge_time_enum = ? or lc.external_id like ?)",
                Long.class, ctx.loanId, ctx.chargeDefinitionId, SPECIFIED_DUE_DATE, GAP_FILL_EXTERNAL_ID_PREFIX + "%");
        for (final Long loanChargeId : ids) {
            this.jdbcTemplate.update(
                    "update m_loan_charge set is_active = 0, amount_outstanding_derived = 0 where id = ?", loanChargeId);
            deleteChargeChildren(loanChargeId);
            reverseAccrualsForCharge(loanChargeId);
        }
    }

    static String gapFillExternalId(final Long loanId, final LocalDate postingDate) {
        return GAP_FILL_EXTERNAL_ID_PREFIX + loanId + ":" + postingDate;
    }

    private void insertMonthlyPenaltyCharge(final RectifyContext ctx, final LocalDate postingDate, final BigDecimal penaltyAmount,
            final BigDecimal overduePrincipal, final Long scheduleId, final Integer frequencyNumber) {
        final Long officeId = ((Number) ctx.loanRow.get("office_id")).longValue();
        final String externalId = gapFillExternalId(ctx.loanId, postingDate);
        this.jdbcTemplate.update(
                "insert into m_loan_charge (loan_id, charge_id, is_penalty, charge_time_enum, charge_calculation_enum,"
                        + " charge_payment_mode_enum, calculation_percentage, calculation_on_amount, charge_amount_or_percentage,"
                        + " amount, amount_outstanding_derived, is_paid_derived, waived, is_active,"
                        + " due_for_collection_as_of_date, external_id)"
                        + " values (?, ?, 1, ?, ?, 0, ?, ?, ?, ?, ?, 0, 0, 1, ?, ?)",
                ctx.loanId, ctx.chargeDefinitionId, OVERDUE_INSTALLMENT, CHARGE_CALC_PERCENT_OF_AMOUNT, ctx.monthlyRate,
                overduePrincipal, penaltyAmount, penaltyAmount, penaltyAmount, postingDate, externalId);
        @SuppressWarnings("deprecation")
        final Long loanChargeId = this.jdbcTemplate.queryForObject("SELECT " + this.sqlGenerator.lastInsertId(), Long.class);

        this.jdbcTemplate.update(
                "insert into m_loan_overdue_installment_charge (loan_charge_id, loan_schedule_id, frequency_number)"
                        + " values (?, ?, ?)",
                loanChargeId, scheduleId, frequencyNumber);

        addChargeToSchedule(ctx.loanId, postingDate, penaltyAmount);

        final BigDecimal appliedRate = ctx.monthlyRate.divide(BigDecimal.valueOf(100L), MoneyHelper.getMathContext());
        final Integer metaExists = this.jdbcTemplate.queryForObject(
                "select count(1) from m_loan_daily_late_fee where loan_id = ? and charge_id = ? and penalty_date = ?", Integer.class,
                ctx.loanId, ctx.chargeDefinitionId, postingDate);
        if (metaExists != null && metaExists > 0) {
            this.jdbcTemplate.update(
                    "update m_loan_daily_late_fee set loan_charge_id = ?, overdue_principal_amount = ?, monthly_rate = ?,"
                            + " daily_rate = ?, penalty_amount = ?, is_active = 1"
                            + " where loan_id = ? and charge_id = ? and penalty_date = ?",
                    loanChargeId, overduePrincipal, ctx.monthlyRate, appliedRate, penaltyAmount, ctx.loanId, ctx.chargeDefinitionId,
                    postingDate);
        } else {
            this.jdbcTemplate.update(
                    "insert into m_loan_daily_late_fee (loan_id, charge_id, loan_charge_id, penalty_date,"
                            + " overdue_principal_amount, monthly_rate, daily_rate, penalty_amount, is_active)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?, 1)",
                    ctx.loanId, ctx.chargeDefinitionId, loanChargeId, postingDate, overduePrincipal, ctx.monthlyRate, appliedRate,
                    penaltyAmount);
        }

        final int accountingType = ((Number) ctx.loanRow.get("accounting_type")).intValue();
        if (accountingType == 3 || accountingType == 4) {
            this.jdbcTemplate.update(
                    "insert into m_loan_transaction (loan_id, office_id, is_reversed, transaction_type_enum, transaction_date,"
                            + " amount, interest_portion_derived, fee_charges_portion_derived, penalty_charges_portion_derived,"
                            + " submitted_on_date, manually_adjusted_or_reversed, is_reversal)"
                            + " values (?, ?, false, ?, ?, ?, null, null, ?, ?, false, false)",
                    ctx.loanId, officeId, LoanTransactionType.ACCRUAL.getValue(), postingDate, penaltyAmount, penaltyAmount,
                    ctx.effectiveDate);
            @SuppressWarnings("deprecation")
            final Long txnId = this.jdbcTemplate.queryForObject("SELECT " + this.sqlGenerator.lastInsertId(), Long.class);
            this.jdbcTemplate.update(
                    "insert into m_loan_charge_paid_by (loan_transaction_id, loan_charge_id, amount, installment_number)"
                            + " values (?, ?, ?, null)",
                    txnId, loanChargeId, penaltyAmount);
            final Map<String, Object> chargeRow = new LinkedHashMap<>();
            chargeRow.put("charge_id", ctx.chargeDefinitionId);
            chargeRow.put("is_penalty", 1);
            chargeRow.put("loan_id", ctx.loanId);
            postAccrualJournal(ctx, chargeRow, txnId, loanChargeId, penaltyAmount, postingDate);
        }
    }

    private void addChargeToSchedule(final Long loanId, final LocalDate dueDate, final BigDecimal amount) {
        if (dueDate == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        final Long scheduleId = findScheduleId(loanId, dueDate);
        if (scheduleId == null) {
            return;
        }
        this.jdbcTemplate.update(
                "update m_loan_repayment_schedule set penalty_charges_amount = coalesce(penalty_charges_amount, 0) + ? where id = ?",
                amount, scheduleId);
    }

    private void postAccrualJournal(final RectifyContext ctx, final Map<String, Object> chargeRow, final Long transactionId,
            final Long loanChargeId, final BigDecimal amount, final LocalDate transactionDate) {
        postJournal(ctx, chargeRow, transactionId, loanChargeId, amount, transactionDate, LoanTransactionType.ACCRUAL, true);
    }

    private void postJournal(final RectifyContext ctx, final Map<String, Object> chargeRow, final Long transactionId,
            final Long loanChargeId, final BigDecimal amount, final LocalDate transactionDate, final LoanTransactionType type,
            final boolean penalty) {
        final String currencyCode = (String) ctx.loanRow.get("currency_code");
        final int digits = ((Number) ctx.loanRow.get("currency_digits")).intValue();
        final Integer multiples = ctx.loanRow.get("currency_multiplesof") == null ? null
                : ((Number) ctx.loanRow.get("currency_multiplesof")).intValue();
        final CurrencyData currencyData = new CurrencyData(currencyCode, digits, multiples);
        final Long officeId = ((Number) ctx.loanRow.get("office_id")).longValue();
        final Long productId = ((Number) ctx.loanRow.get("product_id")).longValue();
        final Long chargeDefinitionId = ((Number) chargeRow.get("charge_id")).longValue();
        final int accountingType = ((Number) ctx.loanRow.get("accounting_type")).intValue();
        final boolean cash = accountingType == 2;
        final boolean periodic = accountingType == 3;
        final boolean upfront = accountingType == 4;

        final LoanTransactionEnumData transactionType = LoanEnumerations.transactionType(type);
        final Map<String, Object> transactionMap = new LinkedHashMap<>();
        transactionMap.put("id", transactionId);
        transactionMap.put("officeId", officeId);
        transactionMap.put("type", transactionType);
        transactionMap.put("reversed", false);
        transactionMap.put("date", transactionDate);
        transactionMap.put("currency", currencyData);
        transactionMap.put("amount", amount);
        transactionMap.put("principalPortion", null);
        transactionMap.put("interestPortion", null);
        transactionMap.put("feeChargesPortion", penalty ? null : amount);
        transactionMap.put("penaltyChargesPortion", penalty ? amount : null);
        transactionMap.put("overPaymentPortion", null);

        final Map<String, Object> paid = new LinkedHashMap<>();
        paid.put("chargeId", chargeDefinitionId);
        paid.put("isPenalty", penalty);
        paid.put("loanChargeId", loanChargeId);
        paid.put("amount", amount);
        final List<Map<String, Object>> paidList = new ArrayList<>();
        paidList.add(paid);
        transactionMap.put("loanChargesPaid", paidList);

        final Map<String, Object> bridge = new LinkedHashMap<>();
        bridge.put("loanId", ctx.loanId);
        bridge.put("loanProductId", productId);
        bridge.put("officeId", officeId);
        bridge.put("currency", currencyData);
        bridge.put("calculatedInterest", ctx.loanRow.get("total_interest_charged"));
        bridge.put("cashBasedAccountingEnabled", cash);
        bridge.put("upfrontAccrualBasedAccountingEnabled", upfront);
        bridge.put("periodicAccrualBasedAccountingEnabled", periodic);
        bridge.put("isAccountTransfer", false);
        bridge.put("fundId", ctx.loanRow.get("fund_id") == null ? null : ((Number) ctx.loanRow.get("fund_id")).longValue());
        final List<Map<String, Object>> newTxns = new ArrayList<>();
        newTxns.add(transactionMap);
        bridge.put("newLoanTransactions", newTxns);
        this.journalEntryWritePlatformService.createJournalEntriesForLoan(bridge);
    }

    private int monthlyPostingInterval(final Map<String, Object> chargeDef) {
        final Integer frequency = chargeDef.get("fee_frequency") == null ? null : ((Number) chargeDef.get("fee_frequency")).intValue();
        int interval = chargeDef.get("fee_interval") == null ? 1 : ((Number) chargeDef.get("fee_interval")).intValue();
        if (interval < 1) {
            interval = 1;
        }
        if (frequency != null && PeriodFrequencyType.YEARS.getValue().equals(frequency)) {
            return interval * 12;
        }
        return interval;
    }

    private TransactionTemplate newRequiresNew() {
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static boolean flag(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return false;
    }

    private static LocalDate toLocalDate(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) value).getTime()).toLocalDate();
        }
        return null;
    }

    private static BigDecimal toBigDecimal(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static BigDecimal nullToZero(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class RectifyContext {
        private final Long loanId;
        private final LocalDate effectiveDate;
        private final Map<String, Object> loanRow;
        private final Long chargeDefinitionId;
        private final String chargeName;
        private final BigDecimal monthlyRate;
        private final int monthInterval;
        private final Integer feeFrequency;
        private final Integer feeInterval;
        private final Integer maxOccurrence;
        private final long gracePeriod;
        private final long penaltyPostingWaitPeriod;
        private final LocalDate expectedMaturity;
        private final LocalDate firstPenaltyDate;
        private final List<Long> dailyChargeIds;

        private RectifyContext(final Long loanId, final LocalDate effectiveDate, final Map<String, Object> loanRow,
                final Long chargeDefinitionId, final String chargeName, final BigDecimal monthlyRate, final int monthInterval,
                final Integer feeFrequency, final Integer feeInterval, final Integer maxOccurrence, final long gracePeriod,
                final long penaltyPostingWaitPeriod, final LocalDate expectedMaturity, final LocalDate firstPenaltyDate,
                final List<Long> dailyChargeIds) {
            this.loanId = loanId;
            this.effectiveDate = effectiveDate;
            this.loanRow = loanRow;
            this.chargeDefinitionId = chargeDefinitionId;
            this.chargeName = chargeName;
            this.monthlyRate = monthlyRate;
            this.monthInterval = monthInterval;
            this.feeFrequency = feeFrequency;
            this.feeInterval = feeInterval;
            this.maxOccurrence = maxOccurrence;
            this.gracePeriod = gracePeriod;
            this.penaltyPostingWaitPeriod = penaltyPostingWaitPeriod;
            this.expectedMaturity = expectedMaturity;
            this.firstPenaltyDate = firstPenaltyDate;
            this.dailyChargeIds = dailyChargeIds;
        }
    }
}
