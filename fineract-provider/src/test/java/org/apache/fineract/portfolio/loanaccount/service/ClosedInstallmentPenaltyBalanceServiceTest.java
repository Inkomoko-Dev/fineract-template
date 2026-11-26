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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ClosedInstallmentPenaltyBalanceServiceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 11, 26);

    private JdbcTemplate jdbc;
    private ClosedInstallmentPenaltyBalanceService service;

    @BeforeEach
    void setUp() {
        final DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:penalty" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        this.jdbc = new JdbcTemplate(dataSource);
        this.service = new ClosedInstallmentPenaltyBalanceService(this.jdbc, new DataSourceTransactionManager(dataSource));
        createSchema();
    }

    @Test
    void readLoanIdsRejectsEmptyOversizedAndNonNumericValues() {
        assertThrows(GeneralPlatformDomainRuleException.class, () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(null));
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(JsonParser.parseString("{}")));
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(JsonParser.parseString("{\"loanIds\":[]}")));
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(JsonParser.parseString("{\"loanIds\":[\"411692\"]}")));
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(JsonParser.parseString("{\"loanIds\":[1.5]}")));
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(JsonParser.parseString("{\"loanIds\":[0]}")));
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(JsonParser.parseString("{\"loanIds\":[-1]}")));
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(JsonParser.parseString("{\"loanIds\":[1],\"reprocess\":true}")));

        final StringBuilder tooMany = new StringBuilder("{\"loanIds\":[");
        for (int i = 1; i <= ClosedInstallmentPenaltyBalanceService.MAX_LOAN_IDS + 1; i++) {
            if (i > 1) {
                tooMany.append(',');
            }
            tooMany.append(i);
        }
        tooMany.append("]}");
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> ClosedInstallmentPenaltyBalanceService.readLoanIds(JsonParser.parseString(tooMany.toString())));
    }

    @Test
    void readLoanIdsKeepsTheFirstCopyOfADuplicate() {
        final List<Long> ids = ClosedInstallmentPenaltyBalanceService
                .readLoanIds(JsonParser.parseString("{\"loanIds\":[411692,12,411692]}"));
        assertEquals(List.of(411692L, 12L), ids);
    }

    @Test
    void paidInFullReducesTheBalanceAndLeavesTheRepayment() {
        insertLoan(411692L, "7850.03", "4166.64", "12000", "24016.67");
        insertSchedule(9009L, 411692L, 9, LocalDate.of(2025, 11, 27), 1, LocalDate.of(2026, 10, 26), "50000", "50000", "4166.67", "4166.67",
                "1000", "0");
        insertSchedule(9010L, 411692L, 10, LocalDate.of(2025, 12, 27), 0, null, "7850.03", "0", "4166.64", "0", "11000", "0");
        insertCharge(854890L, 411692L, LocalDate.of(2025, 12, 2), "1000", "0", 9009L);
        insertCharge(860584L, 411692L, LocalDate.of(2026, 1, 1), "11000", "0", 9010L);
        insertTransaction(3539905L, 411692L, 0, 2, "30000", "24833.33", "4166.67", "1000");
        insertMapping(4598379L, 3539905L, 9009L, "1000");
        insertTransaction(3539999L, 411692L, 1, 2, "1000", "0", "0", "1000");
        insertMapping(4599999L, 3539999L, 9009L, "1000");
        final Map<String, Object> liveRepayment = transaction(3539905L);
        final Map<String, Object> reversedRepayment = transaction(3539999L);

        final CommandProcessingResult result = this.service.applyStoredPenalty(List.of(411692L), BUSINESS_DATE);

        assertEquals("applied", status(result, 411692L));
        assertMoney(new BigDecimal("1000"), (BigDecimal) row(result, 411692L).get("penaltyApplied"));
        assertEquals(0, ((Number) row(result, 411692L).get("installmentsReopened")).intValue());
        assertMoney(new BigDecimal("1000"), chargePaid(854890L));
        assertMoney(BigDecimal.ZERO, chargeOutstanding(854890L));
        assertEquals(1, flag("select is_paid_derived from m_loan_charge where id = ?", 854890L));
        assertMoney(new BigDecimal("1000"), penaltyPaid(9009L));
        assertEquals(1, flag("select completed_derived from m_loan_repayment_schedule where id = ?", 9009L));
        assertEquals(LocalDate.of(2026, 10, 26), scheduleDate(9009L));
        assertMoney(new BigDecimal("50000"),
                money("select principal_completed_derived from m_loan_repayment_schedule where id = ?", 9009L));
        assertMoney(new BigDecimal("4166.67"),
                money("select interest_completed_derived from m_loan_repayment_schedule where id = ?", 9009L));
        assertMoney(new BigDecimal("11000"), penaltyAmount(9010L));
        assertMoney(BigDecimal.ZERO, penaltyPaid(9010L));
        assertMoney(new BigDecimal("11000"), money("select penalty_charges_outstanding_derived from m_loan where id = ?", 411692L));
        assertMoney(new BigDecimal("23016.67"), money("select total_outstanding_derived from m_loan where id = ?", 411692L));
        assertMoney(new BigDecimal("7850.03"), money("select principal_outstanding_derived from m_loan where id = ?", 411692L));
        assertMoney(new BigDecimal("11000"),
                money("select penalty_charges_overdue_derived from m_loan_arrears_aging where loan_id = ?", 411692L));
        assertMoney(new BigDecimal("23016.67"), money("select total_overdue_derived from m_loan_arrears_aging where loan_id = ?", 411692L));
        assertEquals(liveRepayment, transaction(3539905L));
        assertEquals(reversedRepayment, transaction(3539999L));
        assertEquals(2, count("select count(*) from m_loan_transaction where loan_id = ?", 411692L));

        final CommandProcessingResult second = this.service.applyStoredPenalty(List.of(411692L), BUSINESS_DATE);
        assertEquals("unchanged", status(second, 411692L));
        assertMoney(new BigDecimal("1000"), chargePaid(854890L));
        assertEquals(liveRepayment, transaction(3539905L));
    }

    @Test
    void shortfallReopensTheInstallmentAndPutsTheRemainderInArrears() {
        insertLoan(2L, "0", "0", "1000", "1000");
        insertSchedule(21L, 2L, 1, LocalDate.of(2025, 11, 1), 1, LocalDate.of(2026, 7, 10), "0", "0", "0", "0", "1000", "0");
        insertCharge(22L, 2L, LocalDate.of(2025, 12, 2), "1000", "0", 21L);
        insertTransaction(23L, 2L, 0, 2, "400", "0", "0", "400");
        insertMapping(24L, 23L, 21L, "400");
        final Map<String, Object> repayment = transaction(23L);

        final CommandProcessingResult result = this.service.applyStoredPenalty(List.of(2L, 2L), BUSINESS_DATE);

        assertEquals("applied", status(result, 2L));
        assertEquals(1, ((Number) row(result, 2L).get("installmentsReopened")).intValue());
        assertMoney(new BigDecimal("400"), chargePaid(22L));
        assertMoney(new BigDecimal("600"), chargeOutstanding(22L));
        assertMoney(new BigDecimal("400"), penaltyPaid(21L));
        assertEquals(0, flag("select completed_derived from m_loan_repayment_schedule where id = ?", 21L));
        assertNull(scheduleDate(21L));
        assertMoney(new BigDecimal("600"), money("select penalty_charges_outstanding_derived from m_loan where id = ?", 2L));
        assertMoney(new BigDecimal("600"), money("select total_outstanding_derived from m_loan where id = ?", 2L));
        assertMoney(new BigDecimal("600"), money("select penalty_charges_overdue_derived from m_loan_arrears_aging where loan_id = ?", 2L));
        assertMoney(new BigDecimal("600"), money("select total_overdue_derived from m_loan_arrears_aging where loan_id = ?", 2L));
        assertEquals(repayment, transaction(23L));

        this.service.applyStoredPenalty(List.of(2L), BUSINESS_DATE);
        assertMoney(new BigDecimal("400"), chargePaid(22L));
        assertMoney(new BigDecimal("600"), chargeOutstanding(22L));
        assertEquals(repayment, transaction(23L));
    }

    @Test
    void remainderInsideGraceIsOutstandingAndNotInArrears() {
        insertLoan(3L, "0", "0", "1000", "1000");
        this.jdbc.update("update m_loan set grace_on_arrears_ageing = 5 where id = ?", 3L);
        insertSchedule(31L, 3L, 1, BUSINESS_DATE.minusDays(1), 1, BUSINESS_DATE.minusDays(1), "0", "0", "0", "0", "1000", "0");
        insertCharge(32L, 3L, BUSINESS_DATE, "1000", "0", 31L);
        insertTransaction(33L, 3L, 0, 2, "400", "0", "0", "400");
        insertMapping(34L, 33L, 31L, "400");

        this.service.applyStoredPenalty(List.of(3L), BUSINESS_DATE);

        assertMoney(new BigDecimal("600"), money("select total_outstanding_derived from m_loan where id = ?", 3L));
        assertEquals(0, flag("select completed_derived from m_loan_repayment_schedule where id = ?", 31L));
        assertEquals(0, count("select count(*) from m_loan_arrears_aging where loan_id = ?", 3L));
    }

    @Test
    void oldestChargeIsPaidFirst() {
        insertLoan(4L, "0", "0", "1000", "1000");
        insertSchedule(41L, 4L, 1, LocalDate.of(2025, 11, 27), 1, LocalDate.of(2026, 10, 26), "0", "0", "0", "0", "1000", "0");
        insertCharge(42L, 4L, LocalDate.of(2025, 12, 15), "400", "0", 41L);
        insertCharge(43L, 4L, LocalDate.of(2025, 12, 1), "600", "0", 41L);
        insertTransaction(44L, 4L, 0, 2, "700", "0", "0", "700");
        insertMapping(45L, 44L, 41L, "700");

        this.service.applyStoredPenalty(List.of(4L), BUSINESS_DATE);

        assertMoney(new BigDecimal("600"), chargePaid(43L));
        assertMoney(BigDecimal.ZERO, chargeOutstanding(43L));
        assertMoney(new BigDecimal("100"), chargePaid(42L));
        assertMoney(new BigDecimal("300"), chargeOutstanding(42L));
        assertEquals(0, flag("select completed_derived from m_loan_repayment_schedule where id = ?", 41L));
        assertMoney(new BigDecimal("300"), money("select penalty_charges_overdue_derived from m_loan_arrears_aging where loan_id = ?", 4L));
    }

    @Test
    void reversedAndNonRepaymentMappingsAreIgnored() {
        insertLoan(5L, "0", "0", "1000", "1000");
        insertSchedule(51L, 5L, 1, LocalDate.of(2025, 11, 27), 1, LocalDate.of(2026, 10, 26), "0", "0", "0", "0", "1000", "0");
        insertCharge(52L, 5L, LocalDate.of(2025, 12, 2), "1000", "0", 51L);
        insertTransaction(53L, 5L, 1, 2, "1000", "0", "0", "1000");
        insertMapping(54L, 53L, 51L, "1000");

        insertLoan(6L, "0", "0", "1000", "1000");
        insertSchedule(61L, 6L, 1, LocalDate.of(2025, 11, 27), 1, LocalDate.of(2026, 10, 26), "0", "0", "0", "0", "1000", "0");
        insertCharge(62L, 6L, LocalDate.of(2025, 12, 2), "1000", "0", 61L);
        insertTransaction(63L, 6L, 0, 4, "1000", "0", "0", "1000");
        insertMapping(64L, 63L, 61L, "1000");

        final CommandProcessingResult result = this.service.applyStoredPenalty(List.of(5L, 6L), BUSINESS_DATE);

        assertEquals("unchanged", status(result, 5L));
        assertEquals("unchanged", status(result, 6L));
        assertMoney(BigDecimal.ZERO, chargePaid(52L));
        assertMoney(BigDecimal.ZERO, chargePaid(62L));
        assertEquals(1, flag("select completed_derived from m_loan_repayment_schedule where id = ?", 51L));
        assertEquals(1, flag("select is_reversed from m_loan_transaction where id = ?", 53L));
        assertMoney(new BigDecimal("1000"), money("select total_outstanding_derived from m_loan where id = ?", 5L));
        assertMoney(new BigDecimal("1000"), money("select total_outstanding_derived from m_loan where id = ?", 6L));
    }

    @Test
    void unlinkedChargeUsesTheInstallmentDueOnOrBeforeTheChargeDate() {
        insertLoan(7L, "0", "0", "1000", "1000");
        insertSchedule(71L, 7L, 9, LocalDate.of(2025, 11, 27), 1, LocalDate.of(2026, 10, 26), "0", "0", "0", "0", "1000", "0");
        insertSchedule(72L, 7L, 10, LocalDate.of(2025, 12, 27), 0, null, "0", "0", "0", "0", "0", "0");
        insertCharge(73L, 7L, LocalDate.of(2025, 12, 2), "1000", "0", null);
        insertTransaction(74L, 7L, 0, 2, "1000", "0", "0", "1000");
        insertMapping(75L, 74L, 71L, "1000");

        this.service.applyStoredPenalty(List.of(7L), BUSINESS_DATE);

        assertMoney(new BigDecimal("1000"), penaltyPaid(71L));
        assertEquals(1, flag("select completed_derived from m_loan_repayment_schedule where id = ?", 71L));
        assertMoney(BigDecimal.ZERO, penaltyAmount(72L));
        assertMoney(BigDecimal.ZERO, money("select penalty_charges_outstanding_derived from m_loan where id = ?", 7L));
    }

    @Test
    void aLoanOutsideTheListIsLeftUntouched() {
        insertLoan(8L, "0", "0", "1000", "1000");
        insertSchedule(81L, 8L, 1, LocalDate.of(2025, 11, 27), 1, LocalDate.of(2026, 10, 26), "0", "0", "0", "0", "1000", "0");
        insertCharge(82L, 8L, LocalDate.of(2025, 12, 2), "1000", "0", 81L);
        insertTransaction(83L, 8L, 0, 2, "1000", "0", "0", "1000");
        insertMapping(84L, 83L, 81L, "1000");

        insertLoan(9L, "0", "0", "500", "500");
        insertSchedule(91L, 9L, 1, LocalDate.of(2025, 11, 27), 1, LocalDate.of(2026, 10, 26), "0", "0", "0", "0", "500", "0");
        insertCharge(92L, 9L, LocalDate.of(2025, 12, 2), "500", "0", 91L);
        insertTransaction(93L, 9L, 0, 2, "500", "0", "0", "500");
        insertMapping(94L, 93L, 91L, "500");
        final Map<String, Object> untouchedRepayment = transaction(93L);

        final CommandProcessingResult result = this.service.applyStoredPenalty(List.of(8L), BUSINESS_DATE);

        assertEquals("applied", status(result, 8L));
        assertEquals(1, result.getChanges().get("loanCount"));
        assertMoney(new BigDecimal("1000"), chargePaid(82L));
        assertMoney(BigDecimal.ZERO, chargePaid(92L));
        assertEquals(1, flag("select completed_derived from m_loan_repayment_schedule where id = ?", 91L));
        assertMoney(new BigDecimal("500"), money("select total_outstanding_derived from m_loan where id = ?", 9L));
        assertEquals(untouchedRepayment, transaction(93L));
    }

    @Test
    void aMissingLoanIsReportedAndTheListedLoanIsStillRepaired() {
        insertLoan(10L, "0", "0", "1000", "1000");
        insertSchedule(101L, 10L, 1, LocalDate.of(2025, 11, 27), 1, LocalDate.of(2026, 10, 26), "0", "0", "0", "0", "1000", "0");
        insertCharge(102L, 10L, LocalDate.of(2025, 12, 2), "1000", "0", 101L);
        insertTransaction(103L, 10L, 0, 2, "1000", "0", "0", "1000");
        insertMapping(104L, 103L, 101L, "1000");

        final CommandProcessingResult result = this.service.applyStoredPenalty(List.of(10L, 999999L), BUSINESS_DATE);

        assertEquals("applied", status(result, 10L));
        assertEquals("notFound", status(result, 999999L));
        assertMoney(new BigDecimal("1000"), chargePaid(102L));
        assertMoney(BigDecimal.ZERO, money("select total_outstanding_derived from m_loan where id = ?", 10L));
    }

    private void createSchema() {
        this.jdbc.execute("create table m_loan (id bigint primary key, loan_status_id int, grace_on_arrears_ageing int,"
                + " principal_outstanding_derived decimal(19,6) default 0, interest_outstanding_derived decimal(19,6) default 0,"
                + " fee_charges_outstanding_derived decimal(19,6) default 0, penalty_charges_charged_derived decimal(19,6) default 0,"
                + " penalty_charges_repaid_derived decimal(19,6) default 0, penalty_charges_waived_derived decimal(19,6) default 0,"
                + " penalty_charges_writtenoff_derived decimal(19,6) default 0, penalty_charges_outstanding_derived decimal(19,6) default 0,"
                + " total_outstanding_derived decimal(19,6) default 0, total_repayment_derived decimal(19,6) default 0,"
                + " principal_repaid_derived decimal(19,6) default 0, interest_repaid_derived decimal(19,6) default 0,"
                + " fee_charges_repaid_derived decimal(19,6) default 0, interest_waived_derived decimal(19,6) default 0,"
                + " fee_charges_waived_derived decimal(19,6) default 0, principal_disbursed_derived decimal(19,6) default 0,"
                + " interest_charged_derived decimal(19,6) default 0, fee_charges_charged_derived decimal(19,6) default 0,"
                + " total_waived_derived decimal(19,6) default 0, total_expected_repayment_derived decimal(19,6) default 0,"
                + " total_expected_costofloan_derived decimal(19,6) default 0)");
        this.jdbc.execute("create table m_loan_repayment_schedule (id bigint primary key, loan_id bigint, installment int, duedate date,"
                + " recalculated_interest_component tinyint default 0, completed_derived tinyint, obligations_met_on_date date,"
                + " principal_amount decimal(19,6) default 0, principal_completed_derived decimal(19,6) default 0,"
                + " principal_writtenoff_derived decimal(19,6) default 0, interest_amount decimal(19,6) default 0,"
                + " interest_completed_derived decimal(19,6) default 0, interest_waived_derived decimal(19,6) default 0,"
                + " interest_writtenoff_derived decimal(19,6) default 0, fee_charges_amount decimal(19,6) default 0,"
                + " fee_charges_completed_derived decimal(19,6) default 0, fee_charges_waived_derived decimal(19,6) default 0,"
                + " fee_charges_writtenoff_derived decimal(19,6) default 0, penalty_charges_amount decimal(19,6) default 0,"
                + " penalty_charges_completed_derived decimal(19,6) default 0, penalty_charges_waived_derived decimal(19,6) default 0,"
                + " penalty_charges_writtenoff_derived decimal(19,6) default 0)");
        this.jdbc.execute("create table m_loan_charge (id bigint primary key, loan_id bigint, is_penalty tinyint, is_active tinyint,"
                + " waived tinyint, due_for_collection_as_of_date date, amount decimal(19,6), amount_paid_derived decimal(19,6),"
                + " amount_waived_derived decimal(19,6) default 0, amount_writtenoff_derived decimal(19,6) default 0,"
                + " amount_outstanding_derived decimal(19,6), is_paid_derived tinyint)");
        this.jdbc.execute(
                "create table m_loan_overdue_installment_charge (id bigint primary key, loan_charge_id bigint, loan_schedule_id bigint)");
        this.jdbc.execute("create table m_loan_transaction (id bigint primary key, loan_id bigint, is_reversed tinyint,"
                + " transaction_type_enum int, amount decimal(19,6), principal_portion_derived decimal(19,6),"
                + " interest_portion_derived decimal(19,6), penalty_charges_portion_derived decimal(19,6))");
        this.jdbc.execute("create table m_loan_transaction_repayment_schedule_mapping (id bigint primary key, loan_transaction_id bigint,"
                + " loan_repayment_schedule_id bigint, penalty_charges_portion_derived decimal(19,6))");
        this.jdbc.execute("create table m_loan_arrears_aging (loan_id bigint primary key, principal_overdue_derived decimal(19,6),"
                + " interest_overdue_derived decimal(19,6), fee_charges_overdue_derived decimal(19,6),"
                + " penalty_charges_overdue_derived decimal(19,6), total_overdue_derived decimal(19,6), overdue_since_date_derived date)");
    }

    private void insertLoan(final long id, final String principalOutstanding, final String interestOutstanding,
            final String penaltyOutstanding, final String totalOutstanding) {
        this.jdbc.update(
                "insert into m_loan (id, loan_status_id, principal_outstanding_derived, interest_outstanding_derived,"
                        + " penalty_charges_charged_derived, penalty_charges_outstanding_derived, total_outstanding_derived)"
                        + " values (?, 300, ?, ?, ?, ?, ?)",
                id, new BigDecimal(principalOutstanding), new BigDecimal(interestOutstanding), new BigDecimal(penaltyOutstanding),
                new BigDecimal(penaltyOutstanding), new BigDecimal(totalOutstanding));
    }

    private void insertSchedule(final long id, final long loanId, final int installment, final LocalDate due, final int completed,
            final LocalDate obligationsMet, final String principal, final String principalPaid, final String interest,
            final String interestPaid, final String penalty, final String penaltyPaid) {
        this.jdbc.update(
                "insert into m_loan_repayment_schedule (id, loan_id, installment, duedate, completed_derived, obligations_met_on_date,"
                        + " principal_amount, principal_completed_derived, interest_amount, interest_completed_derived,"
                        + " penalty_charges_amount, penalty_charges_completed_derived) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, loanId, installment, Date.valueOf(due), completed, obligationsMet == null ? null : Date.valueOf(obligationsMet),
                new BigDecimal(principal), new BigDecimal(principalPaid), new BigDecimal(interest), new BigDecimal(interestPaid),
                new BigDecimal(penalty), new BigDecimal(penaltyPaid));
    }

    private void insertCharge(final long id, final long loanId, final LocalDate due, final String amount, final String paid,
            final Long scheduleId) {
        final BigDecimal amountValue = new BigDecimal(amount);
        final BigDecimal paidValue = new BigDecimal(paid);
        this.jdbc.update(
                "insert into m_loan_charge (id, loan_id, is_penalty, is_active, waived, due_for_collection_as_of_date, amount,"
                        + " amount_paid_derived, amount_outstanding_derived, is_paid_derived) values (?,?,1,1,0,?,?,?,?,0)",
                id, loanId, Date.valueOf(due), amountValue, paidValue, amountValue.subtract(paidValue));
        if (scheduleId != null) {
            this.jdbc.update("insert into m_loan_overdue_installment_charge (id, loan_charge_id, loan_schedule_id) values (?,?,?)", id, id,
                    scheduleId);
        }
    }

    private void insertTransaction(final long id, final long loanId, final int reversed, final int type, final String amount,
            final String principal, final String interest, final String penalty) {
        this.jdbc.update(
                "insert into m_loan_transaction (id, loan_id, is_reversed, transaction_type_enum, amount,"
                        + " principal_portion_derived, interest_portion_derived, penalty_charges_portion_derived) values (?,?,?,?,?,?,?,?)",
                id, loanId, reversed, type, new BigDecimal(amount), new BigDecimal(principal), new BigDecimal(interest),
                new BigDecimal(penalty));
    }

    private void insertMapping(final long id, final long transactionId, final long scheduleId, final String penalty) {
        this.jdbc.update("insert into m_loan_transaction_repayment_schedule_mapping (id, loan_transaction_id, loan_repayment_schedule_id,"
                + " penalty_charges_portion_derived) values (?,?,?,?)", id, transactionId, scheduleId, new BigDecimal(penalty));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> row(final CommandProcessingResult result, final long loanId) {
        final List<Map<String, Object>> results = (List<Map<String, Object>>) result.getChanges().get("results");
        return results.stream().filter(item -> loanId == ((Number) item.get("loanId")).longValue()).findFirst().orElseThrow();
    }

    private String status(final CommandProcessingResult result, final long loanId) {
        return (String) row(result, loanId).get("status");
    }

    private Map<String, Object> transaction(final long id) {
        return this.jdbc
                .queryForMap("select id, is_reversed, transaction_type_enum, amount, principal_portion_derived, interest_portion_derived,"
                        + " penalty_charges_portion_derived from m_loan_transaction where id = ?", id);
    }

    private BigDecimal chargePaid(final long id) {
        return money("select amount_paid_derived from m_loan_charge where id = ?", id);
    }

    private BigDecimal chargeOutstanding(final long id) {
        return money("select amount_outstanding_derived from m_loan_charge where id = ?", id);
    }

    private BigDecimal penaltyPaid(final long scheduleId) {
        return money("select penalty_charges_completed_derived from m_loan_repayment_schedule where id = ?", scheduleId);
    }

    private BigDecimal penaltyAmount(final long scheduleId) {
        return money("select penalty_charges_amount from m_loan_repayment_schedule where id = ?", scheduleId);
    }

    private BigDecimal money(final String sql, final Object... args) {
        return this.jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private int flag(final String sql, final Object... args) {
        return this.jdbc.queryForObject(sql, Integer.class, args);
    }

    private int count(final String sql, final Object... args) {
        return this.jdbc.queryForObject(sql, Integer.class, args);
    }

    private LocalDate scheduleDate(final long scheduleId) {
        final Date value = this.jdbc.queryForObject("select obligations_met_on_date from m_loan_repayment_schedule where id = ?",
                Date.class, scheduleId);
        return value == null ? null : value.toLocalDate();
    }

    private static void assertMoney(final BigDecimal expected, final BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, expected.compareTo(actual), "expected " + expected.toPlainString() + " but was " + actual.toPlainString());
    }
}
