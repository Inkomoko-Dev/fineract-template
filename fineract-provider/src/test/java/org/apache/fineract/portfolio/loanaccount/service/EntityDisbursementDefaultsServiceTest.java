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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.accounting.journalentry.data.JournalData;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.EntityDisbursementDefaultsConfiguration;
import org.apache.fineract.portfolio.loanaccount.data.EntityDisbursementDefaultsResult;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityDisbursementDefaultsServiceTest {

    @Mock
    private ConfigurationReadPlatformService configurationReadPlatformService;

    @Mock
    private CodeValueRepositoryWrapper codeValueRepository;

    private EntityDisbursementDefaultsService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new EntityDisbursementDefaultsService(configurationReadPlatformService, codeValueRepository, objectMapper);
        stubEntityConfigs();
    }

    @Test
    void resolveReturnsNotApplicableForNonConfiguredOffice() {
        enableDefaults();

        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        when(loan.getOffice()).thenReturn(office);
        when(office.getName()).thenReturn("Inkomoko Kenya");

        final EntityDisbursementDefaultsResult result = service.resolve(loan, LocalDate.now());

        assertFalse(result.isApplicable());
    }

    @Test
    void detectEntityViaClientOfficeWhenLoanOfficeMissing() {
        enableDefaults();

        final Loan loan = mock(Loan.class);
        final Client client = mock(Client.class);
        final Office clientOffice = mock(Office.class);
        when(loan.getOffice()).thenReturn(null);
        when(loan.client()).thenReturn(client);
        when(client.getOffice()).thenReturn(clientOffice);
        when(clientOffice.getName()).thenReturn("Inkomoko - Capital Kenya Limited");

        assertTrue(service.findConfigurationForLoan(loan) != null);
    }

    @Test
    void resolveDefaultsDepartmentAndFlagsMissingBudget() {
        enableDefaults();

        final CodeValue investment = mock(CodeValue.class);
        when(investment.label()).thenReturn("Investment");
        when(codeValueRepository.findOneByCodeNameAndLabelWithNotFoundDetection("Department", "Investment")).thenReturn(investment);
        when(codeValueRepository.findOneByCodeNameAndLabelOptional(eq("InvestmentsBudget"), anyString())).thenReturn(null);

        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        when(loan.getOffice()).thenReturn(office);
        when(office.getName()).thenReturn("Inkomoko - Capital Kenya Limited");

        final LocalDate testDate = LocalDate.of(2026, 7, 31);
        final EntityDisbursementDefaultsResult result = service.resolve(loan, testDate);

        assertTrue(result.isApplicable());
        assertEquals("Kenya Capital", result.getEntityName());
        assertEquals("Investment", result.getDepartmentName());
        // The budget location format is "Investments - MMMM yyyy"
        assertEquals("Investments - July 2026", result.getBudgetLocation());
        assertTrue(result.isBudgetReviewRequired());
    }

    @Test
    void applyDisbursementDefaultsForcesInvestmentEvenWhenDepartmentAlreadySet() {
        enableDefaults();

        final CodeValue investment = mock(CodeValue.class);
        when(investment.getId()).thenReturn(11L);
        when(investment.label()).thenReturn("Investment");
        when(codeValueRepository.findOneByCodeNameAndLabelWithNotFoundDetection("Department", "Investment")).thenReturn(investment);

        final CodeValue existingDepartment = mock(CodeValue.class);
        when(existingDepartment.getId()).thenReturn(99L);

        final CodeValue budget = mock(CodeValue.class);
        when(codeValueRepository.findOneByCodeNameAndLabelOptional("InvestmentsBudget", "Investments - July 2026")).thenReturn(budget);

        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        final LoanDisbursementDetails detail = new LoanDisbursementDetails(LocalDate.now(), null, BigDecimal.TEN, null);
        when(loan.getOffice()).thenReturn(office);
        when(office.getName()).thenReturn("Inkomoko - Capital Kenya Limited");
        when(loan.getDepartment()).thenReturn(existingDepartment);
        when(loan.getDisbursementDetails()).thenReturn(Collections.singletonList(detail));

        final Map<String, Object> changes = new LinkedHashMap<>();
        final EntityDisbursementDefaultsResult result = service.applyDisbursementDefaults(loan, LocalDate.of(2026, 7, 16), null,
                changes);

        assertTrue(result.isApplicable());
        verify(loan).updateDepartment(investment);
        assertEquals("Investments - July 2026", detail.getBudgetLocation());
        assertFalse(detail.getBudgetReviewRequired());
        assertEquals("Investments - July 2026", changes.get(LoanApiConstants.BUDGET_LOCATION_PARAM));
        assertEquals(false, changes.get(LoanApiConstants.BUDGET_REVIEW_REQUIRED_PARAM));
    }

    @Test
    void enrichOdooJournalDataOverridesLocationAndSetsDepartment() {
        enableDefaults();

        final CodeValue investment = mock(CodeValue.class);
        when(investment.label()).thenReturn("Investment");
        when(codeValueRepository.findOneByCodeNameAndLabelWithNotFoundDetection("Department", "Investment")).thenReturn(investment);
        when(codeValueRepository.findOneByCodeNameAndLabelOptional("InvestmentsBudget", "Investments - August 2026")).thenReturn(null);

        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        final LoanTransaction txn = mock(LoanTransaction.class);
        when(loan.getOffice()).thenReturn(office);
        when(office.getName()).thenReturn("Inkomoko - Capital Kenya Limited");
        when(txn.isDisbursement()).thenReturn(true);
        when(txn.getTransactionDate()).thenReturn(LocalDate.now());
        when(txn.getId()).thenReturn(55L);
        when(loan.getId()).thenReturn(100L);
        when(loan.getDisbursementDetails()).thenReturn(Collections.emptyList());

        final JournalData journalData = new JournalData();
        journalData.setLocation("Nairobi");

        service.enrichOdooJournalData(journalData, loan, txn, office);

        assertEquals("Investments - August 2026", journalData.getLocation());
        assertEquals("Investment", journalData.getDepartment());
        assertTrue(journalData.getBudgetReviewRequired());
    }

    @Test
    void enrichOdooJournalDataSkipsNonConfiguredEntity() {
        enableDefaults();

        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        final LoanTransaction txn = mock(LoanTransaction.class);
        when(loan.getOffice()).thenReturn(office);
        when(office.getName()).thenReturn("Inkomoko Kenya");
        when(txn.isDisbursement()).thenReturn(true);
        when(txn.getTransactionDate()).thenReturn(LocalDate.now());
        when(txn.getId()).thenReturn(55L);
        when(loan.getId()).thenReturn(100L);

        final JournalData journalData = new JournalData();
        journalData.setLocation("Nairobi");

        service.enrichOdooJournalData(journalData, loan, txn, office);

        assertEquals("Nairobi", journalData.getLocation());
        assertNull(journalData.getDepartment());
    }

    @Test
    void officeNameMatchingSupportsMultipleOffices() {
        enableDefaults();
        // Override default config for this specific test
        when(configurationReadPlatformService.retrieveGlobalConfiguration(EntityDisbursementDefaultsService.CONFIG_ENTITIES))
                .thenReturn(configWithString("["
                        + "{\"entityName\":\"Kenya Capital\","
                        + "\"officeNames\":[\"Inkomoko - Capital Kenya Limited\",\"Kenya Capital Branch\"],"
                        + "\"defaultDepartmentName\":\"Investment\","
                        + "\"budgetCodeName\":\"InvestmentsBudget\","
                        + "\"budgetLocationPrefix\":\"Investments - \"}]"));

        final EntityDisbursementDefaultsConfiguration config = service.findConfigurationForOffice("Kenya Capital Branch");
        
        assertTrue(config != null);
        assertEquals("Kenya Capital", config.getEntityName());
        assertTrue(config.matchesOfficeName("Kenya Capital Branch"));
    }

    @Test
    void officeNameMatchingIsCaseInsensitive() {
        enableDefaults();
        stubEntityConfigs();

        assertTrue(service.findConfigurationForOffice("inkomoko kenya capital") != null);
        assertTrue(service.findConfigurationForOffice("INKOMOKO KENYA CAPITAL") != null);
        assertTrue(service.findConfigurationForOffice("Inkomoko - Capital Kenya Limited") != null);
    }

    private void enableDefaults() {
        when(configurationReadPlatformService.retrieveGlobalConfiguration(EntityDisbursementDefaultsService.CONFIG_ENABLED))
                .thenReturn(new GlobalConfigurationPropertyData(EntityDisbursementDefaultsService.CONFIG_ENABLED, true, null, null,
                        null, "enabled", false));
    }

    private void stubEntityConfigs() {
        when(configurationReadPlatformService.retrieveGlobalConfiguration(EntityDisbursementDefaultsService.CONFIG_ENTITIES))
                .thenReturn(configWithString("["
                        + "{\"entityName\":\"Kenya Capital\","
                        + "\"officeNames\":[\"Inkomoko - Capital Kenya Limited\"],"
                        + "\"defaultDepartmentName\":\"Investment\","
                        + "\"budgetCodeName\":\"InvestmentsBudget\","
                        + "\"budgetLocationPrefix\":\"Investments - \"}]"));
    }

    private GlobalConfigurationPropertyData configWithString(final String value) {
        return new GlobalConfigurationPropertyData("name", false, null, null, value, "description", false);
    }
}