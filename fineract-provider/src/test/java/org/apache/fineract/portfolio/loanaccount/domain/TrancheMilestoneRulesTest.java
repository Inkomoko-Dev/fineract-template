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
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET, decide(false, true, SurveyAnswer.NONE, false));
    }

    @Test
    public void repaymentMetWithoutAMilestoneIsReady() {
        assertEquals(LoanSubStatus.REPAYMENT_MET, decide(true, false, SurveyAnswer.POSITIVE, false));
        assertEquals(LoanSubStatus.REPAYMENT_MET, decide(true, false, SurveyAnswer.NONE, true));
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET, decide(false, false, SurveyAnswer.NONE, true));
    }

    @Test
    public void nothingSubmittedAfterRepaymentIsAwaitingSurvey() {
        assertEquals(LoanSubStatus.AWAITING_SURVEY, decide(true, true, SurveyAnswer.NONE, false));
        assertEquals(LoanSubStatus.AWAITING_SURVEY, decide(true, true, SurveyAnswer.INCOMPLETE, false));
    }

    @Test
    public void operationalAndNeedsFundsIsSuccessfulOnlyWhenBothVerifiersConfirmEachAnswer() {
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", null, null, null, null));
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", true, true, true, false));
        assertEquals(SurveyAnswer.POSITIVE, TrancheMilestoneRules.answer("OPERATIONAL", "NEEDS_FUNDS", true, true, true, true));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL, decide(true, true, SurveyAnswer.POSITIVE, false));
    }

    @Test
    public void notOperationalOrNoFurtherFundsIsExpired() {
        assertEquals(SurveyAnswer.NEGATIVE, TrancheMilestoneRules.answer("NOT_OPERATIONAL", "NEEDS_FUNDS", true, true, true, true));
        assertEquals(SurveyAnswer.NEGATIVE, TrancheMilestoneRules.answer("OPERATIONAL", "DOES_NOT_NEED_FUNDS", true, true, true, true));
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, false));
    }

    @Test
    public void aSurveyStoredBeforeRepaymentWaitsForTheRepayment() {
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET, decide(false, true, SurveyAnswer.POSITIVE, false));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL, decide(true, true, SurveyAnswer.POSITIVE, false));
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, false));
    }

    @Test
    public void passingTheFirstRepaymentDateRequiresReviewUntilAFinalSurvey() {
        assertEquals(LoanSubStatus.REQUIRES_REVIEW, decide(false, true, SurveyAnswer.NONE, true));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW, decide(true, true, SurveyAnswer.NONE, true));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW, decide(true, true, SurveyAnswer.INCOMPLETE, true));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL, decide(true, true, SurveyAnswer.POSITIVE, true));
        assertEquals(LoanSubStatus.EXPIRED, decide(true, true, SurveyAnswer.NEGATIVE, true));
    }

    @Test
    public void reversingTheQualifyingRepaymentReturnsToUnmetAndKeepsTheDateRule() {
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET, decide(false, true, SurveyAnswer.POSITIVE, false));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW, decide(false, true, SurveyAnswer.POSITIVE, true));
    }

    @Test
    public void aClosedTrancheClearsTheMilestoneStatus() {
        assertEquals(null, TrancheMilestoneRules.decide(false, true, true, SurveyAnswer.POSITIVE, false));
    }

    @Test
    public void blankOrMissingAnswersAreNoneOrIncomplete() {
        assertEquals(SurveyAnswer.NONE, TrancheMilestoneRules.answer(null, null, null, null, null, null));
        assertEquals(SurveyAnswer.NONE, TrancheMilestoneRules.answer("", "  ", null, null, null, null));
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer("OPERATIONAL", null, null, null, null, null));
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer(null, "NEEDS_FUNDS", null, null, null, null));
        assertEquals(SurveyAnswer.INCOMPLETE, TrancheMilestoneRules.answer("OPERATIONAL", "", null, null, null, null));
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
        assertEquals(LoanSubStatus.REPAYMENT_NOT_MET, decide(false, true, SurveyAnswer.NEGATIVE, false));
        assertEquals(LoanSubStatus.EXPIRED, decide(false, true, SurveyAnswer.NEGATIVE, true));
    }

    @Test
    public void nullSurveyIsTreatedAsNone() {
        assertEquals(LoanSubStatus.AWAITING_SURVEY, decide(true, true, null, false));
        assertEquals(LoanSubStatus.REPAYMENT_MET, decide(true, false, null, false));
    }

    @Test
    public void pastDeadlineWithIncompleteSurveyRequiresReviewEvenWhenRepaymentIsMet() {
        assertEquals(LoanSubStatus.REQUIRES_REVIEW, decide(true, true, SurveyAnswer.INCOMPLETE, true));
        assertEquals(LoanSubStatus.REQUIRES_REVIEW, decide(true, true, SurveyAnswer.NONE, true));
        assertEquals(LoanSubStatus.SURVEY_SUCCESSFUL, decide(true, true, SurveyAnswer.POSITIVE, true));
    }

    private static LoanSubStatus decide(final boolean repaymentMet, final boolean milestoneRequired, final SurveyAnswer survey,
            final boolean pastDeadline) {
        return TrancheMilestoneRules.decide(true, repaymentMet, milestoneRequired, survey, pastDeadline);
    }
}
