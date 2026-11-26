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
package org.apache.fineract.portfolio.loanaccount.rescheduleloan.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.junit.jupiter.api.Test;

class RescheduleFromDateBeforeFirstInstallmentTest {

    private final LoanRescheduleRequestDataValidator validator = new LoanRescheduleRequestDataValidator(new FromJsonHelper(),
            mock(LoanRescheduleRequestRepository.class));

    @Test
    void acceptsRescheduleFromDateWithNoMatchingExistingInstallment() {
        LocalDate rescheduleFrom = LocalDate.of(2026, 7, 1);
        Loan loan = loanWithSchedule(List.of(installment(1, LocalDate.of(2027, 1, 1))), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        assertDoesNotThrow(() -> validateCreate(loan, rescheduleFrom, "\"newInterestRate\":\"15\"", true));
    }

    @Test
    void rejectsRescheduleFromDateAfterLastInstallment() {
        LocalDate rescheduleFrom = LocalDate.of(2028, 1, 1);
        Loan loan = loanWithSchedule(List.of(installment(1, LocalDate.of(2027, 1, 1))), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validateCreate(loan, rescheduleFrom, "\"newInterestRate\":\"15\"", true));
        assertHasCode(e, "repayment.schedule.installment.does.not.exist");
    }

    @Test
    void rejectsRescheduleFromDateBeforeLoanDisbursementDate() {
        LocalDate rescheduleFrom = LocalDate.of(2025, 11, 1);
        Loan loan = loanWithSchedule(List.of(installment(1, LocalDate.of(2027, 1, 1))), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validateCreate(loan, rescheduleFrom, "\"newInterestRate\":\"15\"", true));
        assertHasCode(e, "before.loan.disbursement.date");
    }

    @Test
    void rejectsResolvedPaidInstallmentWhenRescheduleFromDateIsDayBeforeDueDate() {
        LocalDate installmentDue = LocalDate.of(2026, 7, 1);
        LocalDate rescheduleFrom = installmentDue.minusDays(1);
        LoanRepaymentScheduleInstallment paid = installment(7, installmentDue);
        paid.updateObligationMet(true);
        Loan loan = loanWithSchedule(List.of(paid), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validateCreate(loan, rescheduleFrom, "\"newInterestRate\":\"15\"", true));
        assertHasCode(e, "repayment.schedule.installment.obligation.met");
    }

    @Test
    void acceptsResolvedPaidInstallmentWhenRecalculateEvenIfObligationsMetAndDateBeforeDue() {
        LocalDate installmentDue = LocalDate.of(2026, 7, 1);
        LocalDate rescheduleFrom = installmentDue.minusDays(1);
        LoanRepaymentScheduleInstallment paid = installment(7, installmentDue);
        paid.updateObligationMet(true);
        Loan loan = loanWithSchedule(List.of(paid), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        assertDoesNotThrow(() -> validateCreate(loan, rescheduleFrom,
                "\"newInterestRate\":\"15\",\"recalculateEvenIfObligationsMet\":true", true));
    }

    @Test
    void approveRejectsResolvedPaidInstallmentWhenRescheduleFromDateIsDayBeforeDueDate() {
        LocalDate installmentDue = LocalDate.of(2026, 7, 1);
        LocalDate rescheduleFrom = installmentDue.minusDays(1);
        LoanRepaymentScheduleInstallment paid = installment(7, installmentDue);
        paid.updateObligationMet(true);
        Loan loan = loanWithSchedule(List.of(paid), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        LoanRescheduleRequest request = pendingRequest(loan, rescheduleFrom, false);
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class, () -> validateApprove(request));
        assertHasCode(e, "repayment.schedule.installment.obligation.met");
    }

    @Test
    void approveAcceptsResolvedPaidInstallmentWhenRecalculateEvenIfObligationsMetAndDateBeforeDue() {
        LocalDate installmentDue = LocalDate.of(2026, 7, 1);
        LocalDate rescheduleFrom = installmentDue.minusDays(1);
        LoanRepaymentScheduleInstallment paid = installment(7, installmentDue);
        paid.updateObligationMet(true);
        Loan loan = loanWithSchedule(List.of(paid), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        LoanRescheduleRequest request = pendingRequest(loan, rescheduleFrom, true);
        assertDoesNotThrow(() -> validateApprove(request));
    }

    @Test
    void rejectsPaidInstallmentWhenObligationsMetAndNoRecalculateFlag() {
        LocalDate rescheduleFrom = LocalDate.of(2026, 7, 1);
        LoanRepaymentScheduleInstallment matched = installment(7, rescheduleFrom);
        matched.updateObligationMet(true);
        Loan loan = loanWithSchedule(List.of(matched), matched, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(matched);
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validateCreate(loan, rescheduleFrom, "\"newInterestRate\":\"15\"", true));
        assertHasCode(e, "repayment.schedule.installment.obligation.met");
    }

    @Test
    void acceptsPaidInstallmentWhenRecalculateEvenIfObligationsMet() {
        LocalDate rescheduleFrom = LocalDate.of(2026, 7, 1);
        LoanRepaymentScheduleInstallment matched = installment(7, rescheduleFrom);
        matched.updateObligationMet(true);
        Loan loan = loanWithSchedule(List.of(matched), matched, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(matched);
        assertDoesNotThrow(() -> validateCreate(loan, rescheduleFrom,
                "\"newInterestRate\":\"15\",\"recalculateEvenIfObligationsMet\":true", true));
    }

    @Test
    void acceptsUnpaidInstallmentWhenObligationsNotMet() {
        LocalDate rescheduleFrom = LocalDate.of(2026, 7, 1);
        LoanRepaymentScheduleInstallment matched = installment(7, rescheduleFrom);
        matched.updateObligationMet(false);
        Loan loan = loanWithSchedule(List.of(matched), matched, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(matched);
        assertDoesNotThrow(() -> validateCreate(loan, rescheduleFrom, "\"newInterestRate\":\"15\"", true));
    }

    @Test
    void requiresOverdueChargeHandlingEvenWhenInstallmentLookupIsNull() {
        LocalDate rescheduleFrom = LocalDate.of(2026, 7, 1);
        LoanCharge overdue = mock(LoanCharge.class);
        when(overdue.isOverdueInstallmentCharge()).thenReturn(true);
        when(overdue.getDueLocalDate()).thenReturn(LocalDate.of(2027, 6, 1));
        when(overdue.isWaived()).thenReturn(false);
        when(overdue.isPaid()).thenReturn(false);
        Loan loan = loanWithSchedule(List.of(installment(1, LocalDate.of(2027, 1, 1))), null, List.of(overdue));
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validateCreate(loan, rescheduleFrom, "\"newInterestRate\":\"15\"", false));
        assertHasCode(e, "not.allowed.due.to.overdue.charges.must.choose.option");
    }

    @Test
    void approveAcceptsRescheduleFromDateWithNoMatchingExistingInstallment() {
        LocalDate rescheduleFrom = LocalDate.of(2026, 7, 1);
        Loan loan = loanWithSchedule(List.of(installment(1, LocalDate.of(2027, 1, 1))), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        LoanRescheduleRequest request = pendingRequest(loan, rescheduleFrom, false);
        assertDoesNotThrow(() -> validateApprove(request));
    }

    @Test
    void approveRejectsPaidInstallmentWhenObligationsMetAndNoRecalculateFlag() {
        LocalDate rescheduleFrom = LocalDate.of(2026, 7, 1);
        LoanRepaymentScheduleInstallment matched = installment(7, rescheduleFrom);
        matched.updateObligationMet(true);
        Loan loan = loanWithSchedule(List.of(matched), matched, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(matched);
        LoanRescheduleRequest request = pendingRequest(loan, rescheduleFrom, false);
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class, () -> validateApprove(request));
        assertHasCode(e, "repayment.schedule.installment.obligation.met");
    }

    @Test
    void approveAcceptsPaidInstallmentWhenRecalculateEvenIfObligationsMet() {
        LocalDate rescheduleFrom = LocalDate.of(2026, 7, 1);
        LoanRepaymentScheduleInstallment matched = installment(7, rescheduleFrom);
        matched.updateObligationMet(true);
        Loan loan = loanWithSchedule(List.of(matched), matched, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(matched);
        LoanRescheduleRequest request = pendingRequest(loan, rescheduleFrom, true);
        assertDoesNotThrow(() -> validateApprove(request));
    }

    @Test
    void approveRejectsRescheduleFromDateAfterLastInstallment() {
        LocalDate rescheduleFrom = LocalDate.of(2028, 1, 1);
        Loan loan = loanWithSchedule(List.of(installment(1, LocalDate.of(2027, 1, 1))), null, new ArrayList<>());
        when(loan.getRepaymentScheduleInstallment(eq(rescheduleFrom))).thenReturn(null);
        LoanRescheduleRequest request = pendingRequest(loan, rescheduleFrom, false);
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class, () -> validateApprove(request));
        assertHasCode(e, "does.not.exist");
    }

    private void validateCreate(Loan loan, LocalDate rescheduleFromDate, String extraFields, boolean includeOverdueHandling) {
        String dateStr = formatDate(rescheduleFromDate);
        String overdue = includeOverdueHandling ? "\"overdueChargeHandling\":{\"id\":1326,\"name\":\"Ignore Charges\"}," : "";
        String json = "{\"submittedOnDate\":\"28 September 2026\",\"rescheduleFromDate\":\"" + dateStr + "\","
                + "\"rescheduleReasonId\":693," + overdue + "\"loanId\":\"422415\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\","
                + extraFields + "}";
        JsonCommand command = JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null,
                null, null, null, null, null, null, null);
        validator.validateForCreateAction(command, loan);
    }

    private void validateApprove(LoanRescheduleRequest request) {
        String json = "{\"approvedOnDate\":\"28 September 2026\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}";
        JsonCommand command = JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null,
                null, null, null, null, null, null, null);
        validator.validateForApproveAction(command, request);
    }

    private static LoanRescheduleRequest pendingRequest(Loan loan, LocalDate rescheduleFrom, boolean recalculateEvenIfObligationsMet) {
        LoanRescheduleRequest request = mock(LoanRescheduleRequest.class);
        when(request.getLoan()).thenReturn(loan);
        when(request.getRescheduleFromDate()).thenReturn(rescheduleFrom);
        when(request.getSubmittedOnDate()).thenReturn(LocalDate.of(2026, 9, 28));
        when(request.getStatusEnum()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getValue());
        when(request.isRecalculateEvenIfObligationsMet()).thenReturn(recalculateEvenIfObligationsMet);
        return request;
    }

    private static Loan loanWithSchedule(List<LoanRepaymentScheduleInstallment> schedule,
            LoanRepaymentScheduleInstallment defaultMatch, List<LoanCharge> charges) {
        Loan loan = mock(Loan.class);
        when(loan.status()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getDisbursementDate()).thenReturn(LocalDate.of(2025, 12, 1));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(schedule);
        when(loan.getRepaymentScheduleInstallment(any(LocalDate.class))).thenReturn(defaultMatch);
        when(loan.fetchRepaymentScheduleInstallment(anyInt())).thenAnswer(invocation -> {
            Integer installmentNumber = invocation.getArgument(0);
            return schedule.stream().filter(i -> i.getInstallmentNumber().equals(installmentNumber)).findFirst().orElse(null);
        });
        when(loan.getLoanCharges()).thenReturn(charges);
        when(loan.getTotal_extensions()).thenReturn(null);
        return loan;
    }

    private static LoanRepaymentScheduleInstallment installment(int number, LocalDate due) {
        return new LoanRepaymentScheduleInstallment(null, number, due.minusMonths(1), due, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, false, new HashSet<>());
    }

    private static String formatDate(LocalDate date) {
        return String.format("%02d %s %d", date.getDayOfMonth(), monthName(date.getMonthValue()), date.getYear());
    }

    private static String monthName(int month) {
        return switch (month) {
            case 1 -> "January";
            case 2 -> "February";
            case 3 -> "March";
            case 4 -> "April";
            case 5 -> "May";
            case 6 -> "June";
            case 7 -> "July";
            case 8 -> "August";
            case 9 -> "September";
            case 10 -> "October";
            case 11 -> "November";
            case 12 -> "December";
            default -> throw new IllegalArgumentException("month");
        };
    }

    private static void assertHasCode(PlatformApiDataValidationException e, String fragment) {
        List<String> codes = e.getErrors().stream().map(err -> err.getUserMessageGlobalisationCode()).collect(Collectors.toList());
        assertTrue(codes.stream().anyMatch(code -> code != null && code.contains(fragment)), "expected code containing " + fragment
                + " but got " + codes);
    }
}