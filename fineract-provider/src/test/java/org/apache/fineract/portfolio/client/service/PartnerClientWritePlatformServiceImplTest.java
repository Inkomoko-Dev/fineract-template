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
package org.apache.fineract.portfolio.client.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.PartnerClientMapping;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingHistory;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingHistoryRepository;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingRepository;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PartnerClientWritePlatformServiceImplTest {

    @Mock
    private PartnerClientMappingRepository partnerClientMappingRepository;
    @Mock
    private PartnerClientMappingHistoryRepository partnerClientMappingHistoryRepository;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private DisbursementProviderReadPlatformService disbursementProviderReadPlatformService;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private Client client;
    @Mock
    private AppUser user;

    private PartnerClientWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        this.underTest = new PartnerClientWritePlatformServiceImpl(this.partnerClientMappingRepository,
                this.partnerClientMappingHistoryRepository, this.clientRepositoryWrapper, this.disbursementProviderReadPlatformService,
                this.context);
    }

    @Test
    void assignDoesNotPutUserIdInCommandId() {
        when(this.disbursementProviderReadPlatformService.isValidPartnerCode("PARTNER_A")).thenReturn(true);
        when(this.clientRepositoryWrapper.findOneWithNotFoundDetection(11L)).thenReturn(this.client);
        when(this.context.authenticatedUser()).thenReturn(this.user);
        when(this.partnerClientMappingRepository.save(any(PartnerClientMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(this.partnerClientMappingHistoryRepository.save(any(PartnerClientMappingHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final CommandProcessingResult result = this.underTest.assignClientToPartner(11L, "PARTNER_A", "Initial assignment");

        assertNull(result.commandId(), "commandId must stay unset so community-app does not treat assign as maker-checker");
    }

    @Test
    void assignRejectsUnknownPartnerWithValidationError() {
        when(this.disbursementProviderReadPlatformService.isValidPartnerCode("UNKNOWN")).thenReturn(false);

        assertThrows(PlatformApiDataValidationException.class, () -> this.underTest.assignClientToPartner(11L, "UNKNOWN", null));
    }
}
