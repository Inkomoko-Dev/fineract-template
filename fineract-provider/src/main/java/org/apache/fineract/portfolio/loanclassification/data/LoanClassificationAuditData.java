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
package org.apache.fineract.portfolio.loanclassification.data;

import java.time.OffsetDateTime;

public final class LoanClassificationAuditData {

    private final Long id;
    private final Long loanId;
    private final Integer oldClassification;
    private final Integer newClassification;
    private final String oldStatus;
    private final String newStatus;
    private final String source;
    private final Integer daysInArrears;
    private final String countryName;
    private final String reason;
    private final String errorMessage;
    private final Long createdBy;
    private final OffsetDateTime createdOnUtc;

    public LoanClassificationAuditData(final Long id, final Long loanId, final Integer oldClassification, final Integer newClassification,
            final String oldStatus, final String newStatus, final String source, final Integer daysInArrears, final String countryName,
            final String reason, final String errorMessage, final Long createdBy, final OffsetDateTime createdOnUtc) {
        this.id = id;
        this.loanId = loanId;
        this.oldClassification = oldClassification;
        this.newClassification = newClassification;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.source = source;
        this.daysInArrears = daysInArrears;
        this.countryName = countryName;
        this.reason = reason;
        this.errorMessage = errorMessage;
        this.createdBy = createdBy;
        this.createdOnUtc = createdOnUtc;
    }

    public Long getId() {
        return this.id;
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

    public String getCountryName() {
        return this.countryName;
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
