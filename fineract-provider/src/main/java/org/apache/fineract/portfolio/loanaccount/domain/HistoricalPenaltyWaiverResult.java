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
 * Outcome of a historical penalty waiver: the waiver transaction, plus the repayments the replay reversed and replaced.
 * {@code Loan#waiveLoanCharge} discards the latter, leaving the caller unable to persist them or post their journal
 * entries.
 */
public class HistoricalPenaltyWaiverResult {

    private final LoanTransaction waiveTransaction;
    private final ChangedTransactionDetail changedTransactionDetail;

    public HistoricalPenaltyWaiverResult(final LoanTransaction waiveTransaction,
            final ChangedTransactionDetail changedTransactionDetail) {
        this.waiveTransaction = waiveTransaction;
        this.changedTransactionDetail = changedTransactionDetail;
    }

    public LoanTransaction getWaiveTransaction() {
        return this.waiveTransaction;
    }

    public ChangedTransactionDetail getChangedTransactionDetail() {
        return this.changedTransactionDetail;
    }
}
