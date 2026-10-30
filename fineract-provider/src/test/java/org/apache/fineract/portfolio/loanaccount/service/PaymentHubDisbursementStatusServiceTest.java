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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.data.PaymentHubStatusQueryResult;
import org.apache.fineract.portfolio.loanaccount.data.PaymentHubTransactionStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.PaymentHubDisbursementPoll;
import org.apache.fineract.portfolio.loanaccount.domain.PaymentHubDisbursementPollRepository;
import org.apache.fineract.portfolio.loanaccount.excessrefund.service.ExcessRefundWritePlatformService;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class PaymentHubDisbursementStatusServiceTest {

    @Mock
    private PaymentHubDisbursementPollRepository pollRepository;
    @Mock
    private DisbursementRequestService disbursementRequestService;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LoanWritePlatformService loanWritePlatformService;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ExcessRefundWritePlatformService excessRefundWritePlatformService;

    private PaymentHubDisbursementStatusService service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        this.service = new PaymentHubDisbursementStatusService(this.pollRepository, this.disbursementRequestService, this.loanRepository,
                this.loanWritePlatformService, this.noteRepository, this.fromApiJsonHelper, this.transactionManager,
                this.excessRefundWritePlatformService);
        lenient().when(this.transactionManager.getTransaction(any())).thenReturn(org.mockito.Mockito.mock(TransactionStatus.class));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clear();
    }

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

    @Test
    void routesExcessRefundRequestIdsToApplyHubCallback() throws Exception {
        final PaymentHubDisbursementPoll poll = PaymentHubDisbursementPoll.pending(5L, null, "cbs_refund_20", "txn-1", 7L,
                LocalDate.of(2026, 10, 30));
        setEntityId(poll, 99L);
        when(this.pollRepository.findByLoanIdAndStatusOrderByIdAsc(5L, PaymentHubTransactionStatus.PENDING)).thenReturn(List.of(poll));
        when(this.pollRepository.findById(99L)).thenReturn(Optional.of(poll));
        when(this.disbursementRequestService.paymentHubAccessToken()).thenReturn("token");
        when(this.disbursementRequestService.queryTransactionStatus("token", "cbs_refund_20", "txn-1"))
                .thenReturn(PaymentHubStatusQueryResult.success(200, PaymentHubTransactionStatus.parse(
                        "{\"status\":\"SUCCESS\",\"statusCode\":200,\"transactionRef\":\"REF-1\",\"reason\":\"ok\"}")));

        this.service.queryForLoan(5L);

        verify(this.excessRefundWritePlatformService).applyHubCallback(eq("cbs_refund_20"), eq(true), eq("REF-1"), eq("ok"));
        verify(this.loanWritePlatformService, never()).disburseLoan(anyLong(), any(), anyBoolean(), anyBoolean());
        verify(this.pollRepository).save(poll);
        assertThat(poll.getStatus()).isEqualTo(PaymentHubTransactionStatus.SUCCESS);
    }

    private static void setEntityId(final AbstractPersistableCustom entity, final Long id) throws Exception {
        final Method setId = AbstractPersistableCustom.class.getDeclaredMethod("setId", Long.class);
        setId.setAccessible(true);
        setId.invoke(entity, id);
    }
}
