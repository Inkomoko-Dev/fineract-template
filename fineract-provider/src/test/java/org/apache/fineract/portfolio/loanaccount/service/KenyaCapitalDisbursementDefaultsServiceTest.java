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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.KenyaCapitalDisbursementDefaultsResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KenyaCapitalDisbursementDefaultsServiceTest {

    @Mock
    private ConfigurationReadPlatformService configurationReadPlatformService;

    @Mock
    private CodeValueRepositoryWrapper codeValueRepository;

    private KenyaCapitalDisbursementDefaultsService service;

    @BeforeEach
    void setUp() {
        service = new KenyaCapitalDisbursementDefaultsService(configurationReadPlatformService, codeValueRepository);
    }

    @Test
    void resolveReturnsNotApplicableForNonKenyaCapitalOffice() {
        enableDefaults();
        when(configurationReadPlatformService.retrieveGlobalConfiguration(KenyaCapitalDisbursementDefaultsService.CONFIG_OFFICE_NAME))
                .thenReturn(configWithString("Inkomoko Kenya Capital"));

        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        when(loan.getOffice()).thenReturn(office);
        when(office.getName()).thenReturn("Inkomoko Kenya");

        final KenyaCapitalDisbursementDefaultsResult result = service.resolve(loan, LocalDate.of(2026, 7, 16));

        assertFalse(result.isKenyaCapital());
    }

    @Test
    void resolveDefaultsDepartmentAndFlagsMissingBudget() {
        enableDefaults();
        when(configurationReadPlatformService.retrieveGlobalConfiguration(KenyaCapitalDisbursementDefaultsService.CONFIG_OFFICE_NAME))
                .thenReturn(configWithString("Inkomoko Kenya Capital"));
        when(configurationReadPlatformService.retrieveGlobalConfiguration(KenyaCapitalDisbursementDefaultsService.CONFIG_DEPARTMENT_NAME))
                .thenReturn(configWithString("Investment"));
        when(configurationReadPlatformService.retrieveGlobalConfiguration(KenyaCapitalDisbursementDefaultsService.CONFIG_BUDGET_CODE_NAME))
                .thenReturn(configWithString("InvestmentsBudget"));

        final CodeValue investment = mock(CodeValue.class);
        when(investment.label()).thenReturn("Investment");
        when(codeValueRepository.findOneByCodeNameAndLabelWithNotFoundDetection("Department", "Investment")).thenReturn(investment);
        when(codeValueRepository.findOneByCodeNameAndLabelOptional(eq("InvestmentsBudget"), anyString())).thenReturn(null);

        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        when(loan.getOffice()).thenReturn(office);
        when(office.getName()).thenReturn("Inkomoko Kenya Capital");
        when(loan.getDepartment()).thenReturn(null);

        final KenyaCapitalDisbursementDefaultsResult result = service.resolve(loan, LocalDate.of(2026, 7, 31));

        assertTrue(result.isKenyaCapital());
        assertEquals("Investment", result.getDepartmentName());
        assertEquals("Investments - July 2026", result.getBudgetLocation());
        assertTrue(result.isBudgetReviewRequired());
    }

    @Test
    void applyDisbursementDefaultsPersistsBudgetOnDetail() {
        enableDefaults();
        when(configurationReadPlatformService.retrieveGlobalConfiguration(KenyaCapitalDisbursementDefaultsService.CONFIG_OFFICE_NAME))
                .thenReturn(configWithString("Inkomoko Kenya Capital"));
        when(configurationReadPlatformService.retrieveGlobalConfiguration(KenyaCapitalDisbursementDefaultsService.CONFIG_DEPARTMENT_NAME))
                .thenReturn(configWithString("Investment"));
        when(configurationReadPlatformService.retrieveGlobalConfiguration(KenyaCapitalDisbursementDefaultsService.CONFIG_BUDGET_CODE_NAME))
                .thenReturn(configWithString("InvestmentsBudget"));

        final CodeValue investment = mock(CodeValue.class);
        when(investment.getId()).thenReturn(11L);
        when(investment.label()).thenReturn("Investment");
        when(codeValueRepository.findOneByCodeNameAndLabelWithNotFoundDetection("Department", "Investment")).thenReturn(investment);

        final CodeValue budget = mock(CodeValue.class);
        when(codeValueRepository.findOneByCodeNameAndLabelOptional("InvestmentsBudget", "Investments - July 2026")).thenReturn(budget);

        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        final LoanDisbursementDetails detail = new LoanDisbursementDetails(LocalDate.of(2026, 7, 16), null, BigDecimal.TEN, null);
        when(loan.getOffice()).thenReturn(office);
        when(office.getName()).thenReturn("Inkomoko Kenya Capital");
        when(loan.getDepartment()).thenReturn(null);
        when(loan.getDisbursementDetails()).thenReturn(Collections.singletonList(detail));

        final Map<String, Object> changes = new LinkedHashMap<>();
        final KenyaCapitalDisbursementDefaultsResult result = service.applyDisbursementDefaults(loan, LocalDate.of(2026, 7, 16), null,
                changes);

        assertTrue(result.isKenyaCapital());
        verify(loan).updateDepartment(investment);
        assertEquals("Investments - July 2026", detail.getBudgetLocation());
        assertFalse(detail.getBudgetReviewRequired());
        assertEquals("Investments - July 2026", changes.get(LoanApiConstants.BUDGET_LOCATION_PARAM));
        assertEquals(false, changes.get(LoanApiConstants.BUDGET_REVIEW_REQUIRED_PARAM));
    }

    private void enableDefaults() {
        when(configurationReadPlatformService.retrieveGlobalConfiguration(KenyaCapitalDisbursementDefaultsService.CONFIG_ENABLED))
                .thenReturn(new GlobalConfigurationPropertyData(KenyaCapitalDisbursementDefaultsService.CONFIG_ENABLED, true, null, null,
                        null, "enabled", false));
    }

    private GlobalConfigurationPropertyData configWithString(final String value) {
        return new GlobalConfigurationPropertyData("name", false, null, null, value, "description", false);
    }
}
