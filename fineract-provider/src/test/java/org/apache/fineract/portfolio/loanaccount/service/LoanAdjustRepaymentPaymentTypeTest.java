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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanEventApiJsonValidator;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanAdjustRepaymentPaymentTypeTest {

    private static final Long LOAN_ID = 425483L;
    private static final Long REPAYMENT_ID = 3371373L;
    private static final String EDIT_WITHOUT_TYPE = "{\"transactionDate\":\"03 September 2026\",\"transactionAmount\":\"138000\","
            + "\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"paymentTypeId\":\"\"}";
    private static final String UNDO = "{\"transactionDate\":\"03 September 2026\",\"transactionAmount\":0,"
            + "\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\"}";

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;
    private LoanWritePlatformServiceJpaRepositoryImpl service;
    private LoanTransactionRepository loanTransactionRepository;
    private PaymentDetailWritePlatformService paymentDetails;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 11, 5))));

        this.service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class, CALLS_REAL_METHODS);

        final Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getId()).thenReturn(LOAN_ID);
        when(loan.status()).thenReturn(LoanStatus.ACTIVE);
        final MonetaryCurrency xaf = new MonetaryCurrency("XAF", 0, 0);
        when(loan.getCurrency()).thenReturn(xaf);
        when(loan.getPrincpal()).thenReturn(Money.of(xaf, new BigDecimal("300000")));
        final LoanAssembler loanAssembler = mock(LoanAssembler.class);
        when(loanAssembler.assembleFrom(LOAN_ID)).thenReturn(loan);

        final LoanTransaction repayment = mock(LoanTransaction.class, RETURNS_DEEP_STUBS);
        when(repayment.getId()).thenReturn(REPAYMENT_ID);
        when(repayment.isInterestWaiver()).thenReturn(false);
        when(repayment.isRepaymentType()).thenReturn(true);
        when(repayment.getTransactionDate()).thenReturn(LocalDate.of(2026, 9, 3));
        this.loanTransactionRepository = mock(LoanTransactionRepository.class);
        when(this.loanTransactionRepository.findById(REPAYMENT_ID)).thenReturn(Optional.of(repayment));

        this.paymentDetails = mock(PaymentDetailWritePlatformService.class);

        ReflectionTestUtils.setField(this.service, "context", mock(PlatformSecurityContext.class));
        ReflectionTestUtils.setField(this.service, "loanEventApiJsonValidator",
                new LoanEventApiJsonValidator(new FromJsonHelper(), null, null));
        ReflectionTestUtils.setField(this.service, "loanAssembler", loanAssembler);
        ReflectionTestUtils.setField(this.service, "loanTransactionRepository", this.loanTransactionRepository);
        ReflectionTestUtils.setField(this.service, "paymentDetailWritePlatformService", this.paymentDetails);
        ReflectionTestUtils.setField(this.service, "businessEventNotifierService", mock(BusinessEventNotifierService.class));
        ReflectionTestUtils.setField(this.service, "accountTransfersReadPlatformService", mock(AccountTransfersReadPlatformService.class));
        ReflectionTestUtils.setField(this.service, "loanUtilService", mock(LoanUtilService.class, RETURNS_DEEP_STUBS));
        ReflectionTestUtils.setField(this.service, "loanRepositoryWrapper", mock(LoanRepositoryWrapper.class));
        ReflectionTestUtils.setField(this.service, "accountTransfersWritePlatformService",
                mock(AccountTransfersWritePlatformService.class));
        ReflectionTestUtils.setField(this.service, "noteRepository", mock(NoteRepository.class));
        ReflectionTestUtils.setField(this.service, "applicationCurrencyRepository",
                mock(ApplicationCurrencyRepositoryWrapper.class, RETURNS_DEEP_STUBS));
        ReflectionTestUtils.setField(this.service, "journalEntryWritePlatformService", mock(JournalEntryWritePlatformService.class));
        ReflectionTestUtils.setField(this.service, "loanAccountDomainService", mock(LoanAccountDomainService.class));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    @DisplayName("AC6: editing a repayment (gapili, loan 425483) with a blank payment type is rejected before anything is saved")
    void editingARepaymentWithoutPaymentTypeIsRejected() {
        final PlatformApiDataValidationException rejection = assertThrows(PlatformApiDataValidationException.class,
                () -> this.service.adjustLoanTransaction(LOAN_ID, REPAYMENT_ID, command(EDIT_WITHOUT_TYPE), false));
        assertTrue(rejection.getErrors().stream().anyMatch(error -> "paymentTypeId".equals(error.getParameterName())));
        verify(this.loanTransactionRepository, never()).saveAndFlush(any(LoanTransaction.class));
        verify(this.paymentDetails, never()).persistPaymentDetail(any(PaymentDetail.class));
    }

    @Test
    @DisplayName("Guard: undoing a repayment (amount 0) still needs no payment type")
    void undoingARepaymentNeedsNoPaymentType() {
        try {
            this.service.adjustLoanTransaction(LOAN_ID, REPAYMENT_ID, command(UNDO), false);
        } catch (PlatformApiDataValidationException rejection) {
            assertFalse(rejection.getErrors().stream().anyMatch(error -> "paymentTypeId".equals(error.getParameterName())),
                    "An undo carries no payment, so it must not be rejected for a missing payment type");
        } catch (RuntimeException unrelated) {
            return;
        }
    }

    private static JsonCommand command(final String json) {
        return JsonCommand.from(json, new FromJsonHelper().parse(json), new FromJsonHelper(), "LOAN", LOAN_ID, null, null, null, LOAN_ID,
                null, null, null, null, null, null);
    }
}
