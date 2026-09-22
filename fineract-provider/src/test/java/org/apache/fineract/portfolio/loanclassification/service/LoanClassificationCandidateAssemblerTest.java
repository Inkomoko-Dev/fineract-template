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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoanClassificationCandidateAssemblerTest {

    @Test
    void noAgingRowMeansCurrentLoanIsZeroDaysNotNull() {
        assertEquals(0, LoanClassificationCandidateAssembler.resolveDaysInArrears(false, false, null, null));
    }

    @Test
    void overdueAmountWithoutOverdueSinceIsNullAndNotDefaultedToZero() {
        assertNull(LoanClassificationCandidateAssembler.resolveDaysInArrears(false, true, null, new BigDecimal("25.00")));
    }

    @Test
    void agingRowWithNullOverdueSinceAndNullAmountIsFlaggedNotZero() {
        assertNull(LoanClassificationCandidateAssembler.resolveDaysInArrears(false, true, null, null));
    }

    @Test
    void agingRowWithExplicitZeroOverdueAndNoSinceIsCurrent() {
        assertEquals(0, LoanClassificationCandidateAssembler.resolveDaysInArrears(false, true, null, BigDecimal.ZERO));
    }

    @Test
    void writtenOffDoesNotUseArrearsDays() {
        assertNull(LoanClassificationCandidateAssembler.resolveDaysInArrears(true, true, null, new BigDecimal("100")));
    }
}
