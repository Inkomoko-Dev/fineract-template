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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoanEventApiJsonValidatorPaymentTypeTest {

    private static final String BASE = "\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"transactionDate\":\"03 September 2026\","
            + "\"transactionAmount\":\"311000\"";

    private LoanEventApiJsonValidator validator;

    @BeforeEach
    void setUp() {
        this.validator = new LoanEventApiJsonValidator(new FromJsonHelper(), null, null);
    }

    @Test
    @DisplayName("AC6/TC-03: a repayment with no paymentTypeId is rejected")
    void repaymentWithoutPaymentTypeIsRejected() {
        final PlatformApiDataValidationException rejection = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateNewRepaymentTransaction("{" + BASE + "}"));
        assertRejectedOnPaymentType(rejection);
    }

    @Test
    @DisplayName("AC6/TC-03: the exact Chad payload from loan 427057 (paymentTypeId \"\") is rejected")
    void repaymentWithBlankPaymentTypeIsRejected() {
        final PlatformApiDataValidationException rejection = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateNewRepaymentTransaction("{" + BASE + ",\"paymentTypeId\":\"\"}"));
        assertRejectedOnPaymentType(rejection);
    }

    @Test
    @DisplayName("AC6/TC-03: a repayment with paymentTypeId null is rejected")
    void repaymentWithNullPaymentTypeIsRejected() {
        final PlatformApiDataValidationException rejection = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateNewRepaymentTransaction("{" + BASE + ",\"paymentTypeId\":null}"));
        assertRejectedOnPaymentType(rejection);
    }

    @Test
    @DisplayName("Guard: a repayment carrying a payment type is accepted, as the UI sends it")
    void repaymentWithStringPaymentTypeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction("{" + BASE + ",\"paymentTypeId\":\"103\"}"));
    }

    @Test
    @DisplayName("Guard: a repayment carrying a numeric payment type is accepted, as Payment Hub sends it")
    void repaymentWithNumericPaymentTypeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction("{" + BASE + ",\"paymentTypeId\":122}"));
    }

    @Test
    @DisplayName("Guard: a zero or negative payment type stays rejected")
    void repaymentWithZeroPaymentTypeIsRejected() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateNewRepaymentTransaction("{" + BASE + ",\"paymentTypeId\":0}"));
    }

    @Test
    @DisplayName("Guard: an interest waiver (validateTransaction) needs no payment type")
    void interestWaiverWithoutPaymentTypeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateTransaction("{" + BASE + "}"));
    }

    @Test
    @DisplayName("Guard: an undo (validateTransaction, amount 0) needs no payment type")
    void undoWithoutPaymentTypeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateTransaction(
                "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"transactionDate\":\"03 September 2026\",\"transactionAmount\":0}"));
    }

    private static void assertRejectedOnPaymentType(final PlatformApiDataValidationException rejection) {
        assertTrue(rejection.getErrors().stream().anyMatch(error -> "paymentTypeId".equals(error.getParameterName())),
                "The rejection must name paymentTypeId so the UI can show it against the field");
    }
}
