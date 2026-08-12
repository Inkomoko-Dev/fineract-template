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
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
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
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        CommandProcessingResult result = loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertNotNull(result);
        verify(loan).undoApproval(any());
        assertFalse(loanDecision.getPrepareAndSignContractSigned());
        assertEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getLoanDecisionState());
        assertEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getNextLoanIcReviewDecisionState());
        assertTrue(loanDecision.getRejectPrepareAndSignContractSigned());
        verify(loanRepositoryWrapper).saveAndFlush(loan);
        verify(loanDecisionRepository).saveAndFlush(loanDecision);
        verify(noteRepository).save(any());
        verify(businessEventNotifierService).notifyPostBusinessEvent(any());
    }

    @Test
    void testReturnToPrepareAndSignContractFromApprovalStage_WhenPendingApproval() {
        when(loan.status()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        loanDecision.setPrepareAndSignContractSigned(Boolean.TRUE);
        loanDecision.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());

        CommandProcessingResult result = loanDecisionService.rejectPrepareAndSignContract(loanId, command);

        assertNotNull(result);
        assertFalse(loanDecision.getPrepareAndSignContractSigned());
        assertEquals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue(), loanDecision.getLoanDecisionState());
        verify(loanRepositoryWrapper).saveAndFlush(loan);
        verify(loanDecisionRepository).saveAndFlush(loanDecision);
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
}
