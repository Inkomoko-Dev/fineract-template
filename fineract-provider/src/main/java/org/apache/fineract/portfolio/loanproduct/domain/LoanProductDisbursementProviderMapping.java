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
package org.apache.fineract.portfolio.loanproduct.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_loan_product_disbursement_provider_mapping")
public class LoanProductDisbursementProviderMapping extends AbstractPersistableCustom {

    @OneToOne(optional = false)
    @JoinColumn(name = "loan_product_id", nullable = false, unique = true)
    private LoanProduct loanProduct;

    @Column(name = "disbursement_provider_code", length = 50, nullable = false)
    private String disbursementProviderCode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected LoanProductDisbursementProviderMapping() {}

    public LoanProductDisbursementProviderMapping(final LoanProduct loanProduct, final String disbursementProviderCode,
            final boolean active) {
        this.loanProduct = loanProduct;
        this.disbursementProviderCode = disbursementProviderCode;
        this.active = active;
    }

    public LoanProduct getLoanProduct() {
        return this.loanProduct;
    }

    public void setLoanProduct(final LoanProduct loanProduct) {
        this.loanProduct = loanProduct;
    }

    public String getDisbursementProviderCode() {
        return this.disbursementProviderCode;
    }

    public void setDisbursementProviderCode(final String disbursementProviderCode) {
        this.disbursementProviderCode = disbursementProviderCode;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }
}
