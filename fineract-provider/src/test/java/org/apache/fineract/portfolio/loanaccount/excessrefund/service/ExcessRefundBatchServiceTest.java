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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundBatch;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundBatchRepository;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ExcessRefundBatchServiceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private LoanExcessRefundBatchRepository batchRepository;
    @Mock
    private LoanExcessRefundRepository refundRepository;
    @Mock
    private ExcessRefundWritePlatformService writePlatformService;
    @Mock
    private FromJsonHelper fromJsonHelper;
    @Mock
    private OfficeHierarchyService officeHierarchyService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private AppUser maker;

    @InjectMocks
    private ExcessRefundBatchService service;

    @Test
    void rejectBatchRejectsWhenSameAsMaker() {
        when(this.context.authenticatedUser()).thenReturn(this.maker);
        when(this.maker.getId()).thenReturn(9L);

        final LoanExcessRefundBatch batch = new LoanExcessRefundBatch();
        batch.setStatusEnum(LoanExcessRefundBatch.STATUS_PENDING_APPROVAL);
        batch.setSubmittedBy(this.maker);
        batch.setOfficeId(1L);
        when(this.batchRepository.findById(55L)).thenReturn(java.util.Optional.of(batch));

        final JsonCommand command = JsonCommand.fromExistingCommand(1L, "{}", JsonParser.parseString("{}"), this.fromJsonHelper,
                "EXCESS_REFUND_BATCH", 55L, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> this.service.rejectBatch(command)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("Maker and checker");
    }

    @Test
    void permitsLaterDecisionsAfterApprovingOnlyTwoOfFiveLines() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        try {
            final LoanExcessRefundBatch batch = new LoanExcessRefundBatch();
            ReflectionTestUtils.setField(batch, "id", 55L);
            batch.setOfficeId(1L);
            batch.setStatusEnum(LoanExcessRefundBatch.STATUS_PENDING_APPROVAL);
            final List<LoanExcessRefund> lines = java.util.stream.LongStream.rangeClosed(1, 5).mapToObj(id -> {
                final LoanExcessRefund line = new LoanExcessRefund();
                ReflectionTestUtils.setField(line, "id", id);
                line.setStatus(LoanExcessRefundStatus.PENDING_APPROVAL);
                return line;
            }).toList();
            when(this.context.authenticatedUser()).thenReturn(this.maker);
            when(this.batchRepository.findById(55L)).thenReturn(Optional.of(batch));
            when(this.batchRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(batch));
            when(this.refundRepository.findByBatchId(55L)).thenReturn(lines);
            when(this.officeHierarchyService.validateUserAccessToOffice(this.maker, 1L)).thenReturn(true);
            when(this.transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
            doAnswer(invocation -> {
                final JsonCommand command = invocation.getArgument(0);
                if (command.entityId() == 3L) {
                    throw new GeneralPlatformDomainRuleException("test.not.eligible", "Excess is no longer available");
                }
                final LoanExcessRefund line = lines.get(command.entityId().intValue() - 1);
                line.setStatus(LoanExcessRefundStatus.APPROVED);
                line.setApprovedAt(LocalDateTime.of(2026, 11, 24, 12, 0));
                return null;
            }).when(this.writePlatformService).approve(any());
            doAnswer(invocation -> {
                final JsonCommand command = invocation.getArgument(0);
                lines.get(command.entityId().intValue() - 1).setStatus(LoanExcessRefundStatus.REJECTED);
                return null;
            }).when(this.writePlatformService).reject(any());

            this.service.approveBatch(batchCommand("{\"refundIds\":[1,2]}"));
            assertThat(batch.getStatusEnum()).isEqualTo(LoanExcessRefundBatch.STATUS_PENDING_APPROVAL);
            assertThat(batch.getSuccessCount()).isEqualTo(2);

            this.service.approveBatch(batchCommand("{\"refundIds\":[3]}"));
            assertThat(batch.getStatusEnum()).isEqualTo(LoanExcessRefundBatch.STATUS_PENDING_APPROVAL);
            assertThat(batch.getSuccessCount()).isEqualTo(2);
            assertThat(batch.getApprovalNote()).contains("refundId=3");

            this.service.rejectBatch(batchCommand("{\"refundIds\":[3,4,5]}"));
            assertThat(batch.getStatusEnum()).isEqualTo(LoanExcessRefundBatch.STATUS_PARTIALLY_COMPLETED);
            assertThat(batch.getSuccessCount()).isEqualTo(2);
            assertThat(batch.getFailedCount()).isEqualTo(3);
        } finally {
            ThreadLocalContextUtil.clear();
        }
    }

    private JsonCommand batchCommand(final String body) {
        return JsonCommand.fromExistingCommand(1L, body, JsonParser.parseString(body), this.fromJsonHelper,
                "EXCESS_REFUND_BATCH", 55L, null, null, null, null, null, null, null, null, null, null);
    }
}
