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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfigRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrixLevelRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagementRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionState;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Disburse-path decision validation must match approve null-safety (Boolean.TRUE.equals + null stage dates).
 */
class LoanDecisionStateUtilServiceDisbursementValidationTest {

    private LoanDecisionRepository loanDecisionRepository;
    private LoanDecisionStateUtilService service;

    @BeforeEach
    void setUp() {
        this.loanDecisionRepository = mock(LoanDecisionRepository.class);
        final ConfigurationReadPlatformService configurationReadPlatformService = mock(ConfigurationReadPlatformService.class);
        final CalendarInstanceRepository calendarInstanceRepository = mock(CalendarInstanceRepository.class);
        final ConfigurationDomainService configurationDomainService = mock(ConfigurationDomainService.class);
        final LoanUtilService loanUtilService = mock(LoanUtilService.class);
        final LoanScheduleAssembler loanScheduleAssembler = mock(LoanScheduleAssembler.class);
        final LoanRepositoryWrapper loanRepositoryWrapper = mock(LoanRepositoryWrapper.class);
        final LoanReadPlatformService loanReadPlatformService = mock(LoanReadPlatformService.class);
        final LoanCollateralManagementRepository loanCollateralManagementRepository = mock(LoanCollateralManagementRepository.class);
        final DynamicIcReviewLevelHelper dynamicIcReviewLevelHelper = mock(DynamicIcReviewLevelHelper.class);
        final IcReviewLevelConfigRepository icReviewLevelConfigRepository = mock(IcReviewLevelConfigRepository.class);
        final LoanApprovalMatrixLevelRepository loanApprovalMatrixLevelRepository = mock(LoanApprovalMatrixLevelRepository.class);

        this.service = new LoanDecisionStateUtilService(configurationReadPlatformService, loanDecisionRepository,
                calendarInstanceRepository, configurationDomainService, loanUtilService, loanScheduleAssembler, loanRepositoryWrapper,
                loanReadPlatformService, loanCollateralManagementRepository, dynamicIcReviewLevelHelper, icReviewLevelConfigRepository,
                loanApprovalMatrixLevelRepository);

        final org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData extendConfig = mock(
                org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData.class);
        when(extendConfig.isEnabled()).thenReturn(true);
        when(configurationReadPlatformService.retrieveGlobalConfiguration("Add-More-Stages-To-A-Loan-Life-Cycle"))
                .thenReturn(extendConfig);
    }

    @Test
    void disbursementValidationWithNullLegacyStageDatesDoesNotNpe() {
        final Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(433618L);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        final LoanDecision decision = mock(LoanDecision.class);
        when(decision.getReviewApplicationSigned()).thenReturn(true);
        when(decision.getPrepareAndSignContractOn()).thenReturn(LocalDate.of(2026, 10, 1));
        when(decision.getIcReviewDecisionLevelOneOn()).thenReturn(null);
        when(decision.getDueDiligenceOn()).thenReturn(null);
        when(decision.getReviewApplicationOn()).thenReturn(null);
        when(decision.getIcReviewDecisionLevelTwoOn()).thenReturn(null);
        when(decision.getIcReviewDecisionLevelThreeOn()).thenReturn(null);
        when(decision.getIcReviewDecisionLevelFourOn()).thenReturn(null);
        when(decision.getIcReviewDecisionLevelFiveOn()).thenReturn(null);
        when(decision.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loanDecisionRepository.findLoanDecisionByLoanId(433618L)).thenReturn(decision);

        final JsonCommand command = jsonCommand(
                "{\"actualDisbursementDate\":\"12 October 2026\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}");

        assertDoesNotThrow(() -> service.validateLoanReviewApplicationStateIsFiredBeforeDisbursal(loan, command));
    }

    @Test
    void disbursementValidationWithNullReviewApplicationSignedThrowsDomainRuleNotNpe() {
        final Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(433618L);

        final LoanDecision decision = mock(LoanDecision.class);
        when(decision.getReviewApplicationSigned()).thenReturn(null);
        when(loanDecisionRepository.findLoanDecisionByLoanId(433618L)).thenReturn(decision);

        final JsonCommand command = jsonCommand(
                "{\"actualDisbursementDate\":\"12 October 2026\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}");

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.validateLoanReviewApplicationStateIsFiredBeforeDisbursal(loan, command));
    }

    private static JsonCommand jsonCommand(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
