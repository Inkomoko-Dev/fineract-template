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

public final class LoanClassificationOutcome {

    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_FLAGGED = "FLAGGED";
    public static final String STATUS_UNCONFIGURED_COUNTRY = "UNCONFIGURED_COUNTRY";

    public static final String SOURCE_AUTO = "AUTO";
    public static final String SOURCE_WRITE_OFF = "WRITE_OFF";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_BACKFILL = "BACKFILL";

    private final Integer classification;
    private final String status;
    private final String source;
    private final String errorMessage;
    private final boolean excludedFromDownstream;

    private LoanClassificationOutcome(final Integer classification, final String status, final String source, final String errorMessage,
            final boolean excludedFromDownstream) {
        this.classification = classification;
        this.status = status;
        this.source = source;
        this.errorMessage = errorMessage;
        this.excludedFromDownstream = excludedFromDownstream;
    }

    public static LoanClassificationOutcome valid(final int classification, final String source) {
        return new LoanClassificationOutcome(classification, STATUS_VALID, source, null, false);
    }

    public static LoanClassificationOutcome flagged(final String errorMessage) {
        return new LoanClassificationOutcome(null, STATUS_FLAGGED, SOURCE_AUTO, errorMessage, true);
    }

    public static LoanClassificationOutcome unconfiguredCountry() {
        return new LoanClassificationOutcome(null, STATUS_UNCONFIGURED_COUNTRY, SOURCE_AUTO,
                "Country is not listed in loan classification thresholds. Configure arrears bands for this country in Organization > Loan Classification before loans can be classified.",
                true);
    }

    public Integer getClassification() {
        return this.classification;
    }

    public String getStatus() {
        return this.status;
    }

    public String getSource() {
        return this.source;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public boolean isExcludedFromDownstream() {
        return this.excludedFromDownstream;
    }

    public boolean isValid() {
        return STATUS_VALID.equals(this.status) && LoanClassificationCodes.isValid(this.classification);
    }
}
