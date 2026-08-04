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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

/** What a historical penalty waiver would do to one transaction. */
@Getter
public class HistoricalPenaltyWaiverTransactionImpactData implements Serializable {

    public static final String UNCHANGED = "UNCHANGED";
    public static final String REVERSED_AND_REPLACED = "REVERSED_AND_REPLACED";
    public static final String NEW = "NEW";

    private final Long transactionId;
    private final LocalDate transactionDate;
    private final String transactionType;
    private final String changeType;

    private final BigDecimal amountBefore;
    private final BigDecimal principalBefore;
    private final BigDecimal interestBefore;
    private final BigDecimal feesBefore;
    private final BigDecimal penaltiesBefore;

    private final BigDecimal amountAfter;
    private final BigDecimal principalAfter;
    private final BigDecimal interestAfter;
    private final BigDecimal feesAfter;
    private final BigDecimal penaltiesAfter;

    public HistoricalPenaltyWaiverTransactionImpactData(final Long transactionId, final LocalDate transactionDate,
            final String transactionType, final String changeType, final BigDecimal amountBefore, final BigDecimal principalBefore,
            final BigDecimal interestBefore, final BigDecimal feesBefore, final BigDecimal penaltiesBefore, final BigDecimal amountAfter,
            final BigDecimal principalAfter, final BigDecimal interestAfter, final BigDecimal feesAfter, final BigDecimal penaltiesAfter) {

        this.transactionId = transactionId;
        this.transactionDate = transactionDate;
        this.transactionType = transactionType;
        this.changeType = changeType;
        this.amountBefore = amountBefore;
        this.principalBefore = principalBefore;
        this.interestBefore = interestBefore;
        this.feesBefore = feesBefore;
        this.penaltiesBefore = penaltiesBefore;
        this.amountAfter = amountAfter;
        this.principalAfter = principalAfter;
        this.interestAfter = interestAfter;
        this.feesAfter = feesAfter;
        this.penaltiesAfter = penaltiesAfter;
    }
}
