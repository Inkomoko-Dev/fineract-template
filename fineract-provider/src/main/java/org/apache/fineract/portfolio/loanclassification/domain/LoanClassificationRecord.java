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
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationOutcome;

@Entity
@Table(name = "m_loan_classification", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "loan_id" }, name = "uk_loan_classification_loan") })
public class LoanClassificationRecord extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "classification_code")
    private Integer classificationCode;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "days_in_arrears")
    private Integer daysInArrears;

    @Column(name = "country_cv_id")
    private Long countryCvId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "excluded_from_downstream", nullable = false)
    private boolean excludedFromDownstream;

    @Column(name = "override_active", nullable = false)
    private boolean overrideActive;

    @Column(name = "override_reason", length = 500)
    private String overrideReason;

    @Column(name = "override_by")
    private Long overrideBy;

    @Column(name = "override_on_utc")
    private OffsetDateTime overrideOnUtc;

    @Column(name = "classified_on_utc", nullable = false)
    private OffsetDateTime classifiedOnUtc;

    @Column(name = "classified_by")
    private Long classifiedBy;

    protected LoanClassificationRecord() {}

    public static LoanClassificationRecord create(final Long loanId) {
        final LoanClassificationRecord record = new LoanClassificationRecord();
        record.loanId = loanId;
        record.status = LoanClassificationOutcome.STATUS_FLAGGED;
        record.source = LoanClassificationOutcome.SOURCE_AUTO;
        record.classifiedOnUtc = DateUtils.getOffsetDateTimeOfTenant();
        return record;
    }

    public void apply(final LoanClassificationOutcome outcome, final Integer daysInArrears, final Long countryCvId, final Long classifiedBy,
            final OffsetDateTime classifiedOnUtc) {
        this.classificationCode = outcome.getClassification();
        this.status = outcome.getStatus();
        this.source = outcome.getSource();
        this.daysInArrears = daysInArrears;
        this.countryCvId = countryCvId;
        this.errorMessage = outcome.getErrorMessage();
        this.excludedFromDownstream = outcome.isExcludedFromDownstream();
        this.classifiedBy = classifiedBy;
        this.classifiedOnUtc = classifiedOnUtc;
        this.overrideActive = false;
        this.overrideReason = null;
        this.overrideBy = null;
        this.overrideOnUtc = null;
    }

    public void applyManualOverride(final int classification, final String reason, final Long overriddenBy,
            final OffsetDateTime overriddenOnUtc) {
        this.classificationCode = classification;
        this.status = LoanClassificationOutcome.STATUS_VALID;
        this.source = LoanClassificationOutcome.SOURCE_MANUAL;
        this.errorMessage = null;
        this.excludedFromDownstream = false;
        this.overrideActive = true;
        this.overrideReason = reason;
        this.overrideBy = overriddenBy;
        this.overrideOnUtc = overriddenOnUtc;
        this.classifiedBy = overriddenBy;
        this.classifiedOnUtc = overriddenOnUtc;
    }

    public Long getLoanId() {
        return this.loanId;
    }

    public Integer getClassificationCode() {
        return this.classificationCode;
    }

    public String getStatus() {
        return this.status;
    }

    public String getSource() {
        return this.source;
    }

    public Integer getDaysInArrears() {
        return this.daysInArrears;
    }

    public Long getCountryCvId() {
        return this.countryCvId;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public boolean isExcludedFromDownstream() {
        return this.excludedFromDownstream;
    }

    public boolean isOverrideActive() {
        return this.overrideActive;
    }

    public String getOverrideReason() {
        return this.overrideReason;
    }

    public Long getOverrideBy() {
        return this.overrideBy;
    }

    public OffsetDateTime getOverrideOnUtc() {
        return this.overrideOnUtc;
    }

    public OffsetDateTime getClassifiedOnUtc() {
        return this.classifiedOnUtc;
    }

    public Long getClassifiedBy() {
        return this.classifiedBy;
    }
}
