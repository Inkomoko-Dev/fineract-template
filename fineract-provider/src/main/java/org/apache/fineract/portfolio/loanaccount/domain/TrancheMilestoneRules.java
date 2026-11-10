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
 * Decides the loan sub-status for the tranche that is waiting to be disbursed.
 * A negative survey is a business that is not operational, or that does not need additional funds.
 * A positive survey also needs both BGS and Investment verifiers for each answer.
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

    public static SurveyAnswer answer(final String operationalStatus, final String additionalFunding) {
        return answer(operationalStatus, additionalFunding, null, null, null, null);
    }

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
        if (!OPERATIONAL.equals(operationalStatus) || !NEEDS_FUNDS.equals(additionalFunding)) {
            return SurveyAnswer.NEGATIVE;
        }
        if (!Boolean.TRUE.equals(operationalVerifiedByBgs) || !Boolean.TRUE.equals(operationalVerifiedByInvestment)
                || !Boolean.TRUE.equals(fundingVerifiedByBgs) || !Boolean.TRUE.equals(fundingVerifiedByInvestment)) {
            return SurveyAnswer.INCOMPLETE;
        }
        return SurveyAnswer.POSITIVE;
    }

    public static LoanSubStatus decide(final boolean openTranche, final boolean repaymentMet, final boolean milestoneRequired,
            final SurveyAnswer survey, final boolean pastDeadline, final LoanSubStatus current) {
        if (!openTranche) {
            return null;
        }
        final SurveyAnswer answer = survey == null ? SurveyAnswer.NONE : survey;
        if (current != null && current.isRequiresReview()) {
            if (milestoneRequired && answer == SurveyAnswer.NEGATIVE) {
                return LoanSubStatus.EXPIRED;
            }
            return LoanSubStatus.REQUIRES_REVIEW;
        }
        if (milestoneRequired && answer == SurveyAnswer.NEGATIVE && (repaymentMet || pastDeadline)) {
            return LoanSubStatus.EXPIRED;
        }
        if (answer == SurveyAnswer.POSITIVE && repaymentMet && milestoneRequired) {
            if (pastDeadline && current != LoanSubStatus.SURVEY_SUCCESSFUL && current != LoanSubStatus.EXPIRED) {
                return LoanSubStatus.REQUIRES_REVIEW;
            }
            return LoanSubStatus.SURVEY_SUCCESSFUL;
        }
        if (!repaymentMet) {
            if (milestoneRequired && pastDeadline) {
                return LoanSubStatus.REQUIRES_REVIEW;
            }
            return LoanSubStatus.REPAYMENT_NOT_MET;
        }
        if (!milestoneRequired) {
            return LoanSubStatus.REPAYMENT_MET;
        }
        if (pastDeadline) {
            return LoanSubStatus.REQUIRES_REVIEW;
        }
        return LoanSubStatus.AWAITING_SURVEY;
    }
}
