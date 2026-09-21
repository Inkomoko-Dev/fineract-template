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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.accounting.common.AccountingConstants.AccrualAccountsForLoan;
import org.apache.fineract.accounting.common.AccountingConstants.CashAccountsForLoan;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WriteOffReversalJournalEntryTest {

    private static final int WRITE_OFF_REVERSAL_TYPE = 33;
    private static final Long OFFICE_ID = 2L;
    private static final Long LOAN_ID = 393409L;
    private static final Long PRODUCT_ID = 7L;
    private static final String CURRENCY = "KES";
    private static final LocalDate WRITE_OFF_DATE = LocalDate.of(2025, 11, 10);
    private static final LocalDate UNDO_DATE = LocalDate.of(2026, 6, 3);
    private static final BigDecimal PRINCIPAL = new BigDecimal("100000.00");
    private static final BigDecimal INTEREST = new BigDecimal("6000.00");
    private static final BigDecimal TOTAL = new BigDecimal("106000.00");

    private AccountingProcessorHelper helper;
    private Office office;
    private GLAccount loanPortfolio;
    private GLAccount interestReceivable;

    @BeforeEach
    void setUp() {
        this.helper = mock(AccountingProcessorHelper.class);
        this.office = mock(Office.class);
        this.loanPortfolio = mock(GLAccount.class);
        this.interestReceivable = mock(GLAccount.class);
        when(this.helper.getOfficeById(OFFICE_ID)).thenReturn(this.office);
        when(this.helper.getLinkedGLAccountForLoanProduct(PRODUCT_ID, AccrualAccountsForLoan.LOAN_PORTFOLIO.getValue(), null))
                .thenReturn(this.loanPortfolio);
        when(this.helper.getLinkedGLAccountForLoanProduct(PRODUCT_ID, AccrualAccountsForLoan.INTEREST_RECEIVABLE.getValue(), null))
                .thenReturn(this.interestReceivable);
    }

    @Test
    void accrualWriteOffReversalOffsetsEveryWriteOffLegOnTheUndoDate() {
        new AccrualBasedAccountingProcessorForLoan(this.helper).createJournalEntriesForLoan(loan(false, writeOffReversal()));

        verify(this.helper).createCreditJournalEntryOrReversalForLoan(this.office, CURRENCY, LOAN_ID, "L77", UNDO_DATE, PRINCIPAL, true,
                this.loanPortfolio, false, null);
        verify(this.helper).createCreditJournalEntryOrReversalForLoan(this.office, CURRENCY, LOAN_ID, "L77", UNDO_DATE, INTEREST, true,
                this.interestReceivable, false, null);
        verify(this.helper).createDebitJournalEntryOrReversalForLoan(this.office, CURRENCY, AccrualAccountsForLoan.LOSSES_WRITTEN_OFF.getValue(),
                PRODUCT_ID, null, LOAN_ID, "L77", UNDO_DATE, TOTAL, true, false, null);
    }

    @Test
    void cashWriteOffReversalOffsetsThePrincipalWriteOffOnTheUndoDate() {
        new CashBasedAccountingProcessorForLoan(this.helper).createJournalEntriesForLoan(loan(true, writeOffReversal()));

        verify(this.helper).createCashBasedJournalEntriesAndReversalsForLoan(this.office, CURRENCY,
                CashAccountsForLoan.LOSSES_WRITTEN_OFF.getValue(), CashAccountsForLoan.LOAN_PORTFOLIO.getValue(), PRODUCT_ID, null, LOAN_ID,
                "L77", UNDO_DATE, PRINCIPAL, true, false, null);
    }

    @Test
    void accrualOriginalWriteOffStillPostsAsBefore() {
        new AccrualBasedAccountingProcessorForLoan(this.helper).createJournalEntriesForLoan(loan(false, writeOff()));

        verify(this.helper).createDebitJournalEntryOrReversalForLoan(this.office, CURRENCY, AccrualAccountsForLoan.LOSSES_WRITTEN_OFF.getValue(),
                PRODUCT_ID, null, LOAN_ID, "L9", WRITE_OFF_DATE, TOTAL, false, false, null);
    }

    @Test
    void cashOriginalWriteOffStillPostsAsBefore() {
        new CashBasedAccountingProcessorForLoan(this.helper).createJournalEntriesForLoan(loan(true, writeOff()));

        verify(this.helper).createCashBasedJournalEntriesAndReversalsForLoan(this.office, CURRENCY,
                CashAccountsForLoan.LOSSES_WRITTEN_OFF.getValue(), CashAccountsForLoan.LOAN_PORTFOLIO.getValue(), PRODUCT_ID, null, LOAN_ID,
                "L9", WRITE_OFF_DATE, PRINCIPAL, false, false, null);
    }

    private LoanDTO loan(final boolean cashBased, final LoanTransactionDTO transaction) {
        return new LoanDTO(LOAN_ID, PRODUCT_ID, OFFICE_ID, CURRENCY, cashBased, false, !cashBased, List.of(transaction), null);
    }

    private LoanTransactionDTO writeOffReversal() {
        return new LoanTransactionDTO(OFFICE_ID, null, "L77", UNDO_DATE, LoanEnumerations.transactionType(WRITE_OFF_REVERSAL_TYPE), TOTAL,
                PRINCIPAL, INTEREST, null, null, null, true, List.of(), List.of(), false);
    }

    private LoanTransactionDTO writeOff() {
        return new LoanTransactionDTO(OFFICE_ID, null, "L9", WRITE_OFF_DATE, LoanEnumerations.transactionType(LoanTransactionType.WRITEOFF),
                TOTAL, PRINCIPAL, INTEREST, null, null, null, false, List.of(), List.of(), false);
    }
}
