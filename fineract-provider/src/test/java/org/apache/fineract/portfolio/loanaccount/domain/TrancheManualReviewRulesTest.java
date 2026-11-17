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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TrancheManualReviewRulesTest {

    @Test
    void pastDeadlineExtendsFromBusinessDate() {
        assertEquals(LocalDate.of(2026, 3, 15),
                TrancheManualReviewRules.extendedDeadline(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), 14));
    }

    @Test
    void futureDeadlineExtendsFromThatDeadline() {
        assertEquals(LocalDate.of(2026, 3, 15),
                TrancheManualReviewRules.extendedDeadline(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 15), 14));
    }

    @Test
    void missingCurrentDeadlineUsesBusinessDate() {
        assertEquals(LocalDate.of(2026, 3, 15), TrancheManualReviewRules.extendedDeadline(null, LocalDate.of(2026, 3, 1), 14));
    }

    @Test
    void invalidExtensionDaysAreRejected() {
        assertFalse(TrancheManualReviewRules.acceptableExtensionDays(null));
        assertFalse(TrancheManualReviewRules.acceptableExtensionDays(0));
        assertFalse(TrancheManualReviewRules.acceptableExtensionDays(3661));
        assertTrue(TrancheManualReviewRules.acceptableExtensionDays(1));
        assertTrue(TrancheManualReviewRules.acceptableExtensionDays(14));
        assertNull(TrancheManualReviewRules.extendedDeadline(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), 0));
    }

    @Test
    void laterOverrideBecomesTheEffectiveDeadline() {
        assertEquals(LocalDate.of(2026, 3, 15),
                TrancheManualReviewRules.effectiveDeadline(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15)));
        assertEquals(LocalDate.of(2026, 3, 1),
                TrancheManualReviewRules.effectiveDeadline(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1)));
        assertEquals(LocalDate.of(2026, 3, 1), TrancheManualReviewRules.effectiveDeadline(LocalDate.of(2026, 3, 1), null));
        assertEquals(LocalDate.of(2026, 3, 15), TrancheManualReviewRules.effectiveDeadline(null, LocalDate.of(2026, 3, 15)));
    }
}
