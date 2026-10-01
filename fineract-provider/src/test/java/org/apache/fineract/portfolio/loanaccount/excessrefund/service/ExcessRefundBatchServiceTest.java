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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundBatch;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundBatchRepository;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

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
}
