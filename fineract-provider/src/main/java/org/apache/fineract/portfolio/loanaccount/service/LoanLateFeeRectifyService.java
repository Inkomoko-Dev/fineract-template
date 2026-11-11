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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Standalone late-fee data repair. Speaks JDBC (+ journal posting) only — does not load {@code Loan}/{@code LoanCharge}
 * graphs or call {@link LoanDailyLateFeeService}. The daily late-fee engine remains responsible for ongoing posting;
 * this service only cleans legacy rows and seeds the corrected monthly charges.
 */
@Service
@RequiredArgsConstructor
public class LoanLateFeeRectifyService {

    private static final int CHARGE_TIME_SPECIFIED_DUE_DATE = ChargeTimeType.SPECIFIED_DUE_DATE.getValue();
    private static final int CHARGE_CALC_FLAT = ChargeCalculationType.FLAT.getValue();
    private static final int OVERDUE_INSTALLMENT = ChargeTimeType.OVERDUE_INSTALLMENT.getValue();

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final ConfigurationDomainService configurationDomainService;
    private final JournalEntryWritePlatformService journalEntryWritePlatformService;

    public CommandProcessingResult rectifyLateFeesForLoan(final Long loanId) {
        final LocalDate effectiveDate = DateUtils.getBusinessLocalDate();
        final RectifyContext ctx = loadContext(loanId, effectiveDate);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("loanId", loanId);

        if (!ctx.legacyLateFees) {
            final int posted = postMissingMonthlyPenalties(ctx);
            changes.put("removedCharges", 0);
            changes.put("postedMonthlyCharges", posted);
            changes.put("recomputed", true);
            return new CommandProcessingResultBuilder().withLoanId(loanId).with(changes).build();
        }

        BigDecimal waivedAmount = BigDecimal.ZERO;
        int removedCharges = 0;
        for (final Long loanChargeId : ctx.legacyChargeIds) {
            final WaiveResult result = waiveOneCharge(ctx, loanChargeId);
            waivedAmount = waivedAmount.add(result.waivedAmount);
            removedCharges += result.removedCharges;
        }

        newRequiresNew().execute(status -> {
            this.jdbcTemplate.update("update m_loan_daily_late_fee set is_active = 0 where loan_id = ? and is_active = 1", loanId);
            refreshLoanPenaltySummary(loanId);
            return null;
        });

        final int posted = postMissingMonthlyPenalties(ctx);

        changes.put("removedCharges", removedCharges);
        changes.put("waivedAmount", waivedAmount);
        changes.put("postedMonthlyCharges", posted);
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

            final List<Map<String, Object>> chargeDefs = this.jdbcTemplate.queryForList(
                    "select c.id, c.amount, c.fee_interval, c.fee_frequency, c.charge_calculation_enum"
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
            final BigDecimal monthlyRate = (BigDecimal) chargeDef.get("amount");
            final int monthInterval = monthlyPostingInterval(chargeDef);

            final long gracePeriod = this.configurationDomainService.retrievePenaltyWaitPeriod();
            final LocalDate earliestDue = this.jdbcTemplate.query(
                    "select min(rs.duedate) as earliest_due from m_loan_repayment_schedule rs"
                            + " where rs.loan_id = ? and rs.recalculated_interest_component = 0"
                            + " and coalesce(rs.principal_amount, 0) > 0",
                    rs -> rs.next() ? JdbcSupport.getLocalDate(rs, "earliest_due") : null, loanId);
            final LocalDate firstPenaltyDate = earliestDue == null ? null : earliestDue.plusDays(gracePeriod + 1);

            final List<LegacyChargeRow> lateFees = loadLegacyCharges(loanId, chargeDefinitionId);
            final boolean legacy = firstPenaltyDate != null
                    && hasLegacyLateFees(lateFees, firstPenaltyDate, monthInterval, effectiveDate);
            final List<Long> chargeIds = new ArrayList<>();
            for (final LegacyChargeRow row : lateFees) {
                chargeIds.add(row.loanChargeId);
            }
            return new RectifyContext(loanId, effectiveDate, loan, chargeDefinitionId, monthlyRate, monthInterval, gracePeriod,
                    firstPenaltyDate, legacy, chargeIds);
        });
    }

    private List<LegacyChargeRow> loadLegacyCharges(final Long loanId, final Long chargeDefinitionId) {
        final String sql = "select loanChargeId, calculationType, dueDate from ("
                + " select lc.id as loanChargeId, lc.charge_calculation_enum as calculationType,"
                + " lc.due_for_collection_as_of_date as dueDate from m_loan_charge lc"
                + " where lc.loan_id = ? and lc.charge_id = ? and lc.is_active = 1 and lc.waived = 0"
                + " union"
                + " select lc.id as loanChargeId, lc.charge_calculation_enum as calculationType,"
                + " lc.due_for_collection_as_of_date as dueDate from m_loan_daily_late_fee dlf"
                + " join m_loan_charge lc on lc.id = dlf.loan_charge_id"
                + " where dlf.loan_id = ? and dlf.is_active = 1 and dlf.loan_charge_id is not null"
                + " and lc.is_active = 1 and lc.waived = 0"
                + ") late_fees order by dueDate, loanChargeId";
        final List<LegacyChargeRow> rows = this.jdbcTemplate.query(sql,
                (rs, rowNum) -> new LegacyChargeRow(rs.getLong("loanChargeId"), JdbcSupport.getInteger(rs, "calculationType"),
                        JdbcSupport.getLocalDate(rs, "dueDate")),
                loanId, chargeDefinitionId, loanId);
        final Map<Long, LegacyChargeRow> byId = new LinkedHashMap<>();
        for (final LegacyChargeRow row : rows) {
            byId.putIfAbsent(row.loanChargeId, row);
        }
        return new ArrayList<>(byId.values());
    }

    private boolean hasLegacyLateFees(final List<LegacyChargeRow> lateFees, final LocalDate firstPenaltyDate, final int monthInterval,
            final LocalDate effectiveDate) {
        if (lateFees.isEmpty()) {
            return false;
        }
        final Set<LocalDate> postingDates = monthlyPostingDates(firstPenaltyDate, monthInterval, effectiveDate);
        final Map<YearMonth, Integer> flatsPerMonth = new LinkedHashMap<>();
        for (final LegacyChargeRow charge : lateFees) {
            if (ChargeCalculationType.fromInt(charge.calculationType).isPercentageOfAmount()) {
                return true;
            }
            if (charge.dueDate == null || !postingDates.contains(charge.dueDate)) {
                return true;
            }
            if (flatsPerMonth.merge(YearMonth.from(charge.dueDate), 1, Integer::sum) > 1) {
                return true;
            }
        }
        return false;
    }

    private WaiveResult waiveOneCharge(final RectifyContext ctx, final Long loanChargeId) {
        return newRequiresNew().execute(status -> {
            final List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
                    "select lc.id, lc.loan_id, lc.charge_id, lc.is_active, lc.waived, lc.is_penalty,"
                            + " coalesce(lc.amount_outstanding_derived, 0) as amount_outstanding,"
                            + " coalesce(lc.amount_paid_derived, 0) as amount_paid,"
                            + " coalesce(lc.amount_waived_derived, 0) as amount_waived,"
                            + " lc.due_for_collection_as_of_date as due_date,"
                            + " coalesce(lc.amount, 0) as amount"
                            + " from m_loan_charge lc where lc.id = ? and lc.loan_id = ?",
                    loanChargeId, ctx.loanId);
            if (rows.isEmpty()) {
                return WaiveResult.empty();
            }
            final Map<String, Object> row = rows.get(0);
            if (!flag(row.get("is_active")) || flag(row.get("waived"))) {
                return WaiveResult.empty();
            }

            final BigDecimal amountPaid = (BigDecimal) row.get("amount_paid");
            final BigDecimal amountOutstanding = (BigDecimal) row.get("amount_outstanding");
            final BigDecimal amountWaivedSoFar = (BigDecimal) row.get("amount_waived");
            final BigDecimal chargeAmount = (BigDecimal) row.get("amount");
            BigDecimal toWaive = amountOutstanding;
            if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                toWaive = amountOutstanding.add(amountPaid);
            }

            if (toWaive.compareTo(BigDecimal.ZERO) <= 0) {
                this.jdbcTemplate.update("update m_loan_charge set is_active = 0 where id = ?", loanChargeId);
                deleteChargeChildren(loanChargeId);
                subtractChargeFromSchedule(ctx.loanId, toLocalDate(row.get("due_date")), chargeAmount, BigDecimal.ZERO);
                return WaiveResult.empty();
            }

            final LocalDate dueDate = toLocalDate(row.get("due_date"));
            final LocalDate transactionDate = dueDate != null ? dueDate : ctx.effectiveDate;
            final BigDecimal newWaivedTotal = amountWaivedSoFar.add(toWaive);
            this.jdbcTemplate.update(
                    "update m_loan_charge set waived = 1, is_active = 0, is_paid_derived = 0,"
                            + " amount_paid_derived = 0, amount_outstanding_derived = 0, amount_waived_derived = ?"
                            + " where id = ?",
                    newWaivedTotal, loanChargeId);
            deleteChargeChildren(loanChargeId);
            subtractChargeFromSchedule(ctx.loanId, dueDate, chargeAmount, toWaive);

            final Long officeId = ((Number) ctx.loanRow.get("office_id")).longValue();
            this.jdbcTemplate.update(
                    "insert into m_loan_transaction (loan_id, office_id, is_reversed, transaction_type_enum, transaction_date,"
                            + " amount, principal_portion_derived, interest_portion_derived, fee_charges_portion_derived,"
                            + " penalty_charges_portion_derived, overpayment_portion_derived, unrecognized_income_portion,"
                            + " submitted_on_date, manually_adjusted_or_reversed, is_reversal)"
                            + " values (?, ?, false, ?, ?, ?, null, null, null, ?, null, null, ?, false, false)",
                    ctx.loanId, officeId, LoanTransactionType.WAIVE_CHARGES.getValue(), transactionDate, toWaive, toWaive,
                    ctx.effectiveDate);
            @SuppressWarnings("deprecation")
            final Long transactionId = this.jdbcTemplate.queryForObject("SELECT " + this.sqlGenerator.lastInsertId(), Long.class);
            this.jdbcTemplate.update(
                    "insert into m_loan_charge_paid_by (loan_transaction_id, loan_charge_id, amount, installment_number)"
                            + " values (?, ?, ?, null)",
                    transactionId, loanChargeId, toWaive);

            postWaiverJournal(ctx, row, transactionId, loanChargeId, toWaive, transactionDate);
            return new WaiveResult(toWaive, 1);
        });
    }

    private void deleteChargeChildren(final Long loanChargeId) {
        this.jdbcTemplate.update("delete from m_loan_overdue_installment_charge where loan_charge_id = ?", loanChargeId);
        this.jdbcTemplate.update("delete from m_loan_installment_charge where loan_charge_id = ?", loanChargeId);
    }

    private void subtractChargeFromSchedule(final Long loanId, final LocalDate dueDate, final BigDecimal chargedAmount,
            final BigDecimal waivedAmount) {
        if (dueDate == null || chargedAmount == null || chargedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        final List<Long> scheduleIds = this.jdbcTemplate.queryForList(
                "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                        + " and duedate >= ? order by duedate, installment limit 1",
                Long.class, loanId, dueDate);
        if (scheduleIds.isEmpty()) {
            final List<Long> fallback = this.jdbcTemplate.queryForList(
                    "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                            + " and duedate <= ? order by duedate desc, installment desc limit 1",
                    Long.class, loanId, dueDate);
            if (fallback.isEmpty()) {
                return;
            }
            scheduleIds.add(fallback.get(0));
        }
        this.jdbcTemplate.update(
                "update m_loan_repayment_schedule set"
                        + " penalty_charges_amount = greatest(0, coalesce(penalty_charges_amount, 0) - ?),"
                        + " penalty_charges_waived_derived = coalesce(penalty_charges_waived_derived, 0) + ?"
                        + " where id = ?",
                chargedAmount, waivedAmount, scheduleIds.get(0));
    }

    private void refreshLoanPenaltySummary(final Long loanId) {
        this.jdbcTemplate.update("update m_loan l set "
                + "l.penalty_charges_charged_derived = (select coalesce(sum(lc.amount), 0) from m_loan_charge lc "
                + " where lc.loan_id = l.id and lc.is_active = 1 and lc.is_penalty = 1), "
                + "l.penalty_charges_waived_derived = (select coalesce(sum(lc.amount_waived_derived), 0) from m_loan_charge lc "
                + " where lc.loan_id = l.id and lc.is_penalty = 1), "
                + "l.penalty_charges_repaid_derived = (select coalesce(sum(lc.amount_paid_derived), 0) from m_loan_charge lc "
                + " where lc.loan_id = l.id and lc.is_penalty = 1), "
                + "l.penalty_charges_outstanding_derived = (select coalesce(sum(lc.amount_outstanding_derived), 0) from m_loan_charge lc "
                + " where lc.loan_id = l.id and lc.is_active = 1 and lc.is_penalty = 1), "
                + "l.total_outstanding_derived = coalesce(l.principal_outstanding_derived, 0)"
                + " + coalesce(l.interest_outstanding_derived, 0) + coalesce(l.fee_charges_outstanding_derived, 0)"
                + " + (select coalesce(sum(lc.amount_outstanding_derived), 0) from m_loan_charge lc"
                + " where lc.loan_id = l.id and lc.is_active = 1 and lc.is_penalty = 1)"
                + " where l.id = ?", loanId);
    }

    private int postMissingMonthlyPenalties(final RectifyContext ctx) {
        if (ctx.firstPenaltyDate == null || ctx.effectiveDate.isBefore(ctx.firstPenaltyDate)) {
            return 0;
        }
        return newRequiresNew().execute(status -> {
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

            LocalDate postingDate = ctx.firstPenaltyDate;
            int guard = 0;
            while (!postingDate.isAfter(ctx.effectiveDate) && cumulative.compareTo(cap) < 0 && guard < 600) {
                final Integer existing = this.jdbcTemplate.queryForObject(
                        "select count(1) from m_loan_charge lc where lc.loan_id = ? and lc.charge_id = ?"
                                + " and lc.is_active = 1 and lc.waived = 0 and lc.due_for_collection_as_of_date = ?",
                        Integer.class, ctx.loanId, ctx.chargeDefinitionId, postingDate);
                if (existing == null || existing == 0) {
                    final BigDecimal overduePrincipal = overduePrincipalOn(ctx.loanId, postingDate, ctx.gracePeriod);
                    if (overduePrincipal.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal penalty = overduePrincipal.multiply(ctx.monthlyRate, MoneyHelper.getMathContext())
                                .divide(BigDecimal.valueOf(100L), scale, MoneyHelper.getRoundingMode());
                        final BigDecimal remainingCap = cap.subtract(cumulative);
                        if (penalty.compareTo(remainingCap) > 0) {
                            penalty = remainingCap.setScale(scale, MoneyHelper.getRoundingMode());
                        }
                        if (penalty.compareTo(BigDecimal.ZERO) > 0) {
                            insertMonthlyPenaltyCharge(ctx, postingDate, penalty, overduePrincipal);
                            cumulative = cumulative.add(penalty);
                            posted++;
                        }
                    }
                }
                postingDate = postingDate.plusMonths(ctx.monthInterval);
                guard++;
            }
            refreshLoanPenaltySummary(ctx.loanId);
            return posted;
        });
    }

    private BigDecimal overduePrincipalOn(final Long loanId, final LocalDate postingDate, final long gracePeriod) {
        final LocalDate eligibleOnOrBefore = postingDate.minusDays(gracePeriod + 1);
        final BigDecimal amount = this.jdbcTemplate.queryForObject(
                "select coalesce(sum(greatest(coalesce(rs.principal_amount, 0) - coalesce(rs.principal_completed_derived, 0)"
                        + " - coalesce(rs.principal_writtenoff_derived, 0), 0)), 0)"
                        + " from m_loan_repayment_schedule rs"
                        + " where rs.loan_id = ? and rs.recalculated_interest_component = 0"
                        + " and rs.duedate <= ?",
                BigDecimal.class, loanId, eligibleOnOrBefore);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private void insertMonthlyPenaltyCharge(final RectifyContext ctx, final LocalDate postingDate, final BigDecimal penaltyAmount,
            final BigDecimal overduePrincipal) {
        final Long officeId = ((Number) ctx.loanRow.get("office_id")).longValue();
        this.jdbcTemplate.update(
                "insert into m_loan_charge (loan_id, charge_id, is_penalty, charge_time_enum, charge_calculation_enum,"
                        + " charge_payment_mode_enum, calculation_percentage, calculation_on_amount, charge_amount_or_percentage,"
                        + " amount, amount_outstanding_derived, is_paid_derived, waived, is_active,"
                        + " due_for_collection_as_of_date)"
                        + " values (?, ?, 1, ?, ?, 0, ?, ?, ?, ?, ?, 0, 0, 1, ?)",
                ctx.loanId, ctx.chargeDefinitionId, CHARGE_TIME_SPECIFIED_DUE_DATE, CHARGE_CALC_FLAT, ctx.monthlyRate, overduePrincipal,
                penaltyAmount, penaltyAmount, penaltyAmount, postingDate);
        @SuppressWarnings("deprecation")
        final Long loanChargeId = this.jdbcTemplate.queryForObject("SELECT " + this.sqlGenerator.lastInsertId(), Long.class);

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

        // Accrual / charge-applied journal when product uses accrual accounting
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
        final List<Long> scheduleIds = this.jdbcTemplate.queryForList(
                "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                        + " and duedate >= ? order by duedate, installment limit 1",
                Long.class, loanId, dueDate);
        Long scheduleId = scheduleIds.isEmpty() ? null : scheduleIds.get(0);
        if (scheduleId == null) {
            final List<Long> fallback = this.jdbcTemplate.queryForList(
                    "select id from m_loan_repayment_schedule where loan_id = ? and recalculated_interest_component = 0"
                            + " order by duedate desc, installment desc limit 1",
                    Long.class, loanId);
            if (fallback.isEmpty()) {
                return;
            }
            scheduleId = fallback.get(0);
        }
        this.jdbcTemplate.update(
                "update m_loan_repayment_schedule set penalty_charges_amount = coalesce(penalty_charges_amount, 0) + ? where id = ?",
                amount, scheduleId);
    }

    private void postWaiverJournal(final RectifyContext ctx, final Map<String, Object> chargeRow, final Long transactionId,
            final Long loanChargeId, final BigDecimal waivedAmount, final LocalDate transactionDate) {
        postJournal(ctx, chargeRow, transactionId, loanChargeId, waivedAmount, transactionDate, LoanTransactionType.WAIVE_CHARGES, true);
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

    private Set<LocalDate> monthlyPostingDates(final LocalDate firstPenaltyDate, final int monthInterval,
            final LocalDate effectiveDate) {
        final Set<LocalDate> postingDates = new HashSet<>();
        LocalDate postingDate = firstPenaltyDate;
        final LocalDate horizon = effectiveDate.plusMonths(monthInterval);
        int guard = 0;
        while (!postingDate.isAfter(horizon) && guard < 600) {
            postingDates.add(postingDate);
            postingDate = postingDate.plusMonths(monthInterval);
            guard++;
        }
        return postingDates;
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
        private final LocalDate effectiveDate;
        private final Map<String, Object> loanRow;
        private final Long chargeDefinitionId;
        private final BigDecimal monthlyRate;
        private final int monthInterval;
        private final long gracePeriod;
        private final LocalDate firstPenaltyDate;
        private final boolean legacyLateFees;
        private final List<Long> legacyChargeIds;

        private RectifyContext(final Long loanId, final LocalDate effectiveDate, final Map<String, Object> loanRow,
                final Long chargeDefinitionId, final BigDecimal monthlyRate, final int monthInterval, final long gracePeriod,
                final LocalDate firstPenaltyDate, final boolean legacyLateFees, final List<Long> legacyChargeIds) {
            this.loanId = loanId;
            this.effectiveDate = effectiveDate;
            this.loanRow = loanRow;
            this.chargeDefinitionId = chargeDefinitionId;
            this.monthlyRate = monthlyRate;
            this.monthInterval = monthInterval;
            this.gracePeriod = gracePeriod;
            this.firstPenaltyDate = firstPenaltyDate;
            this.legacyLateFees = legacyLateFees;
            this.legacyChargeIds = legacyChargeIds;
        }
    }

    private static final class LegacyChargeRow {
        private final Long loanChargeId;
        private final Integer calculationType;
        private final LocalDate dueDate;

        private LegacyChargeRow(final Long loanChargeId, final Integer calculationType, final LocalDate dueDate) {
            this.loanChargeId = loanChargeId;
            this.calculationType = calculationType;
            this.dueDate = dueDate;
        }
    }

    private static final class WaiveResult {
        private final BigDecimal waivedAmount;
        private final int removedCharges;

        private WaiveResult(final BigDecimal waivedAmount, final int removedCharges) {
            this.waivedAmount = waivedAmount;
            this.removedCharges = removedCharges;
        }

        private static WaiveResult empty() {
            return new WaiveResult(BigDecimal.ZERO, 0);
        }
    }
}
