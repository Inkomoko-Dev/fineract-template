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
package org.apache.fineract.portfolio.loanaccount.excessrefund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.PaymentHubDisbursementPoll;
import org.apache.fineract.portfolio.loanaccount.domain.PaymentHubDisbursementPollRepository;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.apache.fineract.portfolio.loanaccount.service.ClientBankDetailsResolver;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exercises service transaction boundaries using independent MariaDB connections and JDBC-backed test repositories.
 * A fresh table per test isolates the fixture from application data.
 */
@EnabledIfEnvironmentVariable(named = "EXCESS_REFUND_TEST_JDBC_URL", matches = ".+")
class ExcessRefundHubTransactionDbTest {

    private final String table = "test_cglt394_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate jdbc;
    private ExcessRefundWritePlatformService service;
    private ExcessRefundPaymentHubService hub;
    private ExcessRefundGlPoster glPoster;
    private TransactionTemplate transaction;

    @BeforeEach
    void createFixture() {
        final var dataSource = new DriverManagerDataSource(System.getenv("EXCESS_REFUND_TEST_JDBC_URL"),
                System.getenv().getOrDefault("EXCESS_REFUND_TEST_JDBC_USER", "root"),
                System.getenv().getOrDefault("EXCESS_REFUND_TEST_JDBC_PASSWORD", ""));
        jdbc = new JdbcTemplate(dataSource);
        final var manager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(manager);
        jdbc.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, state VARCHAR(32), request_id VARCHAR(100), "
                + "poll_state VARCHAR(32)) ENGINE=InnoDB");
        jdbc.update("INSERT INTO " + table + " (id, state) VALUES (1, NULL), (2, 'APPROVED')");

