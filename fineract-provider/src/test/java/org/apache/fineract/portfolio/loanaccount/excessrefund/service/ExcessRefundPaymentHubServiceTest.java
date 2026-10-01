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
package org.apache.fineract.portfolio.loanaccount.excessrefund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementRequestData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class ExcessRefundPaymentHubServiceTest {

    @Mock
    private Environment environment;
    @Mock
    private PaymentTypeReadPlatformService paymentTypeReadPlatformService;
    @Mock
    private Loan loan;

    @InjectMocks
    private ExcessRefundPaymentHubService service;

    @Test
    void buildsDisbursementParityRefundPayload() {
        when(this.loan.getId()).thenReturn(42L);
        when(this.loan.getAccountNumber()).thenReturn("000000042");
        when(this.paymentTypeReadPlatformService.retrieveOne(7L)).thenReturn(PaymentTypeData.instance(7L, "MPesa"));

        final LoanExcessRefund refund = new LoanExcessRefund();
        refund.setAmount(new BigDecimal("150.00"));
        refund.setCurrencyCode("KES");
        refund.setPaymentTypeId(7L);
        refund.setBeneficiaryJson(
                "{\"channel\":\"MOBILE_MONEY\",\"msisdn\":\"254711111111\",\"beneficiaryName\":\"Ada Lovelace\"}");

        // setId via reflection is unnecessary; buildPayload uses refund.getId() only in narration
        final DisbursementRequestData payload = this.service.buildPayload(this.loan, refund, "cbs_refund_1_abc");

        assertThat(payload.getTransactionType()).isEqualTo("REFUND");
        assertThat(payload.getRequestId()).isEqualTo("cbs_refund_1_abc");
        assertThat(payload.getExternalId()).isEqualTo("000000042");
        assertThat(payload.getAmount()).isEqualByComparingTo("150.00");
        assertThat(payload.getCurrencyCode()).isEqualTo("KES");
        assertThat(payload.getPaymentMethod()).isEqualTo("MPesa");
        assertThat(payload.getPaymentMethodId()).isEqualTo(7L);
        assertThat(payload.getClientPhoneNumber()).isEqualTo("254711111111");
        assertThat(payload.getBeneficiaryName()).isEqualTo("Ada Lovelace");
        assertThat(payload.getOrigin()).isEqualTo("CBS");
        assertThat(payload.getLoanId()).isEqualTo(42L);
        assertThat(payload.getNarration()).contains("Excess refund");
    }
}
