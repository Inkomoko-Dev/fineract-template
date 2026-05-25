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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests {@link Loan}.
 */
public class LoanTest {

    @BeforeEach
    public void init() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 5, 25))));
    }

    /**
     * Tests {@link Loan#getCharges()} with charges.
     */
    @Test
    public void testGetChargesWithCharges() {
        Loan loan = new Loan();
        ReflectionTestUtils.setField(loan, "charges", Collections.singleton(buildLoanCharge()));

        final Collection<LoanCharge> chargeIds = loan.getCharges();

        assertEquals(1, chargeIds.size());
    }

    /**
     * Tests {@link Loan#getCharges()} with no charges.
     */
    @Test
    public void testGetChargesWithNoCharges() {
        final Loan loan = new Loan();

        final Collection<LoanCharge> chargeIds = loan.getCharges();

        assertEquals(0, chargeIds.size());
    }

    /**
     * Tests {@link Loan#getCharges()} with null to make sure NPE is not thrown.
     */
    @Test
    public void testGetChargesWithNull() {
        final Loan loan = new Loan();
        ReflectionTestUtils.setField(loan, "charges", null);

        final Collection<LoanCharge> chargeIds = loan.getCharges();

        assertEquals(0, chargeIds.size());
    }

    @Test
    public void loanReversalTransactionKeepsReversedFlagsFalse() {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(1L);
        final Loan loan = mock(Loan.class);
        when(loan.getNetDisbursalAmount()).thenReturn(BigDecimal.ZERO);
        final LoanTransaction originalTransaction = new LoanTransaction();
        ReflectionTestUtils.setField(originalTransaction, "id", 10L);
        ReflectionTestUtils.setField(originalTransaction, "loan", loan);
        ReflectionTestUtils.setField(originalTransaction, "office", office);
        ReflectionTestUtils.setField(originalTransaction, "typeOf", LoanTransactionType.RECOVERY_REPAYMENT.getValue());
        ReflectionTestUtils.setField(originalTransaction, "dateOf", LocalDate.of(2026, 5, 25));
        ReflectionTestUtils.setField(originalTransaction, "amount", BigDecimal.valueOf(100));

        final LoanTransaction reversalTransaction = LoanTransaction.reversal(originalTransaction, LocalDate.of(2026, 5, 25), null);
        final Map<String, Object> accountingData = reversalTransaction.toMapData(new CurrencyData("KES", "Kenyan Shilling", 2, 1, "KSh",
                "currency.KES"));

        assertTrue(reversalTransaction.isReversalTransaction());
        assertFalse(reversalTransaction.isReversed());
        assertFalse(reversalTransaction.isManuallyAdjustedOrReversed());
        assertFalse(reversalTransaction.isRecoveryRepayment());
        assertTrue(reversalTransaction.isRecoveryRepaymentType());
        assertEquals(Boolean.TRUE, accountingData.get("reversed"));
    }

    /**
     * Builds a new loan charge.
     *
     * @return the {@link LoanCharge}
     */
    private LoanCharge buildLoanCharge() {
        return new LoanCharge(mock(Loan.class), mock(Charge.class), new BigDecimal(100), new BigDecimal(100),
                ChargeTimeType.TRANCHE_DISBURSEMENT, ChargeCalculationType.FLAT, LocalDate.of(2022, 6, 27), ChargePaymentMode.REGULAR, 1,
                new BigDecimal(100));
    }
}
