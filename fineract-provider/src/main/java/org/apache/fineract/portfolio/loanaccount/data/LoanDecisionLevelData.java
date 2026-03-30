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
import java.time.LocalDate;

/**
 * DTO representing decision data for a specific IC review level.
 * This supports dynamic IC review levels (1, 2, 3, 4, 5, 6, 7, ...).
 */
public final class LoanDecisionLevelData {

    private final Long id;
    private final Long loanDecisionId;
    private final Integer levelNumber;
    private final String levelName;
    private final String levelCode;
    private final BigDecimal recommendedAmount;
    private final String note;
    private final Long decisionBy;
    private final String decisionByName;
    private final LocalDate decisionOn;
    private final String decision; // "APPROVED", "REJECTED", "PENDING"

    public LoanDecisionLevelData(Long id, Long loanDecisionId, Integer levelNumber, String levelName,
            String levelCode, BigDecimal recommendedAmount, String note, Long decisionBy,
            String decisionByName, LocalDate decisionOn, String decision) {
        this.id = id;
        this.loanDecisionId = loanDecisionId;
        this.levelNumber = levelNumber;
        this.levelName = levelName;
        this.levelCode = levelCode;
        this.recommendedAmount = recommendedAmount;
        this.note = note;
        this.decisionBy = decisionBy;
        this.decisionByName = decisionByName;
        this.decisionOn = decisionOn;
        this.decision = decision;
    }

    public Long getId() {
        return id;
    }

    public Long getLoanDecisionId() {
        return loanDecisionId;
    }

    public Integer getLevelNumber() {
        return levelNumber;
    }

    public String getLevelName() {
        return levelName;
    }

    public String getLevelCode() {
        return levelCode;
    }

    public BigDecimal getRecommendedAmount() {
        return recommendedAmount;
    }

    public String getNote() {
        return note;
    }

    public Long getDecisionBy() {
        return decisionBy;
    }

    public String getDecisionByName() {
        return decisionByName;
    }

    public LocalDate getDecisionOn() {
        return decisionOn;
    }

    public String getDecision() {
        return decision;
    }
}
