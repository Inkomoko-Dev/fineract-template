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
package org.apache.fineract.portfolio.loanaccount.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisbursementInstructionDataValidatorTest {

    private DisbursementInstructionDataValidator validator;

    @BeforeEach
    void setUp() {
        this.validator = new DisbursementInstructionDataValidator(new FromJsonHelper());
    }

    @Test
    void acceptsValidRequest() {
        final String json = "{\"loanAccountNo\":\"000000001\",\"sourceSystem\":\"KIFIYA\",\"supplierExternalId\":\"SUP-001\",\"idempotencyKey\":\"idem-1\"}";
        assertDoesNotThrow(() -> this.validator.validateCreateRequest(json));
    }

    @Test
    void rejectsMissingLoanAccountNo() {
        final String json = "{\"sourceSystem\":\"KIFIYA\",\"supplierExternalId\":\"SUP-001\",\"idempotencyKey\":\"idem-1\"}";
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateCreateRequest(json));
    }

    @Test
    void rejectsMissingIdempotencyKey() {
        final String json = "{\"loanAccountNo\":\"000000001\",\"sourceSystem\":\"KIFIYA\",\"supplierExternalId\":\"SUP-001\"}";
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateCreateRequest(json));
    }

    @Test
    void rejectsUnknownField() {
        final String json = "{\"loanAccountNo\":\"000000001\",\"sourceSystem\":\"KIFIYA\",\"supplierExternalId\":\"SUP-001\",\"idempotencyKey\":\"idem-1\",\"foo\":\"bar\"}";
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateCreateRequest(json));
    }
}
