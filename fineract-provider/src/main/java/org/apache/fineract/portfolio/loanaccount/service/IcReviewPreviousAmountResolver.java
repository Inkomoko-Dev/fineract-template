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

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevel;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevelRepository;

/**
 * Resolves the recommended amount (and level number) that an IC review screen should default from: the latest
 * completed IC review level strictly below the level being approved. Used by CGLT-772 so the UI does not always
 * revert to the Due Diligence recommendation.
 * <p>
 * Preference is given to dynamic {@code m_loan_decision_level} rows (written by the dynamic accept endpoints);
 * levels accepted via legacy endpoints fall back to the legacy columns on {@code m_loan_decision}, guarded by
 * their signed/rejected flags.
 */
public class IcReviewPreviousAmountResolver {

    private final LoanDecisionLevelRepository loanDecisionLevelRepository;

    public IcReviewPreviousAmountResolver(final LoanDecisionLevelRepository loanDecisionLevelRepository) {
        this.loanDecisionLevelRepository = loanDecisionLevelRepository;
    }

    /**
     * @param loanId
     *            the loan identifier
     * @param decision
     *            the loan decision entity for the loan
     * @param approvingLevelNumber
     *            the IC review level about to be approved (may be null)
     * @return the latest valid prior level, or null when there is none
     */
    public PreviousSignedIcReviewLevel resolve(final Long loanId, final LoanDecision decision,
            final Integer approvingLevelNumber) {
        if (approvingLevelNumber == null || approvingLevelNumber <= 1) {
            return null;
        }

        // Preferred source: dynamic decision level rows (written by the dynamic accept endpoints only).
        final List<LoanDecisionLevel> signedLevels = this.loanDecisionLevelRepository
                .findSignedLevelsByLoanIdOrderByLevelNumberDesc(loanId);
        for (final LoanDecisionLevel level : signedLevels) {
            if (level.getLevelNumber() < approvingLevelNumber && !Boolean.TRUE.equals(level.getIsRejected())
                    && level.getRecommendedAmount() != null) {
                return new PreviousSignedIcReviewLevel(level.getLevelNumber(), level.getRecommendedAmount());
            }
        }

        // Fallback for levels accepted via the legacy endpoints: legacy columns guarded by flags.
        for (int levelNumber = approvingLevelNumber - 1; levelNumber >= 1; levelNumber--) {
            final BigDecimal legacyAmount = getLegacySignedLevelRecommendedAmount(decision, levelNumber);
            if (legacyAmount != null) {
                return new PreviousSignedIcReviewLevel(levelNumber, legacyAmount);
            }
        }
        return null;
    }

    /**
     * Reads the legacy (pre-dynamic) recommended amount column for levels 1-5, only when that level is signed and
     * not rejected. Returns null for any other case.
     */
    BigDecimal getLegacySignedLevelRecommendedAmount(final LoanDecision decision, final int levelNumber) {
        final BigDecimal amount;
        final Boolean signed;
        final Boolean rejected;
        switch (levelNumber) {
            case 1:
                amount = decision.getIcReviewDecisionLevelOneRecommendedAmount();
                signed = decision.getIcReviewDecisionLevelOneSigned();
                rejected = decision.getRejectIcReviewDecisionLevelOneSigned();
            break;
            case 2:
                amount = decision.getIcReviewDecisionLevelTwoRecommendedAmount();
                signed = decision.getIcReviewDecisionLevelTwoSigned();
                rejected = decision.getRejectIcReviewDecisionLevelTwoSigned();
            break;
            case 3:
                amount = decision.getIcReviewDecisionLevelThreeRecommendedAmount();
                signed = decision.getIcReviewDecisionLevelThreeSigned();
                rejected = decision.getRejectIcReviewDecisionLevelThreeSigned();
            break;
            case 4:
                amount = decision.getIcReviewDecisionLevelFourRecommendedAmount();
                signed = decision.getIcReviewDecisionLevelFourSigned();
                rejected = decision.getRejectIcReviewDecisionLevelFourSigned();
            break;
            case 5:
                amount = decision.getIcReviewDecisionLevelFiveRecommendedAmount();
                signed = decision.getIcReviewDecisionLevelFiveSigned();
                rejected = decision.getRejectIcReviewDecisionLevelFiveSigned();
            break;
            default:
                return null;
        }
        if (amount != null && Boolean.TRUE.equals(signed) && !Boolean.TRUE.equals(rejected)) {
            return amount;
        }
        return null;
    }

    /**
     * Value holder for the latest completed IC review level below the level being approved, pairing its level number
     * with its recommended amount so both are resolved from a single lookup.
     */
    public static final class PreviousSignedIcReviewLevel {

        private final Integer levelNumber;
        private final BigDecimal recommendedAmount;

        public PreviousSignedIcReviewLevel(final Integer levelNumber, final BigDecimal recommendedAmount) {
            this.levelNumber = levelNumber;
            this.recommendedAmount = recommendedAmount;
        }

        public Integer getLevelNumber() {
            return this.levelNumber;
        }

        public BigDecimal getRecommendedAmount() {
            return this.recommendedAmount;
        }
    }
}
