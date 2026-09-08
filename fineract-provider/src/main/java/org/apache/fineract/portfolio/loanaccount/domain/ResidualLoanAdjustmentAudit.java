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
import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_loan_residual_adjustment")
public class ResidualLoanAdjustmentAudit extends AbstractPersistableCustom {

    @ManyToOne
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne
    @JoinColumn(name = "loan_transaction_id", nullable = false)
    private LoanTransaction loanTransaction;

    @Column(name = "signed_residual_before", nullable = false, scale = 6, precision = 19)
    private BigDecimal signedResidualBefore;

    @Column(name = "threshold_used", nullable = false, scale = 6, precision = 19)
    private BigDecimal thresholdUsed;

    @Column(name = "automatic", nullable = false)
    private boolean automatic;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on", nullable = false)
    private OffsetDateTime createdOn;

    protected ResidualLoanAdjustmentAudit() {}

    public static ResidualLoanAdjustmentAudit automatic(final Loan loan, final LoanTransaction loanTransaction,
            final BigDecimal signedResidualBefore, final BigDecimal thresholdUsed, final OffsetDateTime createdOn) {
        final ResidualLoanAdjustmentAudit audit = new ResidualLoanAdjustmentAudit();
        audit.loan = loan;
        audit.loanTransaction = loanTransaction;
        audit.signedResidualBefore = signedResidualBefore;
        audit.thresholdUsed = thresholdUsed;
        audit.automatic = true;
        audit.createdBy = loanTransaction.getCreatedBy().orElse(null);
        audit.createdOn = createdOn;
        return audit;
    }
}