        final var context = mock(PlatformSecurityContext.class);
        final var user = mock(AppUser.class);
        when(context.authenticatedUser()).thenReturn(user);
        final var offices = mock(OfficeHierarchyService.class);
        when(offices.validateUserAccessToOffice(user, 1L)).thenReturn(true);
        final var loan = mock(Loan.class);
        when(loan.getId()).thenReturn(1L);
        when(loan.getOfficeId()).thenReturn(1L);
        when(loan.getTotalOverpaid()).thenReturn(BigDecimal.TEN);
        final var loans = mock(LoanRepositoryWrapper.class);
        when(loans.findOneForUpdate(1L)).thenAnswer(call -> {
            jdbc.queryForObject("SELECT id FROM " + table + " WHERE id = 1 FOR UPDATE", Long.class);
            return loan;
        });
        when(loans.findOneWithNotFoundDetection(1L)).thenReturn(loan);
        final var refunds = mock(LoanExcessRefundRepository.class);
        when(refunds.findLoanId(2L)).thenReturn(Optional.of(1L));
        when(refunds.findByIdForUpdate(2L)).thenAnswer(call -> Optional.of(loadRefund(true)));
        when(refunds.findOneByHubRequestId(anyString())).thenAnswer(call -> {
            final LoanExcessRefund refund = loadRefund(true);
            return call.getArgument(0).equals(refund.getHubRequestId()) ? Optional.of(refund) : Optional.empty();
        });
        when(refunds.saveAndFlush(any(LoanExcessRefund.class))).thenAnswer(call -> {
            final LoanExcessRefund refund = call.getArgument(0);
            jdbc.update("UPDATE " + table + " SET state = ?, request_id = ? WHERE id = 2", refund.getStatus().name(),
                    refund.getHubRequestId());
            return refund;
        });
        final var polls = mock(PaymentHubDisbursementPollRepository.class);
        when(polls.save(any(PaymentHubDisbursementPoll.class))).thenAnswer(call -> {
            final PaymentHubDisbursementPoll poll = call.getArgument(0);
            jdbc.update("UPDATE " + table + " SET poll_state = ? WHERE id = 2", poll.getStatus());
            return poll;
        });
        when(polls.findByRequestId(anyString())).thenAnswer(call -> {
            final String pollState = jdbc.queryForObject("SELECT poll_state FROM " + table + " WHERE id = 2", String.class);
            if (pollState == null) {
                return Optional.empty();
            }
            final PaymentHubDisbursementPoll poll = PaymentHubDisbursementPoll.pending(1L, null, call.getArgument(0), null, null,
                    LocalDate.of(2026, 11, 24));
            if ("FAILED".equals(pollState)) {
                poll.markRefundOutcome(false, null, null);
            }
            return Optional.of(poll);
        });
        hub = mock(ExcessRefundPaymentHubService.class);
        glPoster = mock(ExcessRefundGlPoster.class);
        service = new ExcessRefundWritePlatformService(context, loans, refunds, mock(NoteRepository.class), new FromJsonHelper(),
                glPoster, hub, offices, mock(ClientBankDetailsResolver.class), manager, polls);
    }

    @AfterEach
    void removeFixture() {
        jdbc.execute("DROP TABLE IF EXISTS " + table);
        ThreadLocalContextUtil.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = { "SUCCESS", "FAILED", "TIMEOUT" })
    void commitsClaimBeforeHubAndReconcilesWithoutASecondSend(final String outcome) throws Exception {
        final var enteredHub = new CountDownLatch(1);
        final var releaseHub = new CountDownLatch(1);
        final var sends = new AtomicInteger();
        when(hub.submitRefund(any(), any(), eq("cbs_refund_2"))).thenAnswer(call -> {
            sends.incrementAndGet();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(loadRefund(false).getStatus()).isEqualTo(LoanExcessRefundStatus.PAYMENT_SUBMITTED);
            assertThat(loadRefund(false).getHubRequestId()).isEqualTo("cbs_refund_2");
            assertThat(jdbc.queryForObject("SELECT poll_state FROM " + table + " WHERE id = 2", String.class)).isEqualTo("PENDING");
            enteredHub.countDown();
            assertThat(releaseHub.await(10, TimeUnit.SECONDS)).isTrue();
            if ("TIMEOUT".equals(outcome)) {
                throw new GeneralPlatformDomainRuleException("test.hub.timeout", "Timeout");
            }
            return "{\"status\":\"" + outcome + "\"}";
        });
        final ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            final var firstSend = workers.submit(() -> {
                setTenantContext();
                try {
                    if ("TIMEOUT".equals(outcome)) {
                        assertThatThrownBy(() -> service.sendToPaymentHub(command())).hasMessageContaining("Timeout");
                    } else {
                        service.sendToPaymentHub(command());
                    }
                } finally {
                    ThreadLocalContextUtil.clear();
                }
            });
            assertThat(enteredHub.await(10, TimeUnit.SECONDS)).isTrue();
            workers.submit(() -> {
                setTenantContext();
                try {
                    assertThatThrownBy(() -> service.sendToPaymentHub(command())).hasMessageContaining("already submitted");
                } finally {
                    ThreadLocalContextUtil.clear();
                }
            }).get(10, TimeUnit.SECONDS);
            releaseHub.countDown();
            firstSend.get(10, TimeUnit.SECONDS);
            if ("TIMEOUT".equals(outcome)) {
                assertThat(loadRefund(false).getStatus()).isEqualTo(LoanExcessRefundStatus.PAYMENT_SUBMITTED);
                setTenantContext();
                transaction.executeWithoutResult(status -> service.applyHubCallback("cbs_refund_2", true, "ref", null));
            }
            assertThat(loadRefund(false).getStatus()).isEqualTo(
                    "FAILED".equals(outcome) ? LoanExcessRefundStatus.PAYMENT_FAILED : LoanExcessRefundStatus.PAID);
            assertThat(sends.get()).isEqualTo(1);
        } finally {
            releaseHub.countDown();
            workers.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 422, 408, 409, 500, 504 })
    void persistsOnlyDefinitiveHubRejections(final int httpStatus) throws Exception {
        setTenantContext();
        final HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/auth", exchange -> {
            final byte[] body = "{\"access_token\":\"test-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        stub.createContext("/refund", exchange -> {
            final byte[] body = "{\"message\":\"Rejected by test hub\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpStatus, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        stub.start();
        try {
            final String base = "http://127.0.0.1:" + stub.getAddress().getPort();
            final String prefix = "fineract.integrations.inkomoko.rest.";
            final var environment = new MockEnvironment().withProperty(prefix + "username", "test")
                    .withProperty(prefix + "password", "test").withProperty(prefix + "authenticationUrl", base + "/auth")
                    .withProperty(prefix + "initiate.refund", base + "/refund");
            final var httpHub = new ExcessRefundPaymentHubService(environment, null);
            when(hub.submitRefund(any(), any(), eq("cbs_refund_2"))).thenAnswer(call ->
                    httpHub.submitRefund(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            assertThatThrownBy(() -> service.sendToPaymentHub(command()))
                    .isInstanceOf(GeneralPlatformDomainRuleException.class);
            verifyNoInteractions(glPoster);
            final boolean definitive = httpStatus == 400 || httpStatus == 422;
            assertThat(loadRefund(false).getStatus())
                    .isEqualTo(definitive ? LoanExcessRefundStatus.PAYMENT_FAILED : LoanExcessRefundStatus.PAYMENT_SUBMITTED);
            assertThat(jdbc.queryForObject("SELECT poll_state FROM " + table + " WHERE id = 2", String.class))
                    .isEqualTo(definitive ? "FAILED" : "PENDING");
            if (!definitive) {
                assertThatThrownBy(() -> service.sendToPaymentHub(command())).hasMessageContaining("already submitted");
            }
        } finally {
            stub.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "PAID", "POSTED" })
    void preservesPaymentWhenARejectionArrivesLate(final String terminalState) {
        setTenantContext();
        when(hub.submitRefund(any(), any(), eq("cbs_refund_2"))).thenAnswer(call -> {
            transaction.executeWithoutResult(status -> jdbc.update(
                    "UPDATE " + table + " SET state = ?, poll_state = 'SUCCESS' WHERE id = 2", terminalState));
            throw new ExcessRefundPaymentHubService.RefundRejectedException("validationFailed", "Late rejection");
        });
        assertThatThrownBy(() -> service.sendToPaymentHub(command())).hasMessageContaining("Late rejection");
        assertThat(loadRefund(false).getStatus().name()).isEqualTo(terminalState);
        assertThat(jdbc.queryForObject("SELECT poll_state FROM " + table + " WHERE id = 2", String.class)).isEqualTo("SUCCESS");
        verifyNoInteractions(glPoster);
    }

    @Test
    void retriesOnlyWithANewCorrelationIdAfterProvenFailureToSend() {
        setTenantContext();
        when(hub.submitRefund(any(), any(), eq("cbs_refund_2")))
                .thenThrow(new ExcessRefundPaymentHubService.RefundNotSentException(new IllegalArgumentException("Missing URL")));
        assertThatThrownBy(() -> service.sendToPaymentHub(command())).hasMessageContaining("was not sent");
        assertThat(loadRefund(false).getStatus()).isEqualTo(LoanExcessRefundStatus.PAYMENT_FAILED);

        when(hub.submitRefund(any(), any(), startsWith("cbs_refund_2_"))).thenAnswer(call -> {
            final String retryId = call.getArgument(2);
            assertThat(loadRefund(false).getHubRequestId()).isEqualTo(retryId);
            assertThatThrownBy(() -> transaction.executeWithoutResult(
                    status -> service.applyHubCallback("cbs_refund_2", false, null, "Old attempt")))
                    .hasMessageContaining("not found");
            assertThat(loadRefund(false).getStatus()).isEqualTo(LoanExcessRefundStatus.PAYMENT_SUBMITTED);
            return "{\"status\":\"SUCCESS\",\"transactionRef\":\"ref\"}";
        });

        service.sendToPaymentHub(command());
        assertThat(loadRefund(false).getStatus()).isEqualTo(LoanExcessRefundStatus.PAID);
    }

    private LoanExcessRefund loadRefund(final boolean lock) {
        return jdbc.queryForObject("SELECT state, request_id FROM " + table + " WHERE id = 2" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> {
                    final var refund = new LoanExcessRefund();
                    ReflectionTestUtils.setField(refund, "id", 2L);
                    refund.setLoanId(1L);
                    refund.setAmount(BigDecimal.TEN);
                    refund.setStatus(LoanExcessRefundStatus.valueOf(rs.getString("state")));
                    refund.setHubRequestId(rs.getString("request_id"));
                    return refund;
                });
    }

    private static JsonCommand command() {
        return JsonCommand.fromExistingCommand(1L, "{}", JsonParser.parseString("{}"), new FromJsonHelper(),
                "EXCESS_REFUND", 2L, null, null, null, null, null, null, null, null, null, null);
    }

    private static void setTenantContext() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 11, 24))));
    }
}
