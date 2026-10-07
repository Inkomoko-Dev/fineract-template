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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevel;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevelRepository;
import org.apache.fineract.portfolio.loanaccount.service.IcReviewPreviousAmountResolver.PreviousSignedIcReviewLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CGLT-772: unit coverage for IC Review recommended-amount inheritance (multi-level, skipped/rejected prior,
 * legacy-only loans, dynamic-over-legacy preference).
 */
@ExtendWith(MockitoExtension.class)
class IcReviewPreviousAmountResolverTest {

    private static final Long LOAN_ID = 42L;

    @Mock
    private LoanDecisionLevelRepository loanDecisionLevelRepository;

    private IcReviewPreviousAmountResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new IcReviewPreviousAmountResolver(loanDecisionLevelRepository);
    }

    @Test
    void levelOneOrNullApprovingReturnsNull() {
        final LoanDecision decision = mock(LoanDecision.class);

        assertNull(resolver.resolve(LOAN_ID, decision, null));
        assertNull(resolver.resolve(LOAN_ID, decision, 1));
    }

    @Test
    void multiLevelInheritanceUsesLatestSignedPriorLevel() {
        when(loanDecisionLevelRepository.findSignedLevelsByLoanIdOrderByLevelNumberDesc(LOAN_ID))
                .thenReturn(Arrays.asList(signedLevel(2, "900"), signedLevel(1, "1000")));

        final PreviousSignedIcReviewLevel result = resolver.resolve(LOAN_ID, mock(LoanDecision.class), 3);

        assertEquals(2, result.getLevelNumber());
        assertEquals(new BigDecimal("900"), result.getRecommendedAmount());
    }

    @Test
    void skippedRejectedPriorFallsBackToEarlierSignedLevel() {
        // Repository query returns only isSigned=true rows; a rejected-but-signed row can still appear and must be skipped.
        when(loanDecisionLevelRepository.findSignedLevelsByLoanIdOrderByLevelNumberDesc(LOAN_ID))
                .thenReturn(Arrays.asList(rejectedSignedLevel(2, "900"), signedLevel(1, "1000")));

        final PreviousSignedIcReviewLevel result = resolver.resolve(LOAN_ID, mock(LoanDecision.class), 3);

        assertEquals(1, result.getLevelNumber());
        assertEquals(new BigDecimal("1000"), result.getRecommendedAmount());
    }

    @Test
    void dynamicRowPreferredOverLegacyColumns() {
        when(loanDecisionLevelRepository.findSignedLevelsByLoanIdOrderByLevelNumberDesc(LOAN_ID))
                .thenReturn(Collections.singletonList(signedLevel(2, "800")));

        // Legacy columns also set; dynamic row must win without consulting them.
        final PreviousSignedIcReviewLevel result = resolver.resolve(LOAN_ID, mock(LoanDecision.class), 3);

        assertEquals(2, result.getLevelNumber());
        assertEquals(new BigDecimal("800"), result.getRecommendedAmount());
    }

    @Test
    void legacyOnlyLoanUsesSignedLegacyColumns() {
        when(loanDecisionLevelRepository.findSignedLevelsByLoanIdOrderByLevelNumberDesc(LOAN_ID))
                .thenReturn(Collections.emptyList());

        final LoanDecision decision = mock(LoanDecision.class);
        when(decision.getIcReviewDecisionLevelOneRecommendedAmount()).thenReturn(new BigDecimal("500"));
        when(decision.getIcReviewDecisionLevelOneSigned()).thenReturn(Boolean.TRUE);
        when(decision.getRejectIcReviewDecisionLevelOneSigned()).thenReturn(Boolean.FALSE);

        final PreviousSignedIcReviewLevel result = resolver.resolve(LOAN_ID, decision, 2);

        assertEquals(1, result.getLevelNumber());
        assertEquals(new BigDecimal("500"), result.getRecommendedAmount());
    }

    @Test
    void unsignedNextApproverRowDoesNotOverrideEarlierSignedLevel() {
        // findSignedLevelsByLoanIdOrderByLevelNumberDesc only returns signed rows, so unsigned L2 never appears.
        when(loanDecisionLevelRepository.findSignedLevelsByLoanIdOrderByLevelNumberDesc(LOAN_ID))
                .thenReturn(Collections.singletonList(signedLevel(1, "1000")));

        final PreviousSignedIcReviewLevel result = resolver.resolve(LOAN_ID, mock(LoanDecision.class), 3);

        assertEquals(1, result.getLevelNumber());
        assertEquals(new BigDecimal("1000"), result.getRecommendedAmount());
    }

    @Test
    void legacyRejectedLevelIsSkipped() {
        when(loanDecisionLevelRepository.findSignedLevelsByLoanIdOrderByLevelNumberDesc(LOAN_ID))
                .thenReturn(Collections.emptyList());

        final LoanDecision decision = mock(LoanDecision.class);
        when(decision.getIcReviewDecisionLevelTwoRecommendedAmount()).thenReturn(new BigDecimal("900"));
        when(decision.getIcReviewDecisionLevelTwoSigned()).thenReturn(Boolean.TRUE);
        when(decision.getRejectIcReviewDecisionLevelTwoSigned()).thenReturn(Boolean.TRUE);
        when(decision.getIcReviewDecisionLevelOneRecommendedAmount()).thenReturn(new BigDecimal("1000"));
        when(decision.getIcReviewDecisionLevelOneSigned()).thenReturn(Boolean.TRUE);
        when(decision.getRejectIcReviewDecisionLevelOneSigned()).thenReturn(Boolean.FALSE);

        final PreviousSignedIcReviewLevel result = resolver.resolve(LOAN_ID, decision, 3);

        assertEquals(1, result.getLevelNumber());
        assertEquals(new BigDecimal("1000"), result.getRecommendedAmount());
    }

    private static LoanDecisionLevel signedLevel(final int levelNumber, final String amount) {
        final LoanDecisionLevel level = new LoanDecisionLevel();
        level.setLevelNumber(levelNumber);
        level.setIsSigned(Boolean.TRUE);
        level.setIsRejected(Boolean.FALSE);
        level.setRecommendedAmount(new BigDecimal(amount));
        return level;
    }

    private static LoanDecisionLevel rejectedSignedLevel(final int levelNumber, final String amount) {
        final LoanDecisionLevel level = signedLevel(levelNumber, amount);
        level.setIsRejected(Boolean.TRUE);
        return level;
    }
}
