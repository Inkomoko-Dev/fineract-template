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

public final class LoanClassificationData {

    private final Long loanId;
    private final Integer classification;
    private final String classificationLabel;
    private final String status;
    private final String source;
    private final Integer daysInArrears;
    private final Long countryId;
    private final String countryName;
    private final String errorMessage;
    private final boolean excludedFromDownstream;
    private final boolean overrideActive;
    private final String overrideReason;
    private final OffsetDateTime classifiedOnUtc;

    public LoanClassificationData(final Long loanId, final Integer classification, final String classificationLabel, final String status,
            final String source, final Integer daysInArrears, final Long countryId, final String countryName, final String errorMessage,
            final boolean excludedFromDownstream, final boolean overrideActive, final String overrideReason,
            final OffsetDateTime classifiedOnUtc) {
        this.loanId = loanId;
        this.classification = classification;
        this.classificationLabel = classificationLabel;
        this.status = status;
        this.source = source;
        this.daysInArrears = daysInArrears;
        this.countryId = countryId;
        this.countryName = countryName;
        this.errorMessage = errorMessage;
        this.excludedFromDownstream = excludedFromDownstream;
        this.overrideActive = overrideActive;
        this.overrideReason = overrideReason;
        this.classifiedOnUtc = classifiedOnUtc;
    }

    public Long getLoanId() {
        return this.loanId;
    }

    public Integer getClassification() {
        return this.classification;
    }

    public String getClassificationLabel() {
        return this.classificationLabel;
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

    public Long getCountryId() {
        return this.countryId;
    }

    public String getCountryName() {
        return this.countryName;
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

    public OffsetDateTime getClassifiedOnUtc() {
        return this.classifiedOnUtc;
    }
}
