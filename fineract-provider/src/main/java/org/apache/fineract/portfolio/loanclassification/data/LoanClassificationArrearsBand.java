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

/**
 * Inclusive arrears-day band mapped to a classification code (1-5). A null maxDays means open-ended (360+).
 */
public final class LoanClassificationArrearsBand {

    private final int classification;
    private final int minDaysInclusive;
    private final Integer maxDaysInclusive;

    public LoanClassificationArrearsBand(final int classification, final int minDaysInclusive, final Integer maxDaysInclusive) {
        this.classification = classification;
        this.minDaysInclusive = minDaysInclusive;
        this.maxDaysInclusive = maxDaysInclusive;
    }

    public int getClassification() {
        return this.classification;
    }

    public int getMinDaysInclusive() {
        return this.minDaysInclusive;
    }

    public Integer getMaxDaysInclusive() {
        return this.maxDaysInclusive;
    }

    public boolean matches(final int daysInArrears) {
        if (daysInArrears < this.minDaysInclusive) {
            return false;
        }
        return this.maxDaysInclusive == null || daysInArrears <= this.maxDaysInclusive;
    }
}
