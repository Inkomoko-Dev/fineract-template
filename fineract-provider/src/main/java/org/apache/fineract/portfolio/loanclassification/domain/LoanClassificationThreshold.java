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
package org.apache.fineract.portfolio.loanclassification.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationArrearsBand;

@Entity
@Table(name = "m_loan_classification_threshold")
public class LoanClassificationThreshold extends AbstractPersistableCustom {

    @ManyToOne(optional = false)
    @JoinColumn(name = "country_config_id", nullable = false)
    private LoanClassificationCountryConfig countryConfig;

    @Column(name = "classification_code", nullable = false)
    private Integer classificationCode;

    @Column(name = "min_days_in_arrears", nullable = false)
    private Integer minDaysInArrears;

    @Column(name = "max_days_in_arrears")
    private Integer maxDaysInArrears;

    protected LoanClassificationThreshold() {}

    public static LoanClassificationThreshold create(final int classificationCode, final int minDaysInArrears,
            final Integer maxDaysInArrears) {
        final LoanClassificationThreshold threshold = new LoanClassificationThreshold();
        threshold.classificationCode = classificationCode;
        threshold.minDaysInArrears = minDaysInArrears;
        threshold.maxDaysInArrears = maxDaysInArrears;
        return threshold;
    }

    void setCountryConfig(final LoanClassificationCountryConfig countryConfig) {
        this.countryConfig = countryConfig;
    }

    public Integer getClassificationCode() {
        return this.classificationCode;
    }

    public Integer getMinDaysInArrears() {
        return this.minDaysInArrears;
    }

    public Integer getMaxDaysInArrears() {
        return this.maxDaysInArrears;
    }

    public LoanClassificationArrearsBand toBand() {
        return new LoanClassificationArrearsBand(this.classificationCode, this.minDaysInArrears, this.maxDaysInArrears);
    }
}
