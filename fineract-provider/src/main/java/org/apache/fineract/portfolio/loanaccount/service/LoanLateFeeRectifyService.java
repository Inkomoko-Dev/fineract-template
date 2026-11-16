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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Late-fee repair: remove daily late-fee charge rows (without creating a waive txn per day), keep existing monthly
 * penalties, then refresh loan summary and {@code m_loan_arrears_aging}. Does not invent missing monthly charges —
 * business can post those separately. Independent of {@link LoanDailyLateFeeService}.
 */
@Service
@RequiredArgsConstructor
public class LoanLateFeeRectifyService {

    private static final int OVERDUE_INSTALLMENT = ChargeTimeType.OVERDUE_INSTALLMENT.getValue();

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final LoanAssembler loanAssembler;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanArrearsAgingService loanArrearsAgingService;

    public CommandProcessingResult rectifyLateFeesForLoan(final Long loanId) {
        final RectifyContext ctx = loadContext(loanId);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("loanId", loanId);

        int removedDaily = 0;
        for (final Long loanChargeId : ctx.dailyChargeIds) {
            if (removeDailyChargeQuietly(ctx, loanChargeId)) {
                removedDaily++;
            }
        }

        newRequiresNew().execute(status -> {
            this.jdbcTemplate.update("update m_loan_daily_late_fee set is_active = 0 where loan_id = ? and is_active = 1", loanId);
            refreshLoanPenaltySummary(loanId);
            return null;
        });

        // Do not post missing monthlies here — leave that to the normal overdue/business process.
        refreshSummaryAndArrears(loanId);

        changes.put("removedDailyCharges", removedDaily);
        changes.put("postedMonthlyCharges", 0);
        changes.put("arrearsRefreshed", true);
        changes.put("recomputed", true);
        return new CommandProcessingResultBuilder().withLoanId(loanId).with(changes).build();
    }

