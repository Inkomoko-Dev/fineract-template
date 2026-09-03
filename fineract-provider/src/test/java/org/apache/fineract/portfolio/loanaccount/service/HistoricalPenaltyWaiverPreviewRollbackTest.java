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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * The preview runs the real correction against the real aggregate, so the only thing standing between it and a
 * committed waiver is the transaction it runs in. These pin that guard rather than trusting it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HistoricalPenaltyWaiverPreviewRollbackTest {

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus status = mock(TransactionStatus.class);
    private final LoanAssembler loanAssembler = mock(LoanAssembler.class);

    private HistoricalPenaltyWaiverPreviewService service() {
        when(this.transactionManager.getTransaction(any())).thenReturn(this.status);
        // Assembling the loan blows up, which is enough: it proves the guard is set before any work is attempted.
        when(this.loanAssembler.assembleFrom(anyLong())).thenThrow(new IllegalStateException("boom"));

        return new HistoricalPenaltyWaiverPreviewService(this.loanAssembler, mock(LoanChargeRepository.class),
                mock(LoanChargeReadPlatformService.class), mock(HistoricalPenaltyWaiverApprovalPolicy.class),
                mock(HistoricalPenaltyWaiverReadPlatformService.class), mock(GLClosureRepository.class),
                mock(ConfigurationDomainService.class), this.transactionManager);
    }

    @Test
    public void thePreviewMarksItsTransactionRollbackOnlyBeforeDoingAnyWork() {
        assertThrows(IllegalStateException.class,
                () -> service().preview(4001L, 77L, new BigDecimal("5000.00"), LocalDate.of(2026, 1, 31)));

        verify(this.status).setRollbackOnly();
        verify(this.transactionManager, never()).commit(any());
    }

    @Test
    public void thePreviewRunsInItsOwnReadOnlyTransaction() {
        assertThrows(IllegalStateException.class,
                () -> service().preview(4001L, 77L, new BigDecimal("5000.00"), LocalDate.of(2026, 1, 31)));

        final ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(this.transactionManager).getTransaction(definition.capture());

        assertTrue(definition.getValue().isReadOnly(), "a read-only transaction puts Hibernate in manual flush mode");
        assertTrue(definition.getValue().getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                "the preview must never join and then poison a caller's transaction");
    }
}
