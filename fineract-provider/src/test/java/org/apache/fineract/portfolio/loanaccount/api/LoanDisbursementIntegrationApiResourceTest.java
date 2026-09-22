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
package org.apache.fineract.portfolio.loanaccount.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoanDisbursementIntegrationApiResourceTest {

    @Test
    void createsFriendlyBankSuccessNoteFromPaymentHubUpdate() {
        assertThat(LoanDisbursementIntegrationApiResource.bankDisbursementResultNote("200", "BANK-123",
                "Payment SUCCESS. BoK transfer completed successfully"))
                .isEqualTo(
                        "Bank disbursement completed successfully. Payment SUCCESS. BoK transfer completed successfully. Reference: BANK-123");
    }

    @Test
    void createsFriendlyBankFailureNoteWithoutRawResponse() {
        assertThat(LoanDisbursementIntegrationApiResource.bankDisbursementResultNote("400", "BANK-456", null)).isEqualTo(
                "The bank could not complete this disbursement. Please review the payment details or contact support. Reference: BANK-456")
                .doesNotContain("resultCode", "{");
    }

    @Test
    void includesPartnerResultMessageOnFailureNote() {
        assertThat(LoanDisbursementIntegrationApiResource.bankDisbursementResultNote("500", "RKO61ZIWGE",
                "DISBURSEMENT Transaction Failed: The balance is insufficient for the transaction"))
                .isEqualTo(
                        "The bank could not complete this disbursement. DISBURSEMENT Transaction Failed: The balance is insufficient for the transaction. Reference: RKO61ZIWGE");
    }
}
