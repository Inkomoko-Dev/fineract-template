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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_loan_classification_country_config", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "country_cv_id" }, name = "uk_loan_classification_country") })
public class LoanClassificationCountryConfig extends AbstractPersistableCustom {

    @Column(name = "country_cv_id", nullable = false)
    private Long countryCvId;

    @OneToMany(mappedBy = "countryConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("minDaysInArrears ASC")
    private List<LoanClassificationThreshold> thresholds = new ArrayList<>();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;

    protected LoanClassificationCountryConfig() {}

    public static LoanClassificationCountryConfig create(final Long countryCvId, final Long createdBy, final OffsetDateTime createdOnUtc) {
        final LoanClassificationCountryConfig config = new LoanClassificationCountryConfig();
        config.countryCvId = countryCvId;
        config.createdBy = createdBy;
        config.createdOnUtc = createdOnUtc;
        return config;
    }

    public void replaceThresholds(final List<LoanClassificationThreshold> newThresholds, final Long modifiedBy,
            final OffsetDateTime modifiedOnUtc) {
        this.thresholds.clear();
        for (final LoanClassificationThreshold threshold : newThresholds) {
            threshold.setCountryConfig(this);
            this.thresholds.add(threshold);
        }
        this.lastModifiedBy = modifiedBy;
        this.lastModifiedOnUtc = modifiedOnUtc;
    }

    public Long getCountryCvId() {
        return this.countryCvId;
    }

    public List<LoanClassificationThreshold> getThresholds() {
        return this.thresholds;
    }
}
