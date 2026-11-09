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
package org.apache.fineract.portfolio.creditdecision.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Starts the automated credit decision for an application after the transaction that accepted its review has committed,
 * on a worker thread so that waiting for the decision engine never delays the person who accepted the review. The tenant
 * and the user are carried over to the worker thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditDecisionAutoStart {

    private final CreditDecisionLoanService service;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        final Thread thread = new Thread(runnable, "credit-decision-auto-start");
        thread.setDaemon(true);
        return thread;
    });

    /** Runs once the surrounding transaction has committed, or at once when there is none. */
    public void schedule(final Long loanId) {
        final FineractContext fineractContext = ThreadLocalContextUtil.getContext();
        final SecurityContext securityContext = SecurityContextHolder.getContext();
        final Runnable task = () -> run(loanId, fineractContext, securityContext);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    executor.execute(task);
                }
            });
        } else {
            executor.execute(task);
        }
    }

    void run(final Long loanId, final FineractContext fineractContext, final SecurityContext securityContext) {
        try {
            ThreadLocalContextUtil.init(fineractContext);
            SecurityContextHolder.setContext(securityContext);
            service.autoStart(loanId);
        } catch (final RuntimeException e) {
            log.warn("Automatic credit decision for loan {} did not start: {}", loanId, e.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
            ThreadLocalContextUtil.clear();
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}
