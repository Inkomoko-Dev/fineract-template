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
package org.apache.fineract.infrastructure.creditbureau.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CRBPostingLogExceptionTest {

    @Test
    void unavailableExceptionExposesTimeoutUserMessage() {
        CRBPostingLogsUnavailableException exception = new CRBPostingLogsUnavailableException();
        assertEquals("Service unavailable / Database query timeout", exception.getDefaultUserMessage());
        assertEquals("error.msg.crb.posting.logs.unavailable", exception.getGlobalisationMessageCode());
    }

    @Test
    void retrievalExceptionExposesActionableUserMessage() {
        CRBPostingLogsRetrievalException exception = new CRBPostingLogsRetrievalException();
        assertEquals("Unable to fetch CRB posting logs. Please retry or narrow the date range.", exception.getDefaultUserMessage());
    }

    @Test
    void notFoundExceptionIncludesLogId() {
        CRBPostingLogNotFoundException exception = new CRBPostingLogNotFoundException(42L);
        assertEquals("CRB posting log with identifier `42` does not exist", exception.getDefaultUserMessage());
    }
}
