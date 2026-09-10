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
package org.apache.fineract.portfolio.loanclassification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationArrearsBand;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationCodes;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationOutcome;
import org.apache.fineract.portfolio.loanclassification.service.LoanClassificationCalculator;
import org.junit.jupiter.api.Test;

class LoanClassificationCalculatorTest {

    private static List<LoanClassificationArrearsBand> defaultBands() {
        return Arrays.asList(new LoanClassificationArrearsBand(1, 0, 30), new LoanClassificationArrearsBand(2, 31, 90),
                new LoanClassificationArrearsBand(3, 91, 180), new LoanClassificationArrearsBand(4, 181, 360),
                new LoanClassificationArrearsBand(5, 361, null));
    }

    @Test
    void mapsDefaultArrearsBandsInclusively() {
        final int[][] cases = { { 0, 1 }, { 30, 1 }, { 31, 2 }, { 60, 2 }, { 90, 2 }, { 91, 3 }, { 120, 3 }, { 180, 3 }, { 181, 4 },
                { 200, 4 }, { 360, 4 }, { 361, 5 }, { 400, 5 } };
        for (final int[] testCase : cases) {
            final LoanClassificationOutcome outcome = LoanClassificationCalculator.classify(false, testCase[0], defaultBands());
            assertTrue(outcome.isValid(), "days=" + testCase[0]);
            assertEquals(testCase[1], outcome.getClassification(), "days=" + testCase[0]);
            assertFalse(outcome.isExcludedFromDownstream());
            assertEquals(LoanClassificationOutcome.SOURCE_AUTO, outcome.getSource());
        }
    }

    @Test
    void writtenOffLoanIsAlwaysClassificationSix() {
        final LoanClassificationOutcome outcome = LoanClassificationCalculator.classify(true, 12, defaultBands());
        assertEquals(LoanClassificationCodes.WRITE_OFF.getCode(), outcome.getClassification());
        assertEquals(LoanClassificationOutcome.SOURCE_WRITE_OFF, outcome.getSource());
        assertTrue(outcome.isValid());
    }

    @Test
    void writtenOffDoesNotRequireCountryConfig() {
        final LoanClassificationOutcome outcome = LoanClassificationCalculator.classify(true, null, Collections.emptyList());
        assertEquals(6, outcome.getClassification());
        assertTrue(outcome.isValid());
    }

    @Test
    void nullDaysInArrearsIsFlaggedAndNotDefaultedToZero() {
        final LoanClassificationOutcome outcome = LoanClassificationCalculator.classify(false, null, defaultBands());
        assertNull(outcome.getClassification());
        assertEquals(LoanClassificationOutcome.STATUS_FLAGGED, outcome.getStatus());
        assertTrue(outcome.isExcludedFromDownstream());
        assertTrue(outcome.getErrorMessage().contains("null"));
        assertFalse(LoanClassificationCodes.isValid(0));
        assertFalse(LoanClassificationCodes.isValid(null));
    }

    @Test
    void missingCountryConfigBlocksClassification() {
        final LoanClassificationOutcome outcome = LoanClassificationCalculator.classify(false, 10, Collections.emptyList());
        assertNull(outcome.getClassification());
        assertEquals(LoanClassificationOutcome.STATUS_UNCONFIGURED_COUNTRY, outcome.getStatus());
        assertTrue(outcome.isExcludedFromDownstream());
        assertTrue(outcome.getErrorMessage().contains("Configure"));
    }

    @Test
    void daysOutsideAllBandsAreFlagged() {
        final List<LoanClassificationArrearsBand> incomplete = Collections.singletonList(new LoanClassificationArrearsBand(1, 0, 10));
        final LoanClassificationOutcome outcome = LoanClassificationCalculator.classify(false, 40, incomplete);
        assertEquals(LoanClassificationOutcome.STATUS_FLAGGED, outcome.getStatus());
        assertNull(outcome.getClassification());
    }

    @Test
    void classificationZeroIsNeverValid() {
        assertFalse(LoanClassificationCodes.isValid(0));
        assertEquals("Normal/Performing", LoanClassificationCodes.labelOf(1));
        assertEquals("Write-off", LoanClassificationCodes.labelOf(6));
    }
}
