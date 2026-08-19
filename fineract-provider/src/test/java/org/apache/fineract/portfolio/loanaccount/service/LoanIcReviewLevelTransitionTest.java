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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfig;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfigRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevel;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevelRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionState;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class LoanIcReviewLevelTransitionTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private LoanDecisionRepository loanDecisionRepository;

    @Mock
    private LoanDecisionLevelRepository loanDecisionLevelRepository;

    @Mock
    private IcReviewLevelConfigRepository icReviewLevelConfigRepository;

    @Mock
    private DynamicIcReviewLevelHelper dynamicIcReviewLevelHelper;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private BusinessEventNotifierService businessEventNotifierService;

    @InjectMocks
    private LoanDecisionWritePlatformServiceJpaRepositoryImpl loanDecisionService;

    private Long loanId = 100L;
    private JsonCommand command;
    private Loan loan;
    private LoanDecision loanDecision;

    private JsonCommand jsonCommand(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private IcReviewLevelConfig levelConfig(final Integer levelNumber, final Integer decisionStateValue) {
        final IcReviewLevelConfig config = new IcReviewLevelConfig();
        config.setLevelNumber(levelNumber);
        config.setDecisionStateValue(decisionStateValue);
        config.setLevelName("IC Review Level " + levelNumber);
        config.setLevelCode("IC_REVIEW_LEVEL_" + levelNumber);
        config.setIsActive(Boolean.TRUE);
        return config;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        command = jsonCommand("{\"note\":\"Returned for rework\"}");
        loan = mock(Loan.class);
        loanDecision = new LoanDecision();
        ReflectionTestUtils.setField(loanDecision, "id", 27349L);

        final AppUser currentUser = mock(AppUser.class);
        when(loan.getId()).thenReturn(loanId);
        when(context.getAuthenticatedUserIfPresent()).thenReturn(currentUser);
        when(loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true)).thenReturn(loan);
        when(loanDecisionRepository.findLoanDecisionByLoanId(loanId)).thenReturn(loanDecision);
        when(dynamicIcReviewLevelHelper.getRejectPermissionForLevel(anyInt())).thenReturn("REJECT_LOANICREVIEWDECISIONLEVELONE");
        when(context.authenticatedUser()).thenReturn(currentUser);
        when(dynamicIcReviewLevelHelper.getLevelDisplayName(any())).thenReturn("IC Review Level");
        when(loanDecisionLevelRepository.findByLoanDecisionIdAndLevelNumber(anyLong(), anyInt())).thenReturn(null);
    }

    @Test
    void testReturnLevelOneRevertsToDueDiligence() {
        when(icReviewLevelConfigRepository.findByLevelNumberAndActive(1)).thenReturn(levelConfig(1, 1400));
        when(dynamicIcReviewLevelHelper.getPreviousIcReviewDecisionState(1400)).thenReturn(null);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());

        final CommandProcessingResult result = loanDecisionService.rejectIcReviewDecisionDynamic(loanId, command, 1);

        assertNotNull(result);
        verify(loan).setLoanDecisionState(LoanDecisionState.DUE_DILIGENCE.getValue());
        assertEquals(LoanDecisionState.DUE_DILIGENCE.getValue(), loanDecision.getLoanDecisionState());
        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue(), loanDecision.getNextLoanIcReviewDecisionState());
    }

    @Test
    void testReturnLevelThreeRevertsToLevelTwo() {
        when(icReviewLevelConfigRepository.findByLevelNumberAndActive(3)).thenReturn(levelConfig(3, 1600));
        when(dynamicIcReviewLevelHelper.getPreviousIcReviewDecisionState(1600)).thenReturn(1500);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());

        final CommandProcessingResult result = loanDecisionService.rejectIcReviewDecisionDynamic(loanId, command, 3);

        assertNotNull(result);
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue());
        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue(), loanDecision.getLoanDecisionState());
        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue(), loanDecision.getNextLoanIcReviewDecisionState());
    }

    @Test
    void testReturnLevelOneClearsLegacySignedFlagAndStampsLevelRow() {
        when(icReviewLevelConfigRepository.findByLevelNumberAndActive(1)).thenReturn(levelConfig(1, 1400));
        when(dynamicIcReviewLevelHelper.getPreviousIcReviewDecisionState(1400)).thenReturn(null);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
        loanDecision.setIcReviewDecisionLevelOneSigned(Boolean.TRUE);

        loanDecisionService.rejectIcReviewDecisionDynamic(loanId, command, 1);

        assertTrue(loanDecision.getRejectIcReviewDecisionLevelOneSigned());
        assertFalse(loanDecision.getIcReviewDecisionLevelOneSigned());

        final ArgumentCaptor<LoanDecisionLevel> captor = ArgumentCaptor.forClass(LoanDecisionLevel.class);
        verify(loanDecisionLevelRepository).save(captor.capture());
        assertFalse(captor.getValue().getIsSigned());
        assertTrue(captor.getValue().getIsRejected());
        assertEquals(1, captor.getValue().getLevelNumber());
    }

    @Test
    void testReturnLevelWhenLoanHasNoDecisionState() {
        when(icReviewLevelConfigRepository.findByLevelNumberAndActive(1)).thenReturn(levelConfig(1, 1400));
        when(dynamicIcReviewLevelHelper.getPreviousIcReviewDecisionState(1400)).thenReturn(null);
        when(loan.getLoanDecisionState()).thenReturn(null);

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectIcReviewDecisionDynamic(loanId, command, 1));

        assertEquals("error.msg.loan.decision.state.invalid.for.reject", exception.getGlobalisationMessageCode());
    }

    @Test
    void testReturnLevelWhenDecisionRowMissing() {
        when(icReviewLevelConfigRepository.findByLevelNumberAndActive(1)).thenReturn(levelConfig(1, 1400));
        when(dynamicIcReviewLevelHelper.getPreviousIcReviewDecisionState(1400)).thenReturn(null);
        when(loanDecisionRepository.findLoanDecisionByLoanId(loanId)).thenReturn(null);

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectIcReviewDecisionDynamic(loanId, command, 1));

        assertEquals("error.msg.loan.account.should.not.found.in.decision.engine", exception.getGlobalisationMessageCode());
    }

    @Test
    void testReturnLevelFromUnrelatedStageIsRejected() {
        when(icReviewLevelConfigRepository.findByLevelNumberAndActive(3)).thenReturn(levelConfig(3, 1600));
        when(dynamicIcReviewLevelHelper.getPreviousIcReviewDecisionState(1600)).thenReturn(1500);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectIcReviewDecisionDynamic(loanId, command, 3));

        assertEquals("error.msg.loan.decision.state.invalid.for.reject", exception.getGlobalisationMessageCode());
    }

    @Test
    void testUnconfiguredLevelIsRejected() {
        when(icReviewLevelConfigRepository.findByLevelNumberAndActive(6)).thenReturn(null);

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectIcReviewDecisionDynamic(loanId, command, 6));

        assertEquals("error.msg.ic.review.level.not.found", exception.getGlobalisationMessageCode());
    }
}
