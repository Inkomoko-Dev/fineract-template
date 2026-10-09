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

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.portfolio.loanaccount.data.PaymentHubTransactionStatus;
import org.junit.jupiter.api.Test;

class PaymentHubDisbursementStatusServiceTest {

    @Test
    void parsesPendingQueryStatus() {
        final String body = """
                {
                  "requestId": "CBS-REQ-1001",
                  "status": "PENDING",
                  "transactionRef": null,
                  "reason": "COLLECTION Transaction in Pending State.",
                  "transactionId": "8f3c1a2e-4b6d-4e91-9c0a-1d2e3f4a5b6c",
                  "statusCode": 202,
                  "transactionType": "DISBURSEMENT",
                  "amount": 1500.00,
                  "currency": "RWF",
                  "externalId": "000000072",
                  "transactionDate": null
                }
                """;

        final PaymentHubTransactionStatus status = PaymentHubTransactionStatus.parse(body);

        assertThat(status.isPending()).isTrue();
        assertThat(status.isTerminal()).isFalse();
        assertThat(status.getTransactionId()).isEqualTo("8f3c1a2e-4b6d-4e91-9c0a-1d2e3f4a5b6c");
        assertThat(status.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(status.getExternalId()).isEqualTo("000000072");
        assertThat(status.getStatusCode()).isEqualTo(202);
    }

    @Test
    void mapsTerminalStatusesToDisbursementResultCodes() {
        final PaymentHubTransactionStatus success = PaymentHubTransactionStatus.parse("""
                {"status":"SUCCESS","statusCode":200,"transactionDate":"2026-10-07T09:15:00Z","transactionRef":"RCPT-1"}
                """);
        final PaymentHubTransactionStatus failed = PaymentHubTransactionStatus.parse("""
                {"status":"FAILED","statusCode":500,"reason":"DISBURSEMENT Transaction Failed"}
                """);
        final PaymentHubTransactionStatus exhausted = PaymentHubTransactionStatus.parse("""
                {"status":"PENDING_ERROR","statusCode":5001,"reason":"Status checks were exhausted"}
                """);

        assertThat(success.isSuccess()).isTrue();
        assertThat(PaymentHubDisbursementStatusService.fineractResultCode(success)).isEqualTo("200");
        assertThat(PaymentHubDisbursementStatusService.disbursementDate(success, LocalDate.of(2026, 10, 1)))
                .isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(PaymentHubDisbursementStatusService.fineractResultCode(failed)).isEqualTo("500");
        assertThat(PaymentHubDisbursementStatusService.fineractResultCode(exhausted)).isEqualTo("5001");
        assertThat(exhausted.isTerminal()).isTrue();
    }

    @Test
    void buildsStatusQueryAndDisbursementCommand() {
        assertThat(PaymentHubTransactionStatus.queryRequestJson("CBS-REQ-1001", "8f3c1a2e-4b6d-4e91-9c0a-1d2e3f4a5b6c"))
                .contains("\"requestId\":\"CBS-REQ-1001\"").contains("\"transactionId\":\"8f3c1a2e-4b6d-4e91-9c0a-1d2e3f4a5b6c\"");

        final String command = PaymentHubDisbursementStatusService.disbursementCommandJson(4L, new BigDecimal("1500.00"),
                LocalDate.of(2026, 10, 7), "200", "RCPT-1");

        assertThat(command).contains("\"paymentTypeId\":4").contains("\"transactionAmount\":1500.00")
                .contains("\"actualDisbursementDate\":\"2026-10-07\"").contains("\"dateFormat\":\"yyyy-MM-dd\"")
                .contains("\"resultCode\":\"200\"").contains("\"receiptNumber\":\"RCPT-1\"");
    }

    @Test
    void tracksAcceptedDisbursementsThatAreStillPending() {
        assertThat(DisbursementRequestServiceImpl.shouldPollPaymentHubStatus(null)).isTrue();
        assertThat(DisbursementRequestServiceImpl.shouldPollPaymentHubStatus(PaymentHubTransactionStatus.parse("{\"status\":\"PENDING\"}")))
                .isTrue();
        assertThat(DisbursementRequestServiceImpl
                .shouldPollPaymentHubStatus(PaymentHubTransactionStatus.parse("{\"status\":\"SUCCESS\",\"statusCode\":200}"))).isFalse();
        assertThat(DisbursementRequestServiceImpl
                .shouldPollPaymentHubStatus(PaymentHubTransactionStatus.parse("{\"status\":\"FAILED\",\"statusCode\":500}"))).isFalse();
    }
}
