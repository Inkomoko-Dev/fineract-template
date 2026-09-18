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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanClassificationJobServiceTest {

    @Test
    void errorLogIsNullWhenThereAreNoIssues() {
        assertNull(LoanClassificationJobService.toErrorLog(new ArrayList<>(), 0));
    }

    @Test
    void errorLogIsCappedAndReportsRemainingCount() {
        final List<String> errors = new ArrayList<>();
        for (int i = 0; i < LoanClassificationJobService.MAX_ERROR_LOG_LINES + 25; i++) {
            LoanClassificationJobService.addError(errors, "loanId=" + i);
        }
        assertEquals(LoanClassificationJobService.MAX_ERROR_LOG_LINES, errors.size());
        final String log = LoanClassificationJobService.toErrorLog(errors, LoanClassificationJobService.MAX_ERROR_LOG_LINES + 25);
        assertTrue(log.contains("loanId=0"));
        assertTrue(log.contains("... and 25 more"));
        assertEquals(LoanClassificationJobService.MAX_ERROR_LOG_LINES + 1, log.split("\n").length);
    }
}
