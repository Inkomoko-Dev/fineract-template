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
package org.apache.fineract.portfolio.loanaccount.loanschedule.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanSchedulePeriodDataTest {

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 6, 8))));
    }

    @Test
    void disbursementPeriodUsesActualFeePaymentState() {
        final LoanSchedulePeriodData period = LoanSchedulePeriodData.disbursementOnlyPeriod(LocalDate.of(2026, 6, 8),
                new BigDecimal("5000.00"), new BigDecimal("1200.00"), new BigDecimal("800.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("400.00"));

        assertEquals(0, new BigDecimal("1200.00").compareTo(period.feeChargesDue()));
        assertEquals(0, new BigDecimal("800.00").compareTo(period.feeChargesPaid()));
        assertEquals(0, new BigDecimal("400.00").compareTo(period.feeChargesOutstanding()));
        assertEquals(0, new BigDecimal("800.00").compareTo(period.totalPaidForPeriod()));
        assertEquals(0, new BigDecimal("400.00").compareTo(period.totalOutstandingForPeriod()));
    }
}
