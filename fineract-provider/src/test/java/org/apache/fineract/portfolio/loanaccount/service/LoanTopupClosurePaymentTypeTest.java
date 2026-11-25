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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTopupDetails;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LoanTopupClosurePaymentTypeTest {

    private static final String TOP_UP_PAYMENT = "Top-up payment";
    private static final Long NEW_LOAN_ID = 435001L;
    private static final Long CLOSED_LOAN_ID = 401562L;
    private static final BigDecimal SETTLEMENT = new BigDecimal("39120002.03");

    private LoanWritePlatformServiceJpaRepositoryImpl service;
    private AccountTransfersWritePlatformService accountTransfers;
    private PaymentType topUpPayment;
    private Loan newLoan;
    private JsonCommand command;

    @BeforeEach
    void setUp() {
        this.topUpPayment = new PaymentType(TOP_UP_PAYMENT, "Settlement of the old loan by a top-up", false, false, 0L);
        ReflectionTestUtils.setField(this.topUpPayment, "id", 128L);

        this.service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class, CALLS_REAL_METHODS);
        this.accountTransfers = mock(AccountTransfersWritePlatformService.class);
        final AccountTransferDetails transferDetails = mock(AccountTransferDetails.class);
        when(transferDetails.getId()).thenReturn(9001L);
        when(this.accountTransfers.repayLoanWithTopup(any(AccountTransferDTO.class))).thenReturn(transferDetails);

        final PaymentTypeRepositoryWrapper paymentTypes = mock(PaymentTypeRepositoryWrapper.class, invocation -> {
            final Class<?> returned = invocation.getMethod().getReturnType();
            if (PaymentType.class.equals(returned)) {
                return this.topUpPayment;
            }
            if (Optional.class.equals(returned)) {
                return Optional.of(this.topUpPayment);
            }
            if (List.class.isAssignableFrom(returned)) {
                return List.of(this.topUpPayment);
            }
            return null;
        });

        ReflectionTestUtils.setField(this.service, "accountTransfersWritePlatformService", this.accountTransfers);
        ReflectionTestUtils.setField(this.service, "paymentTypeRepositoryWrapper", paymentTypes);
        ReflectionTestUtils.setField(this.service, "paymentDetailWritePlatformService", mock(PaymentDetailWritePlatformService.class));

        final LoanTopupDetails topupDetails = mock(LoanTopupDetails.class);
        when(topupDetails.getLoanIdToClose()).thenReturn(CLOSED_LOAN_ID);
        this.newLoan = mock(Loan.class);
        when(this.newLoan.getId()).thenReturn(NEW_LOAN_ID);
        when(this.newLoan.getTopupLoanDetails()).thenReturn(topupDetails);

        final String json = "{\"actualDisbursementDate\":\"05 September 2025\",\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\"}";
        this.command = JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), "LOAN", NEW_LOAN_ID, null, null, null,
                NEW_LOAN_ID, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Top-up closure repayment (LOAN/15866/2023 txn 2954481) carries the 'Top-up payment' type")
    void topupClosureRepaymentCarriesTopUpPaymentType() {
        this.service.disburseLoanToLoan(this.newLoan, this.command, SETTLEMENT);

        final AccountTransferDTO transfer = capturedTransfer();
        assertNotNull(transfer.getPaymentDetail(),
                "The old loan's closing repayment is posted with no payment detail, so the Loan Payment Details Report drops it");
        assertNotNull(transfer.getPaymentDetail().getPaymentType(), "The closing repayment must carry a payment type");
        assertEquals(TOP_UP_PAYMENT, transfer.getPaymentDetail().getPaymentType().getPaymentName());
    }

    @Test
    @DisplayName("Guard: the top-up still moves the settlement from the new loan to the loan it closes")
    void topupStillTransfersTheSettlementToTheClosedLoan() {
        this.service.disburseLoanToLoan(this.newLoan, this.command, SETTLEMENT);

        final AccountTransferDTO transfer = capturedTransfer();
        assertEquals(NEW_LOAN_ID, transfer.getFromAccountId());
        assertEquals(CLOSED_LOAN_ID, transfer.getToAccountId());
        assertEquals(0, SETTLEMENT.compareTo(transfer.getTransactionAmount()));
    }

    private AccountTransferDTO capturedTransfer() {
        final ArgumentCaptor<AccountTransferDTO> transfer = ArgumentCaptor.forClass(AccountTransferDTO.class);
        verify(this.accountTransfers).repayLoanWithTopup(transfer.capture());
        return transfer.getValue();
    }
}
