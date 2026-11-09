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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.Test;

class MultiTrancheRescheduleRulesTest {

    private final LoanRescheduleRequestDataValidator validator = new LoanRescheduleRequestDataValidator(new FromJsonHelper());

    @Test
    void singleDisbursementLoanDoesNotNeedTheTrancheConfirmation() {
        assertNull(MultiTrancheRescheduleRules.rejection(loan(false, List.of()), LocalDate.of(2026, 7, 1), null));
    }

    @Test
    void refusesATrancheLoanUntilTheOfficerConfirmsTheScheduleLimits() {
        final Loan loan = loan(true, List.of(disbursed(LocalDate.of(2026, 1, 15), "100000"), pending("50000")));
        final String rejection = MultiTrancheRescheduleRules.rejection(loan, LocalDate.of(2026, 7, 1), false);

        assertTrue(rejection.contains("principal already disbursed"));
        assertTrue(rejection.contains("1 expected tranche"));
        assertTrue(rejection.contains("can replace this reschedule"));
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validate(loan, false));
        assertTrue(exception.getErrors().get(0).getDefaultUserMessage().contains("1 expected tranche"));
    }

    @Test
    void tellsTheOfficerWhenALaterTrancheIsAlreadyInsideTheReschedule() {
        final Loan loan = loan(true, List.of(disbursed(LocalDate.of(2026, 1, 15), "100000"),
                disbursed(LocalDate.of(2026, 8, 1), "40000"), pending("20000")));
        final String rejection = MultiTrancheRescheduleRules.rejection(loan, LocalDate.of(2026, 7, 1), null);

        assertTrue(rejection.contains("Other tranches have already been disbursed"));
        assertTrue(rejection.contains("2026-08-01"));
        assertTrue(rejection.contains("folded into the rebuilt installments"));
    }

    @Test
    void acceptsTheRescheduleOnceTheOfficerConfirms() {
        final Loan loan = loan(true, List.of(disbursed(LocalDate.of(2026, 1, 15), "100000"), pending("50000")));
        assertNull(MultiTrancheRescheduleRules.rejection(loan, LocalDate.of(2026, 7, 1), true));
        assertDoesNotThrow(() -> validate(loan, true));
    }

    @Test
    void refusesATrancheLoanThatHasNotBeenDisbursed() {
        final Loan loan = loan(true, List.of(pending("100000"), pending("50000")));
        assertTrue(MultiTrancheRescheduleRules.NOTHING_DISBURSED.equals(MultiTrancheRescheduleRules.rejection(loan,
                LocalDate.of(2026, 7, 1), true)));
    }

    private void validate(Loan loan, boolean acknowledged) {
        String json = "{\"submittedOnDate\":\"28 September 2026\",\"rescheduleFromDate\":\"01 July 2026\",\"rescheduleReasonId\":693,"
                + "\"overdueChargeHandling\":{\"id\":1326,\"name\":\"Ignore Charges\"},\"newInterestRate\":\"15\","
                + "\"loanId\":\"422415\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\","
                + "\"acknowledgeMultiTrancheReschedule\":" + acknowledged + "}";
        JsonCommand command = JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null,
                null, null, null, null, null, null, null);
        this.validator.validateForCreateAction(command, loan);
    }

    private static LoanDisbursementDetails disbursed(LocalDate on, String principal) {
        return new LoanDisbursementDetails(on, on, new BigDecimal(principal), new BigDecimal(principal));
    }

    private static LoanDisbursementDetails pending(String principal) {
        return new LoanDisbursementDetails(LocalDate.of(2026, 6, 1), null, new BigDecimal(principal), null);
    }

    private static Loan loan(boolean multiDisburse, List<LoanDisbursementDetails> tranches) {
        Loan loan = mock(Loan.class);
        when(loan.status()).thenReturn(LoanStatus.ACTIVE);
        when(loan.isMultiDisburmentLoan()).thenReturn(multiDisburse);
        when(loan.getDisbursementDetails()).thenReturn(tranches);
        when(loan.getDisbursementDate()).thenReturn(LocalDate.of(2025, 12, 1));
        LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 7, LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1), new BigDecimal("70000"), new BigDecimal("5833.33"), BigDecimal.ZERO, BigDecimal.ZERO, false,
                new HashSet<>());
        when(loan.getRepaymentScheduleInstallment(any(LocalDate.class))).thenReturn(installment);
        when(loan.getLoanCharges()).thenReturn(new ArrayList<>());
        when(loan.getTotal_extensions()).thenReturn(null);
        return loan;
    }
}
