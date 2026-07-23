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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionData;
import org.apache.fineract.portfolio.loanaccount.domain.DisbursementInstructionStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstruction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstructionRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanDisbursementInstructionNotFoundException;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementPartnerAccessService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisbursementInstructionReadPlatformServiceImplTest {

    @Mock
    private LoanDisbursementInstructionRepository instructionRepository;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private DisbursementPartnerAccessService disbursementPartnerAccessService;
    @Mock
    private AppUser appUser;

    @InjectMocks
    private DisbursementInstructionReadPlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        given(this.context.authenticatedUser()).willReturn(this.appUser);
        given(this.disbursementPartnerAccessService.resolveProviderCodeForUser(this.appUser)).willReturn(Optional.of("KIFIYA"));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void retrieveOneReturnsOwnProviderInstruction() {
        final LoanDisbursementInstruction instruction = LoanDisbursementInstruction.createReceived(10L, "KIFIYA", 3L, "SUP-001", "key-1",
                "hash", 1L);
        given(this.instructionRepository.findById(55L)).willReturn(Optional.of(instruction));

        final DisbursementInstructionData data = this.underTest.retrieveOne(55L);

        assertThat(data.getLoanId()).isEqualTo(10L);
        assertThat(data.getStatus()).isEqualTo(DisbursementInstructionStatus.RECEIVED);
        assertThat(data.getDisbursementProviderCode()).isEqualTo("KIFIYA");
    }

    @Test
    void retrieveOneRejectsOtherProvider() {
        final LoanDisbursementInstruction instruction = LoanDisbursementInstruction.createReceived(10L, "OTHER", 3L, "SUP-001", "key-1",
                "hash", 1L);
        given(this.instructionRepository.findById(55L)).willReturn(Optional.of(instruction));

        assertThatThrownBy(() -> this.underTest.retrieveOne(55L)).isInstanceOf(LoanDisbursementInstructionNotFoundException.class);
    }

    @Test
    void retrieveOneMissingThrowsNotFound() {
        given(this.instructionRepository.findById(55L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> this.underTest.retrieveOne(55L)).isInstanceOf(LoanDisbursementInstructionNotFoundException.class);
    }

    @Test
    void retrieveByLoanFiltersToBoundProvider() {
        final LoanDisbursementInstruction own = LoanDisbursementInstruction.createReceived(10L, "KIFIYA", 3L, "SUP-001", "key-1", "hash",
                1L);
        final LoanDisbursementInstruction other = LoanDisbursementInstruction.createReceived(10L, "OTHER", 4L, "SUP-002", "key-2", "hash2",
                1L);
        given(this.instructionRepository.findByLoanIdOrderByIdDesc(10L)).willReturn(List.of(own, other));

        final List<DisbursementInstructionData> data = this.underTest.retrieveByLoanId(10L);

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getDisbursementProviderCode()).isEqualTo("KIFIYA");
    }
}
