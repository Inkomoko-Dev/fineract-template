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

/**
 * Reads one milestone survey. The request has two yes/no answers, operational and additional funding, and each
 * answer can name a BGS verifier and an Investment verifier.
 *
 * <p>Positive means the business is operational, it needs funds, and both verifiers confirmed each answer.
 * Negative means a verified answer says it is not operational or it does not need funds. A missing answer or
 * verifier is incomplete. No survey at all is none.
 *
 * <p>The waiting tranche's sub-status follows that result together with the repayment threshold and the deadline.
 * A positive survey after the deadline stays in review until a deadline exception is approved. Each waiting
 * tranche has its own survey. The previous sub-status is not an input.
 */
public final class TrancheMilestoneRules {

    public static final String OPERATIONAL = "OPERATIONAL";
    public static final String NOT_OPERATIONAL = "NOT_OPERATIONAL";
    public static final String NEEDS_FUNDS = "NEEDS_FUNDS";
    public static final String DOES_NOT_NEED_FUNDS = "DOES_NOT_NEED_FUNDS";

    public enum SurveyAnswer {
        NONE, INCOMPLETE, POSITIVE, NEGATIVE
    }

    private TrancheMilestoneRules() {}

    public static SurveyAnswer answer(final String operationalStatus, final String additionalFunding,
            final Boolean operationalVerifiedByBgs, final Boolean operationalVerifiedByInvestment,
            final Boolean fundingVerifiedByBgs, final Boolean fundingVerifiedByInvestment) {
        final boolean operationalKnown = operationalStatus != null && !operationalStatus.isBlank();
        final boolean fundingKnown = additionalFunding != null && !additionalFunding.isBlank();
        if (!operationalKnown && !fundingKnown) {
            return SurveyAnswer.NONE;
        }
        if (!operationalKnown || !fundingKnown) {
            return SurveyAnswer.INCOMPLETE;
        }
        final boolean operationalPositive = OPERATIONAL.equals(operationalStatus);
        final boolean operationalNegative = NOT_OPERATIONAL.equals(operationalStatus);
        final boolean fundingPositive = NEEDS_FUNDS.equals(additionalFunding);
        final boolean fundingNegative = DOES_NOT_NEED_FUNDS.equals(additionalFunding);
        if ((!operationalPositive && !operationalNegative) || (!fundingPositive && !fundingNegative)) {
            return SurveyAnswer.INCOMPLETE;
        }
        if (operationalNegative) {
            return verified(operationalVerifiedByBgs, operationalVerifiedByInvestment) ? SurveyAnswer.NEGATIVE : SurveyAnswer.INCOMPLETE;
        }
        if (fundingNegative) {
            return verified(fundingVerifiedByBgs, fundingVerifiedByInvestment) ? SurveyAnswer.NEGATIVE : SurveyAnswer.INCOMPLETE;
        }
        if (!verified(operationalVerifiedByBgs, operationalVerifiedByInvestment)
                || !verified(fundingVerifiedByBgs, fundingVerifiedByInvestment)) {
            return SurveyAnswer.INCOMPLETE;
        }
        return SurveyAnswer.POSITIVE;
    }

    public static LoanSubStatus decide(final boolean openTranche, final boolean repaymentMet, final boolean milestoneRequired,
            final SurveyAnswer survey, final boolean pastDeadline, final boolean deadlineExceptionApproved) {
        if (!openTranche) {
            return null;
        }
        final SurveyAnswer answer = survey == null ? SurveyAnswer.NONE : survey;
        if (milestoneRequired && answer == SurveyAnswer.NEGATIVE && (repaymentMet || pastDeadline)) {
            return LoanSubStatus.EXPIRED;
        }
        if (milestoneRequired && answer == SurveyAnswer.POSITIVE && repaymentMet) {
            if (pastDeadline && !deadlineExceptionApproved) {
                return LoanSubStatus.REQUIRES_REVIEW;
            }
            return LoanSubStatus.SURVEY_SUCCESSFUL;
        }
        if (!repaymentMet) {
            return milestoneRequired && pastDeadline ? LoanSubStatus.REQUIRES_REVIEW : LoanSubStatus.REPAYMENT_NOT_MET;
        }
        if (!milestoneRequired) {
            return LoanSubStatus.REPAYMENT_MET;
        }
        return pastDeadline ? LoanSubStatus.REQUIRES_REVIEW : LoanSubStatus.AWAITING_SURVEY;
    }

    private static boolean verified(final Boolean verifiedByBgs, final Boolean verifiedByInvestment) {
        return Boolean.TRUE.equals(verifiedByBgs) && Boolean.TRUE.equals(verifiedByInvestment);
    }
}
