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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.exception.CrbLocalValidationException;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionRwandaConsumerCreditData;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionRwandaCorporateCreditData;
import org.apache.fineract.portfolio.loanaccount.domain.CRBPostingLoggerRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.TransunionCrbCorporateLoggerRepository;
import org.apache.fineract.portfolio.loanaccount.domain.TransunionCrbConsumerLoggerRepository;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransUnionCrbServiceImplTest {

    @InjectMocks
    private TransUnionCrbServiceImpl service;

    @Mock
    private TransUnionCrbPostConsumerCreditReadPlatformServiceImpl transUnionCrbPostConsumerCreditReadPlatformServiceImpl;

    @Mock
    private TransUnionCrbPostCorporateCreditReadPlatformServiceImpl transUnionCrbPostCorporateCreditReadPlatformServiceImpl;

    @Mock
    private LoanRepositoryWrapper loanRepository;

    @Mock
    private TransunionCrbConsumerLoggerRepository crbConsumerLoggerRepository;

    @Mock
    private TransunionCrbCorporateLoggerRepository crbCorporateLoggerRepository;

    @Mock
    private CRBPostingLoggerRepository crbPostingLoggerRepository;

    @Mock
    private PlatformSecurityContext context;

    @Test
    void validateConsumerAddressForCrbRejectsMissingActiveAddress() {
        TransUnionRwandaConsumerCreditData creditData = new TransUnionRwandaConsumerCreditData();
        creditData.setAccountNumber("LN-001");

        CrbLocalValidationException exception = assertThrows(CrbLocalValidationException.class,
                () -> service.validateConsumerAddressForCrb(creditData));

        assertTrue(exception.getMessage().contains("no active address"));
    }

    @Test
    void validateConsumerAddressForCrbRejectsMissingCountryOnSelectedAddress() {
        TransUnionRwandaConsumerCreditData creditData = new TransUnionRwandaConsumerCreditData();
        creditData.setAccountNumber("LN-002");
        creditData.setSelectedAddressId(44L);
        creditData.setSelectedAddressType("CURRENT ADDRESS");
        creditData.setCountry(" ");

        CrbLocalValidationException exception = assertThrows(CrbLocalValidationException.class,
                () -> service.validateConsumerAddressForCrb(creditData));

        assertTrue(exception.getMessage().contains("CURRENT ADDRESS"));
        assertTrue(exception.getMessage().contains("has no country"));
    }

    @Test
    void validateCorporateAddressForCrbAllowsSelectedAddressWithCountry() {
        TransUnionRwandaCorporateCreditData creditData = new TransUnionRwandaCorporateCreditData();
        creditData.setAccountNumber("LN-003");
        creditData.setSelectedAddressId(55L);
        creditData.setSelectedAddressType("CURRENT ADDRESS");
        creditData.setCountry("Rwanda");

        assertDoesNotThrow(() -> service.validateCorporateAddressForCrb(creditData));
    }
}
