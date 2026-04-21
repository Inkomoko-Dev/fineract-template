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
import java.util.HashSet;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.CascadeType;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaDefinition;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaVersion;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCategory;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;

@Entity
@Table(name = "m_loanproduct_provisioning_entry")
public class LoanProductProvisioningEntry extends AbstractPersistableCustom {

    @ManyToOne(optional = false)
    @JoinColumn(name = "history_id", referencedColumnName = "id", nullable = false)
    private ProvisioningEntry entry;

    @Column(name = "criteria_id", nullable = false)
    private Long criteriaId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "criteria_version_id", referencedColumnName = "id", nullable = false)
    private ProvisioningCriteriaVersion criteriaVersion;

    @ManyToOne
    @JoinColumn(name = "criteria_definition_id")
    private ProvisioningCriteriaDefinition criteriaDefinition;

    @ManyToOne
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private LoanProduct loanProduct;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private ProvisioningCategory provisioningCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_type", nullable = false)
    private ProvisioningClassificationType classificationType;

    @Column(name = "overdue_in_days", nullable = false)
    private Long overdueInDays;

    @Column(name = "reseve_amount", nullable = false)
    private BigDecimal reservedAmount;

    @ManyToOne
    @JoinColumn(name = "liability_account")
    private GLAccount liabilityAccount;

    @ManyToOne
    @JoinColumn(name = "expense_account")
    private GLAccount expenseAccount;

    @OneToMany(mappedBy = "entry", orphanRemoval = true, cascade = CascadeType.ALL)
    private Set<LoanProductProvisioningEntryLoan> loanSnapshots;

    protected LoanProductProvisioningEntry() {}

    public LoanProductProvisioningEntry(final LoanProduct loanProduct, final Office office, final String currencyCode,
            final ProvisioningCategory provisioningCategory, final ProvisioningCriteriaVersion criteriaVersion,
            final ProvisioningCriteriaDefinition criteriaDefinition, final ProvisioningClassificationType classificationType,
            final Long overdueInDays, final BigDecimal reservedAmount, final GLAccount liabilityAccount, final GLAccount expenseAccount,
            final Long criteriaId) {
        this.loanProduct = loanProduct;
        this.office = office;
        this.currencyCode = currencyCode;
        this.provisioningCategory = provisioningCategory;
        this.criteriaVersion = criteriaVersion;
        this.criteriaDefinition = criteriaDefinition;
        this.classificationType = classificationType;
        this.overdueInDays = overdueInDays;
        this.reservedAmount = reservedAmount;
        this.liabilityAccount = liabilityAccount;
        this.expenseAccount = expenseAccount;
        this.criteriaId = criteriaId;
        this.loanSnapshots = new HashSet<>();

    }

    public void setProvisioningEntry(ProvisioningEntry provisioningEntry) {
        this.entry = provisioningEntry;
    }

    public BigDecimal getReservedAmount() {
        return this.reservedAmount;
    }

    public void addReservedAmount(BigDecimal value) {
        this.reservedAmount = this.reservedAmount.add(value);
    }

    public void addLoan(Loan loan, String accountNo, BigDecimal outstandingBalance, BigDecimal provisioningAmount) {
        this.loanSnapshots.add(LoanProductProvisioningEntryLoan.of(this, loan, accountNo, outstandingBalance, provisioningAmount));
    }

    public Office getOffice() {
        return this.office;
    }

    public GLAccount getLiabilityAccount() {
        return this.liabilityAccount;
    }

    public String getCurrencyCode() {
        return this.currencyCode;
    }

    public GLAccount getExpenseAccount() {
        return this.expenseAccount;
    }

    public Long getCriteriaId() {
        return this.criteriaId;
    }

    public ProvisioningCategory getProvisioningCategory() {
        return this.provisioningCategory;
    }

    public ProvisioningClassificationType getClassificationType() {
        return this.classificationType;
    }

    public LoanProduct getLoanProduct() {
        return this.loanProduct;
    }

    public Long getOverdueInDays() {
        return this.overdueInDays;
    }

    public ProvisioningCriteriaVersion getCriteriaVersion() {
        return this.criteriaVersion;
    }

    public ProvisioningCriteriaDefinition getCriteriaDefinition() {
        return this.criteriaDefinition;
    }

    public Set<LoanProductProvisioningEntryLoan> getLoanSnapshots() {
        return this.loanSnapshots;
    }
}
