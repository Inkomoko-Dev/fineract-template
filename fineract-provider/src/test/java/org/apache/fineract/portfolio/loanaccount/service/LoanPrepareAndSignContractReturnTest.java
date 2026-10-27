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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevel;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionState;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class LoanPrepareAndSignContractReturnTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private LoanDecisionRepository loanDecisionRepository;

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
    private AppUser currentUser;

    private JsonCommand jsonCommand(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        command = jsonCommand("{\"note\":\"Test note\"}");
        loan = mock(Loan.class);
        loanDecision = new LoanDecision();
        ReflectionTestUtils.setField(loanDecision, "id", 1L);

        currentUser = mock(AppUser.class);
        when(loan.getId()).thenReturn(loanId);
        when(context.getAuthenticatedUserIfPresent()).thenReturn(currentUser);
        when(loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true)).thenReturn(loan);
        when(loanDecisionRepository.findLoanDecisionByLoanId(loanId)).thenReturn(loanDecision);
    }

    @Test
    void testReturnToPrepareAndSignContractFromApprovalStage_WhenLoanApproved() {
        when(loan.status()).thenReturn(LoanStatus.APPROVED);
        loanDecision.setPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setPreviousLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        CommandProcessingResult result = loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertNotNull(result);
        verify(loan).undoApproval(any());
        assertFalse(loanDecision.getPrepareAndSignContractSigned());
        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue(), loanDecision.getLoanDecisionState());
        assertEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getNextLoanIcReviewDecisionState());
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());
        assertTrue(loanDecision.getRejectPrepareAndSignContractSigned());
        verify(loanRepositoryWrapper).saveAndFlush(loan);
        verify(loanDecisionRepository).saveAndFlush(loanDecision);
        verify(noteRepository).save(any());
        verify(businessEventNotifierService).notifyPostBusinessEvent(any());
    }

    @Test
    void testReturnToPrepareAndSignContractAfterDisbursementRejected_LeavesThePrepareAndSignContractStepPending() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setPreviousLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        verify(loan, never()).undoApproval(any());
        assertFalse(loanDecision.getPrepareAndSignContractSigned());
        assertNotEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getLoanDecisionState(),
                "Approve is offered while the loan sits at PREPARE_AND_SIGN_CONTRACT, so the return would look like it did nothing");
        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue(), loanDecision.getLoanDecisionState());
        assertEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getNextLoanIcReviewDecisionState());
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
    }

    @Test
    void testReturnFromApprovalStage_ExistingLoanWithoutRecordedPreviousStage_UsesHighestSignedIcLevel() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setIcReviewDecisionLevelOneSigned(Boolean.TRUE);
        loanDecision.setIcReviewDecisionLevelTwoSigned(Boolean.TRUE);
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue(), loanDecision.getLoanDecisionState());
        assertEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getNextLoanIcReviewDecisionState());
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue());
    }

    @Test
    void testReturnFromApprovalStage_PreviousStageRecordedAsTheContractStageItself_UsesHighestSignedIcLevel() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setPreviousLoanIcReviewDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        loanDecision.setIcReviewDecisionLevelOneSigned(Boolean.TRUE);
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue(), loanDecision.getLoanDecisionState());
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
    }

    @Test
    void testReturnFromApprovalStage_WithoutRecordedPreviousStage_UsesHighestSignedDynamicIcLevel() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setIcReviewDecisionLevelOneSigned(Boolean.TRUE);
        loanDecision.getDecisionLevels().add(decisionLevel(5, 1800, true, false));
        loanDecision.getDecisionLevels().add(decisionLevel(6, 1801, true, false));
        loanDecision.getDecisionLevels().add(decisionLevel(7, 1802, false, true));
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertEquals(1801, loanDecision.getLoanDecisionState());
        verify(loan).setLoanDecisionState(1801);
    }

    @Test
    void testReturnFromApprovalStage_WithNoIcLevelRecordedAtAll_FallsBackToIcLevelOne() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue(), loanDecision.getLoanDecisionState());
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
    }

    @Test
    void testRejectPrepareAndSignContractFromContractStage_RevertsToPreviousIcLevel() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.FALSE);
        loanDecision.setPreviousLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_FIVE.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        CommandProcessingResult result = loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertNotNull(result);
        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_FIVE.getValue(), loanDecision.getLoanDecisionState());
        assertEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getNextLoanIcReviewDecisionState());
        assertTrue(loanDecision.getRejectPrepareAndSignContractSigned());
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_FIVE.getValue());
    }

    @Test
    void testRejectPrepareAndSignContractFromContractStage_ExistingLoanWithoutRecordedPreviousStage_UsesHighestSignedIcLevel() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.FALSE);
        loanDecision.setIcReviewDecisionLevelOneSigned(Boolean.TRUE);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue(), loanDecision.getLoanDecisionState());
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
    }

    @Test
    void testReturn_LoanAlreadyStuckByEarlierReturn_MovesBackToHighestSignedIcLevelWithContractPending() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.FALSE);
        loanDecision.setRejectPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        loanDecision.setIcReviewDecisionLevelOneSigned(Boolean.TRUE);
        loanDecision.setIcReviewDecisionLevelTwoSigned(Boolean.TRUE);
        loanDecision.setIcReviewDecisionLevelThreeSigned(Boolean.TRUE);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        verify(loan, never()).undoApproval(any());
        assertFalse(loanDecision.getPrepareAndSignContractSigned());
        assertEquals(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue(), loanDecision.getLoanDecisionState());
        assertEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getNextLoanIcReviewDecisionState());
        verify(loan).setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());
        verify(loanDecisionRepository).saveAndFlush(loanDecision);
    }

    @Test
    void testRejectPrepareAndSignContract_WhenContractFlagIsStaleButLoanIsBackAtAnIcLevel_ThrowsDomainException() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setPreviousLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue());

        GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectPrepareAndSignContract(loanId, command));

        assertEquals("error.msg.loan.decision.state.invalid.for.reject", exception.getGlobalisationMessageCode());
        verify(loan, never()).setLoanDecisionState(any());
        verify(loanDecisionRepository, never()).saveAndFlush(any());
    }

    @Test
    void testRejectPrepareAndSignContract_WhenLoanDecisionNotFound_ThrowsDomainException() {
        when(loanDecisionRepository.findLoanDecisionByLoanId(loanId)).thenReturn(null);

        GeneralPlatformDomainRuleException exception = assertThrows(
                GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectPrepareAndSignContract(loanId, command)
        );

        assertEquals("error.msg.loan.account.should.not.found.in.decision.engine", exception.getGlobalisationMessageCode());
    }

    @Test
    void testRejectPrepareAndSignContract_WhenInvalidState_ThrowsDomainException() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.FALSE);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.DUE_DILIGENCE.getValue());

        GeneralPlatformDomainRuleException exception = assertThrows(
                GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectPrepareAndSignContract(loanId, command)
        );

        assertEquals("error.msg.loan.decision.state.invalid.for.reject", exception.getGlobalisationMessageCode());
    }

    private LoanDecisionLevel decisionLevel(final int levelNumber, final int decisionStateValue, final boolean signed,
            final boolean rejected) {
        final IcReviewLevelConfig config = new IcReviewLevelConfig();
        config.setLevelNumber(levelNumber);
        config.setDecisionStateValue(decisionStateValue);
        return new LoanDecisionLevel(loanDecision, config, levelNumber, null, signed, rejected, null, null, null, null, null);
    }
}
