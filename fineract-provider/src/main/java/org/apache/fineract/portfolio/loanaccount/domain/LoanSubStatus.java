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

import org.apache.fineract.infrastructure.core.data.EnumOptionData;

public enum LoanSubStatus {

    INVALID(0, "loanSubStatusType.invalid"), //
    FORECLOSED(100, "loanSubStatusType.foreclosed"), //
    PENDINGDISBURSEMENT(200, "loanSubStatusType.pending.disbursement"),
    PENDINGDISBURSEMENTAPPROVAL(300, "loanSubStatusType.pre.disbursement"),
    REPAYMENT_NOT_MET(400, "loanSubStatusType.repayment.not.met"),
    REPAYMENT_MET(410, "loanSubStatusType.repayment.met"),
    AWAITING_SURVEY(420, "loanSubStatusType.awaiting.survey"),
    SURVEY_SUCCESSFUL(430, "loanSubStatusType.survey.successful"),
    EXPIRED(440, "loanSubStatusType.expired"),
    REQUIRES_REVIEW(450, "loanSubStatusType.requires.review"),;

    private final Integer value;
    private final String code;

    public static LoanSubStatus fromInt(final Integer statusValue) {

        LoanSubStatus enumeration = LoanSubStatus.INVALID;
        switch (statusValue) {
            case 100:
                enumeration = LoanSubStatus.FORECLOSED;
            break;
            case 200:
                enumeration = LoanSubStatus.PENDINGDISBURSEMENT;
            break;
            case 300:
                enumeration = LoanSubStatus.PENDINGDISBURSEMENTAPPROVAL;
            break;
            case 400:
                enumeration = LoanSubStatus.REPAYMENT_NOT_MET;
            break;
            case 410:
                enumeration = LoanSubStatus.REPAYMENT_MET;
            break;
            case 420:
                enumeration = LoanSubStatus.AWAITING_SURVEY;
            break;
            case 430:
                enumeration = LoanSubStatus.SURVEY_SUCCESSFUL;
            break;
            case 440:
                enumeration = LoanSubStatus.EXPIRED;
            break;
            case 450:
                enumeration = LoanSubStatus.REQUIRES_REVIEW;
            break;
        }
        return enumeration;
    }

    LoanSubStatus(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public boolean hasStateOf(final LoanSubStatus state) {
        return this.value.equals(state.getValue());
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    public boolean isPendingDisbursement() {
        return this.value.equals(LoanSubStatus.PENDINGDISBURSEMENT.getValue());
    }

    public boolean isForeclosed() {
        return this.value.equals(LoanSubStatus.FORECLOSED.getValue());
    }

    public boolean isTrancheMilestone() {
        return this == REPAYMENT_NOT_MET || this == REPAYMENT_MET || this == AWAITING_SURVEY || this == SURVEY_SUCCESSFUL
                || this == EXPIRED || this == REQUIRES_REVIEW;
    }

    public boolean isRequiresReview() {
        return this == REQUIRES_REVIEW;
    }

    public static EnumOptionData loanSubStatus(final int id) {
        return loanSubStatusEnum(LoanSubStatus.fromInt(id));
    }

    public static EnumOptionData loanSubStatusEnum(final LoanSubStatus type) {
        final String codePrefix = "loanSubStatus.";
        EnumOptionData optionData = null;
        switch (type) {
            case FORECLOSED:
                optionData = new EnumOptionData(LoanSubStatus.FORECLOSED.getValue().longValue(),
                        codePrefix + LoanSubStatus.FORECLOSED.getCode(), "Foreclosed");
            break;
            case PENDINGDISBURSEMENT:
                optionData = new EnumOptionData(LoanSubStatus.PENDINGDISBURSEMENT.getValue().longValue(),
                        codePrefix + LoanSubStatus.PENDINGDISBURSEMENT.getCode(), "Pending Disbursement");
            break;
            case PENDINGDISBURSEMENTAPPROVAL:
                optionData = new EnumOptionData(LoanSubStatus.PENDINGDISBURSEMENTAPPROVAL.getValue().longValue(),
                        codePrefix + LoanSubStatus.PENDINGDISBURSEMENTAPPROVAL.getCode(), "Pending Disbursement Approval");
            break;
            case REPAYMENT_NOT_MET:
                optionData = new EnumOptionData(LoanSubStatus.REPAYMENT_NOT_MET.getValue().longValue(),
                        codePrefix + LoanSubStatus.REPAYMENT_NOT_MET.getCode(), "Repayment conditions not met");
            break;
            case REPAYMENT_MET:
                optionData = new EnumOptionData(LoanSubStatus.REPAYMENT_MET.getValue().longValue(),
                        codePrefix + LoanSubStatus.REPAYMENT_MET.getCode(), "Repayment conditions met");
            break;
            case AWAITING_SURVEY:
                optionData = new EnumOptionData(LoanSubStatus.AWAITING_SURVEY.getValue().longValue(),
                        codePrefix + LoanSubStatus.AWAITING_SURVEY.getCode(), "Awaiting survey");
            break;
            case SURVEY_SUCCESSFUL:
                optionData = new EnumOptionData(LoanSubStatus.SURVEY_SUCCESSFUL.getValue().longValue(),
                        codePrefix + LoanSubStatus.SURVEY_SUCCESSFUL.getCode(), "Survey successful");
            break;
            case EXPIRED:
                optionData = new EnumOptionData(LoanSubStatus.EXPIRED.getValue().longValue(),
                        codePrefix + LoanSubStatus.EXPIRED.getCode(), "Expired");
            break;
            case REQUIRES_REVIEW:
                optionData = new EnumOptionData(LoanSubStatus.REQUIRES_REVIEW.getValue().longValue(),
                        codePrefix + LoanSubStatus.REQUIRES_REVIEW.getCode(), "Requires review");
            break;
            default:
                optionData = new EnumOptionData(LoanSubStatus.INVALID.getValue().longValue(), LoanSubStatus.INVALID.getCode(), "Invalid");
            break;
        }
        return optionData;
    }
}
