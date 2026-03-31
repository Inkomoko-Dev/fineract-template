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
package org.apache.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.util.List;

public final class LoanDecisionData {

    private Long loanId;
    private Long clientId;
    private Long loanStatus;
    private Long loanSubStatus;

    private Integer loanDecisionState;
    private Integer loanNextDecisionState;

    // Legacy fields for backward compatibility (levels 1-5)
    private BigDecimal icReviewDecisionLevelOneRecommendedAmount;
    private BigDecimal icReviewDecisionLevelTwoRecommendedAmount;
    private BigDecimal icReviewDecisionLevelThreeRecommendedAmount;
    private BigDecimal icReviewDecisionLevelFourRecommendedAmount;
    private BigDecimal icReviewDecisionLevelFiveRecommendedAmount;

    // Dynamic levels data (supports unlimited levels)
    private List<LoanDecisionLevelData> decisionLevels;

    public LoanDecisionData(Long loanId, Integer loanDecisionState, Integer loanNextDecisionState,
            BigDecimal icReviewDecisionLevelOneRecommendedAmount, BigDecimal icReviewDecisionLevelTwoRecommendedAmount,
            BigDecimal icReviewDecisionLevelThreeRecommendedAmount, BigDecimal icReviewDecisionLevelFourRecommendedAmount,
            BigDecimal icReviewDecisionLevelFiveRecommendedAmount) {
        this.loanId = loanId;
        this.loanDecisionState = loanDecisionState;
        this.loanNextDecisionState = loanNextDecisionState;
        this.icReviewDecisionLevelOneRecommendedAmount = icReviewDecisionLevelOneRecommendedAmount;
        this.icReviewDecisionLevelTwoRecommendedAmount = icReviewDecisionLevelTwoRecommendedAmount;
        this.icReviewDecisionLevelThreeRecommendedAmount = icReviewDecisionLevelThreeRecommendedAmount;
        this.icReviewDecisionLevelFourRecommendedAmount = icReviewDecisionLevelFourRecommendedAmount;
        this.icReviewDecisionLevelFiveRecommendedAmount = icReviewDecisionLevelFiveRecommendedAmount;
    }

    public Integer getLoanDecisionState() {
        return loanDecisionState;
    }

    public Integer getLoanNextDecisionState() {
        return loanNextDecisionState;
    }

    public List<LoanDecisionLevelData> getDecisionLevels() {
        return decisionLevels;
    }

    public void setDecisionLevels(List<LoanDecisionLevelData> decisionLevels) {
        this.decisionLevels = decisionLevels;
    }
}
