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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.fineract.portfolio.loanaccount.domain.TrancheMilestoneRules.SurveyAnswer;
import org.junit.jupiter.api.Test;

public class TrancheMilestoneRulesTest {

    @Test
    public void repaymentBelowTheRuleStaysUnmet() {
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET, decide(false, true, SurveyAnswer.NONE, false, null));
    }

    @Test
    public void repaymentMetWithoutAMilestoneIsReady() {
        assertEquals(LoanSubStatus.REPAYMENT_MET, decide(true, false, SurveyAnswer.POSITIVE, false, null));
        assertEquals(LoanSubStatus.REPAYMENT_MET, decide(true, false, SurveyAnswer.NONE, true, null));
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET, decide(false, false, SurveyAnswer.NONE, true, null));
    }

    @Test
    public void nothingSubmittedAfterRepaymentIsAwaitingSurvey() {
        assertEquals(LoanSubStatus.AWAITING_SURVEY, decide(true, true, SurveyAnswer.NONE, false, null));
        assertEquals(LoanSubStatus.AWAITING_SURVEY, decide(true, true, SurveyAnswer.INCOMPLETE, false, null));
    }

    @Test
    public void operationalAndNeedsFundsIsSuccessfulOnlyWhenBothVerifiersConfirmEachAnswer() {
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS"));
        assertEquals(SurveyAnswer.INCOMPLETE,
                TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", true, true, true, false));
        assertEquals(SurveyAnswer.POSITIVE,
                TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", true, true, true, true));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL,
                decide(true, true, SurveyAnswer.POSITIVE, false, LoanSubStatus.AWAITING_SURVEY));
    }

    @Test
    public void notOperationalOrNoFurtherFundsIsExpired() {
        assertEquals(SurveyAnswer.NEGATIVE, TrancheMilestoneRules.answer("NOT_OPERATIONAL", "NEEDS_FUNDS", true, true, true, true));
        assertEquals(SurveyAnswer.NEGATIVE, TrancheMilestoneRules.answer("OPERATIONAL", "DOES_NOT_NEED_FUNDS", true, true, true, true));
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, false, LoanSubStatus.AWAITING_SURVEY));
    }

    @Test
    public void aSurveyStoredBeforeRepaymentWaitsForTheRepayment() {
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET,
                decide(false, true, SurveyAnswer.POSITIVE, false, LoanSubStatus.REPAYMENT_NOT_MET));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL,
                decide(true, true, SurveyAnswer.POSITIVE, false, LoanSubStatus.REPAYMENT_NOT_MET));
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, false, LoanSubStatus.REPAYMENT_NOT_MET));
    }

    @Test
    public void passingTheFirstRepaymentDateRequiresReviewUntilAFinalSurvey() {
        assertEquals(LoanSubStatus.REQUIRES_REVIEW,
                decide(false, true, SurveyAnswer.NONE, true, LoanSubStatus.REPAYMENT_NOT_MET));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW,
                decide(true, true, SurveyAnswer.NONE, true, LoanSubStatus.AWAITING_SURVEY));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW,
                decide(true, true, SurveyAnswer.INCOMPLETE, true, LoanSubStatus.AWAITING_SURVEY));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL,
                decide(true, true, SurveyAnswer.POSITIVE, true, LoanSubStatus.AWAITING_SURVEY));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL,
                decide(true, true, SurveyAnswer.POSITIVE, true, LoanSubStatus.REQUIRES_REVIEW));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL,
                decide(true, true, SurveyAnswer.POSITIVE, true, LoanSubStatus.SURVEY_SUCCESSFUL));
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, true, LoanSubStatus.REQUIRES_REVIEW));
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, true, LoanSubStatus.EXPIRED));
    }

    @Test
    public void aLaterSubmissionMovesBetweenSuccessfulAndExpired() {
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, false, LoanSubStatus.SURVEY_SUCCESSFUL));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL, decide(true, true, SurveyAnswer.POSITIVE, false, LoanSubStatus.EXPIRED));
    }

    @Test
    public void reversingTheQualifyingRepaymentReturnsToUnmetAndKeepsTheDateRule() {
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET,
                decide(false, true, SurveyAnswer.POSITIVE, false, LoanSubStatus.SURVEY_SUCCESSFUL));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW,
                decide(false, true, SurveyAnswer.POSITIVE, true, LoanSubStatus.SURVEY_SUCCESSFUL));
    }

    @Test
    public void aClosedTrancheClearsTheMilestoneStatus() {
        assertEquals(null, TrancheMilestoneRules.decide(false, true, true, SurveyAnswer.POSITIVE, false, LoanSubStatus.AWAITING_SURVEY));
    }

    @Test
    public void blankOrMissingAnswersAreNoneOrIncomplete() {
        assertEquals(SurveyAnswer.NONE, TrancheMilestoneRules.answer(null, null));
        assertEquals(SurveyAnswer.NONE, TrancheMilestoneRules.answer("", "  "));
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer("OPERATIONAL", null));
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer(null, "NEEDS_FUNDS"));
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer("OPERATIONAL", ""));
    }

    @Test
    public void eachMissingVerifierKeepsTheSurveyIncomplete() {
        assertEquals(SurveyAnswer.INCOMPLETE,
                TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", false, true, true, true));
        assertEquals(SurveyAnswer.INCOMPLETE,
                TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", true, false, true, true));
        assertEquals(SurveyAnswer.INCOMPLETE,
                TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", true, true, false, true));
        assertEquals(SurveyAnswer.INCOMPLETE,
                TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", true, true, true, null));
    }

    @Test
    public void negativeSurveyBeforeRepaymentAndDeadlineStaysUnmet() {
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET,
                decide(false, true, SurveyAnswer.NEGATIVE, false, LoanSubStatus.REPAYMENT_NOT_MET));
        assertEquals(LoanSubStatus.EXPIRED, decide(false, true, SurveyAnswer.NEGATIVE, true, LoanSubStatus.REPAYMENT_NOT_MET));
    }

    @Test
    public void nullSurveyIsTreatedAsNone() {
        assertEquals(LoanSubStatus.AWAITING_SURVEY, decide(true, true, null, false, null));
        assertEquals(LoanSubStatus.REPAYMENT_MET, decide(true, false, null, false, null));
    }

    @Test
    public void requiresReviewSticksOnlyWhileTheSurveyIsStillIncomplete() {
        assertEquals(LoanSubStatus.REQUIRES_REVIEW,
                decide(false, true, SurveyAnswer.NONE, true, LoanSubStatus.REQUIRES_REVIEW));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW,
                decide(true, true, SurveyAnswer.INCOMPLETE, true, LoanSubStatus.REQUIRES_REVIEW));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW,
                decide(false, true, SurveyAnswer.POSITIVE, true, LoanSubStatus.REQUIRES_REVIEW));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL,
                decide(true, true, SurveyAnswer.POSITIVE, false, LoanSubStatus.REQUIRES_REVIEW));
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, false, LoanSubStatus.REQUIRES_REVIEW));
    }

    @Test
    public void pastDeadlineWithIncompleteSurveyRequiresReviewEvenWhenRepaymentIsMet() {
        assertEquals(LoanSubStatus.REQUIRES_REVIEW,
                decide(true, true, SurveyAnswer.INCOMPLETE, true, LoanSubStatus.AWAITING_SURVEY));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW, decide(true, true, SurveyAnswer.NONE, true, null));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL, decide(true, true, SurveyAnswer.POSITIVE, true, null));
    }

    private static LoanSubStatus decide(final boolean repaymentMet, final boolean milestoneRequired, final SurveyAnswer survey,
            final boolean pastDeadline, final LoanSubStatus current) {
        return TrancheMilestoneRules.decide(true, repaymentMet, milestoneRequired, survey, pastDeadline, current);
    }
}
