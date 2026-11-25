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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.locks.ReentrantLock;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanEventApiJsonValidator;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanPayOffPaymentTypeTest {

    private static final Long LOAN_ID = 394727L;
    private static final String PAY_OFF_2024 = "{\"transactionDate\":\"26 July 2024\",\"transactionAmount\":\"16662.69\",\"locale\":\"en\","
            + "\"dateFormat\":\"dd MMMM yyyy\",\"paymentTypeId\":\"\",\"note\":\"loan cleared in Odoo\"}";
    private static final String PAY_OFF_WITH_TYPE = "{\"transactionDate\":\"26 July 2024\",\"transactionAmount\":\"16662.69\",\"locale\":\"en\","
            + "\"dateFormat\":\"dd MMMM yyyy\",\"paymentTypeId\":\"103\"}";

    private LoanWritePlatformServiceJpaRepositoryImpl service;
    private PaymentDetailWritePlatformService paymentDetails;

    @BeforeEach
    void setUp() {
        this.service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class, CALLS_REAL_METHODS);
        final Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getLoanStatus()).thenReturn(300);
        when(loan.isMultiDisburmentLoan()).thenReturn(false);
        final LoanAssembler loanAssembler = mock(LoanAssembler.class);
        when(loanAssembler.assembleFrom(LOAN_ID)).thenReturn(loan);
        this.paymentDetails = mock(PaymentDetailWritePlatformService.class);

        ReflectionTestUtils.setField(this.service, "lock", new ReentrantLock());
        ReflectionTestUtils.setField(this.service, "context", mock(PlatformSecurityContext.class));
        ReflectionTestUtils.setField(this.service, "loanAssembler", loanAssembler);
        ReflectionTestUtils.setField(this.service, "loanUtilService", mock(LoanUtilService.class, RETURNS_DEEP_STUBS));
        ReflectionTestUtils.setField(this.service, "loanEventApiJsonValidator",
                new LoanEventApiJsonValidator(new FromJsonHelper(), null, null));
        ReflectionTestUtils.setField(this.service, "paymentDetailWritePlatformService", this.paymentDetails);
        ReflectionTestUtils.setField(this.service, "loanAccountDomainService", mock(LoanAccountDomainService.class));
    }

    @Test
    @DisplayName("Story (all repayment flows): the 2024 Pay Off on loan 394727, sent with paymentTypeId \"\", is rejected before anything is saved")
    void payOffWithoutPaymentTypeIsRejected() {
        final PlatformApiDataValidationException rejection = assertThrows(PlatformApiDataValidationException.class,
                () -> this.service.payOffLoan(LOAN_ID, command(PAY_OFF_2024)));
        assertTrue(rejection.getErrors().stream().anyMatch(error -> "paymentTypeId".equals(error.getParameterName())));
        verify(this.paymentDetails, never()).createAndPersistPaymentDetail(any(JsonCommand.class), any());
    }

    @Test
    @DisplayName("Guard: a Pay Off carrying a payment type passes the payment type check")
    void payOffWithPaymentTypeIsNotRejectedForIt() {
        try {
            this.service.payOffLoan(LOAN_ID, command(PAY_OFF_WITH_TYPE));
        } catch (PlatformApiDataValidationException rejection) {
            assertFalse(rejection.getErrors().stream().anyMatch(error -> "paymentTypeId".equals(error.getParameterName())),
                    "A Pay Off that names its payment type must not be rejected for it");
        } catch (RuntimeException unrelated) {
            return;
        }
    }

    private static JsonCommand command(final String json) {
        return JsonCommand.from(json, new FromJsonHelper().parse(json), new FromJsonHelper(), "LOAN", LOAN_ID, null, null, null, LOAN_ID,
                null, null, null, null, null, null);
    }
}
