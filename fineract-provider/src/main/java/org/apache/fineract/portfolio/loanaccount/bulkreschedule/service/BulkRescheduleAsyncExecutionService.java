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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.springframework.stereotype.Service;

/** Restores request context on the async thread before invoking tenant-aware loan services. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleAsyncExecutionService {

    private final BulkRescheduleExecutionService executionService;
    private ExecutorService executor;

    @PostConstruct
    void initialize() {
        executor = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void submit(final Long executionId, final FineractContext context) {
        try {
            executor.execute(() -> execute(executionId, context));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.error("Bulk reschedule execution queue is full for execution {}", executionId, e);
            executionService.markExecutionFailed(executionId, e);
        }
    }

    private void execute(final Long executionId, final FineractContext context) {
        try {
            ThreadLocalContextUtil.init(context);
            executionService.executeReschedule(executionId);
        } catch (Exception e) {
            log.error("Background bulk reschedule {} failed", executionId, e);
            executionService.markExecutionFailed(executionId, e);
        } finally {
            ThreadLocalContextUtil.clear();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
