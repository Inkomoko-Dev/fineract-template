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
import lombok.Getter;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverTxn;

/** One transaction a historical penalty waiver touched, with its allocation before and after. */
@Getter
public class HistoricalPenaltyWaiverTxnData implements Serializable {

    private final Long loanTransactionId;
    private final String txnRole;
    private final BigDecimal principalBefore;
    private final BigDecimal interestBefore;
    private final BigDecimal feesBefore;
    private final BigDecimal penaltiesBefore;
    private final BigDecimal principalAfter;
    private final BigDecimal interestAfter;
    private final BigDecimal feesAfter;
    private final BigDecimal penaltiesAfter;

    private HistoricalPenaltyWaiverTxnData(final LoanHistoricalPenaltyWaiverTxn row) {
        this.loanTransactionId = row.getLoanTransactionId();
        this.txnRole = row.getTxnRole();
        this.principalBefore = row.getPrincipalBefore();
        this.interestBefore = row.getInterestBefore();
        this.feesBefore = row.getFeesBefore();
        this.penaltiesBefore = row.getPenaltiesBefore();
        this.principalAfter = row.getPrincipalAfter();
        this.interestAfter = row.getInterestAfter();
        this.feesAfter = row.getFeesAfter();
        this.penaltiesAfter = row.getPenaltiesAfter();
    }

    public static HistoricalPenaltyWaiverTxnData from(final LoanHistoricalPenaltyWaiverTxn row) {
        return new HistoricalPenaltyWaiverTxnData(row);
    }
}