    private RectifyContext loadContext(final Long loanId) {
        return newRequiresNew().execute(status -> {
            final List<Map<String, Object>> loanRows = this.jdbcTemplate.queryForList(
                    "select l.id, l.loan_status_id, l.product_id,"
                            + " c.status_enum as client_status, g.status_enum as group_status"
                            + " from m_loan l"
                            + " left join m_client c on c.id = l.client_id"
                            + " left join m_group g on g.id = l.group_id"
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

            final List<Map<String, Object>> chargeDefs = this.jdbcTemplate.queryForList(
                    "select c.id from m_product_loan_charge plc"
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
            final Long chargeDefinitionId = ((Number) chargeDefs.get(0).get("id")).longValue();

            // Remove every flat late-fee row (daily and the flat “monthly” posts from the daily late-fee job).
            // Keep percentage-of-amount rows — those are the original monthly overdue penalties.
            final List<Map<String, Object>> activeCharges = this.jdbcTemplate.queryForList(
                    "select lc.id, lc.charge_calculation_enum"
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

            return new RectifyContext(loanId, dailyChargeIds);
        });
    }

    /**
     * Deactivate a daily late-fee charge without inserting WAIVE_CHARGES (avoids Accrual+Waive pairs per day). Adjusts
     * schedule and reverses linked accrual transactions when present.
     */
    private boolean removeDailyChargeQuietly(final RectifyContext ctx, final Long loanChargeId) {
        return Boolean.TRUE.equals(newRequiresNew().execute(status -> {
            final List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
                    "select lc.id, coalesce(lc.amount_outstanding_derived, 0) as amount_outstanding,"
                            + " coalesce(lc.amount_paid_derived, 0) as amount_paid,"
                            + " coalesce(lc.amount, 0) as amount, lc.due_for_collection_as_of_date as due_date,"
                            + " lc.is_active, lc.waived"
                            + " from m_loan_charge lc where lc.id = ? and lc.loan_id = ?",
                    loanChargeId, ctx.loanId);
            if (rows.isEmpty()) {
                return false;
            }
            final Map<String, Object> row = rows.get(0);
            if (!flag(row.get("is_active")) || flag(row.get("waived"))) {
                return false;
            }

            final BigDecimal amountPaid = (BigDecimal) row.get("amount_paid");
            final BigDecimal chargeAmount = (BigDecimal) row.get("amount");
            final LocalDate dueDate = toLocalDate(row.get("due_date"));

            this.jdbcTemplate.update(
                    "update m_loan_charge set is_active = 0, amount_outstanding_derived = 0 where id = ?", loanChargeId);
            deleteChargeChildren(loanChargeId);

            if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                removePaidChargeFromSchedule(ctx.loanId, dueDate, chargeAmount, amountPaid);
            } else {
                reduceScheduleCharged(ctx.loanId, dueDate, chargeAmount);
            }

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

    private Long findScheduleId(final Long loanId, final LocalDate dueDate) {
        if (dueDate == null) {
            return null;
        }
        final List<Long> scheduleIds = this.jdbcTemplate.queryForList(
                "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                        + " and duedate >= ? order by duedate, installment limit 1",
                Long.class, loanId, dueDate);
        if (!scheduleIds.isEmpty()) {
            return scheduleIds.get(0);
        }
        final List<Long> fallback = this.jdbcTemplate.queryForList(
                "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                        + " and duedate <= ? order by duedate desc, installment desc limit 1",
                Long.class, loanId, dueDate);
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    private void reduceScheduleCharged(final Long loanId, final LocalDate dueDate, final BigDecimal chargeAmount) {
        if (chargeAmount == null || chargeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        final Long scheduleId = findScheduleId(loanId, dueDate);
        if (scheduleId == null) {
            return;
        }
        this.jdbcTemplate.update(
                "update m_loan_repayment_schedule set"
                        + " penalty_charges_amount = greatest(0, coalesce(penalty_charges_amount, 0) - ?)"
                        + " where id = ?",
                chargeAmount, scheduleId);
    }

    private void removePaidChargeFromSchedule(final Long loanId, final LocalDate dueDate, final BigDecimal chargeAmount,
            final BigDecimal amountPaid) {
        if (chargeAmount == null || chargeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        final Long scheduleId = findScheduleId(loanId, dueDate);
        if (scheduleId == null) {
            return;
        }
        final BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        this.jdbcTemplate.update(
                "update m_loan_repayment_schedule set"
                        + " penalty_charges_amount = greatest(0, coalesce(penalty_charges_amount, 0) - ?),"
                        + " penalty_charges_completed_derived = greatest(0, coalesce(penalty_charges_completed_derived, 0) - ?)"
                        + " where id = ?",
                chargeAmount, paid, scheduleId);
    }

    private void refreshLoanPenaltySummary(final Long loanId) {
        this.jdbcTemplate.update("update m_loan l set "
                + "l.penalty_charges_charged_derived = (select coalesce(sum(rs.penalty_charges_amount), 0)"
                + " from m_loan_repayment_schedule rs where rs.loan_id = l.id), "
                + "l.penalty_charges_waived_derived = (select coalesce(sum(rs.penalty_charges_waived_derived), 0)"
                + " from m_loan_repayment_schedule rs where rs.loan_id = l.id), "
                + "l.penalty_charges_repaid_derived = (select coalesce(sum(rs.penalty_charges_completed_derived), 0)"
                + " from m_loan_repayment_schedule rs where rs.loan_id = l.id), "
                + "l.penalty_charges_outstanding_derived = (select coalesce(sum(greatest(0,"
                + " coalesce(rs.penalty_charges_amount, 0) - coalesce(rs.penalty_charges_completed_derived, 0)"
                + " - coalesce(rs.penalty_charges_waived_derived, 0) - coalesce(rs.penalty_charges_writtenoff_derived, 0))), 0)"
                + " from m_loan_repayment_schedule rs where rs.loan_id = l.id), "
                + "l.total_waived_derived = coalesce(l.interest_waived_derived, 0)"
                + " + coalesce(l.fee_charges_waived_derived, 0)"
                + " + (select coalesce(sum(rs.penalty_charges_waived_derived), 0) from m_loan_repayment_schedule rs"
                + " where rs.loan_id = l.id), "
                + "l.total_expected_repayment_derived = coalesce(l.principal_disbursed_derived, 0)"
                + " + coalesce(l.interest_charged_derived, 0) + coalesce(l.fee_charges_charged_derived, 0)"
                + " + (select coalesce(sum(rs.penalty_charges_amount), 0) from m_loan_repayment_schedule rs"
                + " where rs.loan_id = l.id), "
                + "l.total_expected_costofloan_derived = coalesce(l.interest_charged_derived, 0)"
                + " + coalesce(l.fee_charges_charged_derived, 0)"
                + " + (select coalesce(sum(rs.penalty_charges_amount), 0) from m_loan_repayment_schedule rs"
                + " where rs.loan_id = l.id), "
                + "l.total_outstanding_derived = coalesce(l.principal_outstanding_derived, 0)"
                + " + coalesce(l.interest_outstanding_derived, 0) + coalesce(l.fee_charges_outstanding_derived, 0)"
                + " + (select coalesce(sum(greatest(0, coalesce(rs.penalty_charges_amount, 0)"
                + " - coalesce(rs.penalty_charges_completed_derived, 0) - coalesce(rs.penalty_charges_waived_derived, 0)"
                + " - coalesce(rs.penalty_charges_writtenoff_derived, 0))), 0)"
                + " from m_loan_repayment_schedule rs where rs.loan_id = l.id)"
                + " where l.id = ?", loanId);
    }

    private void refreshSummaryAndArrears(final Long loanId) {
        newRequiresNew().execute(status -> {
            final Loan loan = this.loanAssembler.assembleFrom(loanId);
            loan.updateLoanSummaryDerivedFields();
            this.loanRepositoryWrapper.save(loan);
            if (loan.isOpen() && loan.repaymentScheduleDetail().isInterestRecalculationEnabled()
                    && loan.loanProduct().isArrearsBasedOnOriginalSchedule()) {
                this.loanArrearsAgingService.updateLoanArrearsAgeingDetailsWithOriginalSchedule(loan);
            } else {
                this.loanArrearsAgingService.updateLoanArrearsAgeingDetails(loan);
            }
            return null;
        });
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

    private static final class RectifyContext {
        private final Long loanId;
        private final List<Long> dailyChargeIds;

        private RectifyContext(final Long loanId, final List<Long> dailyChargeIds) {
            this.loanId = loanId;
            this.dailyChargeIds = dailyChargeIds;
        }
    }
}
