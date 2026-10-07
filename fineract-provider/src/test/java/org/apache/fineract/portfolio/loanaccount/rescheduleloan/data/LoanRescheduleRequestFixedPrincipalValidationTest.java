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
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.Test;

class LoanRescheduleRequestFixedPrincipalValidationTest {

    private final LoanRescheduleRequestDataValidator validator = new LoanRescheduleRequestDataValidator(new FromJsonHelper());

    @Test
    void acceptsPositiveFixedPrincipalAmount() {
        assertDoesNotThrow(() -> validate("\"newPrincipalDueFixedAmount\":\"30,000\""));
    }

    @Test
    void acceptsPercentageWithinRange() {
        assertDoesNotThrow(() -> validate("\"newFixedPrincipalPercentagePerInstallment\":\"4.2857\""));
    }

    @Test
    void rejectsZeroFixedPrincipalAmount() {
        assertRejected("\"newPrincipalDueFixedAmount\":\"0\"", "newPrincipalDueFixedAmount");
    }

    @Test
    void rejectsNegativeFixedPrincipalAmount() {
        assertRejected("\"newPrincipalDueFixedAmount\":\"-30000\"", "newPrincipalDueFixedAmount");
    }

    @Test
    void rejectsZeroPercentage() {
        assertRejected("\"newFixedPrincipalPercentagePerInstallment\":\"0\"", "newFixedPrincipalPercentagePerInstallment");
    }

    @Test
    void rejectsPercentageAboveOneHundred() {
        assertRejected("\"newFixedPrincipalPercentagePerInstallment\":\"100.5\"", "newFixedPrincipalPercentagePerInstallment");
    }

    @Test
    void rejectsAmountAndPercentageTogether() {
        assertRejected("\"newPrincipalDueFixedAmount\":\"30000\",\"newFixedPrincipalPercentagePerInstallment\":\"4.2857\"",
                "newFixedPrincipalPercentagePerInstallment");
    }

    @Test
    void rejectsFixedPrincipalCombinedWithEmiChange() {
        assertRejected("\"newPrincipalDueFixedAmount\":\"30000\",\"emi\":\"40000\",\"endDate\":\"01 October 2026\"", "emi");
    }

    private void assertRejected(String fields, String parameter) {
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class, () -> validate(fields));
        List<String> parameters = e.getErrors().stream().map(error -> error.getParameterName()).collect(Collectors.toList());
        assertTrue(parameters.contains(parameter), "expected an error on " + parameter + " but got " + parameters);
    }

    private void validate(String fields) {
        String json = "{\"submittedOnDate\":\"28 September 2026\",\"rescheduleFromDate\":\"01 July 2026\",\"rescheduleReasonId\":693,"
                + "\"overdueChargeHandling\":{\"id\":1326,\"name\":\"Ignore Charges\"},\"loanId\":\"422415\","
                + "\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"," + fields + "}";
        JsonCommand command = JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null,
                null, null, null, null, null, null, null);
        this.validator.validateForCreateAction(command, loan());
    }

    private static Loan loan() {
        Loan loan = mock(Loan.class);
        when(loan.status()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getDisbursementDate()).thenReturn(LocalDate.of(2025, 12, 1));
        LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 7, LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1), new BigDecimal("70000"), new BigDecimal("5833.33"), BigDecimal.ZERO, BigDecimal.ZERO, false, new HashSet<>());
        when(loan.getRepaymentScheduleInstallment(any(LocalDate.class))).thenReturn(installment);
        when(loan.getLoanCharges()).thenReturn(new ArrayList<>());
        when(loan.getTotal_extensions()).thenReturn(null);
        return loan;
    }
}
