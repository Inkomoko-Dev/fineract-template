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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists a failure after the failed loan transaction has ended. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleFailureService {

    private final BulkRescheduleExecutionRepository executionRepository;
    private final BulkRescheduleResultRepository resultRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final Long resultId, final Long executionId, final Long loanId, final Exception failure) {
        final BulkRescheduleResult result = resultRepository.findById(resultId).orElse(null);
        if (result == null || !executionId.equals(result.getExecution().getId()) || !loanId.equals(result.getLoanId())) {
            log.error("Could not persist failure for execution {} loan {}", executionId, loanId, failure);
            return;
        }
        result.setStatus(BulkRescheduleResultStatus.FAILED);
        result.setErrorMessage(normalize(failure));
        resultRepository.save(result);
        executionRepository.findById(executionId).ifPresent(execution -> {
            final int failures = execution.getTotalExecutionFailed() == null ? 0 : execution.getTotalExecutionFailed();
            execution.setTotalExecutionFailed(failures + 1);
            executionRepository.save(execution);
        });
    }

    private String normalize(final Exception failure) {
        final String message = failure.getMessage();
        return message == null || message.trim().isEmpty() ? failure.getClass().getSimpleName() : message;
    }
}
