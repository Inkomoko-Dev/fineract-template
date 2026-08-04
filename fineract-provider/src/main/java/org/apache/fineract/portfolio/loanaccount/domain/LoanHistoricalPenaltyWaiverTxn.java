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

import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * One transaction touched by a historical penalty waiver, with its allocation before and after. Recording these as rows
 * rather than as text is what lets the Odoo completion sweep be a join.
 */
@Entity
@Getter
@Table(name = "m_loan_historical_penalty_waiver_txn")
public class LoanHistoricalPenaltyWaiverTxn extends AbstractPersistableCustom {

    public static final String ROLE_WAIVE = "WAIVE";
    public static final String ROLE_REVERSED = "REVERSED";
    public static final String ROLE_REPLACEMENT = "REPLACEMENT";

    @Column(name = "waiver_id", nullable = false)
    private Long waiverId;

    @Column(name = "loan_transaction_id", nullable = false)
    private Long loanTransactionId;

    @Column(name = "txn_role", length = 20, nullable = false)
    private String txnRole;

    @Column(name = "principal_before", scale = 6, precision = 19)
    private BigDecimal principalBefore;

    @Column(name = "interest_before", scale = 6, precision = 19)
    private BigDecimal interestBefore;

    @Column(name = "fees_before", scale = 6, precision = 19)
    private BigDecimal feesBefore;

    @Column(name = "penalties_before", scale = 6, precision = 19)
    private BigDecimal penaltiesBefore;

    @Column(name = "principal_after", scale = 6, precision = 19)
    private BigDecimal principalAfter;

    @Column(name = "interest_after", scale = 6, precision = 19)
    private BigDecimal interestAfter;

    @Column(name = "fees_after", scale = 6, precision = 19)
    private BigDecimal feesAfter;

    @Column(name = "penalties_after", scale = 6, precision = 19)
    private BigDecimal penaltiesAfter;

    protected LoanHistoricalPenaltyWaiverTxn() {
        // for JPA
    }

    public static LoanHistoricalPenaltyWaiverTxn create(final Long waiverId, final Long loanTransactionId, final String txnRole,
            final BigDecimal principalBefore, final BigDecimal interestBefore, final BigDecimal feesBefore,
            final BigDecimal penaltiesBefore, final BigDecimal principalAfter, final BigDecimal interestAfter, final BigDecimal feesAfter,
            final BigDecimal penaltiesAfter) {

        final LoanHistoricalPenaltyWaiverTxn row = new LoanHistoricalPenaltyWaiverTxn();
        row.waiverId = waiverId;
        row.loanTransactionId = loanTransactionId;
        row.txnRole = txnRole;
        row.principalBefore = principalBefore;
        row.interestBefore = interestBefore;
        row.feesBefore = feesBefore;
        row.penaltiesBefore = penaltiesBefore;
        row.principalAfter = principalAfter;
        row.interestAfter = interestAfter;
        row.feesAfter = feesAfter;
        row.penaltiesAfter = penaltiesAfter;
        return row;
    }

}
