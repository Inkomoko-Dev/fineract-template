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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.accounting.common.AccountingConstants.AccrualAccountsForLoan;
import org.apache.fineract.accounting.common.AccountingConstants.CashAccountsForLoan;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.junit.jupiter.api.Test;

class ResidualBalanceAdjustmentAccountingProcessorTest {

    private static final Long LOAN_ID = 11L;
    private static final Long PRODUCT_ID = 22L;
    private static final Long OFFICE_ID = 33L;
    private static final String CURRENCY = "KES";
    private static final String TRANSACTION_ID = "44";
    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 9, 3);

    @Test
    void cashAccountingPostsPrincipalOnly() {
        final AccountingProcessorHelper helper = mock(AccountingProcessorHelper.class);
        when(helper.getOfficeById(OFFICE_ID)).thenReturn(mock(Office.class));

        new CashBasedAccountingProcessorForLoan(helper).createJournalEntriesForLoan(loan());

        verify(helper).createCashBasedJournalEntriesAndReversalsForLoan(any(Office.class), eq(CURRENCY),
                eq(CashAccountsForLoan.LOSSES_WRITTEN_OFF.getValue()), eq(CashAccountsForLoan.LOAN_PORTFOLIO.getValue()), eq(PRODUCT_ID),
                eq(null), eq(LOAN_ID), eq(TRANSACTION_ID), eq(TRANSACTION_DATE), eq(new BigDecimal("0.10")), eq(false), eq(false),
                eq(null));
        verify(helper, times(1)).createCashBasedJournalEntriesAndReversalsForLoan(any(Office.class), eq(CURRENCY), anyInt(), anyInt(),
                eq(PRODUCT_ID), eq(null), eq(LOAN_ID), eq(TRANSACTION_ID), eq(TRANSACTION_DATE), any(BigDecimal.class), eq(false),
                eq(false), eq(null));
    }

    @Test
    void accrualAccountingReversesEachRecognizedComponentThroughExistingMappings() {
        final AccountingProcessorHelper helper = mock(AccountingProcessorHelper.class);
        when(helper.getOfficeById(OFFICE_ID)).thenReturn(mock(Office.class));
        when(helper.getLinkedGLAccountForLoanProduct(eq(PRODUCT_ID), anyInt(), eq(null))).thenReturn(mock(GLAccount.class));

        new AccrualBasedAccountingProcessorForLoan(helper).createJournalEntriesForLoan(loan());

        verifyDebit(helper, AccrualAccountsForLoan.LOSSES_WRITTEN_OFF.getValue(), "0.10");
        verifyDebit(helper, AccrualAccountsForLoan.INTEREST_ON_LOANS.getValue(), "0.20");
        verifyDebit(helper, AccrualAccountsForLoan.INCOME_FROM_FEES.getValue(), "0.30");
        verifyDebit(helper, AccrualAccountsForLoan.INCOME_FROM_PENALTIES.getValue(), "0.40");
        verify(helper).getLinkedGLAccountForLoanProduct(PRODUCT_ID, AccrualAccountsForLoan.LOAN_PORTFOLIO.getValue(), null);
        verify(helper).getLinkedGLAccountForLoanProduct(PRODUCT_ID, AccrualAccountsForLoan.INTEREST_RECEIVABLE.getValue(), null);
        verify(helper).getLinkedGLAccountForLoanProduct(PRODUCT_ID, AccrualAccountsForLoan.FEES_RECEIVABLE.getValue(), null);
        verify(helper).getLinkedGLAccountForLoanProduct(PRODUCT_ID, AccrualAccountsForLoan.PENALTIES_RECEIVABLE.getValue(), null);
    }

    private void verifyDebit(final AccountingProcessorHelper helper, final int accountType, final String amount) {
        verify(helper).createDebitJournalEntryOrReversalForLoan(any(Office.class), eq(CURRENCY), eq(accountType), eq(PRODUCT_ID), eq(null),
                eq(LOAN_ID), eq(TRANSACTION_ID), eq(TRANSACTION_DATE), eq(new BigDecimal(amount)), eq(false), eq(false), eq(null));
    }

    private LoanDTO loan() {
        final LoanTransactionEnumData transactionType = new LoanTransactionEnumData(32L,
                "loanTransactionType.residualBalanceAdjustment", "Residual Balance Adjustment");
        final LoanTransactionDTO transaction = new LoanTransactionDTO(OFFICE_ID, null, TRANSACTION_ID, TRANSACTION_DATE, transactionType,
                BigDecimal.ONE, new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.30"), new BigDecimal("0.40"),
                BigDecimal.ZERO, false, Collections.emptyList(), Collections.emptyList(), false);
        return new LoanDTO(LOAN_ID, PRODUCT_ID, OFFICE_ID, CURRENCY, false, false, true, List.of(transaction), null);
    }
}
