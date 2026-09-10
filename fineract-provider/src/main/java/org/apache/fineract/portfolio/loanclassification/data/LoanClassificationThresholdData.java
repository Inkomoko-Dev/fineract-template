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

public final class LoanClassificationThresholdData {

    private final Integer classification;
    private final String classificationLabel;
    private final Integer minDaysInArrears;
    private final Integer maxDaysInArrears;

    public LoanClassificationThresholdData(final Integer classification, final String classificationLabel, final Integer minDaysInArrears,
            final Integer maxDaysInArrears) {
        this.classification = classification;
        this.classificationLabel = classificationLabel;
        this.minDaysInArrears = minDaysInArrears;
        this.maxDaysInArrears = maxDaysInArrears;
    }

    public Integer getClassification() {
        return this.classification;
    }

    public String getClassificationLabel() {
        return this.classificationLabel;
    }

    public Integer getMinDaysInArrears() {
        return this.minDaysInArrears;
    }

    public Integer getMaxDaysInArrears() {
        return this.maxDaysInArrears;
    }
}
