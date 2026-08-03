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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.exception.CrbLocalValidationException;
import org.apache.fineract.infrastructure.core.exception.CrbPreSubmissionValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionRwandaConsumerCreditData;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionRwandaCorporateCreditData;
import org.apache.fineract.portfolio.loanaccount.domain.CRBPostingLoggerRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.TransunionCrbConsumerLoggerRepository;
import org.apache.fineract.portfolio.loanaccount.domain.TransunionCrbCorporateLoggerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @Mock
    private Environment env;

    @Test
    void validateConsumerAddressForCrbRejectsMissingAddress() {
        TransUnionRwandaConsumerCreditData creditData = new TransUnionRwandaConsumerCreditData();
        creditData.setAccountNumber("LN-001");

        CrbLocalValidationException exception = assertThrows(CrbLocalValidationException.class,
                () -> service.validateConsumerAddressForCrb(creditData));

        assertTrue(exception.getMessage().contains("no address available"));
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

    @Test
    void validateCorporateCreditRecordRejectsDefaultIndicatorWhenDaysInArrearsIsZero() {
        TransUnionRwandaCorporateCreditData creditData = corporateCreditData("LOAN/19301/2024", "D", 0);

        CrbPreSubmissionValidationException exception = assertThrows(CrbPreSubmissionValidationException.class,
                () -> service.validateCorporateCreditRecord(creditData));

        assertTrue(exception.getMessage().contains("blocked before sending"));
        assertTrue(exception.getMessage().contains("LOAN/19301/2024"));
        assertTrue(exception.getMessage().contains("requires days in arrears greater than 90"));
        assertEquals(exception.getMessage(), exception.getUserMessage());
    }

    @Test
    void validateCorporateCreditRecordRejectsDefaultIndicatorWhenDaysInArrearsIsNinety() {
        TransUnionRwandaCorporateCreditData creditData = corporateCreditData("LOAN/19301/2024", "D", 90);

        assertThrows(CrbPreSubmissionValidationException.class, () -> service.validateCorporateCreditRecord(creditData));
    }

    @Test
    void validateCorporateCreditRecordAllowsDefaultIndicatorWhenDaysInArrearsExceedsNinety() {
        TransUnionRwandaCorporateCreditData creditData = corporateCreditData("LOAN/19301/2024", "D", 91);

        assertDoesNotThrow(() -> service.validateCorporateCreditRecord(creditData));
    }

    @Test
    void validateCorporateCreditRecordAllowsCurrentIndicatorWhenDaysInArrearsIsZero() {
        TransUnionRwandaCorporateCreditData creditData = corporateCreditData("LOAN/19301/2024", "C", 0);

        assertDoesNotThrow(() -> service.validateCorporateCreditRecord(creditData));
    }

    private TransUnionRwandaCorporateCreditData corporateCreditData(String accountNumber, String indicator, Integer daysInArrears) {
        TransUnionRwandaCorporateCreditData creditData = new TransUnionRwandaCorporateCreditData();
        creditData.setAccountNumber(accountNumber);
        creditData.setCurrentBalanceIndicator(indicator);
        creditData.setDaysInArrears(daysInArrears);
        creditData.setLoanId(401532);
        return creditData;
    }
}
