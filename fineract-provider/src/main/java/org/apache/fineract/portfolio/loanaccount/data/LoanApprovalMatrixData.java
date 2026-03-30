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
import java.util.Collection;
import java.util.List;
import lombok.Data;
import org.apache.fineract.organisation.monetary.data.CurrencyData;

@Data
public final class LoanApprovalMatrixData {

    private Long id;

    private String currency;
    private BigDecimal levelOneUnsecuredFirstCycleMaxAmount;
    private Integer levelOneUnsecuredFirstCycleMinTerm;
    private Integer levelOneUnsecuredFirstCycleMaxTerm;
    private BigDecimal levelOneUnsecuredSecondCycleMaxAmount;
    private Integer levelOneUnsecuredSecondCycleMinTerm;
    private Integer levelOneUnsecuredSecondCycleMaxTerm;
    private BigDecimal levelOneSecuredFirstCycleMaxAmount;
    private Integer levelOneSecuredFirstCycleMinTerm;
    private Integer levelOneSecuredFirstCycleMaxTerm;
    private BigDecimal levelOneSecuredSecondCycleMaxAmount;
    private Integer levelOneSecuredSecondCycleMinTerm;
    private Integer levelOneSecuredSecondCycleMaxTerm;

    private BigDecimal levelTwoUnsecuredFirstCycleMaxAmount;
    private Integer levelTwoUnsecuredFirstCycleMinTerm;
    private Integer levelTwoUnsecuredFirstCycleMaxTerm;
    private BigDecimal levelTwoUnsecuredSecondCycleMaxAmount;
    private Integer levelTwoUnsecuredSecondCycleMinTerm;
    private Integer levelTwoUnsecuredSecondCycleMaxTerm;
    private BigDecimal levelTwoSecuredFirstCycleMaxAmount;
    private Integer levelTwoSecuredFirstCycleMinTerm;
    private Integer levelTwoSecuredFirstCycleMaxTerm;
    private BigDecimal levelTwoSecuredSecondCycleMaxAmount;
    private Integer levelTwoSecuredSecondCycleMinTerm;
    private Integer levelTwoSecuredSecondCycleMaxTerm;

    private BigDecimal levelThreeUnsecuredFirstCycleMaxAmount;
    private Integer levelThreeUnsecuredFirstCycleMinTerm;
    private Integer levelThreeUnsecuredFirstCycleMaxTerm;
    private BigDecimal levelThreeUnsecuredSecondCycleMaxAmount;
    private Integer levelThreeUnsecuredSecondCycleMinTerm;
    private Integer levelThreeUnsecuredSecondCycleMaxTerm;
    private BigDecimal levelThreeSecuredFirstCycleMaxAmount;
    private Integer levelThreeSecuredFirstCycleMinTerm;
    private Integer levelThreeSecuredFirstCycleMaxTerm;
    private BigDecimal levelThreeSecuredSecondCycleMaxAmount;
    private Integer levelThreeSecuredSecondCycleMinTerm;
    private Integer levelThreeSecuredSecondCycleMaxTerm;

    private BigDecimal levelFourUnsecuredFirstCycleMaxAmount;
    private Integer levelFourUnsecuredFirstCycleMinTerm;
    private Integer levelFourUnsecuredFirstCycleMaxTerm;
    private BigDecimal levelFourUnsecuredSecondCycleMaxAmount;
    private Integer levelFourUnsecuredSecondCycleMinTerm;
    private Integer levelFourUnsecuredSecondCycleMaxTerm;
    private BigDecimal levelFourSecuredFirstCycleMaxAmount;
    private Integer levelFourSecuredFirstCycleMinTerm;
    private Integer levelFourSecuredFirstCycleMaxTerm;
    private BigDecimal levelFourSecuredSecondCycleMaxAmount;
    private Integer levelFourSecuredSecondCycleMinTerm;
    private Integer levelFourSecuredSecondCycleMaxTerm;

    private BigDecimal levelFiveUnsecuredFirstCycleMaxAmount;
    private Integer levelFiveUnsecuredFirstCycleMinTerm;
    private Integer levelFiveUnsecuredFirstCycleMaxTerm;
    private BigDecimal levelFiveUnsecuredSecondCycleMaxAmount;
    private Integer levelFiveUnsecuredSecondCycleMinTerm;

    private Integer levelFiveUnsecuredSecondCycleMaxTerm;

    private BigDecimal levelFiveSecuredFirstCycleMaxAmount;

    private Integer levelFiveSecuredFirstCycleMinTerm;

    private Integer levelFiveSecuredFirstCycleMaxTerm;
    private BigDecimal levelFiveSecuredSecondCycleMaxAmount;
    private Integer levelFiveSecuredSecondCycleMinTerm;
    private Integer levelFiveSecuredSecondCycleMaxTerm;
    Collection<CurrencyData> currencyOptions;
    private CurrencyData currencyData;
    private Boolean isExtendLoanLifeCycleConfig;

    /**
     * Dynamic IC Review Levels - list of approval matrix levels from m_loan_approval_matrix_level table.
     * This supports unlimited IC review levels beyond the hardcoded 5 levels above.
     * The hardcoded fields above are kept for backward compatibility.
     */
    private List<LoanApprovalMatrixLevelData> dynamicLevels;

    /**
     * Active IC Review Levels - list of active IC review level configurations from m_ic_review_level_config table.
     * This provides the frontend with the list of available IC review levels for configuration.
     */
    private List<ActiveIcReviewLevelData> activeIcReviewLevels;
}
