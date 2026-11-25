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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LoanEventApiJsonValidatorMfiCodeTest {

    private LoanEventApiJsonValidator validator;

    @BeforeEach
    public void setUp() {
        this.validator = new LoanEventApiJsonValidator(new FromJsonHelper(), null, null);
    }

    private String repaymentPayload(final String mfiCode) {
        return "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"transactionDate\":\"01 February 2026\","
                + "\"transactionAmount\":5000,\"mfiCode\":\"" + mfiCode + "\"}";
    }

    private String repaymentPayloadWithoutMfi() {
        return "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"transactionDate\":\"01 February 2026\","
                + "\"transactionAmount\":5000}";
    }

    @Test
    public void aRepaymentCarryingAnMfiCodeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction(repaymentPayload("MFI-001")));
    }

    @Test
    public void aTransactionCarryingAnMfiCodeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateTransaction(repaymentPayload("MFI-001")));
    }

    @Test
    public void aRepaymentWithAlphanumericMfiCodeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction(repaymentPayload("RAYSMFI")));
    }

    @Test
    public void aRepaymentWithMfiCodeContainingSpacesAndHyphensIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction(repaymentPayload("Express Union MFI")));
    }

    @Test
    public void aRepaymentWithMfiCodeContainingUnderscoresAndSlashesIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction(repaymentPayload("BG_MFI_2026/ET")));
    }

    @Test
    public void aRepaymentWithFiftyCharacterMfiCodeIsAccepted() {
        final String fiftyChars = "MFI-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction(repaymentPayload(fiftyChars)));
    }

    @Test
    public void aRepaymentWithBlankMfiCodeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction(repaymentPayload("")));
    }

    @Test
    public void aRepaymentWithoutMfiCodeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction(repaymentPayloadWithoutMfi()));
    }

    @Test
    public void aRepaymentWithSpecialCharactersInMfiCodeIsRejected() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateNewRepaymentTransaction(repaymentPayload("MFI@#$%")));
    }

    @Test
    public void aRepaymentWithPaddedMfiCodeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateNewRepaymentTransaction(repaymentPayload("  MFI-001  ")));
    }

    @Test
    public void aRepaymentWithFiftyOneCharacterMfiCodeIsRejected() {
        final String fiftyOneChars = "MFI-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijk";
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateNewRepaymentTransaction(repaymentPayload(fiftyOneChars)));
    }

    @Test
    public void anUnrelatedUnknownParameterIsStillRejected() {
        final String json = "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"transactionDate\":\"01 February 2026\","
                + "\"transactionAmount\":5000,\"notAField\":\"x\"}";
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateNewRepaymentTransaction(json));
    }

    private String disbursePayload(final String mfiCode) {
        return "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"actualDisbursementDate\":\"01 February 2026\","
                + "\"paymentTypeId\":1,\"mfiCode\":\"" + mfiCode + "\"}";
    }

    @Test
    public void aDisbursementCarryingAnMfiCodeIsAccepted() {
        assertDoesNotThrow(() -> this.validator.validateDisbursement(disbursePayload("ET-MFI-02"), false));
    }

    @Test
    public void aDisbursementWithoutMfiCodeIsAccepted() {
        final String json = "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"actualDisbursementDate\":\"01 February 2026\","
                + "\"paymentTypeId\":1}";
        assertDoesNotThrow(() -> this.validator.validateDisbursement(json, false));
    }

    @Test
    public void aRepaymentWithInvalidMfiCodeShowsFriendlyMessage() {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateNewRepaymentTransaction(repaymentPayload("MFI@01")));
        assertTrue(ex.getErrors().stream().anyMatch(
                e -> e.getDefaultUserMessage() != null && e.getDefaultUserMessage().contains("MFI Code can only contain letters")));
    }

    @Test
    public void resolvesPaddedInheritedCodeAtPaymentDetailLimit() {
        final String code = "A".repeat(50);
        assertEquals(code, this.validator.resolveDisbursementPaymentMfiCode(" ", "  " + code + " "));
    }

    @Test
    public void rejectsOversizedOrInvalidInheritedCodeWithCorrectionMessage() {
        for (final String storedCode : new String[] { "A".repeat(51), "MFI@01" }) {
            final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                    () -> this.validator.resolveDisbursementPaymentMfiCode(null, storedCode));
            assertTrue(exception.getErrors().stream().anyMatch(
                    error -> error.getDefaultUserMessage().contains("Enter a corrected MFI Code")));
        }
    }

    @Test
    public void explicitPostingCodeOverridesAnOversizedStoredCode() {
        assertEquals("MFI-POSTED", this.validator.resolveDisbursementPaymentMfiCode(" MFI-POSTED ", "A".repeat(51)));
    }

    @Test
    public void absentPostingAndStoredCodesStayEmpty() {
        assertNull(this.validator.resolveDisbursementPaymentMfiCode(null, " "));
    }
}
