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
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.charge.domain.Charge;

@Entity
@Table(name = "m_loan_daily_late_fee", uniqueConstraints = {
        @UniqueConstraint(name = "uk_m_loan_daily_late_fee", columnNames = { "loan_id", "charge_id", "penalty_date" }) })
public class LoanDailyLateFee extends AbstractPersistableCustom {

    @ManyToOne(optional = false)
    @JoinColumn(name = "loan_id", referencedColumnName = "id", nullable = false)
    private Loan loan;

    @ManyToOne(optional = false)
    @JoinColumn(name = "charge_id", referencedColumnName = "id", nullable = false)
    private Charge charge;

    @OneToOne(optional = true)
    @JoinColumn(name = "loan_charge_id", referencedColumnName = "id")
    private LoanCharge loanCharge;

    @Column(name = "penalty_date", nullable = false)
    private LocalDate penaltyDate;

    @Column(name = "overdue_principal_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal overduePrincipalAmount;

    @Column(name = "monthly_rate", precision = 19, scale = 6, nullable = false)
    private BigDecimal monthlyRate;

    @Column(name = "daily_rate", precision = 19, scale = 12, nullable = false)
    private BigDecimal dailyRate;

    @Column(name = "penalty_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal penaltyAmount;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected LoanDailyLateFee() {
        //
    }

    public LoanDailyLateFee(final Loan loan, final Charge charge, final LoanCharge loanCharge, final LocalDate penaltyDate,
            final BigDecimal overduePrincipalAmount, final BigDecimal monthlyRate, final BigDecimal dailyRate,
            final BigDecimal penaltyAmount) {
        this.loan = loan;
        this.charge = charge;
        this.loanCharge = loanCharge;
        this.penaltyDate = penaltyDate;
        this.overduePrincipalAmount = overduePrincipalAmount;
        this.monthlyRate = monthlyRate;
        this.dailyRate = dailyRate;
        this.penaltyAmount = penaltyAmount;
        this.active = true;
    }

    public void reactivate(final LoanCharge loanCharge, final BigDecimal overduePrincipalAmount, final BigDecimal monthlyRate,
            final BigDecimal dailyRate, final BigDecimal penaltyAmount) {
        this.loanCharge = loanCharge;
        this.overduePrincipalAmount = overduePrincipalAmount;
        this.monthlyRate = monthlyRate;
        this.dailyRate = dailyRate;
        this.penaltyAmount = penaltyAmount;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public LocalDate getPenaltyDate() {
        return this.penaltyDate;
    }

    public LoanCharge getLoanCharge() {
        return this.loanCharge;
    }

    public BigDecimal getPenaltyAmount() {
        return this.penaltyAmount;
    }

    public boolean isActive() {
        return this.active;
    }
}
