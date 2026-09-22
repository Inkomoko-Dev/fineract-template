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

public final class LoanClassificationSummaryRowData {

    private final String countryName;
    private final String officeName;
    private final String loanProductName;
    private final Integer classification;
    private final String classificationLabel;
    private final Long loanCount;
    private final Long excludedCount;
    private final Long overrideCount;

    public LoanClassificationSummaryRowData(final String countryName, final String officeName, final String loanProductName,
            final Integer classification, final String classificationLabel, final Long loanCount, final Long excludedCount,
            final Long overrideCount) {
        this.countryName = countryName;
        this.officeName = officeName;
        this.loanProductName = loanProductName;
        this.classification = classification;
        this.classificationLabel = classificationLabel;
        this.loanCount = loanCount;
        this.excludedCount = excludedCount;
        this.overrideCount = overrideCount;
    }

    public String getCountryName() {
        return this.countryName;
    }

    public String getOfficeName() {
        return this.officeName;
    }

    public String getLoanProductName() {
        return this.loanProductName;
    }

    public Integer getClassification() {
        return this.classification;
    }

    public String getClassificationLabel() {
        return this.classificationLabel;
    }

    public Long getLoanCount() {
        return this.loanCount;
    }

    public Long getExcludedCount() {
        return this.excludedCount;
    }

    public Long getOverrideCount() {
        return this.overrideCount;
    }
}
