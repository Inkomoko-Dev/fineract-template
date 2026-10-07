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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The transfer flags on {@link LoanTransactionDTO} decide which financial activity a journal entry is posted against:
 * a loan-to-loan transfer (top-up) leg posts against Asset Transfer, a savings-to-loan transfer leg against Liability
 * Transfer. Both flags therefore have to survive a re-post of journal entries that happens outside of the original
 * transfer command - e.g. when editing a disbursement/insurance charge reprocesses the loan (CGLT-532/562).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AccountingProcessorHelperTransferFlagTest {

    private static final Long TOP_UP_LEG_TRANSACTION_ID = 3244922L;
    private static final Long ORDINARY_TRANSACTION_ID = 3292737L;

    @Mock
    private AccountTransfersReadPlatformService accountTransfersReadPlatformService;

    @InjectMocks
    private AccountingProcessorHelper underTest;

    @Test
    public void repostedLoanToLoanTransferLegKeepsPostingAgainstAssetTransfer() {
        when(accountTransfersReadPlatformService.isAccountTransfer(eq(TOP_UP_LEG_TRANSACTION_ID), eq(PortfolioAccountType.LOAN)))
                .thenReturn(true);
        when(accountTransfersReadPlatformService.isLoanToLoanTransfer(eq(TOP_UP_LEG_TRANSACTION_ID))).thenReturn(true);

        final LoanDTO loanDTO = populate(bridgeDataWithoutTransferFlags(TOP_UP_LEG_TRANSACTION_ID));

        final LoanTransactionDTO topUpLeg = loanDTO.getNewLoanTransactions().get(0);
        assertTrue(topUpLeg.isAccountTransfer(), "top-up leg is still an account transfer when re-posted");
        assertTrue(topUpLeg.isLoanToLoanTransfer(),
                "top-up leg must stay a loan-to-loan transfer when re-posted, otherwise it demands the Liability Transfer mapping");
    }

    @Test
    public void savingsToLoanTransferLegStillPostsAgainstLiabilityTransfer() {
        when(accountTransfersReadPlatformService.isAccountTransfer(eq(TOP_UP_LEG_TRANSACTION_ID), eq(PortfolioAccountType.LOAN)))
                .thenReturn(true);
        when(accountTransfersReadPlatformService.isLoanToLoanTransfer(eq(TOP_UP_LEG_TRANSACTION_ID))).thenReturn(false);

        final LoanDTO loanDTO = populate(bridgeDataWithoutTransferFlags(TOP_UP_LEG_TRANSACTION_ID));

        final LoanTransactionDTO transferLeg = loanDTO.getNewLoanTransactions().get(0);
        assertTrue(transferLeg.isAccountTransfer());
        assertFalse(transferLeg.isLoanToLoanTransfer(), "a transfer with a savings leg is not a loan-to-loan transfer");
    }

    @Test
    public void transferLegDoesNotTurnTheRestOfTheBatchIntoTransfers() {
        when(accountTransfersReadPlatformService.isAccountTransfer(eq(TOP_UP_LEG_TRANSACTION_ID), eq(PortfolioAccountType.LOAN)))
                .thenReturn(true);
        when(accountTransfersReadPlatformService.isLoanToLoanTransfer(eq(TOP_UP_LEG_TRANSACTION_ID))).thenReturn(true);
        when(accountTransfersReadPlatformService.isAccountTransfer(eq(ORDINARY_TRANSACTION_ID), eq(PortfolioAccountType.LOAN)))
                .thenReturn(false);

        final LoanDTO loanDTO = populate(
                bridgeDataWithoutTransferFlags(TOP_UP_LEG_TRANSACTION_ID, ORDINARY_TRANSACTION_ID));

        final LoanTransactionDTO ordinaryTransaction = loanDTO.getNewLoanTransactions().get(1);
        assertFalse(ordinaryTransaction.isAccountTransfer(),
                "an ordinary transaction posted in the same batch as a transfer leg is not itself a transfer");
        assertFalse(ordinaryTransaction.isLoanToLoanTransfer());
    }

    @Test
    public void loanToLoanTransferFlagFromTheTransferCommandIsHonouredWithoutALookup() {
        final Map<String, Object> bridgeData = bridgeDataWithoutTransferFlags(TOP_UP_LEG_TRANSACTION_ID);
        bridgeData.put("isAccountTransfer", Boolean.TRUE);
        bridgeData.put("isLoanToLoanTransfer", Boolean.TRUE);

        final LoanTransactionDTO topUpLeg = populate(bridgeData).getNewLoanTransactions().get(0);

        assertTrue(topUpLeg.isAccountTransfer());
        assertTrue(topUpLeg.isLoanToLoanTransfer());
        verify(accountTransfersReadPlatformService, never()).isAccountTransfer(eq(TOP_UP_LEG_TRANSACTION_ID),
                eq(PortfolioAccountType.LOAN));
        verify(accountTransfersReadPlatformService, never()).isLoanToLoanTransfer(eq(TOP_UP_LEG_TRANSACTION_ID));
    }

    @Test
    public void ordinaryTransactionIsNeitherTransferKind() {
        when(accountTransfersReadPlatformService.isAccountTransfer(eq(ORDINARY_TRANSACTION_ID), eq(PortfolioAccountType.LOAN)))
                .thenReturn(false);

        final LoanTransactionDTO ordinaryTransaction = populate(bridgeDataWithoutTransferFlags(ORDINARY_TRANSACTION_ID))
                .getNewLoanTransactions().get(0);

        assertFalse(ordinaryTransaction.isAccountTransfer());
        assertFalse(ordinaryTransaction.isLoanToLoanTransfer());
        verify(accountTransfersReadPlatformService, never()).isLoanToLoanTransfer(eq(ORDINARY_TRANSACTION_ID));
    }

    private LoanDTO populate(final Map<String, Object> bridgeData) {
        return underTest.populateLoanDtoFromMap(bridgeData, false, false, true);
    }

    private Map<String, Object> bridgeDataWithoutTransferFlags(final Long... transactionIds) {
        final Map<String, Object> bridgeData = new LinkedHashMap<>();
        bridgeData.put("loanId", 410740L);
        bridgeData.put("loanProductId", 16L);
        bridgeData.put("officeId", 2L);
        bridgeData.put("currency", new CurrencyData("RWF", "Rwandan Franc", 2, 0, "RWF", "RWF"));
        // a re-post outside of the transfer command carries neither flag
        bridgeData.put("isAccountTransfer", Boolean.FALSE);
        bridgeData.put("fundId", null);
        final List<Map<String, Object>> newLoanTransactions = new ArrayList<>();
        for (final Long transactionId : transactionIds) {
            newLoanTransactions.add(transaction(transactionId));
        }
        bridgeData.put("newLoanTransactions", newLoanTransactions);
        return bridgeData;
    }

    private Map<String, Object> transaction(final Long transactionId) {
        final Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("id", transactionId);
        transaction.put("officeId", 2L);
        transaction.put("type", new LoanTransactionEnumData(2L, "loanTransactionType.repayment", "Repayment"));
        transaction.put("reversed", Boolean.FALSE);
        transaction.put("date", LocalDate.of(2026, 6, 5));
        transaction.put("amount", new BigDecimal("1199900.00"));
        transaction.put("principalPortion", new BigDecimal("1199900.00"));
        transaction.put("interestPortion", BigDecimal.ZERO);
        transaction.put("feeChargesPortion", BigDecimal.ZERO);
        transaction.put("penaltyChargesPortion", BigDecimal.ZERO);
        transaction.put("overPaymentPortion", BigDecimal.ZERO);
        transaction.put("correctionDate", null);
        transaction.put("paymentTypeId", null);
        return transaction;
    }
}
