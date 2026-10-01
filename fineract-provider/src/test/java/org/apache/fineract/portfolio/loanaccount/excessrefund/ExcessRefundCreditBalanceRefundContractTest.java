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
package org.apache.fineract.portfolio.loanaccount.excessrefund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.excessrefund.service.ExcessRefundGlPoster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Locks the CGLT-394 contract that excess refund posting goes only through
 * {@link LoanAccountDomainService#creditBalanceRefund}. Journal entry shape for CBR is covered by
 * ClientLoanCreditBalanceRefundandRepaymentTypeIntegrationTest
 * (#newCreditBalanceRefundCreatesCorrectJournalEntriesForPeriodicAccrualsTest,
 * #newCreditBalanceRefundCreatesCorrectJournalEntriesForCashAccountingTest).
 */
@ExtendWith(MockitoExtension.class)
class ExcessRefundCreditBalanceRefundContractTest {

    @Mock
    private LoanAccountDomainService loanAccountDomainService;

    @InjectMocks
    private ExcessRefundGlPoster glPoster;

    @Test
    void postsViaCreditBalanceRefundOnly() {
        final Long loanId = 42L;
        final LocalDate date = LocalDate.of(2026, 9, 30);
        final BigDecimal amount = new BigDecimal("100.00");
        when(this.loanAccountDomainService.creditBalanceRefund(eq(loanId), eq(date), eq(amount), eq("note"), isNull()))
                .thenReturn(new CommandProcessingResultBuilder().withEntityId(99L));

        final Long transactionId = this.glPoster.post(loanId, date, amount, "note", null);

        assertThat(transactionId).isEqualTo(99L);
        verify(this.loanAccountDomainService).creditBalanceRefund(loanId, date, amount, "note", null);
        verifyNoMoreInteractions(this.loanAccountDomainService);
    }
}
