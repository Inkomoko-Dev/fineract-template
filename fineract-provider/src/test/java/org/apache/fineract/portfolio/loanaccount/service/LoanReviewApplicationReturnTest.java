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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevel;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionState;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class LoanReviewApplicationReturnTest {

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
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private LoanDecisionLevel decisionLevelFor(final LoanDecision parent, final Integer levelNumber) {
        final LoanDecisionLevel level = new LoanDecisionLevel();
        ReflectionTestUtils.setField(level, "id", 500L + levelNumber);
        level.setLoanDecision(parent);
        level.setLevelNumber(levelNumber);
        return level;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        command = jsonCommand("{\"note\":\"Returned for correction\"}");
        loan = mock(Loan.class);
        loanDecision = new LoanDecision();
        ReflectionTestUtils.setField(loanDecision, "id", 27259L);

        currentUser = mock(AppUser.class);
        when(loan.getId()).thenReturn(loanId);
        when(context.getAuthenticatedUserIfPresent()).thenReturn(currentUser);
        when(loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true)).thenReturn(loan);
        when(loanDecisionRepository.findLoanDecisionByLoanId(loanId)).thenReturn(loanDecision);
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.REVIEW_APPLICATION.getValue());
    }

    @Test
    void testLoanDecisionHashCodeDoesNotRecurseThroughDecisionLevels() {
        final List<LoanDecisionLevel> levels = new ArrayList<>();
        levels.add(decisionLevelFor(loanDecision, 1));
        levels.add(decisionLevelFor(loanDecision, 2));
        loanDecision.setDecisionLevels(levels);

        assertDoesNotThrow(() -> loanDecision.hashCode());
        assertDoesNotThrow(() -> levels.get(0).hashCode());
        assertDoesNotThrow(() -> loanDecision.equals(new LoanDecision()));
        assertDoesNotThrow(() -> loanDecision.toString());
        assertDoesNotThrow(() -> levels.get(0).toString());
    }

    @Test
    void testReturnReviewApplicationWithDecisionLevelsPresent() {
        final List<LoanDecisionLevel> levels = new ArrayList<>();
        levels.add(decisionLevelFor(loanDecision, 1));
        loanDecision.setDecisionLevels(levels);

        final CommandProcessingResult result = loanDecisionService.rejectLoanApplicationReview(loanId, command);

        assertNotNull(result);
        assertEquals(27259L, result.resourceId());
        assertEquals(loanId, result.getLoanId());
        verify(loan).setLoanDecisionState(null);
        verify(loanDecisionRepository).delete(loanDecision);
        verify(loanRepositoryWrapper).saveAndFlush(loan);
        verify(noteRepository).save(any());
        verify(businessEventNotifierService).notifyPostBusinessEvent(any());
    }

    @Test
    void testReturnReviewApplicationWithoutDecisionLevels() {
        final CommandProcessingResult result = loanDecisionService.rejectLoanApplicationReview(loanId, command);

        assertNotNull(result);
        assertEquals(27259L, result.resourceId());
        verify(loan).setLoanDecisionState(null);
        verify(loanDecisionRepository).delete(loanDecision);
    }

    @Test
    void testReturnReviewApplicationWithoutNote() {
        final CommandProcessingResult result = loanDecisionService.rejectLoanApplicationReview(loanId, jsonCommand("{}"));

        assertNotNull(result);
        verify(loanDecisionRepository).delete(loanDecision);
        verify(noteRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void testReturnReviewApplicationWhenNotInReviewApplicationStage() {
        when(loan.getLoanDecisionState()).thenReturn(LoanDecisionState.DUE_DILIGENCE.getValue());

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectLoanApplicationReview(loanId, command));

        assertEquals("error.msg.loan.invalid.state", exception.getGlobalisationMessageCode());
    }

    @Test
    void testReturnReviewApplicationWhenDecisionRowMissing() {
        when(loanDecisionRepository.findLoanDecisionByLoanId(loanId)).thenReturn(null);

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> loanDecisionService.rejectLoanApplicationReview(loanId, command));

        assertEquals("error.msg.loan.account.should.not.found.in.decision.engine", exception.getGlobalisationMessageCode());
    }
}
