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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ThirdPartyDisbursementRepaymentEventServiceTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);

    @Test
    void eventPayloadIncludesLoanExternalId() {
        final Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(501L);
        when(loan.getAccountNumber()).thenReturn("000000501");
        when(loan.getExternalId()).thenReturn("KI-LOAN-77");
        when(loan.getClient()).thenReturn(null);
        when(loan.getCurrency()).thenReturn(KES);

        final LoanTransaction transaction = mock(LoanTransaction.class);
        when(transaction.getLoan()).thenReturn(loan);
        when(transaction.getId()).thenReturn(98765L);
        when(transaction.getOriginalTransactionId()).thenReturn(null);
        when(transaction.getAmount(KES)).thenReturn(Money.of(KES, new BigDecimal("1500.00")));
        when(transaction.getPrincipalPortion()).thenReturn(new BigDecimal("1200.00"));
        when(transaction.getInterestPortion(KES)).thenReturn(Money.of(KES, new BigDecimal("300.00")));
        when(transaction.getFeeChargesPortion(KES)).thenReturn(Money.of(KES, BigDecimal.ZERO));
        when(transaction.getPenaltyChargesPortion(KES)).thenReturn(Money.of(KES, BigDecimal.ZERO));
        when(transaction.getOutstandingLoanBalance()).thenReturn(new BigDecimal("8500.00"));
        when(transaction.getTransactionDate()).thenReturn(LocalDate.of(2026, 10, 2));
        when(transaction.isReversed()).thenReturn(false);
        when(transaction.isReversalTransaction()).thenReturn(false);

        final String json = ReflectionTestUtils.invokeMethod(ThirdPartyDisbursementRepaymentEventService.class, "eventPayload",
                transaction, "KIFIYA", "REPAYMENT", "7c9e6679-7425-40de-944b-e07fc1f90ae7");

        final JsonObject envelope = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("CBS_LOAN_REPAYMENT", envelope.get("eventType").getAsString());
        assertEquals("REPAYMENT", envelope.get("action").getAsString());
        final JsonObject payload = envelope.getAsJsonObject("payload");
        assertEquals("KI-LOAN-77", payload.get("externalId").getAsString());
        assertEquals(501L, payload.get("loanId").getAsLong());
        assertEquals("000000501", payload.get("loanAccountNo").getAsString());
        assertEquals(98765L, payload.get("transactionId").getAsLong());
        assertTrue(payload.get("amount").getAsBigDecimal().compareTo(new BigDecimal("1500.00")) == 0);
        assertEquals("2026-10-02", payload.get("transactionDate").getAsString());
        assertEquals(false, payload.get("reversed").getAsBoolean());
    }
}
