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

import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The historical waiver has its own whitelist rather than widening the one the standard waive endpoint shares, so these
 * pin both what it accepts and what the standard endpoint must keep rejecting.
 */
public class LoanEventApiJsonValidatorHistoricalPenaltyWaiverTest {

    private LoanEventApiJsonValidator validator;

    @BeforeEach
    public void setUp() {
        this.validator = new LoanEventApiJsonValidator(new FromJsonHelper(), null, null);
    }

    private String payload(final String body) {
        return "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\"," + body + "}";
    }

    private String validPayload() {
        return payload("\"waiverEffectiveDate\":\"01 February 2026\",\"expectedPaidAmount\":5000,"
                + "\"reason\":\"Penalty charged in error during the system migration\"");
    }

    private PlatformApiDataValidationException validationFailureFor(final String json) {
        return assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateHistoricalPenaltyWaiver(json));
    }

    private void assertRejectedFor(final String json, final String parameter) {
        final List<ApiParameterError> errors = validationFailureFor(json).getErrors();
        assertTrue(errors.stream().anyMatch(error -> parameter.equals(error.getParameterName())),
                "expected a validation error naming " + parameter + " but got " + errors);
    }

    @Test
    public void aMinimalRequestIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateHistoricalPenaltyWaiver(validPayload()));
    }

    @Test
    public void everySupportedParameterIsAccepted() {
        final String json = payload("\"waiverEffectiveDate\":\"01 February 2026\",\"expectedPaidAmount\":5000,\"waiverAmount\":2500,"
                + "\"installmentNumber\":2,\"nextApproverUserId\":7,\"reason\":\"Waived per credit committee\","
                + "\"note\":\"See CGLT-656\"");
        assertDoesNotThrow(() -> this.validator.validateHistoricalPenaltyWaiver(json));
    }

    @Test
    public void anUnsupportedParameterIsRejected() {
        assertThrows(UnsupportedParameterException.class,
                () -> this.validator.validateHistoricalPenaltyWaiver(payload("\"dueDate\":\"01 February 2026\"")));
    }

    @Test
    public void aBlankRequestIsRejected() {
        assertThrows(InvalidJsonException.class, () -> this.validator.validateHistoricalPenaltyWaiver(""));
    }

    @Test
    public void reasonIsMandatory() {
        assertRejectedFor(payload("\"waiverEffectiveDate\":\"01 February 2026\",\"expectedPaidAmount\":5000"), "reason");
    }

    @Test
    public void reasonMayNotExceedOneThousandCharacters() {
        final String longReason = "x".repeat(1001);
        assertRejectedFor(payload("\"waiverEffectiveDate\":\"01 February 2026\",\"expectedPaidAmount\":5000,\"reason\":\"" + longReason
                + "\""), "reason");
    }

    @Test
    public void expectedPaidAmountIsMandatory() {
        assertRejectedFor(payload("\"waiverEffectiveDate\":\"01 February 2026\",\"reason\":\"Charged in error\""), "expectedPaidAmount");
    }

    @Test
    public void waiverEffectiveDateIsMandatory() {
        assertRejectedFor(payload("\"expectedPaidAmount\":5000,\"reason\":\"Charged in error\""), "waiverEffectiveDate");
    }

    @Test
    public void aZeroWaiverAmountIsRejected() {
        assertRejectedFor(payload("\"waiverEffectiveDate\":\"01 February 2026\",\"expectedPaidAmount\":5000,\"waiverAmount\":0,"
                + "\"reason\":\"Charged in error\""), "waiverAmount");
    }

    @Test
    public void aNegativeWaiverAmountIsRejected() {
        assertRejectedFor(payload("\"waiverEffectiveDate\":\"01 February 2026\",\"expectedPaidAmount\":5000,\"waiverAmount\":-1,"
                + "\"reason\":\"Charged in error\""), "waiverAmount");
    }

    @Test
    public void aZeroInstallmentNumberIsRejected() {
        assertRejectedFor(payload("\"waiverEffectiveDate\":\"01 February 2026\",\"expectedPaidAmount\":5000,\"installmentNumber\":0,"
                + "\"reason\":\"Charged in error\""), "installmentNumber");
    }

    @Test
    public void theStandardWaiveEndpointStillRejectsHistoricalWaiverParameters() {
        assertThrows(UnsupportedParameterException.class, () -> this.validator
                .validateInstallmentChargeTransaction(payload("\"waiverEffectiveDate\":\"01 February 2026\"")));
    }
}
