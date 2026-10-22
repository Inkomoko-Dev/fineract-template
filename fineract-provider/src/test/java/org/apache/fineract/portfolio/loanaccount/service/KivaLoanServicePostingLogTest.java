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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.fineract.infrastructure.core.service.IntegrationHttpRetryService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.jobs.service.SchedulerServiceConstants;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.KivaPostingLogger;
import org.apache.fineract.portfolio.loanaccount.domain.KivaPostingLoggerRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KivaLoanServicePostingLogTest {

    private static final String KIVA_LOGIN_ERROR = "{\"error\":\"invalid_client\",\"error_description\":\"Client authentication failed\"}";

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private KivaPostingLoggerRepository kivaPostingLoggerRepository;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private IntegrationHttpRetryService integrationHttpRetryService;
    @Mock
    private Environment env;

    @InjectMocks
    private KivaLoanServiceImpl kivaLoanService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kivaLoanService, "integrationHttpRetryService", integrationHttpRetryService);
        ReflectionTestUtils.setField(kivaLoanService, "env", env);
        ReflectionTestUtils.setField(kivaLoanService, "kivaHttpClient", new OkHttpClient());
        given(env.getProperty(anyString())).willReturn("https://kiva.test/oauth/token");
        AppUser systemUser = mock(AppUser.class);
        lenient().when(systemUser.getId()).thenReturn(1L);
        given(context.authenticatedUser()).willReturn(systemUser);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearJobTrigger();
    }

    @Test
    void kivaLoginFailureLogsEveryDueLoanAsNotPostedWithTheManualRunner() throws Exception {
        ThreadLocalContextUtil.setJobTrigger(SchedulerServiceConstants.TRIGGER_TYPE_APPLICATION, 42L);
        Loan first = loan(101L, "000000101", 11L);
        Loan second = loan(102L, "000000102", 12L);
        given(loanRepository.findLoanAccountsToBePostedToKiva()).willReturn(List.of(first, second));
        given(integrationHttpRetryService.execute(anyString(), anyString(), any(OkHttpClient.class), any(Request.class)))
                .willAnswer(invocation -> response(invocation.getArgument(3), 401, KIVA_LOGIN_ERROR));

        assertThatThrownBy(() -> kivaLoanService.postLoanAccountsToKiva()).isInstanceOf(RuntimeException.class);

        ArgumentCaptor<KivaPostingLogger> logged = ArgumentCaptor.forClass(KivaPostingLogger.class);
        verify(kivaPostingLoggerRepository, times(2)).saveAndFlush(logged.capture());
        assertThat(logged.getAllValues()).extracting(KivaPostingLogger::getLoanAccountNo).containsExactly("000000101", "000000102");
        assertThat(logged.getAllValues()).allSatisfy(row -> {
            assertThat(row.getHasPassed()).isFalse();
            assertThat(row.getFailureStage()).isEqualTo("AUTHENTICATION");
            assertThat(row.getFailureCategory()).isEqualTo("KIVA_AUTHENTICATION_FAILED");
            assertThat(row.getErrorLogs()).isEqualTo(KIVA_LOGIN_ERROR);
            assertThat(row.getTriggerType()).isEqualTo(SchedulerServiceConstants.TRIGGER_TYPE_APPLICATION);
            assertThat(row.getTriggeredByUserId()).isEqualTo(42L);
        });
        verify(integrationHttpRetryService, times(1)).execute(anyString(), anyString(), any(OkHttpClient.class), any(Request.class));
    }

    @Test
    void kivaLoginFailureWithNothingDueStillFailsTheJobWithoutLogRows() throws Exception {
        given(loanRepository.findLoanAccountsToBePostedToKiva()).willReturn(List.of());
        given(integrationHttpRetryService.execute(anyString(), anyString(), any(OkHttpClient.class), any(Request.class)))
                .willAnswer(invocation -> response(invocation.getArgument(3), 500, ""));

        assertThatThrownBy(() -> kivaLoanService.postLoanAccountsToKiva()).isInstanceOf(RuntimeException.class);

        verify(kivaPostingLoggerRepository, times(0)).saveAndFlush(any(KivaPostingLogger.class));
    }

    private static Loan loan(Long id, String accountNo, Long clientId) {
        Loan loan = mock(Loan.class);
        given(loan.getId()).willReturn(id);
        given(loan.getAccountNumber()).willReturn(accountNo);
        given(loan.getClientId()).willReturn(clientId);
        return loan;
    }

    private static Response response(Request request, int code, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("status " + code)
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }
}
