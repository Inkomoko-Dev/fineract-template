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
 * Enum representation of loan decision states.
 */
public enum LoanDecisionState {

    INVALID(0, "loanStatusType.invalid"),
    REVIEW_APPLICATION(1000, "loanDecisionStateType.submitted.and.review.Pending"),
    DUE_DILIGENCE(1200, "loanDecisionStateType.submitted.and.due.diligence.Pending"),
    COLLATERAL_REVIEW(1300, "loanDecisionStateType.submitted.and.collateral.review.Pending"),
    IC_REVIEW_LEVEL_ONE(1400, "loanDecisionStateType.submitted.and.ic.level.one.Pending"),
    IC_REVIEW_LEVEL_TWO(1500,"loanDecisionStateType.submitted.and.ic.level.two.Pending"),
    IC_REVIEW_LEVEL_THREE(1600, "loanDecisionStateType.submitted.and.ic.level.three.Pending"),
    IC_REVIEW_LEVEL_FOUR(1700, "loanDecisionStateType.submitted.and.ic.four.Pending"),
    IC_REVIEW_LEVEL_FIVE(1800, "loanDecisionStateType.submitted.and.ic.five.Pending"),
    PREPARE_AND_SIGN_CONTRACT(1900, "loanDecisionStateType.submitted.and.prepare.sign.contract.Pending"),;

    private final Integer value;
    private final String code;

    public static LoanDecisionState fromInt(final Integer statusValue) {

        LoanDecisionState enumeration = LoanDecisionState.INVALID;
        switch (statusValue) {
            case 1000:
                enumeration = LoanDecisionState.REVIEW_APPLICATION;
                break;
            case 1200:
                enumeration = LoanDecisionState.DUE_DILIGENCE;
                break;
            case 1300:
                enumeration = LoanDecisionState.COLLATERAL_REVIEW;
                break;
            case 1400:
                enumeration = LoanDecisionState.IC_REVIEW_LEVEL_ONE;
                break;
            case 1500:
                enumeration = LoanDecisionState.IC_REVIEW_LEVEL_TWO;
                break;
            case 1600:
                enumeration = LoanDecisionState.IC_REVIEW_LEVEL_THREE;
                break;
            case 1700:
                enumeration = LoanDecisionState.IC_REVIEW_LEVEL_FOUR;
                break;
            case 1800:
                enumeration = LoanDecisionState.IC_REVIEW_LEVEL_FIVE;
                break;
            case 1900:
                enumeration = LoanDecisionState.PREPARE_AND_SIGN_CONTRACT;
                break;
        }
        return enumeration;
    }

    LoanDecisionState(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public boolean hasStateOf(final LoanDecisionState state) {
        return this.value.equals(state.getValue());
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    public boolean isReviewApplication() {
        return this.value.equals(LoanDecisionState.REVIEW_APPLICATION.getValue());
    }

    public boolean isDueDiligence() {
        return this.value.equals(LoanDecisionState.DUE_DILIGENCE.getValue());
    }

    public boolean isCollateralReview() {
        return this.value.equals(LoanDecisionState.COLLATERAL_REVIEW.getValue());
    }

    public boolean isIcReviewLevelOne() {
        return this.value.equals(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
    }

    public boolean isIcReviewLevelTwo() {
        return this.value.equals(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue());
    }

    public boolean isIcReviewLevelThree() {
        return this.value.equals(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());
    }

    public boolean isIcReviewLevelFour() {
        return this.value.equals(LoanDecisionState.IC_REVIEW_LEVEL_FOUR.getValue());
    }

    public boolean isIcReviewLevelFive() {
        return this.value.equals(LoanDecisionState.IC_REVIEW_LEVEL_FIVE.getValue());
    }

    public boolean isPrepareAndSignContract() {
        return this.value.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
    }
}
