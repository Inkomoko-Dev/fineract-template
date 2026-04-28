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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanRepaymentScheduleProcessingWrapperTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUpMoneyHelper() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
    }

    @AfterEach
    void resetMoneyHelper() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void displaysExplicitPenaltyAllocationAgainstMappedInstallment() {
        final LoanRepaymentScheduleInstallment februaryInstallment = installment(1, LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 5));
        final LoanRepaymentScheduleInstallment marchInstallment = installment(2, LocalDate.of(2026, 2, 5),
                LocalDate.of(2026, 3, 5));
        final LoanCharge dailyLateFeeCharge = dailyLateFeeCharge(LocalDate.of(2026, 2, 11), new BigDecimal("1.190479"));
        final LoanInstallmentCharge allocation = new LoanInstallmentCharge(new BigDecimal("1.190479"), dailyLateFeeCharge,
                februaryInstallment);

        dailyLateFeeCharge.addLoanInstallmentCharges(List.of(allocation));

        new LoanRepaymentScheduleProcessingWrapper().reprocess(KES, LocalDate.of(2026, 1, 5),
                List.of(februaryInstallment, marchInstallment), Set.of(dailyLateFeeCharge));

        assertEquals(0, new BigDecimal("1.19").compareTo(februaryInstallment.getPenaltyChargesCharged(KES).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(marchInstallment.getPenaltyChargesCharged(KES).getAmount()));
    }

    @Test
    void splitsCumulativeDailyPenaltyAcrossMappedOverdueInstallments() {
        final LoanRepaymentScheduleInstallment februaryInstallment = installment(1, LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 5));
        final LoanRepaymentScheduleInstallment marchInstallment = installment(2, LocalDate.of(2026, 2, 5),
                LocalDate.of(2026, 3, 5));
        final LoanRepaymentScheduleInstallment aprilInstallment = installment(3, LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 4, 5));
        final LoanRepaymentScheduleInstallment mayInstallment = installment(4, LocalDate.of(2026, 4, 5),
                LocalDate.of(2026, 5, 5));
        final LoanCharge dailyLateFeeCharge = dailyLateFeeCharge(LocalDate.of(2026, 4, 11), new BigDecimal("2.222227"));

        dailyLateFeeCharge.addLoanInstallmentCharges(List.of(
                new LoanInstallmentCharge(new BigDecimal("1.111113"), dailyLateFeeCharge, marchInstallment),
                new LoanInstallmentCharge(new BigDecimal("1.111114"), dailyLateFeeCharge, aprilInstallment)));

        new LoanRepaymentScheduleProcessingWrapper().reprocess(KES, LocalDate.of(2026, 1, 5),
                List.of(februaryInstallment, marchInstallment, aprilInstallment, mayInstallment), Set.of(dailyLateFeeCharge));

        assertEquals(0, BigDecimal.ZERO.compareTo(februaryInstallment.getPenaltyChargesCharged(KES).getAmount()));
        assertEquals(0, new BigDecimal("1.11").compareTo(marchInstallment.getPenaltyChargesCharged(KES).getAmount()));
        assertEquals(0, new BigDecimal("1.11").compareTo(aprilInstallment.getPenaltyChargesCharged(KES).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(mayInstallment.getPenaltyChargesCharged(KES).getAmount()));
    }

    private LoanRepaymentScheduleInstallment installment(final Integer installmentNumber, final LocalDate fromDate,
            final LocalDate dueDate) {
        return new LoanRepaymentScheduleInstallment(null, installmentNumber, fromDate, dueDate, new BigDecimal("1666.67"),
                new BigDecimal("83.33"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
    }

    private LoanCharge dailyLateFeeCharge(final LocalDate dueDate, final BigDecimal amount) {
        final LoanCharge loanCharge = new LoanCharge();
        ReflectionTestUtils.setField(loanCharge, "penaltyCharge", true);
        ReflectionTestUtils.setField(loanCharge, "chargeTime", ChargeTimeType.OVERDUE_INSTALLMENT.getValue());
        ReflectionTestUtils.setField(loanCharge, "chargeCalculation", ChargeCalculationType.FLAT.getValue());
        ReflectionTestUtils.setField(loanCharge, "dueDate", dueDate);
        ReflectionTestUtils.setField(loanCharge, "amount", amount);
        ReflectionTestUtils.setField(loanCharge, "amountOutstanding", amount);
        ReflectionTestUtils.setField(loanCharge, "amountOrPercentage", amount);
        return loanCharge;
    }
}
