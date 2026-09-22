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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisbursementRequestServiceImplTest {

    @Test
    void categorizesTimeoutResponses() {
        assertThat(DisbursementRequestServiceImpl.disbursementFailureCategory(408)).isEqualTo("timeout");
        assertThat(DisbursementRequestServiceImpl.disbursementFailureCategory(504)).isEqualTo("timeout");
    }

    @Test
    void categorizesInvalidPaymentDetails() {
        assertThat(DisbursementRequestServiceImpl.disbursementFailureCategory(400)).isEqualTo("validationFailed");
        assertThat(DisbursementRequestServiceImpl.disbursementFailureCategory(422)).isEqualTo("validationFailed");
    }

    @Test
    void categorizesBankRejectionsAndServiceFailures() {
        assertThat(DisbursementRequestServiceImpl.disbursementFailureCategory(409)).isEqualTo("paymentHubRejection");
        assertThat(DisbursementRequestServiceImpl.disbursementFailureCategory(503)).isEqualTo("serviceUnavailable");
    }

    @Test
    void usesPaymentHubFieldErrorsInValidationMessage() {
        final String body = """
                {
                  "status": 422,
                  "code": "VALIDATION_FAILED",
                  "message": "There are invalid fields in the request",
                  "errors": [
                    {
                      "field": "clientPhoneNumber",
                      "message": "The length of the number is too short"
                    },
                    {
                      "field": "amount",
                      "message": "Value must be greater than 1.00"
                    }
                  ]
                }
                """;

        final DisbursementRequestServiceImpl.PaymentHubErrorResponse parsed = DisbursementRequestServiceImpl
                .parsePaymentHubErrorResponse(body);

        assertThat(DisbursementRequestServiceImpl.disbursementFailureCategory(422, parsed)).isEqualTo("validationFailed");
        assertThat(DisbursementRequestServiceImpl.failureMessage("validationFailed", parsed)).isEqualTo(
                "clientPhoneNumber: The length of the number is too short; amount: Value must be greater than 1.00");
    }

    @Test
    void fallsBackToGenericMessageWhenPaymentHubBodyHasNoFieldErrors() {
        assertThat(DisbursementRequestServiceImpl.failureMessage("validationFailed", null))
                .isEqualTo("The Payment Hub could not accept the disbursement details. Please review them or contact support.");
        assertThat(DisbursementRequestServiceImpl.parsePaymentHubErrorResponse("not-json")).isNull();
    }

    @Test
    void createsSingleUserFriendlyPaymentHubSuccessNoteWithoutTechnicalPayloads() {
        final String requestId = "cbs_disb_42_7";

        assertThat(DisbursementRequestServiceImpl.paymentHubSubmissionSuccessNote(requestId))
                .isEqualTo("Disbursement request sent to the Payment Hub successfully. Reference: cbs_disb_42_7").doesNotContain("{");
    }

    @Test
    void createsPaymentHubFailureNoteWithFieldErrors() {
        assertThat(DisbursementRequestServiceImpl.paymentHubSubmissionFailureNote("cbs_disb_42_7",
                "clientPhoneNumber: The length of the number is too short"))
                .isEqualTo(
                        "Payment Hub rejected this disbursement. clientPhoneNumber: The length of the number is too short Reference: cbs_disb_42_7");
    }
}
