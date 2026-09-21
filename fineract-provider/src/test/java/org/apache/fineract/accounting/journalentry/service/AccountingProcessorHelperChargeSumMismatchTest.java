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
package org.apache.fineract.accounting.journalentry.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import org.apache.fineract.accounting.common.AccountingConstants.CashAccountsForLoan;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.organisation.office.domain.Office;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AccountingProcessorHelperChargeSumMismatchTest {

    @InjectMocks
    private AccountingProcessorHelper underTest;

    @Test
    public void chargeSumMismatchIncludesLoanAndTransactionInMessage() {
        final Office office = mock(Office.class);
        try {
            underTest.createCreditJournalEntryOrReversalForLoanCharges(office, "KES", CashAccountsForLoan.INCOME_FROM_FEES.getValue(), 1L,
                    42L, "9001", LocalDate.of(2024, 6, 1), BigDecimal.TEN, false, Collections.emptyList(), false, null);
        } catch (final PlatformDataIntegrityException ex) {
            assertTrue(ex.getDefaultUserMessage().contains("Loan: 42"));
            assertTrue(ex.getDefaultUserMessage().contains("transaction: 9001"));
            assertTrue(ex.getDefaultUserMessage().contains("expected portion: 10"));
            assertTrue(ex.getDefaultUserMessage().contains("charge lines total: 0"));
            return;
        }
        throw new AssertionError("Expected PlatformDataIntegrityException");
    }
}
