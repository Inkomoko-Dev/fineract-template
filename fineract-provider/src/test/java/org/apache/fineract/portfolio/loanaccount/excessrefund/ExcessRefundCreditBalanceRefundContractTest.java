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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.excessrefund.service.ExcessRefundGlPoster;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Locks the CGLT-394 contract that excess refund posting goes through
 * {@link LoanAccountDomainService#creditBalanceRefund} with the refund payment type for fund-source mapping.
 */
@ExtendWith(MockitoExtension.class)
class ExcessRefundCreditBalanceRefundContractTest {

    @Mock
    private LoanAccountDomainService loanAccountDomainService;

    @Mock
    private PaymentTypeRepositoryWrapper paymentTypeRepository;

    @InjectMocks
    private ExcessRefundGlPoster glPoster;

    @Test
    void postsViaCreditBalanceRefundWithPaymentType() {
        final Long loanId = 42L;
        final LocalDate date = LocalDate.of(2026, 9, 30);
        final BigDecimal amount = new BigDecimal("100.00");
        final Long paymentTypeId = 7L;
        final PaymentType paymentType = org.mockito.Mockito.mock(PaymentType.class);
        when(this.paymentTypeRepository.findOneWithNotFoundDetection(paymentTypeId)).thenReturn(paymentType);
        when(this.loanAccountDomainService.creditBalanceRefund(eq(loanId), eq(date), eq(amount), eq("note"), isNull(),
                any(PaymentDetail.class))).thenReturn(new CommandProcessingResultBuilder().withEntityId(99L));

        final Long transactionId = this.glPoster.post(loanId, date, amount, "note", null, paymentTypeId);

        assertThat(transactionId).isEqualTo(99L);
        verify(this.loanAccountDomainService).creditBalanceRefund(eq(loanId), eq(date), eq(amount), eq("note"), isNull(),
                any(PaymentDetail.class));
    }
}
