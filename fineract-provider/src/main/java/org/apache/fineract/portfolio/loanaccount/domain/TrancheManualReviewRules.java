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

import java.time.LocalDate;

/**
 * Pure helpers for manual-review decisions on multi-tranche loans.
 */
public final class TrancheManualReviewRules {

    public static final int MIN_EXTENSION_DAYS = 1;
    public static final int MAX_EXTENSION_DAYS = 3660;

    private TrancheManualReviewRules() {}

    /**
     * Extends from the later of the current deadline and the business date so a past deadline still
     * receives a full extension window.
     */
    public static LocalDate extendedDeadline(final LocalDate currentDeadline, final LocalDate businessDate, final int extensionDays) {
        if (businessDate == null || extensionDays < MIN_EXTENSION_DAYS || extensionDays > MAX_EXTENSION_DAYS) {
            return null;
        }
        final LocalDate base = currentDeadline == null || !currentDeadline.isAfter(businessDate) ? businessDate : currentDeadline;
        return base.plusDays(extensionDays);
    }

    public static LocalDate effectiveDeadline(final LocalDate scheduleDeadline, final LocalDate override) {
        if (override == null) {
            return scheduleDeadline;
        }
        if (scheduleDeadline == null || override.isAfter(scheduleDeadline)) {
            return override;
        }
        return scheduleDeadline;
    }

    public static boolean acceptableExtensionDays(final Integer days) {
        return days != null && days >= MIN_EXTENSION_DAYS && days <= MAX_EXTENSION_DAYS;
    }
}
