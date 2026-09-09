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
package org.apache.fineract.infrastructure.africastalking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommunicationDispatchErrorClassifierTest {

    @Test
    void classifiesInvalidNumber() {
        final var result = CommunicationDispatchErrorClassifier.classify(400, "Invalid phone number supplied");
        assertEquals(CommunicationDispatchErrorClassifier.ErrorCategory.INVALID_NUMBER, result.category());
        assertFalse(result.retryable());
    }

    @Test
    void classifiesTemplateMismatch() {
        final var result = CommunicationDispatchErrorClassifier.classify(400, "Template language not found");
        assertEquals(CommunicationDispatchErrorClassifier.ErrorCategory.TEMPLATE_MISMATCH, result.category());
        assertFalse(result.retryable());
    }

    @Test
    void classifiesTransientHttpErrors() {
        final var result = CommunicationDispatchErrorClassifier.classify(503, "Service unavailable");
        assertEquals(CommunicationDispatchErrorClassifier.ErrorCategory.TRANSIENT, result.category());
        assertTrue(result.retryable());
    }

    @Test
    void classifiesAuthConfig() {
        final var result = CommunicationDispatchErrorClassifier.classify(401, "Unauthorized");
        assertEquals(CommunicationDispatchErrorClassifier.ErrorCategory.AUTH_CONFIG, result.category());
        assertFalse(result.retryable());
    }
}
