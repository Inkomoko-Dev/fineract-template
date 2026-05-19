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
package org.apache.fineract.accounting.provisioning.domain;

import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

@Entity
@Table(name = "m_loanproduct_provisioning_entry_loans")
public class LoanProductProvisioningEntryLoan {

    @EmbeddedId
    private LoanProductProvisioningEntryLoanId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("loanProductProvisionEntryId")
    @JoinColumn(name = "loanproduct_provision_entry_id", nullable = false)
    private LoanProductProvisioningEntry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("loanId")
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "account_no")
    private String accountNo;

    @Column(name = "outstanding_balance")
    private BigDecimal outstandingBalance;

    @Column(name = "provisioning_amount")
    private BigDecimal provisioningAmount;

    protected LoanProductProvisioningEntryLoan() {}

    private LoanProductProvisioningEntryLoan(LoanProductProvisioningEntry entry, Loan loan, String accountNo,
            BigDecimal outstandingBalance, BigDecimal provisioningAmount) {
        this.entry = entry;
        this.loan = loan;
        this.id = new LoanProductProvisioningEntryLoanId(entry.getId(), loan.getId());
        this.accountNo = accountNo;
        this.outstandingBalance = outstandingBalance;
        this.provisioningAmount = provisioningAmount;
    }

    public static LoanProductProvisioningEntryLoan of(LoanProductProvisioningEntry entry, Loan loan, String accountNo,
            BigDecimal outstandingBalance, BigDecimal provisioningAmount) {
        return new LoanProductProvisioningEntryLoan(entry, loan, accountNo, outstandingBalance, provisioningAmount);
    }
}
