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
package org.apache.fineract.portfolio.loanclassification.service;

import java.util.List;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationArrearsBand;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationCodes;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationOutcome;

/**
 * Pure mapping of loan status + days-in-arrears onto standard classifications 1-6.
 * Country bands are injected by the caller so rules can change without code changes.
 */
public final class LoanClassificationCalculator {

    private LoanClassificationCalculator() {}

    public static LoanClassificationOutcome classify(final boolean writtenOff, final Integer daysInArrears,
            final List<LoanClassificationArrearsBand> countryBands) {
        if (writtenOff) {
            return LoanClassificationOutcome.valid(LoanClassificationCodes.WRITE_OFF.getCode(), LoanClassificationOutcome.SOURCE_WRITE_OFF);
        }
        if (countryBands == null || countryBands.isEmpty()) {
            return LoanClassificationOutcome.unconfiguredCountry();
        }
        if (daysInArrears == null) {
            return LoanClassificationOutcome.flagged(
                    "Days in arrears is null. Loan flagged for review and not defaulted to classification 0.");
        }
        if (daysInArrears < 0) {
            return LoanClassificationOutcome.flagged("Days in arrears is negative (" + daysInArrears + "). Loan flagged for review.");
        }
        for (final LoanClassificationArrearsBand band : countryBands) {
            if (band.matches(daysInArrears)) {
                final int code = band.getClassification();
                if (!LoanClassificationCodes.isValid(code) || code == LoanClassificationCodes.WRITE_OFF.getCode()) {
                    return LoanClassificationOutcome.flagged("Configured arrears band maps to invalid classification " + code + ".");
                }
                return LoanClassificationOutcome.valid(code, LoanClassificationOutcome.SOURCE_AUTO);
            }
        }
        return LoanClassificationOutcome.flagged("No arrears threshold matches " + daysInArrears
                + " days in arrears. Review country bands in Organization > Loan Classification.");
    }
}
