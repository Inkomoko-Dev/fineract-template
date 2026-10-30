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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.PaymentHubDisbursementPollRepository;
import org.apache.fineract.portfolio.loanaccount.service.ClientBankDetailsResolver;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class ExcessRefundWritePlatformServiceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private LoanRepositoryWrapper loanRepository;
    @Mock
    private LoanExcessRefundRepository refundRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private FromJsonHelper fromJsonHelper;
    @Mock
    private ExcessRefundGlPoster glPoster;
    @Mock
    private ExcessRefundPaymentHubService paymentHubService;
    @Mock
    private OfficeHierarchyService officeHierarchyService;
    @Mock
    private ClientBankDetailsResolver clientBankDetailsResolver;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private PaymentHubDisbursementPollRepository paymentHubDisbursementPollRepository;
    @Mock
    private AppUser checker;

    @InjectMocks
    private ExcessRefundWritePlatformService service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        when(this.context.authenticatedUser()).thenReturn(this.checker);
        lenient().when(this.checker.getId()).thenReturn(2L);
        lenient().when(this.transactionManager.getTransaction(any())).thenReturn(org.mockito.Mockito.mock(TransactionStatus.class));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    void approveRejectsWhenSameAsMaker() throws Exception {
        final LoanExcessRefund refund = new LoanExcessRefund();
        setEntityId(refund, 10L);
        refund.setLoanId(5L);
        refund.setStatus(LoanExcessRefundStatus.PENDING_APPROVAL);
        refund.setSubmittedBy(this.checker);
        refund.setSubmittedAt(LocalDateTime.of(2026, 9, 30, 12, 0));
        when(this.refundRepository.findById(10L)).thenReturn(java.util.Optional.of(refund));

        final JsonCommand command = JsonCommand.fromExistingCommand(1L, "{}", JsonParser.parseString("{}"), this.fromJsonHelper,
                "EXCESS_REFUND", 10L, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> this.service.approve(command)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("Maker and checker");
        verify(this.refundRepository, never()).saveAndFlush(any());
    }

    @Test
    void postRejectsWhenNotPaid() throws Exception {
        final LoanExcessRefund refund = new LoanExcessRefund();
        setEntityId(refund, 11L);
        refund.setLoanId(5L);
        refund.setStatus(LoanExcessRefundStatus.APPROVED);
        when(this.refundRepository.findById(11L)).thenReturn(java.util.Optional.of(refund));

        final JsonCommand command = JsonCommand.fromExistingCommand(1L, "{}", JsonParser.parseString("{}"), this.fromJsonHelper,
                "EXCESS_REFUND", 11L, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> this.service.post(command)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("invalid status");
        verify(this.glPoster, never()).post(any(), any(), any(), any(), any());
    }

    @Test
    void approveRejectsWhenAlreadyPaid() throws Exception {
        final LoanExcessRefund refund = new LoanExcessRefund();
        setEntityId(refund, 12L);
        refund.setLoanId(5L);
        refund.setStatus(LoanExcessRefundStatus.PAID);
        when(this.refundRepository.findById(12L)).thenReturn(java.util.Optional.of(refund));

        final JsonCommand command = JsonCommand.fromExistingCommand(1L, "{}", JsonParser.parseString("{}"), this.fromJsonHelper,
                "EXCESS_REFUND", 12L, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> this.service.approve(command)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("invalid status");
        verify(this.refundRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendToPaymentHubPersistsFailedWhenSubmitThrows() throws Exception {
        final LoanExcessRefund refund = new LoanExcessRefund();
        setEntityId(refund, 20L);
        refund.setLoanId(5L);
        refund.setAmount(new BigDecimal("100.00"));
        refund.setStatus(LoanExcessRefundStatus.APPROVED);
        when(this.refundRepository.findById(20L)).thenReturn(java.util.Optional.of(refund));
        final java.util.List<LoanExcessRefundStatus> savedStatuses = new java.util.ArrayList<>();
        when(this.refundRepository.saveAndFlush(any(LoanExcessRefund.class))).thenAnswer(invocation -> {
            final LoanExcessRefund saved = invocation.getArgument(0);
            savedStatuses.add(saved.getStatus());
            return saved;
        });

        final Loan loan = org.mockito.Mockito.mock(Loan.class);
        when(loan.getOfficeId()).thenReturn(1L);
        when(loan.getTotalOverpaid()).thenReturn(new BigDecimal("100.00"));
        when(this.loanRepository.findOneForUpdate(5L)).thenReturn(loan);
        when(this.officeHierarchyService.validateUserAccessToOffice(this.checker, 1L)).thenReturn(true);
        doNothing().when(this.checker).validateHasPermissionTo("SEND_TO_PAYMENTHUB_EXCESS_REFUND");

        when(this.paymentHubService.submitRefund(any(Loan.class), any(LoanExcessRefund.class), any()))
                .thenThrow(new GeneralPlatformDomainRuleException("error.msg.hub.down", "Hub unavailable"));

        final JsonCommand command = JsonCommand.fromExistingCommand(1L, "{}", JsonParser.parseString("{}"), this.fromJsonHelper,
                "EXCESS_REFUND", 20L, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> this.service.sendToPaymentHub(command)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("Hub unavailable");

        verify(this.refundRepository, times(2)).saveAndFlush(any(LoanExcessRefund.class));
        assertThat(savedStatuses).containsExactly(LoanExcessRefundStatus.PAYMENT_SUBMITTED, LoanExcessRefundStatus.PAYMENT_FAILED);
        assertThat(refund.getFailureReason()).contains("Hub unavailable");
        verify(this.paymentHubDisbursementPollRepository, never()).save(any());
    }

    private static void setEntityId(final AbstractPersistableCustom entity, final Long id) throws Exception {
        final Method setId = AbstractPersistableCustom.class.getDeclaredMethod("setId", Long.class);
        setId.setAccessible(true);
        setId.invoke(entity, id);
    }
}
