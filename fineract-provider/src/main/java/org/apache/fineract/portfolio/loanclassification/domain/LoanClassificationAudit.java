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
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_loan_classification_audit")
public class LoanClassificationAudit extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "old_classification")
    private Integer oldClassification;

    @Column(name = "new_classification")
    private Integer newClassification;

    @Column(name = "old_status", length = 40)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 40)
    private String newStatus;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "days_in_arrears")
    private Integer daysInArrears;

    @Column(name = "country_cv_id")
    private Long countryCvId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    protected LoanClassificationAudit() {}

    public static LoanClassificationAudit entry(final Long loanId, final Integer oldClassification, final Integer newClassification,
            final String oldStatus, final String newStatus, final String source, final Integer daysInArrears, final Long countryCvId,
            final String reason, final String errorMessage, final Long createdBy, final OffsetDateTime createdOnUtc) {
        final LoanClassificationAudit audit = new LoanClassificationAudit();
        audit.loanId = loanId;
        audit.oldClassification = oldClassification;
        audit.newClassification = newClassification;
        audit.oldStatus = oldStatus;
        audit.newStatus = newStatus;
        audit.source = source;
        audit.daysInArrears = daysInArrears;
        audit.countryCvId = countryCvId;
        audit.reason = reason;
        audit.errorMessage = errorMessage;
        audit.createdBy = createdBy;
        audit.createdOnUtc = createdOnUtc;
        return audit;
    }

    public Long getLoanId() {
        return this.loanId;
    }

    public Integer getOldClassification() {
        return this.oldClassification;
    }

    public Integer getNewClassification() {
        return this.newClassification;
    }

    public String getOldStatus() {
        return this.oldStatus;
    }

    public String getNewStatus() {
        return this.newStatus;
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

    public String getReason() {
        return this.reason;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public Long getCreatedBy() {
        return this.createdBy;
    }

    public OffsetDateTime getCreatedOnUtc() {
        return this.createdOnUtc;
    }
}
