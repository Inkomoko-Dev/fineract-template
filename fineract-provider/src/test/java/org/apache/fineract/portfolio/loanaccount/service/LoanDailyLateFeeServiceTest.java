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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanDailyLateFeeServiceTest {

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
    void calculatesDailyLateFeeRateWithThirtyDayCommercialMonth() {
        final BigDecimal dailyRate = LoanDailyLateFeeService.calculateDailyLateFeeRate(new BigDecimal("2"), DaysInMonthType.DAYS_30,
                LocalDate.of(2026, 4, 1));
        final BigDecimal dailyPenaltyAmount = new BigDecimal("2000").multiply(dailyRate, MoneyHelper.getMathContext()).setScale(6,
                MoneyHelper.getRoundingMode());
        final BigDecimal postedPenaltyAmount = dailyPenaltyAmount.multiply(new BigDecimal("4")).setScale(6,
                MoneyHelper.getRoundingMode());

        assertEquals(0, new BigDecimal("0.000666666667").compareTo(dailyRate));
        assertEquals(0, new BigDecimal("5.33").compareTo(postedPenaltyAmount.setScale(2, MoneyHelper.getRoundingMode())));
    }
}
