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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ApplicationContext;

/**
 * Reproduction / investigation test for CGLT-181: the APPLY_CHARGE_TO_OVERDUE_LOAN_INSTALLMENT cron job appears to hang
 * ("logs stuck at an old date / cron never finishes").
 *
 * The test drives {@link LoanSchedularServiceImpl#applyChargeForOverdueLoans(Map)} with a stubbed read service that
 * implements the same key-set pagination contract as the production SQL ({@code ml.id > ?} ordered ascending, limited by
 * page size). Each overdue loan should be handed to a poster exactly once and the job must terminate.
 */
class LoanSchedularServiceOverdueCronTest {

    private static final long PENALTY_WAIT = 5L;

    private ConfigurationDomainService configurationDomainService;
    private LoanReadPlatformService loanReadPlatformService;
    private LoanWritePlatformService loanWritePlatformService;
    private ApplicationContext applicationContext;

    /** collects every loan id handed to a poster (with duplicates preserved). */
    private final List<Long> processedLoanIds = new CopyOnWriteArrayList<>();

    /**
     * A poster that records the loan ids it was asked to process instead of hitting the database. Subclasses the real
     * poster so that the production cast in {@link LoanSchedularServiceImpl} keeps working.
     */
    private final class RecordingPoster extends ApplyChargeToOverdueLoansPoster {

        private List<Long> loanIds = Collections.emptyList();

        @Override
        public void setLoanIds(final List<Long> loanIds) {
            this.loanIds = loanIds;
        }

        @Override
        public void setLoanWritePlatformService(final LoanWritePlatformService service) {
            // no-op for the test
        }

        @Override
        public void setLoanReadPlatformService(final LoanReadPlatformService service) {
            // no-op for the test
        }

        @Override
        public void setConfigurationDomainService(final ConfigurationDomainService service) {
            // no-op for the test
        }

        @Override
        public void setContext(final org.apache.fineract.infrastructure.core.domain.FineractContext context) {
            // no-op for the test
        }

        @Override
        public Void call() {
            processedLoanIds.addAll(this.loanIds);
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        final LocalDate today = LocalDate.of(2026, 7, 9);
        businessDates.put(BusinessDateType.BUSINESS_DATE, today);
        businessDates.put(BusinessDateType.COB_DATE, today.minusDays(1));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        this.configurationDomainService = mock(ConfigurationDomainService.class);
        this.loanReadPlatformService = mock(LoanReadPlatformService.class);
        this.loanWritePlatformService = mock(LoanWritePlatformService.class);
        this.applicationContext = mock(ApplicationContext.class);

        when(this.configurationDomainService.retrievePenaltyWaitPeriod()).thenReturn(PENALTY_WAIT);
        when(this.applicationContext.getBean("applyChargeToOverdueLoansPoster")).thenAnswer(invocation -> new RecordingPoster());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    private LoanSchedularServiceImpl newService() {
        return new LoanSchedularServiceImpl(configurationDomainService, loanReadPlatformService, loanWritePlatformService,
                mock(org.apache.fineract.organisation.office.service.OfficeReadPlatformService.class), applicationContext,
                mock(org.apache.fineract.portfolio.loanaccount.domain.LoanRepository.class),
                mock(org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentReminderSettingsRepository.class),
                mock(org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueReminderSettingsRepository.class),
                mock(org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentReminderRepository.class),
                mock(org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueReminderRepository.class),
                mock(org.apache.fineract.infrastructure.security.service.PlatformSecurityContext.class),
                mock(org.apache.fineract.infrastructure.core.serialization.FromJsonHelper.class));
    }

    /**
     * Stub the production key-set pagination: given a max id, return the next {@code pageSize} overdue loan ids strictly
     * greater than that id, in ascending order.
     */
    private void stubOverdueLoans(final List<Long> allOverdueIds) {
        final List<Long> sorted = new ArrayList<>(allOverdueIds);
        Collections.sort(sorted);
        when(this.loanReadPlatformService.retrieveAllLoanIdsWithOverdueInstallments(eq(PENALTY_WAIT), anyBoolean(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    final Long maxId = invocation.getArgument(2);
                    final int pageSize = invocation.getArgument(3);
                    final long floor = maxId == null ? 0L : maxId;
                    return sorted.stream().filter(id -> id > floor).limit(pageSize).collect(Collectors.toList());
                });
    }

    private Map<String, String> jobParameters(final int threadPoolSize, final int batchSize) {
        final Map<String, String> params = new HashMap<>();
        params.put("thread-pool-size", Integer.toString(threadPoolSize));
        params.put("batch-size", Integer.toString(batchSize));
        return params;
    }

    @Test
    @Timeout(60)
    void cronFinishesAndProcessesEveryOverdueLoanExactlyOnce() throws JobExecutionException {
        final List<Long> overdueIds = LongStream.rangeClosed(1L, 250L).boxed().collect(Collectors.toList());
        stubOverdueLoans(overdueIds);

        final LoanSchedularServiceImpl service = newService();
        service.applyChargeForOverdueLoans(jobParameters(4, 10));

        final Set<Long> distinctProcessed = new HashSet<>(processedLoanIds);
        assertEquals(new HashSet<>(overdueIds), distinctProcessed, "every overdue loan should be processed");
        assertEquals(overdueIds.size(), processedLoanIds.size(),
                "no loan should be processed more than once (duplicates => wasted/looping work). processed=" + processedLoanIds.size());
    }

    @Test
    @Timeout(60)
    void cronFinishesWhenPortfolioSmallerThanOnePage() throws JobExecutionException {
        final List<Long> overdueIds = LongStream.rangeClosed(1L, 7L).boxed().collect(Collectors.toList());
        stubOverdueLoans(overdueIds);

        final LoanSchedularServiceImpl service = newService();
        service.applyChargeForOverdueLoans(jobParameters(4, 10));

        assertEquals(new HashSet<>(overdueIds), new HashSet<>(processedLoanIds));
        assertEquals(overdueIds.size(), processedLoanIds.size(), "small portfolio must not reprocess loans");
    }

    @Test
    @Timeout(60)
    void cronTerminatesWhenNoOverdueLoans() throws JobExecutionException {
        stubOverdueLoans(Collections.emptyList());

        final LoanSchedularServiceImpl service = newService();
        service.applyChargeForOverdueLoans(jobParameters(4, 10));

        assertTrue(processedLoanIds.isEmpty(), "no loans should be processed when none are overdue");
    }
}
